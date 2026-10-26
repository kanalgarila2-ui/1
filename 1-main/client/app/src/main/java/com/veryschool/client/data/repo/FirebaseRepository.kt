package com.veryschool.client.data.repo

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.messaging.FirebaseMessaging
import com.veryschool.client.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "FirebaseRepo"

object Col {
    const val USERS            = "users"
    const val CHATS            = "chats"
    const val MESSAGES         = "messages"
    const val DELETED_MESSAGES = "deleted_messages"
    const val STARRED          = "starred_messages"   // ФИЧА #3
    const val LOGS             = "logs"
    const val PASSPHRASES      = "passphrases"
    const val COUNTERS         = "counters"
    const val TYPING           = "typing"
}

class FirebaseRepository {
    private val auth  = FirebaseAuth.getInstance()
    private val db    = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val currentUid: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    fun cleanup() { scope.cancel() }

    // ── Числовой ID ───────────────────────────────────────────────────────────

    private suspend fun generateNumericId(): Long = try {
        val ref = db.collection(Col.COUNTERS).document("user_ids")
        var result = 100000L
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val next = (snap.getLong("next") ?: 100000L) + 1L
            tx.set(ref, mapOf("next" to next), SetOptions.merge())
            result = next
        }.await()
        result
    } catch (_: Exception) { (100000L..999999L).random() }

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun register(email: String, password: String, username: String, displayName: String, passphrase: String): AuthResult {
        return try {
            val validPhrases = getValidPassphrases()
            if (passphrase !in validPhrases) return AuthResult(false, error = "Неверная ключевая фраза")
            val existing = db.collection(Col.USERS).whereEqualTo("username", username.lowercase().trim()).get().await()
            if (!existing.isEmpty) return AuthResult(false, error = "Имя пользователя уже занято")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return AuthResult(false, error = "Ошибка создания аккаунта")
            val numId = generateNumericId()
            var done = false; var retries = 5
            while (!done && retries > 0) {
                try {
                    db.collection(Col.USERS).document(uid).set(
                        UserModel(id = uid, numericId = numId,
                            username = username.lowercase().trim(),
                            displayName = displayName.trim(),
                            passphrase = passphrase,
                            nameHistory = listOf(displayName.trim()))
                    ).await()
                    done = true
                } catch (e: Exception) {
                    retries--
                    if (retries > 0) { delay(1000); try { auth.currentUser?.getIdToken(true)?.await() } catch (_: Exception) {} }
                    else { try { result.user?.delete()?.await() } catch (_: Exception) {}; return AuthResult(false, error = "Не удалось создать профиль. Попробуйте ещё раз.") }
                }
            }
            try { createBotChat(uid, displayName.trim()) } catch (_: Exception) {}
            try { updateFcmToken(uid) } catch (_: Exception) {}
            try { writeLog(uid, "REGISTER", "", "@$username") } catch (_: Exception) {}
            val finalUser = db.collection(Col.USERS).document(uid).get().await().toObject<UserModel>() ?: return AuthResult(false, error = "Ошибка чтения профиля")
            AuthResult(true, finalUser.copy(id = uid))
        } catch (e: Exception) { Log.e(TAG, "register: ${e.message}"); AuthResult(false, error = mapAuthError(e.message ?: "")) }
    }

    suspend fun login(email: String, password: String, passphrase: String): AuthResult {
        return try {
            val validPhrases = getValidPassphrases()
            if (passphrase !in validPhrases) return AuthResult(false, error = "Неверная ключевая фраза")
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return AuthResult(false, error = "Ошибка входа")
            val user = db.collection(Col.USERS).document(uid).get().await().toObject<UserModel>() ?: return AuthResult(false, error = "Профиль не найден")
            if (user.isBanned) return AuthResult(false, error = "Аккаунт заблокирован: ${user.banReason}")
            db.collection(Col.USERS).document(uid).update(mapOf("online" to true, "lastSeen" to FieldValue.serverTimestamp())).await()
            try { updateFcmToken(uid) } catch (_: Exception) {}
            try { writeLog(uid, "LOGIN", "", "@${user.username}") } catch (_: Exception) {}
            AuthResult(true, user.copy(id = uid))
        } catch (e: Exception) { AuthResult(false, error = mapAuthError(e.message ?: "")) }
    }

    suspend fun logout() {
        currentUid?.let { uid ->
            try { db.collection(Col.USERS).document(uid).update("online", false).await() } catch (_: Exception) {}
            try { clearTypingStatus(uid) } catch (_: Exception) {}
            try { writeLog(uid, "LOGOUT", "", "") } catch (_: Exception) {}
        }
        auth.signOut()
    }

    private suspend fun getValidPassphrases(): List<String> = try {
        @Suppress("UNCHECKED_CAST")
        (db.collection(Col.PASSPHRASES).document("active").get().await().get("phrases") as? List<String>) ?: listOf("22sch")
    } catch (_: Exception) { listOf("22sch") }

    private suspend fun updateFcmToken(uid: String) {
        try { val token = FirebaseMessaging.getInstance().token.await(); db.collection(Col.USERS).document(uid).update("fcmToken", token).await() } catch (_: Exception) {}
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    fun getUserFlow(userId: String): Flow<UserModel?> = callbackFlow {
        val reg = db.collection(Col.USERS).document(userId).addSnapshotListener { snap, _ -> trySend(snap?.toObject<UserModel>()?.copy(id = snap.id)) }
        awaitClose { reg.remove() }
    }

    fun getAllUsersFlow(): Flow<List<UserModel>> = callbackFlow {
        val reg = db.collection(Col.USERS).orderBy("displayName").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toObject<UserModel>()?.copy(id = it.id) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    suspend fun findUserByNumericIdOrUsername(query: String): UserModel? {
        return try {
            val numId = query.toLongOrNull()
            if (numId != null) {
                val res = db.collection(Col.USERS).whereEqualTo("numericId", numId).get().await()
                if (!res.isEmpty) return res.documents[0].toObject<UserModel>()?.copy(id = res.documents[0].id)
            }
            val res = db.collection(Col.USERS).whereEqualTo("username", query.lowercase().trim()).get().await()
            if (!res.isEmpty) res.documents[0].toObject<UserModel>()?.copy(id = res.documents[0].id) else null
        } catch (_: Exception) { null }
    }

    suspend fun updateProfileMap(uid: String, updates: Map<String, Any>): Boolean = try {
        db.collection(Col.USERS).document(uid).update(updates).await(); true
    } catch (e: Exception) { Log.e(TAG, "updateProfile: ${e.message}"); false }

    // ФИЧА #18: история имён — обновить displayName и добавить в nameHistory
    suspend fun updateDisplayName(uid: String, newName: String, currentHistory: List<String>) {
        val hist = (currentHistory + newName).takeLast(10) // хранить последние 10
        db.collection(Col.USERS).document(uid).update(mapOf(
            "displayName" to newName,
            "nameHistory" to hist
        )).await()
    }

    // ФИЧА #8: блокировка пользователя (не бан — только скрытие)
    suspend fun blockUser(myUid: String, targetUid: String) {
        db.collection(Col.USERS).document(myUid).update("blockedUsers", FieldValue.arrayUnion(targetUid)).await()
    }
    suspend fun unblockUser(myUid: String, targetUid: String) {
        db.collection(Col.USERS).document(myUid).update("blockedUsers", FieldValue.arrayRemove(targetUid)).await()
    }

    // ФИЧА #20: верификация
    suspend fun setVerified(adminId: String, targetUid: String, verified: Boolean) {
        db.collection(Col.USERS).document(targetUid).update("isVerified", verified).await()
        writeLog(adminId, if (verified) "VERIFY" else "UNVERIFY", targetUid, "")
    }

    // ФИЧА #17: мьют чата
    suspend fun muteChat(uid: String, chatId: String) {
        db.collection(Col.USERS).document(uid).update("mutedChats", FieldValue.arrayUnion(chatId)).await()
    }
    suspend fun unmuteChat(uid: String, chatId: String) {
        db.collection(Col.USERS).document(uid).update("mutedChats", FieldValue.arrayRemove(chatId)).await()
    }

    // ФИЧА #16: DND
    suspend fun setDnd(uid: String, enabled: Boolean, from: String = "23:00", to: String = "07:00") {
        db.collection(Col.USERS).document(uid).update(mapOf("dndEnabled" to enabled, "dndFrom" to from, "dndTo" to to)).await()
    }

    // ── Chats ─────────────────────────────────────────────────────────────────

    fun getChatsFlow(userId: String): Flow<List<ChatModel>> = callbackFlow {
        val reg = db.collection(Col.CHATS).whereArrayContains("members", userId)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getChats: ${err.message}"); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.toObject<ChatModel>()?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun createDm(myUid: String, otherUid: String, otherDisplayName: String): String {
        if (myUid == otherUid) throw IllegalArgumentException("Нельзя создать чат с собой")
        val existing = db.collection(Col.CHATS).whereEqualTo("isDm", true).whereArrayContains("members", myUid).get().await()
        val found = existing.documents.firstOrNull { doc -> val m = doc.get("members") as? List<*>; m != null && otherUid in m && m.size == 2 }
        if (found != null) return found.id
        val chatId = UUID.randomUUID().toString()
        db.collection(Col.CHATS).document(chatId).set(ChatModel(id = chatId, name = otherDisplayName, isDm = true, members = listOf(myUid, otherUid), createdBy = myUid)).await()
        return chatId
    }

    suspend fun createGroup(name: String, memberIds: List<String>, creatorId: String, description: String = ""): String {
        val chatId = UUID.randomUUID().toString()
        val inviteCode = UUID.randomUUID().toString().take(8).uppercase()
        db.collection(Col.CHATS).document(chatId).set(
            ChatModel(id = chatId, name = name, isGroup = true,
                members = (memberIds + creatorId).distinct(),
                adminIds = listOf(creatorId), createdBy = creatorId,
                description = description, inviteCode = inviteCode)
        ).await()
        return chatId
    }

    // ФИЧА #12: join by invite code
    suspend fun joinByInviteCode(uid: String, code: String): String? {
        val snap = db.collection(Col.CHATS).whereEqualTo("inviteCode", code.uppercase()).get().await()
        val doc = snap.documents.firstOrNull() ?: return null
        val chatId = doc.id
        if (uid !in (doc.get("members") as? List<*> ?: emptyList<String>())) {
            db.collection(Col.CHATS).document(chatId).update("members", FieldValue.arrayUnion(uid)).await()
        }
        return chatId
    }

    // ФИЧА #13: покинуть группу
    suspend fun leaveGroup(chatId: String, uid: String) {
        db.collection(Col.CHATS).document(chatId).update("members", FieldValue.arrayRemove(uid)).await()
    }

    // ФИЧА #11: прикреплённые ссылки
    suspend fun addPinnedLink(chatId: String, link: String) {
        db.collection(Col.CHATS).document(chatId).update("pinnedLinks", FieldValue.arrayUnion(link)).await()
    }
    suspend fun removePinnedLink(chatId: String, link: String) {
        db.collection(Col.CHATS).document(chatId).update("pinnedLinks", FieldValue.arrayRemove(link)).await()
    }

    suspend fun archiveChat(chatId: String, userId: String) {
        db.collection(Col.CHATS).document(chatId).update("archivedBy", FieldValue.arrayUnion(userId)).await()
    }
    suspend fun unarchiveChat(chatId: String, userId: String) {
        db.collection(Col.CHATS).document(chatId).update("archivedBy", FieldValue.arrayRemove(userId)).await()
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessagesFlow(chatId: String, limit: Long = 80): Flow<List<MessageModel>> = callbackFlow {
        val reg = db.collection(Col.MESSAGES).document(chatId).collection("msgs")
            .orderBy("timestamp", Query.Direction.ASCENDING).limitToLast(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getMessages: ${err.message}"); return@addSnapshotListener }
                val now = System.currentTimeMillis()
                trySend(snap?.documents?.mapNotNull { it.toObject<MessageModel>()?.copy(id = it.id) }
                    ?.filter { !it.isDeleted && (it.expiresAt == null || it.expiresAt.toDate().time > now) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun getDeletedMessagesFlow(): Flow<List<String>> = callbackFlow {
        val reg = db.collection(Col.DELETED_MESSAGES).addSnapshotListener { snap, _ -> trySend(snap?.documents?.map { it.id } ?: emptyList()) }
        awaitClose { reg.remove() }
    }

    // ФИЧА #4: медиа-галерея чата
    suspend fun getMediaMessages(chatId: String): List<MessageModel> = try {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs")
            .whereNotEqualTo("imageBase64", "").orderBy("imageBase64").orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100).get().await()
            .documents.mapNotNull { it.toObject<MessageModel>()?.copy(id = it.id) }
            .filter { it.imageBase64.isNotEmpty() || it.imageUrl.isNotEmpty() }
    } catch (_: Exception) { emptyList() }

    suspend fun sendMessage(
        chatId: String, senderId: String, senderName: String, senderAvatarUrl: String,
        text: String, imageUrl: String = "", imageBase64: String = "",
        voiceBase64: String = "", voiceDurationSec: Int = 0,
        gifUrl: String = "",
        replyToId: String = "", replyToText: String = "",
        isPoll: Boolean = false, pollQuestion: String = "", pollOptions: List<String> = emptyList(),
        expirySec: Int? = null
    ): String {
        val msgId = UUID.randomUUID().toString()
        val expiresAt = expirySec?.let { com.google.firebase.Timestamp(System.currentTimeMillis() / 1000 + it, 0) }
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(msgId).set(
            MessageModel(id = msgId, chatId = chatId, senderId = senderId, senderName = senderName,
                senderAvatarUrl = senderAvatarUrl, text = text, imageUrl = imageUrl, imageBase64 = imageBase64,
                voiceBase64 = voiceBase64, voiceDurationSec = voiceDurationSec, gifUrl = gifUrl,
                replyToId = replyToId, replyToText = replyToText,
                isPoll = isPoll, pollQuestion = pollQuestion, pollOptions = pollOptions,
                expiresAt = expiresAt, clientTimestamp = System.currentTimeMillis())
        ).await()
        // Инкрементируем счётчик сообщений (ФИЧА #14) и обновляем lastMessage
        try {
            db.collection(Col.CHATS).document(chatId).update(mapOf(
                "lastMessage" to when { isPoll -> "📊 $pollQuestion"; voiceBase64.isNotEmpty() -> "🎤 Голосовое"; gifUrl.isNotEmpty() -> "🎬 GIF"; text.isNotEmpty() -> text; else -> "📷 Фото" },
                "lastMessageTime" to FieldValue.serverTimestamp(),
                "lastMessageSenderId" to senderId,
                "messageCount" to FieldValue.increment(1)
            )).await()
        } catch (_: Exception) {
            db.collection(Col.CHATS).document(chatId).set(mapOf(
                "lastMessage" to text.ifEmpty { "📷 Фото" },
                "lastMessageTime" to FieldValue.serverTimestamp(),
                "lastMessageSenderId" to senderId,
                "messageCount" to 1
            ), SetOptions.merge()).await()
        }
        // ФИЧА #19: счётчик отправленных
        try { db.collection(Col.USERS).document(senderId).update("msgSentCount", FieldValue.increment(1)).await() } catch (_: Exception) {}
        return msgId
    }

    // ФИЧА #1: редактирование сообщения
    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update(mapOf("text" to newText, "isEdited" to true, "editedAt" to FieldValue.serverTimestamp())).await()
    }

    // ФИЧА #3: звёздочка (избранное)
    suspend fun starMessage(chatId: String, messageId: String, userId: String, msg: MessageModel) {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update("starredBy", FieldValue.arrayUnion(userId)).await()
        // Дублируем в starred_messages для быстрого доступа
        db.collection(Col.STARRED).document("${userId}_${messageId}").set(
            StarredMessage(userId = userId, messageId = messageId, chatId = chatId,
                text = msg.text, senderName = msg.senderName)
        ).await()
    }
    suspend fun unstarMessage(chatId: String, messageId: String, userId: String) {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update("starredBy", FieldValue.arrayRemove(userId)).await()
        db.collection(Col.STARRED).document("${userId}_${messageId}").delete().await()
    }
    fun getStarredMessagesFlow(userId: String): Flow<List<StarredMessage>> = callbackFlow {
        val reg = db.collection(Col.STARRED).whereEqualTo("userId", userId)
            .orderBy("starredAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toObject<StarredMessage>()?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ФИЧА #15: кто прочитал
    suspend fun getReadByUsers(chatId: String, messageId: String): List<String> = try {
        val snap = db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId).get().await()
        @Suppress("UNCHECKED_CAST")
        (snap.get("readBy") as? List<String>) ?: emptyList()
    } catch (_: Exception) { emptyList() }

    suspend fun votePoll(chatId: String, messageId: String, userId: String, optionIndex: Int) {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update("pollVotes.$userId", optionIndex.toString()).await()
    }

    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String) {
        val ref = db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
        db.runTransaction { tx ->
            @Suppress("UNCHECKED_CAST")
            val reactions = (tx.get(ref).get("reactions") as? Map<String, List<String>>)?.toMutableMap() ?: mutableMapOf()
            val current = reactions[emoji]?.toMutableList() ?: mutableListOf()
            if (userId in current) current.remove(userId) else current.add(userId)
            if (current.isEmpty()) reactions.remove(emoji) else reactions[emoji] = current
            tx.update(ref, "reactions", reactions)
        }.await()
    }

    suspend fun markAsRead(chatId: String, messageId: String, userId: String) {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update("readBy", FieldValue.arrayUnion(userId)).await()
    }

    suspend fun deleteMessage(chatId: String, messageId: String, deletedBy: String) {
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update(mapOf("isDeleted" to true, "deletedBy" to deletedBy, "text" to "")).await()
        db.collection(Col.DELETED_MESSAGES).document(messageId)
            .set(DeletedMessageModel(id = messageId, chatId = chatId, deletedBy = deletedBy)).await()
    }
    suspend fun deleteExpiredMessage(chatId: String, messageId: String) {
        try { db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update(mapOf("isDeleted" to true, "text" to "", "deletedBy" to "SYSTEM")).await() } catch (_: Exception) {}
    }

    suspend fun pinMessage(chatId: String, messageId: String, messageText: String) {
        db.collection(Col.CHATS).document(chatId).update("pinnedMessageId", messageId).await()
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId).update("isPinned", true).await()
    }

    // ── Typing ────────────────────────────────────────────────────────────────

    suspend fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        try {
            val ref = db.collection(Col.TYPING).document(chatId)
            if (isTyping) ref.set(mapOf(userId to System.currentTimeMillis()), SetOptions.merge()).await()
            else ref.update(userId, FieldValue.delete()).await()
        } catch (_: Exception) {}
    }
    private suspend fun clearTypingStatus(userId: String) {
        try {
            val chats = db.collection(Col.CHATS).whereArrayContains("members", userId).get().await()
            chats.documents.forEach { doc ->
                try { db.collection(Col.TYPING).document(doc.id).update(userId, FieldValue.delete()).await() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
    fun getTypingFlow(chatId: String): Flow<List<String>> = callbackFlow {
        val reg = db.collection(Col.TYPING).document(chatId).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) { trySend(emptyList()); return@addSnapshotListener }
            val now = System.currentTimeMillis()
            trySend(snap.data?.entries?.filter { (_, v) -> (v as? Long)?.let { now - it < 5000 } == true }?.map { it.key } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    // ── Logs & Admin ──────────────────────────────────────────────────────────

    suspend fun writeLog(userId: String, action: String, targetId: String, details: String) {
        try { db.collection(Col.LOGS).add(LogModel(action = action, userId = userId, targetId = targetId, details = details)).await() } catch (_: Exception) {}
    }

    suspend fun banUser(adminId: String, targetId: String, reason: String) {
        db.collection(Col.USERS).document(targetId).update(mapOf("isBanned" to true, "banReason" to reason)).await()
        sendBotMessage(targetId, "🚫 Вы заблокированы. Причина: $reason"); writeLog(adminId, "BAN", targetId, reason)
    }
    suspend fun unbanUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update(mapOf("isBanned" to false, "banReason" to "")).await()
        sendBotMessage(targetId, "✅ Вы разблокированы."); writeLog(adminId, "UNBAN", targetId, "")
    }
    suspend fun freezeUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update("isFrozen", true).await()
        sendBotMessage(targetId, "❄️ Аккаунт заморожен."); writeLog(adminId, "FREEZE", targetId, "")
    }
    suspend fun unfreezeUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update("isFrozen", false).await()
        sendBotMessage(targetId, "✅ Заморозка снята."); writeLog(adminId, "UNFREEZE", targetId, "")
    }
    suspend fun adminDeleteMessage(adminId: String, chatId: String, messageId: String) {
        deleteMessage(chatId, messageId, adminId); writeLog(adminId, "DELETE_MSG", messageId, "chatId=$chatId")
    }
    suspend fun broadcastBotMessage(adminId: String, text: String) {
        val uids = db.collection(Col.USERS).get().await().documents.map { it.id }.filter { it != "BOT" }
        uids.forEach { sendBotMessage(it, text) }
        writeLog(adminId, "BOT_BROADCAST", "", "To ${uids.size}: ${text.take(60)}")
    }
    suspend fun sendBotMessage(targetUserId: String, text: String) {
        try { sendMessage("bot_$targetUserId", "BOT", "VerySchool BOT", "", text) }
        catch (e: Exception) { Log.e(TAG, "sendBotMessage: ${e.message}") }
    }
    private suspend fun createBotChat(userId: String, displayName: String) {
        val chatId = "bot_$userId"
        db.collection(Col.CHATS).document(chatId).set(ChatModel(id = chatId, name = "VerySchool BOT", isBot = true, members = listOf(userId, "BOT"), createdBy = "BOT")).await()
        sendMessage(chatId, "BOT", "VerySchool BOT", "", "👋 Привет, $displayName! Добро пожаловать в VerySchool.\n\nТвой числовой ID генерируется автоматически. Используй его чтобы другие могли найти тебя.")
    }
    suspend fun getPassphrases(): List<String> = getValidPassphrases()
    suspend fun updatePassphrases(adminId: String, phrases: List<String>) {
        db.collection(Col.PASSPHRASES).document("active").set(mapOf("phrases" to phrases)).await()
        writeLog(adminId, "UPDATE_PASSPHRASES", "", "${phrases.size}")
    }

    private fun mapAuthError(msg: String): String = when {
        "email" in msg && "already" in msg -> "Email уже зарегистрирован"
        "weak-password" in msg -> "Пароль минимум 6 символов"
        "network" in msg -> "Нет интернета"
        "INVALID_LOGIN" in msg || "credential" in msg -> "Неверный email или пароль"
        "user-not-found" in msg -> "Пользователь не найден"
        "too-many-requests" in msg -> "Слишком много попыток. Подождите немного"
        else -> msg.take(100)
    }
}
