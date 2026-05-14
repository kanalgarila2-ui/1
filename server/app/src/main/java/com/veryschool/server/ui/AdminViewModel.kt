package com.veryschool.server.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.veryschool.server.data.models.*
import com.veryschool.server.data.AdminRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class AdminEvent {
    data class Error(val msg: String) : AdminEvent()
    data class Success(val msg: String) : AdminEvent()
}

class AdminViewModel : ViewModel() {
    private val repo = AdminRepository()

    private val _event = MutableSharedFlow<AdminEvent>()
    val event: SharedFlow<AdminEvent> = _event

    private val _isLoggedIn = MutableStateFlow(repo.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    val users = repo.getUsersFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chats = repo.getChatsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs  = repo.getLogsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages

    private val _passphrases = MutableStateFlow<List<String>>(emptyList())
    val passphrases: StateFlow<List<String>> = _passphrases

    // FIX #6: отменяем предыдущий collect
    private var messagesJob: Job? = null

    init {
        if (repo.isLoggedIn) viewModelScope.launch { _passphrases.value = repo.getPassphrases() }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        val (ok, err) = repo.loginAdmin(email, password)
        if (ok) { _isLoggedIn.value = true; _passphrases.value = repo.getPassphrases(); _event.emit(AdminEvent.Success("Добро пожаловать!")) }
        else _event.emit(AdminEvent.Error(err))
    }

    // FIX #6: отменяем старый job перед новым
    fun openChatMessages(chatId: String) {
        messagesJob?.cancel()
        _messages.value = emptyList()
        messagesJob = viewModelScope.launch {
            repo.getMessagesFlow(chatId).collect { _messages.value = it }
        }
    }

    fun ban(uid: String, reason: String)   = viewModelScope.launch { try { repo.banUser(uid, reason); _event.emit(AdminEvent.Success("Заблокирован")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun unban(uid: String)                 = viewModelScope.launch { try { repo.unbanUser(uid); _event.emit(AdminEvent.Success("Разблокирован")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun freeze(uid: String)                = viewModelScope.launch { try { repo.freezeUser(uid); _event.emit(AdminEvent.Success("Заморожен")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun unfreeze(uid: String)              = viewModelScope.launch { try { repo.unfreezeUser(uid); _event.emit(AdminEvent.Success("Разморожен")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun deleteUser(uid: String)            = viewModelScope.launch { try { repo.deleteUser(uid); _event.emit(AdminEvent.Success("Удалён")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun deleteMessage(chatId: String, msgId: String) = viewModelScope.launch { try { repo.deleteMessage(chatId, msgId) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun updateUser(uid: String, updates: Map<String, Any>) = viewModelScope.launch { try { repo.updateUser(uid, updates) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun sendBotToUser(uid: String, text: String) = viewModelScope.launch { try { repo.sendBotMessage(uid, text); _event.emit(AdminEvent.Success("Отправлено")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun broadcastBot(text: String)         = viewModelScope.launch { try { repo.broadcastBotMessage(text); _event.emit(AdminEvent.Success("Broadcast отправлен")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
    fun savePassphrases(list: List<String>) = viewModelScope.launch { try { repo.savePassphrases(list); _passphrases.value = list; _event.emit(AdminEvent.Success("Фразы сохранены")) } catch (e: Exception) { _event.emit(AdminEvent.Error(e.message ?: "Ошибка")) } }
}

class AdminViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(mc: Class<T>): T = AdminViewModel() as T
}
