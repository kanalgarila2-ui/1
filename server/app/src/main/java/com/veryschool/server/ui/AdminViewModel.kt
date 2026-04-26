package com.veryschool.server.ui

import android.content.Context
import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.veryschool.server.data.models.*
import com.veryschool.server.data.AdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AdminEvent { data class Error(val msg: String): AdminEvent(); data class Success(val msg: String): AdminEvent() }

class AdminViewModel : ViewModel() {
    private val repo = AdminRepository()

    private val _event = MutableSharedFlow<AdminEvent>()
    val event: SharedFlow<AdminEvent> = _event

    private val _isLoggedIn = MutableStateFlow(repo.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    val users  = repo.getUsersFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chats  = repo.getChatsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs   = repo.getLogsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages

    private val _passphrases = MutableStateFlow<List<String>>(emptyList())
    val passphrases: StateFlow<List<String>> = _passphrases

    init {
        if (repo.isLoggedIn) viewModelScope.launch { _passphrases.value = repo.getPassphrases() }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        val (ok, err) = repo.loginAdmin(email, password)
        if (ok) { _isLoggedIn.value = true; _passphrases.value = repo.getPassphrases(); _event.emit(AdminEvent.Success("Добро пожаловать!")) }
        else _event.emit(AdminEvent.Error(err))
    }

    fun openChatMessages(chatId: String) = viewModelScope.launch {
        repo.getMessagesFlow(chatId).collect { _messages.value = it }
    }

    fun ban(uid: String, reason: String)  = viewModelScope.launch { repo.banUser(uid, reason); _event.emit(AdminEvent.Success("Заблокирован")) }
    fun unban(uid: String)                = viewModelScope.launch { repo.unbanUser(uid); _event.emit(AdminEvent.Success("Разблокирован")) }
    fun freeze(uid: String)               = viewModelScope.launch { repo.freezeUser(uid); _event.emit(AdminEvent.Success("Заморожен")) }
    fun unfreeze(uid: String)             = viewModelScope.launch { repo.unfreezeUser(uid); _event.emit(AdminEvent.Success("Разморожен")) }
    fun deleteUser(uid: String)           = viewModelScope.launch { repo.deleteUser(uid); _event.emit(AdminEvent.Success("Удалён")) }
    fun deleteMessage(chatId: String, msgId: String) = viewModelScope.launch { repo.deleteMessage(chatId, msgId) }
    fun updateUser(uid: String, updates: Map<String, Any>) = viewModelScope.launch { repo.updateUser(uid, updates) }
    fun sendBotToUser(uid: String, text: String) = viewModelScope.launch { repo.sendBotMessage(uid, text); _event.emit(AdminEvent.Success("Отправлено")) }
    fun broadcastBot(text: String)        = viewModelScope.launch { repo.broadcastBotMessage(text); _event.emit(AdminEvent.Success("Broadcast отправлен")) }
    fun savePassphrases(list: List<String>) = viewModelScope.launch { repo.savePassphrases(list); _passphrases.value = list; _event.emit(AdminEvent.Success("Фразы сохранены")) }
}

class AdminViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(mc: Class<T>): T = AdminViewModel() as T
}
