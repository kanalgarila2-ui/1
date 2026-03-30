package com.veryschool.server.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.veryschool.server.core.ServerService
import com.veryschool.server.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.Inet4Address
import java.net.NetworkInterface

class AdminViewModel(private val context: Context) : ViewModel() {
    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    private val _publicIp = MutableStateFlow("Определяется...")
    val publicIp: StateFlow<String> = _publicIp
    private val _localIp = MutableStateFlow("...")
    val localIp: StateFlow<String> = _localIp
    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount
    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users
    private val _chats = MutableStateFlow<List<ChatEntity>>(emptyList())
    val chats: StateFlow<List<ChatEntity>> = _chats
    private val _msgCount = MutableStateFlow(0)
    val msgCount: StateFlow<Int> = _msgCount

    val db = Room.databaseBuilder(context, ServerDatabase::class.java, "vs_server.db")
        .fallbackToDestructiveMigration().build()

    init { detectLocalIp(); fetchPublicIp(); startPolling(); loadDbData() }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val svc = ServerService.instance
                _serverRunning.value = svc?.server?.isRunning ?: false
                if (svc != null) { _logs.value = svc.server.logs.toList().takeLast(300); _onlineCount.value = svc.server.connections.count() }
                delay(800)
            }
        }
    }

    private fun loadDbData() {
        viewModelScope.launch { db.userDao().getAllFlow().collect { _users.value = it } }
        viewModelScope.launch { db.chatDao().getAllFlow().collect { _chats.value = it } }
        viewModelScope.launch { while (true) { _msgCount.value = db.messageDao().count(); delay(5000) } }
    }

    fun startServer() = context.startForegroundService(Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_START })
    fun stopServer() = context.startService(Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP })
    fun restartServer() = context.startForegroundService(Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_RESTART })

    fun deleteUser(userId: String) {
        viewModelScope.launch { db.userDao().delete(userId); db.tokenDao().deleteForUser(userId); ServerService.instance?.server?.log("🗑️ User deleted: $userId", "WARN") }
    }
    fun setAdmin(userId: String, isAdmin: Boolean) {
        viewModelScope.launch { db.userDao().setAdmin(userId, isAdmin); ServerService.instance?.server?.log("👑 Admin: $userId → $isAdmin") }
    }
    fun banUser(userId: String, minutes: Long, reason: String) {
        viewModelScope.launch {
            val until = if (minutes == 0L) 0L else System.currentTimeMillis() + minutes * 60_000
            db.userDao().setBan(userId, true, until, reason)
            val svc = ServerService.instance
            svc?.server?.let { srv ->
                srv.log("🚫 Banned: $userId for ${if (minutes == 0L) "∞" else "${minutes}m"}. Reason: $reason", "WARN")
                srv.connections.sendTo(userId, com.veryschool.server.core.WsMessage(com.veryschool.server.core.WsTypes.BANNED, "Вы заблокированы: $reason"))
            }
        }
    }
    fun unbanUser(userId: String) {
        viewModelScope.launch { db.userDao().setBan(userId, false, 0L, ""); ServerService.instance?.server?.log("✅ Unbanned: $userId") }
    }
    fun blockDm(userId: String, minutes: Long) {
        viewModelScope.launch {
            val until = if (minutes == 0L) 0L else System.currentTimeMillis() + minutes * 60_000
            db.userDao().setDmBlock(userId, true, until)
            ServerService.instance?.server?.log("🔇 DM blocked: $userId")
        }
    }
    fun unblockDm(userId: String) {
        viewModelScope.launch { db.userDao().setDmBlock(userId, false, 0L); ServerService.instance?.server?.log("✅ DM unblocked: $userId") }
    }
    fun sendBotMessage(text: String, targetUserId: String = "") {
        viewModelScope.launch {
            val svc = ServerService.instance ?: return@launch
            if (targetUserId.isEmpty()) {
                val allUsers = db.userDao().getAll()
                allUsers.forEach { svc.server.sendBotMessage(it.id, text) }
                svc.server.log("🤖 Bot broadcast: $text")
            } else {
                svc.server.sendBotMessage(targetUserId, text)
                svc.server.log("🤖 Bot → $targetUserId: $text")
            }
        }
    }
    fun deleteChat(chatId: String) {
        viewModelScope.launch { db.chatDao().delete(chatId); db.messageDao().deleteForChat(chatId); ServerService.instance?.server?.log("🗑️ Chat deleted: $chatId", "WARN") }
    }
    fun clearLogs() { ServerService.instance?.server?.logs?.clear(); _logs.value = emptyList() }

    private fun detectLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ip = NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "Нет IP"
                _localIp.value = "$ip:8080"
            } catch (_: Exception) { _localIp.value = "Ошибка" }
        }
    }
    private fun fetchPublicIp() {
        viewModelScope.launch(Dispatchers.IO) {
            try { _publicIp.value = "${java.net.URL("https://api.ipify.org").readText().trim()}:8080" }
            catch (_: Exception) {
                try { _publicIp.value = "${java.net.URL("https://checkip.amazonaws.com").readText().trim()}:8080" }
                catch (_: Exception) { _publicIp.value = "Нет интернета" }
            }
        }
    }
}

class AdminViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST"); return AdminViewModel(context) as T
    }
}
