package com.veryschool.client.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.veryschool.client.data.AppRepository
import com.veryschool.client.data.db.AppDatabase
import com.veryschool.client.data.db.MessageEntity
import com.veryschool.client.data.models.*
import com.veryschool.client.data.prefs.PrefsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthState {
    object Unknown : AuthState()
    object NeedServer : AuthState()
    object NeedAuth : AuthState()
    object Authenticated : AuthState()
}

sealed class UiEvent {
    data class Error(val msg: String) : UiEvent()
    data class Success(val msg: String) : UiEvent()
}

class MainViewModel(
    private val repo: AppRepository,
    private val prefs: PrefsManager
) : ViewModel() {

    private val TAG = "MainViewModel"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId

    private val _currentUsername = MutableStateFlow("")
    val currentUsername: StateFlow<String> = _currentUsername

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName

    private val _avatar = MutableStateFlow("")
    val avatar: StateFlow<String> = _avatar

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl

    private val _token = MutableStateFlow("")

    // StateFlow — гарантированно доставляет данные в UI
    val chats: StateFlow<List<com.veryschool.client.data.db.ChatEntity>> =
        repo.chats.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val users: StateFlow<List<com.veryschool.client.data.db.UserEntity>> =
        repo.users.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connected: StateFlow<Boolean> = repo.connected

    private val _selectedChatMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val selectedChatMessages: StateFlow<List<MessageEntity>> = _selectedChatMessages

    private var wsJob: Job? = null

    init {
        viewModelScope.launch {
            // Не используем distinctUntilChanged — нам нужно переподключаться даже
            // если url/token те же (например после перезапуска приложения)
            combine(prefs.serverUrl, prefs.authToken) { url, token -> Pair(url, token) }
                .collect { (url, token) ->
                    Log.d(TAG, "Prefs changed: url=${url.take(20)} hasToken=${token.isNotEmpty()}")
                    _serverUrl.value = url

                    if (url.isEmpty()) {
                        _authState.value = AuthState.NeedServer
                        return@collect
                    }

                    repo.initClient(url)

                    if (token.isEmpty()) {
                        _authState.value = AuthState.NeedAuth
                        return@collect
                    }

                    // Загружаем данные пользователя
                    _token.value = token
                    _currentUserId.value = prefs.userId.first()
                    _currentUsername.value = prefs.username.first()
                    _displayName.value = prefs.displayName.first()
                    _avatar.value = prefs.avatarBase64.first()
                    _authState.value = AuthState.Authenticated

                    // Подключаемся к WS
                    connectWs(url, token)
                }
        }
    }

    private fun connectWs(url: String, token: String) {
        Log.d(TAG, "connectWs called: $url")
        // Отменяем предыдущее соединение
        wsJob?.cancel()
        wsJob = repo.startWsConnection(url, token)
    }

    fun saveServer(url: String) {
        viewModelScope.launch {
            val clean = if (url.startsWith("http")) url else "http://$url"
            prefs.saveServerUrl(clean)
        }
    }

    fun login(username: String, password: String, passphrase: String) {
        viewModelScope.launch {
            val api = repo.getApiClient() ?: run { _uiEvent.emit(UiEvent.Error("Сначала укажите сервер")); return@launch }
            val res = api.login(AuthRequest(username.trim(), password, passphrase))
            if (res.success && res.user != null) {
                prefs.saveAuth(res.token, res.user.id, res.user.username, res.user.displayName)
                _uiEvent.emit(UiEvent.Success("Добро пожаловать, ${res.user.displayName}!"))
            } else {
                _uiEvent.emit(UiEvent.Error(res.error.ifEmpty { "Неверный логин или пароль" }))
            }
        }
    }

    fun register(username: String, password: String, displayName: String, passphrase: String) {
        viewModelScope.launch {
            val api = repo.getApiClient() ?: run { _uiEvent.emit(UiEvent.Error("Сначала укажите сервер")); return@launch }
            val exists = api.checkUsername(username.trim())
            if (exists) { _uiEvent.emit(UiEvent.Error("Имя пользователя уже занято")); return@launch }
            val res = api.register(RegisterRequest(username.trim(), password, displayName.trim(), passphrase))
            if (res.success && res.user != null) {
                prefs.saveAuth(res.token, res.user.id, res.user.username, res.user.displayName)
                _uiEvent.emit(UiEvent.Success("Аккаунт создан!"))
            } else {
                _uiEvent.emit(UiEvent.Error(res.error.ifEmpty { "Ошибка регистрации" }))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            wsJob?.cancel()
            wsJob = null
            repo.disconnect()
            prefs.clearAuth()
        }
    }

    fun openChat(chatId: String) {
        viewModelScope.launch {
            repo.requestMessages(chatId)
            repo.getMessages(chatId).collect { msgs ->
                _selectedChatMessages.value = msgs
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        viewModelScope.launch { repo.sendMessage(chatId, text) }
    }

    fun sendImageMessage(chatId: String, uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                repo.sendMessage(chatId, "", b64)
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка отправки изображения"))
            }
        }
    }

    fun sendReaction(messageId: String, chatId: String, emoji: String) {
        viewModelScope.launch { repo.sendReaction(messageId, chatId, emoji) }
    }

    fun startDm(otherUserId: String) {
        viewModelScope.launch {
            val allUsers = repo.users.first()
            val otherUser = allUsers.firstOrNull { it.id == otherUserId } ?: return@launch
            repo.createDm(otherUserId, _currentUserId.value, otherUser.displayName)
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        viewModelScope.launch { repo.createGroup(name, memberIds) }
    }

    fun updateProfile(newDisplayName: String, avatarUri: Uri?, context: Context) {
        viewModelScope.launch {
            var b64 = _avatar.value
            if (avatarUri != null) {
                try {
                    val bytes = context.contentResolver.openInputStream(avatarUri)?.readBytes() ?: return@launch
                    b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                } catch (e: Exception) {
                    _uiEvent.emit(UiEvent.Error("Ошибка загрузки фото")); return@launch
                }
            }
            val api = repo.getApiClient() ?: return@launch
            val ok = api.updateProfile(_token.value, UpdateProfileRequest(newDisplayName.trim(), b64))
            if (ok) {
                prefs.saveAuth(_token.value, _currentUserId.value, _currentUsername.value, newDisplayName.trim())
                if (avatarUri != null) prefs.saveAvatar(b64)
                _displayName.value = newDisplayName.trim()
                _avatar.value = b64
                _uiEvent.emit(UiEvent.Success("Профиль обновлён"))
            } else {
                _uiEvent.emit(UiEvent.Error("Ошибка обновления профиля"))
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            val api = repo.getApiClient() ?: return@launch
            val ok = api.changePassword(_token.value, ChangePasswordRequest(oldPassword, newPassword))
            if (ok) _uiEvent.emit(UiEvent.Success("Пароль изменён"))
            else _uiEvent.emit(UiEvent.Error("Неверный текущий пароль"))
        }
    }

    fun sendTyping(chatId: String) { viewModelScope.launch { repo.sendTyping(chatId) } }
    fun sendTypingStop(chatId: String) { viewModelScope.launch { repo.sendTypingStop(chatId) } }

    override fun onCleared() {
        super.onCleared()
        wsJob?.cancel()
        repo.disconnect()
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "veryschool.db")
            .fallbackToDestructiveMigration()
            .build()
        val prefs = PrefsManager(context)
        val repo = AppRepository(db, prefs)
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(repo, prefs) as T
    }
}
