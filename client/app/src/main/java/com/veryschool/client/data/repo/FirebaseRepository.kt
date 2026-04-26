package com.veryschool.client.data.repo

import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.veryschool.client.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "FirebaseRepo"

// Firestore collection names
object Col {
    const val USERS           = "users"
    const val CHATS           = "chats"
    const val MESSAGES        = "messages"
    const val DELETED_MESSAGES = "deleted_messages"
    const val LOGS            = "logs"
    const val PASSPHRASES     = "passphrases"
}

class FirebaseRepository {

    private val auth     = FirebaseAuth.getInstance()
    private val db       = FirebaseFirestore.getInstance()
    private val storage  = FirebaseStorage.getInstance()
    private val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val currentUid: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    // ── Auth ─────────────────────────────────────────────────────────────────

    suspend fun register(
        email: String,
        password: String,
        username: String,
        displayName: String,
        passphrase: String
    ): AuthResult {
        Log.d(TAG, "═══════ REGISTER START ═══════")
        Log.d(TAG, "email=$email, username=$username, displayName=$displayName")
        return try {
            // 1. Проверяем фразу входа
            Log.d(TAG, "Step 1: Loading valid passphrases...")
            val validPhrases = getValidPassphrases()
            Log.d(TAG, "Valid passphrases count: ${validPhrases.size}")
            if (passphrase !in validPhrases) {
                Log.w(TAG, "Passphrase not in list")
                return AuthResult(false, error = "Неверная ключевая фраза")
            }
            Log.d(TAG, "Passphrase OK")

            // 2. Проверяем уникальность username
            Log.d(TAG, "Step 2: Checking username uniqueness for '$username'...")
            try {
                val existing = db.collection(Col.USERS)
                    .whereEqualTo("username", username.lowercase().trim())
                    .get().await()
                Log.d(TAG, "Username query returned ${existing.size()} documents")
                if (!existing.isEmpty) {
                    Log.w(TAG, "Username already taken")
                    return AuthResult(false, error = "Имя пользователя уже занято")
                }
                Log.d(TAG, "Username is available")
            } catch (checkEx: Exception) {
                Log.e(TAG, "Username check FAILED: ${checkEx.message}", checkEx)
                return AuthResult(false, error = "Ошибка проверки имени: ${checkEx.message}")
            }

            // 3. Регистрируем в Firebase Auth
            Log.d(TAG, "Step 3: Creating Firebase Auth user...")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid
            if (uid == null) {
                Log.e(TAG, "Auth returned null uid")
                return AuthResult(false, error = "Ошибка создания аккаунта")
            }
            Log.d(TAG, "Auth user created: uid=$uid")

            // 4. Обновление токена
            Log.d(TAG, "Step 4: Refreshing token...")
            try {
                val tokenResult = result.user?.getIdToken(true)?.await()
                Log.d(TAG, "Token refreshed, new token length: ${tokenResult?.token?.length ?: 0}")
            } catch (tokenEx: Exception) {
                Log.w(TAG, "Token refresh failed (non‑fatal): ${tokenEx.message}")
            }

            // 5. Запись профиля в Firestore
            Log.d(TAG, "Step 5: Writing user document to Firestore...")
            var firestoreDone = false
            var retries = 5
            var lastWriteError: Exception? = null
            while (!firestoreDone && retries > 0) {
                try {
                    Log.d(TAG, "Firestore set attempt #${6 - retries}")
                    val user = UserModel(
                        id = uid,
                        username = username.lowercase().trim(),
                        displayName = displayName.trim(),
                        passphrase = passphrase
                    )
                    db.collection(Col.USERS).document(uid).set(user).await()
                    firestoreDone = true
                    Log.d(TAG, "Firestore document created successfully for $uid")
                } catch (writeError: Exception) {
                    lastWriteError = writeError
                    retries--
                    Log.e(TAG, "Firestore write error (retries left: $retries): ${writeError.message}", writeError)
                    if (retries > 0) {
                        Log.d(TAG, "Waiting 1s before retry...")
                        delay(1000)
                        try { auth.currentUser?.getIdToken(true)?.await() } catch (_: Exception) {}
                    }
                }
            }
            if (!firestoreDone) {
                Log.e(TAG, "Firestore write FAILED after 5 retries. Last error: ${lastWriteError?.message}", lastWriteError)
                try { result.user?.delete()?.await(); Log.d(TAG, "Deleted auth user due to Firestore failure") } catch (_: Exception) {}
                return AuthResult(false, error = "Не удалось создать профиль. Попробуйте ещё раз.")
            }

            // 6. Создаём BOT чат
            Log.d(TAG, "Step 6: Creating bot chat...")
            try {
                createBotChat(uid, displayName.trim())
                Log.d(TAG, "Bot chat created")
            } catch (botEx: Exception) {
                Log.w(TAG, "Bot chat creation failed (non‑fatal): ${botEx.message}")
            }

            // 7. FCM token
            Log.d(TAG, "Step 7: Updating FCM token...")
            try {
                updateFcmToken(uid)
                Log.d(TAG, "FCM token updated")
            } catch (fcmEx: Exception) {
                Log.w(TAG, "FCM token update failed (non‑fatal): ${fcmEx.message}")
            }

            // 8. Лог
            Log.d(TAG, "Step 8: Writing log entry...")
            try {
                writeLog(uid, "REGISTER", "", "Registered: @${username}")
                Log.d(TAG, "Log entry written")
            } catch (logEx: Exception) {
                Log.w(TAG, "Log write failed (non‑fatal): ${logEx.message}")
            }

            // 9. Читаем профиль обратно
            Log.d(TAG, "Step 9: Reading back user document...")
            val userDoc = try {
                db.collection(Col.USERS).document(uid).get().await()
            } catch (readEx: Exception) {
                Log.e(TAG, "Read after create failed: ${readEx.message}", readEx)
                null
            }
            if (userDoc == null || !userDoc.exists()) {
                Log.e(TAG, "User document not found after creation")
                return AuthResult(false, error = "Профиль не найден после создания")
            }
            val finalUser = userDoc.toObject<UserModel>()
            if (finalUser == null) {
                Log.e(TAG, "User document deserialization failed")
                return AuthResult(false, error = "Ошибка чтения профиля")
            }

            Log.i(TAG, "═══════ REGISTER SUCCESS: $uid @${finalUser.username} ═══════")
            AuthResult(true, finalUser.copy(id = uid))
        } catch (e: Exception) {
            Log.e(TAG, "═══════ REGISTER UNEXPECTED ERROR ═══════", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception class: ${e.javaClass.name}")
            AuthResult(false, error = mapAuthError(e.message ?: ""))
        }
    }
    
    suspend fun login(email: String, password: String, passphrase: String): AuthResult {
        return try {
            val validPhrases = getValidPassphrases()
            if (passphrase !in validPhrases) {
                return AuthResult(false, error = "Неверная ключевая фраза")
            }
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return AuthResult(false, error = "Ошибка входа")
            val userDoc = db.collection(Col.USERS).document(uid).get().await()
            val user = userDoc.toObject<UserModel>() ?: return AuthResult(false, error = "Профиль не найден")

            if (user.isBanned) return AuthResult(false, error = "Аккаунт заблокирован: ${user.banReason}")

            // Обновляем online status + FCM token
            db.collection(Col.USERS).document(uid).update(
                mapOf("online" to true, "lastSeen" to FieldValue.serverTimestamp())
            ).await()
            updateFcmToken(uid)
            writeLog(uid, "LOGIN", "", "Login: @${user.username}")

            Log.i(TAG, "Login: $uid @${user.username}")
            AuthResult(true, user.copy(id = uid))
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}")
            AuthResult(false, error = mapAuthError(e.message ?: ""))
        }
    }

