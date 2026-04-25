package com.veryschool.server.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.toObject
import com.veryschool.server.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

private const val TAG = "AdminRepo"

class AdminRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun loginAdmin(email: String, password: String): Pair<Boolean, String> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser?.uid ?: return Pair(false, "Нет UID")
            val user = db.collection("users").document(uid).get().await().toObject<UserModel>()
            if (user?.isAdmin != true) {
                auth.signOut()
                Pair(false, "Нет прав администратора")
            } else Pair(true, "")
        } catch (e: Exception) { Pair(false, e.message ?: "Ошибка") }
    }

    fun getUsersFlow(): Flow<List<UserModel>> = callbackFlow {
        val reg = db.collection("users").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toObject<UserModel>()?.copy(id = it.id) } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun getChatsFlow(): Flow<List<ChatModel>> = callbackFlow {
        val reg = db.collection("chats").orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<ChatModel>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun getLogsFlow(): Flow<List<LogModel>> = callbackFlow {
        val reg = db.collection("logs").orderBy("timestamp", Query.Direction.DESCENDING).limit(500)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<LogModel>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun getMessagesFlow(chatId: String): Flow<List<MessageModel>> = callbackFlow {
        val reg = db.collection("messages").document(chatId).collection("msgs")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<MessageModel>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun banUser(uid: String, reason: String) {
        db.collection("users").document(uid).update(mapOf("isBanned" to true, "banReason" to reason)).await()
        sendBotMessage(uid, "🚫 Вы заблокированы. Причина: $reason")
        log("BAN", uid, "Reason: $reason")
    }
    suspend fun unbanUser(uid: String) {
        db.collection("users").document(uid).update(mapOf("isBanned" to false, "banReason" to "")).await()
        sendBotMessage(uid, "✅ Вы разблокированы.")
        log("UNBAN", uid, "")
    }
    suspend fun freezeUser(uid: String) {
        db.collection("users").document(uid).update("isFrozen", true).await()
        sendBotMessage(uid, "❄️ Аккаунт заморожен. Читать сообщения можно, отправлять — нет.")
        log("FREEZE", uid, "")
    }
    suspend fun unfreezeUser(uid: String) {
        db.collection("users").document(uid).update("isFrozen", false).await()
        sendBotMessage(uid, "✅ Заморозка снята. Все функции восстановлены.")
        log("UNFREEZE", uid, "")
    }
    suspend fun updateUser(uid: String, updates: Map<String, Any>) {
        db.collection("users").document(uid).update(updates).await()
        log("EDIT_USER", uid, updates.keys.joinToString())
    }
    suspend fun deleteMessage(chatId: String, msgId: String) {
        db.collection("messages").document(chatId).collection("msgs").document(msgId)
            .update(mapOf("isDeleted" to true, "text" to "")).await()
        db.collection("deleted_messages").document(msgId)
            .set(mapOf("chatId" to chatId, "deletedBy" to (currentUid ?: "admin"), "deletedAt" to FieldValue.serverTimestamp())).await()
        log("DELETE_MSG", msgId, "chatId=$chatId")
    }
    suspend fun deleteUser(uid: String) {
        db.collection("users").document(uid).delete().await()
        log("DELETE_USER", uid, "")
    }

    suspend fun getPassphrases(): List<String> = try {
        val doc = db.collection("passphrases").document("active").get().await()
        @Suppress("UNCHECKED_CAST") (doc.get("phrases") as? List<String>) ?: listOf("22sch")
    } catch (_: Exception) { listOf("22sch") }

    suspend fun savePassphrases(phrases: List<String>) {
        db.collection("passphrases").document("active").set(mapOf("phrases" to phrases)).await()
        log("UPDATE_PASSPHRASES", "", "Count: ${phrases.size}")
    }

    suspend fun sendBotMessage(targetUid: String, text: String) {
        try {
            val chatId = "bot_$targetUid"
            db.collection("messages").document(chatId).collection("msgs").add(
                mapOf("senderId" to "BOT", "senderName" to "VerySchool BOT", "text" to text,
                    "chatId" to chatId, "reactions" to emptyMap<String,Any>(), "readBy" to emptyList<String>(),
                    "timestamp" to FieldValue.serverTimestamp(), "clientTimestamp" to System.currentTimeMillis())
            ).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to text, "lastMessageTime" to FieldValue.serverTimestamp())).await()
        } catch (e: Exception) { Log.e(TAG, "sendBotMessage: ${e.message}") }
    }

    suspend fun broadcastBotMessage(text: String) {
        val users = db.collection("users").get().await().documents.map { it.id }.filter { it != "BOT" }
        users.forEach { uid -> sendBotMessage(uid, text) }
        log("BOT_BROADCAST", "", "To ${users.size}: ${text.take(60)}")
    }

    private suspend fun log(action: String, targetId: String, details: String) {
        try {
            db.collection("logs").add(mapOf(
                "action" to action, "userId" to (currentUid ?: "admin"),
                "targetId" to targetId, "details" to details,
                "timestamp" to FieldValue.serverTimestamp()
            )).await()
        } catch (_: Exception) {}
    }
}
