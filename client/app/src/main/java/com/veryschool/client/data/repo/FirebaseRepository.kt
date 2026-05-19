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
    const val LOGS             = "logs"
    const val PASSPHRASES      = "passphrases"
    const val COUNTERS         = "counters"   // для числовых ID
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

    suspend fun register(
        email: String, password: String,
        username: String, displayName: String, passphrase: String
    ): AuthResult {
        return try {
            // 1. Фраза входа
            val validPhrases = getValidPassphrases()
            if (passphrase !in validPhrases)
                return AuthResult(false, error = "Неверная ключевая фраза")

            // 2. Уникальность username
            val existing = db.collection(Col.USERS)
                .whereEqualTo("username", username.lowercase().trim()).get().await()
            if (!existing.isEmpty) return AuthResult(false, error = "Имя пользователя уже занято")

            // 3. Firebase Auth
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return AuthResult(false, error = "Ошибка создания аккаунта")

            // 4. Числовой ID
            val numId = generateNumericId()

            // 5. Запись в Firestore с ретраями
            var done = false; var retries = 5
            while (!done && retries > 0) {
                try {
                    db.collection(Col.USERS).document(uid).set(
                        UserModel(
                            id = uid,
                            numericId = numId,
                            username = username.lowercase().trim(),
                            displayName = displayName.trim(),
                            passphrase = passphrase
                        )
                    ).await()
                    done = true
                } catch (e: Exception) {
                    retries--
                    if (retries > 0) {
                        delay(1000)
                        try { auth.currentUser?.getIdToken(true)?.await() } catch (_: Exception) {}
                    } else {
                        try { result.user?.delete()?.await() } catch (_: Exception) {}
                        return AuthResult(false, error = "Не удалось создать профиль. Попробуйте ещё раз.")
                    }
                }
            }

            try { createBotChat(uid, displayName.trim()) } catch (_: Exception) {}
            try { updateFcmToken(uid) } catch (_: Exception) {}
            try { writeLog(uid, "REGISTER", "", "@$username") } catch (_: Exception) {}

            val finalUser = db.collection(Col.USERS).document(uid).get().await()
                .toObject<UserModel>() ?: return AuthResult(false, error = "Ошибка чтения профиля")
            AuthResult(true, finalUser.copy(id = uid))
        } catch (e: Exception) {
            Log.e(TAG, "register: ${e.message}")
            AuthResult(false, error = mapAuthError(e.message ?: ""))
        }
    }

    suspend fun login(email: String, password: String, passphrase: String): AuthResult {
        return try {
            val validPhrases = getValidPassphrases()
            if (passphrase !in validPhrases)
                return AuthResult(false, error = "Неверная ключевая фраза")
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return AuthResult(false, error = "Ошибка входа")
            val user = db.collection(Col.USERS).document(uid).get().await()
                .toObject<UserModel>() ?: return AuthResult(false, error = "Профиль не найден")
            if (user.isBanned) return AuthResult(false, error = "Аккаунт заблокирован: ${user.banReason}")
            db.collection(Col.USERS).document(uid).update(
                mapOf("online" to true, "lastSeen" to FieldValue.serverTimestamp())
            ).await()
            try { updateFcmToken(uid) } catch (_: Exception) {}
            try { writeLog(uid, "LOGIN", "", "@${user.username}") } catch (_: Exception) {}
            AuthResult(true, user.copy(id = uid))
        } catch (e: Exception) {
            AuthResult(false, error = mapAuthError(e.message ?: ""))
        }
    }

    suspend fun logout() {
        currentUid?.let { uid ->
            try { db.collection(Col.USERS).document(uid).update("online", false).await() } catch (_: Exception) {}
            try { writeLog(uid, "LOGOUT", "", "") } catch (_: Exception) {}
        }
        auth.signOut()
    }

    private suspend fun getValidPassphrases(): List<String> = try {
        @Suppress("UNCHECKED_CAST")
        (db.collection(Col.PASSPHRASES).document("active").get().await().get("phrases") as? List<String>)
            ?: listOf("22sch")
    } catch (_: Exception) { listOf("22sch") }

    private suspend fun updateFcmToken(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            db.collection(Col.USERS).document(uid).update("fcmToken", token).await()
        } catch (_: Exception) {}
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    fun getUserFlow(userId: String): Flow<UserModel?> = callbackFlow {
        val reg = db.collection(Col.USERS).document(userId).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject<UserModel>()?.copy(id = snap.id))
        }
        awaitClose { reg.remove() }
    }

    fun getAllUsersFlow(): Flow<List<UserModel>> = callbackFlow {
        val reg = db.collection(Col.USERS).orderBy("displayName").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toObject<UserModel>()?.copy(id = it.id) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    // Поиск по числовому ID или юзернейму
    suspend fun findUserByNumericIdOrUsername(query: String): UserModel? = try {
        // Пробуем как число
        val numId = query.toLongOrNull()
        if (numId != null) {
            val res = db.collection(Col.USERS).whereEqualTo("numericId", numId).get().await()
            if (!res.isEmpty) return res.documents[0].toObject<UserModel>()?.copy(id = res.documents[0].id)
        }
        // По юзернейму
        val res = db.collection(Col.USERS).whereEqualTo("username", query.lowercase().trim()).get().await()
        if (!res.isEmpty) res.documents[0].toObject<UserModel>()?.copy(id = res.documents[0].id)
        else null
    } catch (_: Exception) { null }

    suspend fun updateProfileMap(uid: String, updates: Map<String, Any>): Boolean = try {
        db.collection(Col.USERS).document(uid).update(updates).await(); true
    } catch (e: Exception) { Log.e(TAG, "updateProfile: ${e.message}"); false }

    // ── Chats ─────────────────────────────────────────────────────────────────

    fun getChatsFlow(userId: String): Flow<List<ChatModel>> = callbackFlow {
        val reg = db.collection(Col.CHATS)
            .whereArrayContains("members", userId)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getChats: ${err.message}"); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.toObject<ChatModel>()?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // FIX: createDm — правильная проверка существующего ДМ
    suspend fun createDm(myUid: String, otherUid: String, otherDisplayName: String): String {
        // Ищем существующий DM
        val existing = db.collection(Col.CHATS)
            .whereEqualTo("isDm", true)
            .whereArrayContains("members", myUid)
            .get().await()
        val found = existing.documents.firstOrNull { doc ->
            val members = doc.get("members") as? List<*>
            members != null && otherUid in members && members.size == 2
        }
        if (found != null) return found.id

        // Создаём новый
        val chatId = UUID.randomUUID().toString()
        db.collection(Col.CHATS).document(chatId).set(
            ChatModel(
                id = chatId, name = otherDisplayName, isDm = true,
                members = listOf(myUid, otherUid), createdBy = myUid
            )
        ).await()
        Log.d(TAG, "Created DM: $chatId between $myUid and $otherUid")
        return chatId
    }

    suspend fun createGroup(name: String, memberIds: List<String>, creatorId: String): String {
        val chatId = UUID.randomUUID().toString()
        db.collection(Col.CHATS).document(chatId).set(
            ChatModel(
                id = chatId, name = name, isGroup = true,
                members = memberIds + creatorId, adminIds = listOf(creatorId), createdBy = creatorId
            )
        ).await()
        return chatId
    }

    private suspend fun createBotChat(userId: String, displayName: String) {
        val chatId = "bot_$userId"
        db.collection(Col.CHATS).document(chatId).set(
            ChatModel(id = chatId, name = "VerySchool BOT", isBot = true,
                members = listOf(userId, "BOT"), createdBy = "BOT")
        ).await()
        sendMessage(chatId, "BOT", "VerySchool BOT", "",
            "👋 Привет, $displayName! Добро пожаловать в VerySchool.")
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessagesFlow(chatId: String, limit: Long = 50): Flow<List<MessageModel>> = callbackFlow {
        val reg = db.collection(Col.MESSAGES).document(chatId).collection("msgs")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getMessages: ${err.message}"); return@addSnapshotListener }
                trySend(
                    snap?.documents?.mapNotNull { it.toObject<MessageModel>()?.copy(id = it.id) }
                        ?.filter { !it.isDeleted } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun getDeletedMessagesFlow(): Flow<List<String>> = callbackFlow {
        val reg = db.collection(Col.DELETED_MESSAGES).addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.id } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(
        chatId: String, senderId: String, senderName: String, senderAvatarUrl: String,
        text: String, imageUrl: String = "", imageBase64: String = "",
        replyToId: String = "", replyToText: String = "",
        isPoll: Boolean = false, pollQuestion: String = "", pollOptions: List<String> = emptyList(),
        expirySec: Int? = null
    ): String {
        val msgId = UUID.randomUUID().toString()
        val expiresAt = expirySec?.let { com.google.firebase.Timestamp(System.currentTimeMillis() / 1000 + it, 0) }
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(msgId).set(
            MessageModel(
                id = msgId, chatId = chatId, senderId = senderId,
                senderName = senderName, senderAvatarUrl = senderAvatarUrl,
                text = text, imageUrl = imageUrl, imageBase64 = imageBase64,
                replyToId = replyToId, replyToText = replyToText,
                isPoll = isPoll, pollQuestion = pollQuestion, pollOptions = pollOptions,
                expiresAt = expiresAt,
                clientTimestamp = System.currentTimeMillis()
            )
        ).await()
        db.collection(Col.CHATS).document(chatId).update(
            mapOf(
                "lastMessage" to when { isPoll -> "📊 $pollQuestion"; text.isNotEmpty() -> text; else -> "📷 Фото" },
                "lastMessageTime" to FieldValue.serverTimestamp(),
                "lastMessageSenderId" to senderId
            )
        ).await()
        return msgId
    }

    suspend fun votePoll(chatId: String, messageId: String, userId: String, optionIndex: Int) {
        val ref = db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
        ref.update("pollVotes.$userId", optionIndex.toString()).await()
    }

    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String) {
        val ref = db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
        db.runTransaction { tx ->
            @Suppress("UNCHECKED_CAST")
            val reactions = (tx.get(ref).get("reactions") as? Map<String, List<String>>)
                ?.toMutableMap() ?: mutableMapOf()
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

    suspend fun pinMessage(chatId: String, messageId: String, messageText: String) {
        db.collection(Col.CHATS).document(chatId).update("pinnedMessageId", messageId).await()
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update("isPinned", true).await()
    }

    // ── Logs ─────────────────────────────────────────────────────────────────

    suspend fun writeLog(userId: String, action: String, targetId: String, details: String) {
        try {
            db.collection(Col.LOGS).add(
                LogModel(action = action, userId = userId, targetId = targetId, details = details)
            ).await()
        } catch (_: Exception) {}
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    suspend fun banUser(adminId: String, targetId: String, reason: String) {
        db.collection(Col.USERS).document(targetId)
            .update(mapOf("isBanned" to true, "banReason" to reason)).await()
        sendBotMessage(targetId, "🚫 Вы заблокированы. Причина: $reason")
        writeLog(adminId, "BAN", targetId, reason)
    }
    suspend fun unbanUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId)
            .update(mapOf("isBanned" to false, "banReason" to "")).await()
        sendBotMessage(targetId, "✅ Вы разблокированы.")
        writeLog(adminId, "UNBAN", targetId, "")
    }
    suspend fun freezeUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update("isFrozen", true).await()
        sendBotMessage(targetId, "❄️ Аккаунт заморожен.")
        writeLog(adminId, "FREEZE", targetId, "")
    }
    suspend fun unfreezeUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update("isFrozen", false).await()
        sendBotMessage(targetId, "✅ Заморозка снята.")
        writeLog(adminId, "UNFREEZE", targetId, "")
    }
    suspend fun adminDeleteMessage(adminId: String, chatId: String, messageId: String) {
        deleteMessage(chatId, messageId, adminId)
        writeLog(adminId, "DELETE_MSG", messageId, "chatId=$chatId")
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
    suspend fun getPassphrases(): List<String> = getValidPassphrases()
    suspend fun updatePassphrases(adminId: String, phrases: List<String>) {
        db.collection(Col.PASSPHRASES).document("active").set(mapOf("phrases" to phrases)).await()
        writeLog(adminId, "UPDATE_PASSPHRASES", "", "${phrases.size}")
    }

    private fun mapAuthError(msg: String): String = when {
        "email" in msg && "already" in msg -> "Email уже зарегистрирован"
        "weak-password" in msg             -> "Пароль минимум 6 символов"
        "network" in msg                   -> "Нет интернета"
        "INVALID_LOGIN" in msg || "credential" in msg -> "Неверный email или пароль"
        "user-not-found" in msg            -> "Пользователь не найден"
        else                               -> msg.take(100)
    }
}
