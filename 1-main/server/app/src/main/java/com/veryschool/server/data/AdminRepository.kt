package com.veryschool.server.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.toObject
import com.veryschool.server.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

private const val TAG = "AdminRepo"

class AdminRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun loginAdmin(email: String, password: String): Pair<Boolean, String> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser?.uid ?: return Pair(false, "Нет UID")
            val doc = db.collection("users").document(uid).get().await()
            if (!doc.exists()) { auth.signOut(); return Pair(false, "Профиль не найден") }
            if (doc.getBoolean("isAdmin") != true) { auth.signOut(); return Pair(false, "Нет прав администратора") }
            Pair(true, "")
        } catch (e: Exception) { Pair(false, e.message ?: "Ошибка") }
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    fun getUsersFlow(): Flow<List<UserModel>> = callbackFlow {
        val reg = db.collection("users").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toObject<UserModel>()?.copy(id = it.id) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun getChatsFlow(): Flow<List<ChatModel>> = callbackFlow {
        val reg = db.collection("chats").orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toObject<ChatModel>()?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun getLogsFlow(): Flow<List<LogModel>> = callbackFlow {
        val reg = db.collection("logs").orderBy("timestamp", Query.Direction.DESCENDING).limit(500)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toObject<LogModel>()?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun getMessagesFlow(chatId: String): Flow<List<MessageModel>> = callbackFlow {
        val reg = db.collection("messages").document(chatId).collection("msgs")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toObject<MessageModel>()?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ── GlobalSettings ────────────────────────────────────────────────────────

    fun getGlobalSettingsFlow(): Flow<GlobalSettings> = callbackFlow {
        val ref = db.collection("global_settings").document("config")
        val reg = ref.addSnapshotListener { snap, _ ->
            val settings = if (snap != null && snap.exists())
                snap.toObject<GlobalSettings>() ?: GlobalSettings()
            else GlobalSettings()
            trySend(settings)
        }
        awaitClose { reg.remove() }
    }

    suspend fun getGlobalSettings(): GlobalSettings = try {
        val snap = db.collection("global_settings").document("config").get().await()
        if (snap.exists()) snap.toObject<GlobalSettings>() ?: GlobalSettings() else GlobalSettings()
    } catch (_: Exception) { GlobalSettings() }

    suspend fun saveGlobalSettings(settings: GlobalSettings) {
        val uid = currentUid ?: "admin"
        db.collection("global_settings").document("config").set(
            settings.copy(updatedBy = uid)
        ).await()
        log("UPDATE_GLOBAL_SETTINGS", "", "updatedBy=$uid")
    }

    // Инициализация документа с дефолтными значениями (вызывается один раз)
    suspend fun initGlobalSettingsIfNeeded() {
        val ref = db.collection("global_settings").document("config")
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(GlobalSettings()).await()
            Log.i(TAG, "GlobalSettings initialized with defaults")
        }
    }

    // ── Users ─────────────────────────────────────────────────────────────────

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
        sendBotMessage(uid, "❄️ Аккаунт заморожен.")
        log("FREEZE", uid, "")
    }
    suspend fun unfreezeUser(uid: String) {
        db.collection("users").document(uid).update("isFrozen", false).await()
        sendBotMessage(uid, "✅ Заморозка снята.")
        log("UNFREEZE", uid, "")
    }
    suspend fun setVerified(uid: String, verified: Boolean) {
        db.collection("users").document(uid).update("isVerified", verified).await()
        log(if (verified) "VERIFY" else "UNVERIFY", uid, "")
    }
    suspend fun updateUser(uid: String, updates: Map<String, Any>) {
        db.collection("users").document(uid).update(updates).await()
        log("EDIT_USER", uid, updates.keys.joinToString())
    }
    suspend fun deleteUser(uid: String) {
        db.collection("users").document(uid).delete().await()
        log("DELETE_USER", uid, "")
    }
    suspend fun getUserStats(): Map<String, Int> {
        val users = db.collection("users").get().await().documents
        return mapOf(
            "total"    to users.size,
            "online"   to users.count { it.getBoolean("online") == true },
            "banned"   to users.count { it.getBoolean("isBanned") == true },
            "frozen"   to users.count { it.getBoolean("isFrozen") == true },
            "verified" to users.count { it.getBoolean("isVerified") == true },
            "admin"    to users.count { it.getBoolean("isAdmin") == true }
        )
    }

    // ── Chats ─────────────────────────────────────────────────────────────────

    suspend fun deleteChat(chatId: String) {
        db.collection("chats").document(chatId).delete().await()
        log("DELETE_CHAT", chatId, "")
    }
    suspend fun pinChat(chatId: String, pinned: Boolean) {
        db.collection("chats").document(chatId).update("pinned", pinned).await()
    }
    suspend fun getChatStats(): Map<String, Int> {
        val chats = db.collection("chats").get().await().documents
        return mapOf(
            "total"  to chats.size,
            "groups" to chats.count { it.getBoolean("isGroup") == true },
            "dm"     to chats.count { it.getBoolean("isDm") == true },
            "bot"    to chats.count { it.getBoolean("isBot") == true }
        )
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    suspend fun deleteMessage(chatId: String, msgId: String) {
        db.collection("messages").document(chatId).collection("msgs").document(msgId)
            .update(mapOf("isDeleted" to true, "text" to "")).await()
        db.collection("deleted_messages").document(msgId).set(
            mapOf("chatId" to chatId, "deletedBy" to (currentUid ?: "admin"),
                  "deletedAt" to FieldValue.serverTimestamp())
        ).await()
        log("DELETE_MSG", msgId, "chatId=$chatId")
    }

    // ── Passphrases ───────────────────────────────────────────────────────────

    suspend fun getPassphrases(): List<String> = try {
        @Suppress("UNCHECKED_CAST")
        (db.collection("passphrases").document("active").get().await().get("phrases") as? List<String>) ?: listOf("22sch")
    } catch (_: Exception) { listOf("22sch") }

    suspend fun savePassphrases(phrases: List<String>) {
        db.collection("passphrases").document("active").set(mapOf("phrases" to phrases)).await()
        log("UPDATE_PASSPHRASES", "", "${phrases.size}")
    }

    // ── BOT ───────────────────────────────────────────────────────────────────

    suspend fun sendBotMessage(targetUid: String, text: String) {
        try {
            val chatId = "bot_$targetUid"
            db.collection("messages").document(chatId).collection("msgs").add(
                mapOf("senderId" to "BOT", "senderName" to "VerySchool BOT", "text" to text,
                    "chatId" to chatId, "reactions" to emptyMap<String, Any>(),
                    "readBy" to emptyList<String>(), "isDeleted" to false,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "clientTimestamp" to System.currentTimeMillis())
            ).await()
            try {
                db.collection("chats").document(chatId)
                    .update(mapOf("lastMessage" to text, "lastMessageTime" to FieldValue.serverTimestamp())).await()
            } catch (_: Exception) {
                db.collection("chats").document(chatId).set(
                    mapOf("lastMessage" to text, "lastMessageTime" to FieldValue.serverTimestamp(),
                        "isBot" to true, "members" to listOf(targetUid, "BOT")), SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) { Log.e(TAG, "sendBotMessage: ${e.message}") }
    }

    suspend fun broadcastBotMessage(text: String) {
        val uids = db.collection("users").get().await().documents.map { it.id }.filter { it != "BOT" }
        uids.forEach { sendBotMessage(it, text) }
        log("BOT_BROADCAST", "", "To ${uids.size}: ${text.take(60)}")
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    suspend fun setMaintenanceMode(enabled: Boolean, message: String) {
        db.collection("global_settings").document("config")
            .update(mapOf("maintenanceMode" to enabled, "maintenanceMessage" to message,
                "updatedBy" to (currentUid ?: "admin"))).await()
        log(if (enabled) "MAINTENANCE_ON" else "MAINTENANCE_OFF", "", message.take(60))
    }

    suspend fun setAnnouncement(enabled: Boolean, text: String) {
        db.collection("global_settings").document("config")
            .update(mapOf("announcementEnabled" to enabled, "announcementText" to text,
                "updatedBy" to (currentUid ?: "admin"))).await()
        log("SET_ANNOUNCEMENT", "", if (enabled) text.take(60) else "disabled")
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

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
