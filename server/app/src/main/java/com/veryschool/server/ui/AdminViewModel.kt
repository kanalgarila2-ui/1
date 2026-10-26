package com.veryschool.server.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.veryschool.server.data.models.*
import com.veryschool.server.data.AdminRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class AdminEvent {
    data class Error(val msg: String)   : AdminEvent()
    data class Success(val msg: String) : AdminEvent()
}

class AdminViewModel : ViewModel() {
    private val repo = AdminRepository()

    private val _event = MutableSharedFlow<AdminEvent>()
    val event: SharedFlow<AdminEvent> = _event

    private val _isLoggedIn = MutableStateFlow(repo.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val users = repo.getUsersFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chats = repo.getChatsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs  = repo.getLogsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages

    private val _passphrases = MutableStateFlow<List<String>>(emptyList())
    val passphrases: StateFlow<List<String>> = _passphrases

    // GlobalSettings
    val globalSettings = repo.getGlobalSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSettings())

    private val _userStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val userStats: StateFlow<Map<String, Int>> = _userStats

    private val _chatStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val chatStats: StateFlow<Map<String, Int>> = _chatStats

    private var messagesJob: Job? = null

    init {
        if (repo.isLoggedIn) {
            viewModelScope.launch {
                _passphrases.value = repo.getPassphrases()
                repo.initGlobalSettingsIfNeeded()
                loadStats()
            }
        }
    }

    override fun onCleared() { super.onCleared(); messagesJob?.cancel() }

    fun login(email: String, password: String) = viewModelScope.launch {
        _isLoading.value = true
        val (ok, err) = repo.loginAdmin(email, password)
        _isLoading.value = false
        if (ok) {
            _isLoggedIn.value = true
            _passphrases.value = repo.getPassphrases()
            repo.initGlobalSettingsIfNeeded()
            loadStats()
            _event.emit(AdminEvent.Success("Добро пожаловать!"))
        } else _event.emit(AdminEvent.Error(err))
    }

    fun openChatMessages(chatId: String) {
        messagesJob?.cancel()
        _messages.value = emptyList()
        messagesJob = viewModelScope.launch {
            repo.getMessagesFlow(chatId).collect { _messages.value = it }
        }
    }

    private fun loadStats() = viewModelScope.launch {
        try { _userStats.value = repo.getUserStats() } catch (_: Exception) {}
        try { _chatStats.value = repo.getChatStats() } catch (_: Exception) {}
    }

    fun refreshStats() = loadStats()

    // ── Users ─────────────────────────────────────────────────────────────────

    fun ban(uid: String, reason: String)   = launch { repo.banUser(uid, reason); ok("Заблокирован") }
    fun unban(uid: String)                 = launch { repo.unbanUser(uid); ok("Разблокирован") }
    fun freeze(uid: String)                = launch { repo.freezeUser(uid); ok("Заморожен") }
    fun unfreeze(uid: String)              = launch { repo.unfreezeUser(uid); ok("Разморожен") }
    fun deleteUser(uid: String)            = launch { repo.deleteUser(uid); ok("Удалён") }
    fun updateUser(uid: String, updates: Map<String, Any>) = launch { repo.updateUser(uid, updates); ok("Обновлено") }
    fun setVerified(uid: String, v: Boolean) = launch { repo.setVerified(uid, v); ok(if (v) "✓ Верифицирован" else "Снята верификация") }

    // ── Chats ─────────────────────────────────────────────────────────────────

    fun deleteMessage(chatId: String, msgId: String) = launch { repo.deleteMessage(chatId, msgId) }
    fun deleteChat(chatId: String) = launch { repo.deleteChat(chatId); ok("Чат удалён") }
    fun pinChat(chatId: String, pinned: Boolean) = launch { repo.pinChat(chatId, pinned) }

    // ── BOT ───────────────────────────────────────────────────────────────────

    fun sendBotToUser(uid: String, text: String) = launch { repo.sendBotMessage(uid, text); ok("Отправлено") }
    fun broadcastBot(text: String)               = launch { repo.broadcastBotMessage(text); ok("Broadcast отправлен всем") }

    // ── Passphrases ───────────────────────────────────────────────────────────

    fun savePassphrases(list: List<String>) = launch {
        repo.savePassphrases(list); _passphrases.value = list; ok("Фразы сохранены")
    }

    // ── GlobalSettings ────────────────────────────────────────────────────────

    fun saveGlobalSettings(settings: GlobalSettings) = launch {
        repo.saveGlobalSettings(settings); ok("✅ Настройки сохранены в Firestore")
    }

    fun setMaintenanceMode(enabled: Boolean, message: String) = launch {
        repo.setMaintenanceMode(enabled, message)
        ok(if (enabled) "⚠️ Режим обслуживания включён" else "✅ Режим обслуживания выключен")
    }

    fun setAnnouncement(enabled: Boolean, text: String) = launch {
        repo.setAnnouncement(enabled, text)
        ok(if (enabled) "📢 Объявление активно" else "Объявление скрыто")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) }
    }
    private suspend fun ok(msg: String) = _event.emit(AdminEvent.Success(msg))
}

class AdminViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(mc: Class<T>): T = AdminViewModel() as T
}