    suspend fun logout() {
        currentUid?.let { uid ->
            try {
                db.collection(Col.USERS).document(uid).update("online", false).await()
                writeLog(uid, "LOGOUT", "", "")
            } catch (_: Exception) {}
        }
        auth.signOut()
        Log.i(TAG, "Logged out")
    }

    private suspend fun getValidPassphrases(): List<String> {
        return try {
            val doc = db.collection(Col.PASSPHRASES).document("active").get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("phrases") as? List<String>) ?: listOf("22sch")
        } catch (_: Exception) { listOf("22sch") }
    }

    private suspend fun updateFcmToken(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            db.collection(Col.USERS).document(uid).update("fcmToken", token).await()
        } catch (_: Exception) {}
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    fun getUserFlow(userId: String): Flow<UserModel?> = callbackFlow {
        val reg = db.collection(Col.USERS).document(userId)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getUserFlow: ${err.message}"); return@addSnapshotListener }
                trySend(snap?.toObject<UserModel>()?.copy(id = snap.id))
            }
        awaitClose { reg.remove() }
    }

    fun getAllUsersFlow(): Flow<List<UserModel>> = callbackFlow {
        val reg = db.collection(Col.USERS)
            .orderBy("displayName")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getAllUsers: ${err.message}"); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { it.toObject<UserModel>()?.copy(id = it.id) } ?: emptyList()
                Log.d(TAG, "Users snapshot: ${list.size}")
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun searchUsers(query: String): List<UserModel> = try {
        val byUsername = db.collection(Col.USERS)
            .whereGreaterThanOrEqualTo("username", query.lowercase())
            .whereLessThanOrEqualTo("username", query.lowercase() + "\uf8ff")
            .get().await().toObjects<UserModel>()
        val byDisplay = db.collection(Col.USERS)
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
            .get().await().toObjects<UserModel>()
        (byUsername + byDisplay).distinctBy { it.id }.filter { !it.isBanned && !it.isDeleted }
    } catch (e: Exception) { Log.e(TAG, "searchUsers: ${e.message}"); emptyList() }

    suspend fun updateProfile(uid: String, displayName: String, avatarUri: Uri?): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>("displayName" to displayName.trim())
            if (avatarUri != null) {
                val url = uploadImage(uid, avatarUri, "avatars")
                if (url != null) updates["avatarUrl"] = url
            }
            db.collection(Col.USERS).document(uid).update(updates).await()
            true
        } catch (e: Exception) { Log.e(TAG, "updateProfile: ${e.message}"); false }
    }

    // ── Chats ─────────────────────────────────────────────────────────────────

    fun getChatsFlow(userId: String): Flow<List<ChatModel>> = callbackFlow {
        val reg = db.collection(Col.CHATS)
            .whereArrayContains("members", userId)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getChats: ${err.message}"); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { it.toObject<ChatModel>()?.copy(id = it.id) } ?: emptyList()
                Log.d(TAG, "Chats snapshot: ${list.size} for $userId")
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createDm(myUid: String, otherUid: String, otherDisplayName: String): String {
        // Проверяем существующий DM
        val existing = db.collection(Col.CHATS)
            .whereEqualTo("isDm", true)
            .whereArrayContains("members", myUid)
            .get().await()
        val existingDm = existing.documents.firstOrNull { doc ->
            val members = doc.get("members") as? List<*>
            members != null && otherUid in members
        }
        if (existingDm != null) return existingDm.id

        val chatId = UUID.randomUUID().toString()
        val chat = ChatModel(
            id = chatId, name = otherDisplayName, isGroup = false, isDm = true,
            members = listOf(myUid, otherUid), createdBy = myUid
        )
        db.collection(Col.CHATS).document(chatId).set(chat).await()
        return chatId
    }

    suspend fun createGroup(name: String, memberIds: List<String>, creatorId: String): String {
        val chatId = UUID.randomUUID().toString()
        val chat = ChatModel(
            id = chatId, name = name, isGroup = true, isDm = false,
            members = memberIds + creatorId, adminIds = listOf(creatorId), createdBy = creatorId
        )
        db.collection(Col.CHATS).document(chatId).set(chat).await()
        return chatId
    }

    private suspend fun createBotChat(userId: String, displayName: String) {
        val chatId = "bot_$userId"
        val chat = ChatModel(
            id = chatId, name = "VerySchool BOT", isGroup = false,
            isBot = true, members = listOf(userId, "BOT"), createdBy = "BOT"
        )
        db.collection(Col.CHATS).document(chatId).set(chat).await()
        // Приветственное сообщение
        sendMessage(chatId, "BOT", "VerySchool BOT", "",
            "👋 Привет, $displayName! Это твой личный канал от VerySchool. Здесь будут системные уведомления.")
    }

    suspend fun getChatById(chatId: String): ChatModel? = try {
        db.collection(Col.CHATS).document(chatId).get().await().toObject<ChatModel>()
    } catch (_: Exception) { null }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessagesFlow(chatId: String, limit: Long = 50): Flow<List<MessageModel>> = callbackFlow {
        val reg = db.collection(Col.MESSAGES).document(chatId).collection("msgs")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "getMessages: ${err.message}"); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull {
                    it.toObject<MessageModel>()?.copy(id = it.id)
                }?.filter { !it.isDeleted } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    // Слушатель удалённых сообщений
    fun getDeletedMessagesFlow(): Flow<List<String>> = callbackFlow {
        val reg = db.collection(Col.DELETED_MESSAGES)
            .addSnapshotListener { snap, _ ->
                val ids = snap?.documents?.map { it.id } ?: emptyList()
                trySend(ids)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        senderAvatarUrl: String,
        text: String,
        imageUrl: String = "",
        imageBase64: String = "",
        replyToId: String = "",
        replyToText: String = ""
    ): String {
        val msgId = UUID.randomUUID().toString()
        val msg = MessageModel(
            id = msgId, chatId = chatId,
            senderId = senderId, senderName = senderName,
            senderAvatarUrl = senderAvatarUrl,
            text = text, imageUrl = imageUrl, imageBase64 = imageBase64,
            replyToId = replyToId, replyToText = replyToText,
            clientTimestamp = System.currentTimeMillis()
        )
        db.collection(Col.MESSAGES).document(chatId).collection("msgs")
            .document(msgId).set(msg).await()
        // Обновляем lastMessage в чате
        db.collection(Col.CHATS).document(chatId).update(
            mapOf(
                "lastMessage" to text.ifEmpty { "📷 Фото" },
                "lastMessageTime" to FieldValue.serverTimestamp(),
                "lastMessageSenderId" to senderId
            )
        ).await()
        return msgId
    }

    suspend fun uploadImageAndSend(chatId: String, senderId: String, senderName: String, senderAvatarUrl: String, uri: Uri): String {
        val url = uploadImage(senderId, uri, "chat_images/$chatId") ?: return ""
        return sendMessage(chatId, senderId, senderName, senderAvatarUrl, "", imageUrl = url)
    }

    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String) {
        val ref = db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            @Suppress("UNCHECKED_CAST")
            val reactions = (snap.get("reactions") as? Map<String, List<String>>)?.toMutableMap() ?: mutableMapOf()
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

    suspend fun deleteMessage( chatId: String, messageId: String, deletedBy: String) {
        // Мягкое удаление в messages
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update(mapOf("isDeleted" to true, "deletedBy" to deletedBy, "text" to "")).await()
        // Запись в deleted_messages (для синхронизации)
        db.collection(Col.DELETED_MESSAGES).document(messageId)
            .set(DeletedMessageModel(id = messageId, chatId = chatId, deletedBy = deletedBy)).await()
    }

    suspend fun pinMessage(chatId: String, messageId: String, messageText: String) {
        db.collection(Col.CHATS).document(chatId)
            .update(mapOf("pinnedMessageId" to messageId)).await()
        db.collection(Col.MESSAGES).document(chatId).collection("msgs").document(messageId)
            .update("isPinned", true).await()
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    private suspend fun uploadImage(uid: String, uri: Uri, path: String): String? = try {
        val ref = storage.reference.child("$path/${uid}_${System.currentTimeMillis()}.jpg")
        ref.putFile(uri).await()
        ref.downloadUrl.await().toString()
    } catch (e: Exception) { Log.e(TAG, "uploadImage: ${e.message}"); null }

    // ── Logs ─────────────────────────────────────────────────────────────────

    suspend fun writeLog(userId: String, action: String, targetId: String, details: String) {
        try {
            db.collection(Col.LOGS).add(LogModel(
                action = action, userId = userId, targetId = targetId, details = details
            )).await()
        } catch (_: Exception) {}
    }

    fun getLogsFlow(limit: Long = 200): Flow<List<LogModel>> = callbackFlow {
        val reg = db.collection(Col.LOGS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<LogModel>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    suspend fun banUser(adminId: String, targetId: String, reason: String) {
        db.collection(Col.USERS).document(targetId)
            .update(mapOf("isBanned" to true, "banReason" to reason)).await()
        // BOT уведомление
        sendBotMessage(targetId, "🚫 Ваш аккаунт заблокирован. Причина: $reason")
        writeLog(adminId, "BAN", targetId, "Reason: $reason")
    }

    suspend fun unbanUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId)
            .update(mapOf("isBanned" to false, "banReason" to "")).await()
        sendBotMessage(targetId, "✅ Ваш аккаунт разблокирован.")
        writeLog(adminId, "UNBAN", targetId, "")
    }

    suspend fun freezeUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update("isFrozen", true).await()
        sendBotMessage(targetId, "❄️ Ваш аккаунт заморожен. Вы можете читать сообщения, но не можете отправлять.")
        writeLog(adminId, "FREEZE", targetId, "")
    }

    suspend fun unfreezeUser(adminId: String, targetId: String) {
        db.collection(Col.USERS).document(targetId).update("isFrozen", false).await()
        sendBotMessage(targetId, "✅ Аккаунт разморожен. Все функции восстановлены.")
        writeLog(adminId, "UNFREEZE", targetId, "")
    }

    suspend fun adminUpdateUser(adminId: String, targetId: String, updates: Map<String, Any>) {
        db.collection(Col.USERS).document(targetId).update(updates).await()
        writeLog(adminId, "EDIT_USER", targetId, updates.keys.joinToString())
    }

    suspend fun adminDeleteMessage(adminId: String, chatId: String, messageId: String) {
        deleteMessage(chatId, messageId, adminId)
        writeLog(adminId, "DELETE_MSG", messageId, "chatId=$chatId")
    }

    // Управление фразами входа
    suspend fun getPassphrases(): List<String> = getValidPassphrases()
    suspend fun updatePassphrases(adminId: String, phrases: List<String>) {
        db.collection(Col.PASSPHRASES).document("active")
            .set(mapOf("phrases" to phrases)).await()
        writeLog(adminId, "UPDATE_PASSPHRASES", "", "Count: ${phrases.size}")
    }

    // BOT сообщение конкретному юзеру
    suspend fun sendBotMessage(targetUserId: String, text: String) {
        val chatId = "bot_$targetUserId"
        try {
            sendMessage(chatId, "BOT", "VerySchool BOT", "", text)
        } catch (e: Exception) { Log.e(TAG, "sendBotMessage: ${e.message}") }
    }

    // BOT broadcast всем
    suspend fun broadcastBotMessage(adminId: String, text: String) {
        val users = db.collection(Col.USERS).get().await()
            .documents.mapNotNull { it.id }.filter { it != "BOT" }
        users.forEach { uid -> sendBotMessage(uid, text) }
        writeLog(adminId, "BOT_BROADCAST", "", "To ${users.size} users: ${text.take(80)}")
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun mapAuthError(msg: String): String = when {
        "email" in msg && "already" in msg -> "Email уже зарегистрирован"
        "password" in msg -> "Пароль слишком короткий (минимум 6 символов)"
        "network" in msg -> "Нет подключения к интернету"
        "credential" in msg || "password" in msg.lowercase() -> "Неверный email или пароль"
        "user-not-found" in msg -> "Пользователь не найден"
        else -> msg.take(100)
    }
}