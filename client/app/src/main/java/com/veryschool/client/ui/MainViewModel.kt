package com.veryschool.client.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.veryschool.client.data.models.*
import com.veryschool.client.data.prefs.*
import com.veryschool.client.data.repo.FirebaseRepository
import com.veryschool.client.notifications.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthState { object Unknown : AuthState(); object NotAuth : AuthState(); object Auth : AuthState() }
sealed class UiEvent  { data class Error(val msg: String) : UiEvent(); data class Success(val msg: String) : UiEvent(); data class NavigateToChat(val chatId: String) : UiEvent() }

class MainViewModel(private val prefs: PrefsManager) : ViewModel() {

    private val repo = FirebaseRepository()
    private val TAG  = "MainVM"

    // ── Auth state ────────────────────────────────────────────────────────────
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    // ── User info ─────────────────────────────────────────────────────────────
    val userId      = prefs.userId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val displayName = prefs.displayName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val avatarUrl   = prefs.avatarUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val isAdmin     = prefs.isAdmin.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Settings ──────────────────────────────────────────────────────────────
    val theme      = prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.DARK)
    val notifMsg   = prefs.notifMsg.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSys   = prefs.notifSys.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifErr   = prefs.notifErr.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSound = prefs.notifSound.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifVib   = prefs.notifVib.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ── Live data ─────────────────────────────────────────────────────────────
    private val _chats = MutableStateFlow<List<ChatModel>>(emptyList())
    val chats: StateFlow<List<ChatModel>> = _chats

    private val _users = MutableStateFlow<List<UserModel>>(emptyList())
    val users: StateFlow<List<UserModel>> = _users

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages

    // Оптимистичные сообщения (isPending=true) пока Firestore не подтвердит
    private val _optimisticMessages = MutableStateFlow<List<MessageUiModel>>(emptyList())
    val optimisticMessages: StateFlow<List<MessageUiModel>> = _optimisticMessages

    private val _currentChat = MutableStateFlow<ChatModel?>(null)
    val currentChat: StateFlow<ChatModel?> = _currentChat

    private val _deletedMessageIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        _authState.value = if (repo.isLoggedIn) AuthState.Auth else AuthState.NotAuth
        if (repo.isLoggedIn) startListeners()
    }

    private fun startListeners() {
        val uid = repo.currentUid ?: return
        // Chats
        viewModelScope.launch {
            repo.getChatsFlow(uid).collect { list ->
                Log.d(TAG, "Chats: ${list.size}")
                _chats.value = list.sortedWith(compareByDescending<ChatModel> { it.pinned }.thenByDescending { it.lastMessageTime?.toDate()?.time ?: 0L })
            }
        }
        // All users
        viewModelScope.launch {
            repo.getAllUsersFlow().collect { list ->
                Log.d(TAG, "Users: ${list.size}")
                _users.value = list
            }
        }
        // Deleted messages
        viewModelScope.launch {
            repo.getDeletedMessagesFlow().collect { ids ->
                _deletedMessageIds.value = ids.toSet()
            }
        }
        // Watch own user for ban/freeze
        viewModelScope.launch {
            repo.getUserFlow(uid).collect { user ->
                if (user != null) {
                    if (user.isBanned) _uiEvent.emit(UiEvent.Error("Аккаунт заблокирован: ${user.banReason}"))
                }
            }
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun register(email: String, password: String, username: String, displayName: String, passphrase: String) {
        viewModelScope.launch {
            val res = repo.register(email.trim(), password, username.trim(), displayName.trim(), passphrase)
            if (res.success && res.user != null) {
                prefs.saveUser(res.user.id, res.user.username, res.user.displayName, res.user.avatarUrl, res.user.isAdmin)
                _authState.value = AuthState.Auth
                startListeners()
                _uiEvent.emit(UiEvent.Success("Добро пожаловать, ${res.user.displayName}!"))
            } else { _uiEvent.emit(UiEvent.Error(res.error)) }
        }
    }

    fun login(email: String, password: String, passphrase: String) {
        viewModelScope.launch {
            val res = repo.login(email.trim(), password, passphrase)
            if (res.success && res.user != null) {
                prefs.saveUser(res.user.id, res.user.username, res.user.displayName, res.user.avatarUrl, res.user.isAdmin)
                _authState.value = AuthState.Auth
                startListeners()
                _uiEvent.emit(UiEvent.Success("Добро пожаловать, ${res.user.displayName}!"))
            } else { _uiEvent.emit(UiEvent.Error(res.error)) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            prefs.clear()
            _authState.value = AuthState.NotAuth
            _chats.value = emptyList(); _users.value = emptyList(); _messages.value = emptyList()
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    fun openChat(chat: ChatModel) {
        _currentChat.value = chat
        _optimisticMessages.value = emptyList()
        viewModelScope.launch {
            repo.getMessagesFlow(chat.id).collect { list ->
                val deleted = _deletedMessageIds.value
                _messages.value = list.filter { it.id !in deleted }
                // Убираем оптимистичные которые уже пришли из Firestore
                val firestoreIds = list.map { it.id }.toSet()
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id !in firestoreIds }
                // Mark as read
                repo.currentUid?.let { uid ->
                    list.lastOrNull()?.let { msg ->
                        if (uid !in msg.readBy) repo.markAsRead(chat.id, msg.id, uid)
                    }
                }
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        val uid = repo.currentUid ?: return
        val dn = displayName.value
        val av = avatarUrl.value
        // Оптимистичное добавление
        val optimistic = MessageUiModel(
            id = java.util.UUID.randomUUID().toString(),
            chatId = chatId, senderId = uid, senderName = dn, senderAvatarUrl = av,
            text = text, imageUrl = "", imageBase64 = "", replyToId = "", replyToText = "",
            reactions = emptyMap(), readBy = emptyList(), isDeleted = false, isPinned = false,
            timestamp = System.currentTimeMillis(), isPending = true
        )
        _optimisticMessages.value = _optimisticMessages.value + optimistic
        viewModelScope.launch {
            try { repo.sendMessage(chatId, uid, dn, av, text) }
            catch (e: Exception) {
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id != optimistic.id }
                _uiEvent.emit(UiEvent.Error("Ошибка отправки: ${e.message}"))
            }
        }
    }

    fun sendImage(chatId: String, uri: Uri) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.uploadImageAndSend(chatId, uid, displayName.value, avatarUrl.value, uri) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка отправки фото")) }
        }
    }

    fun addReaction(chatId: String, messageId: String, emoji: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.addReaction(chatId, messageId, uid, emoji) }
            catch (e: Exception) { Log.e(TAG, "addReaction: ${e.message}") }
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.deleteMessage(chatId, messageId, uid) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка удаления")) }
        }
    }

    fun pinMessage(chatId: String, messageId: String, text: String) {
        viewModelScope.launch { repo.pinMessage(chatId, messageId, text) }
    }

    fun startDm(otherUser: UserModel) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val chatId = repo.createDm(uid, otherUser.id, otherUser.displayName)
                _uiEvent.emit(UiEvent.NavigateToChat(chatId))
            } catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка создания чата")) }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val chatId = repo.createGroup(name, memberIds, uid)
                _uiEvent.emit(UiEvent.NavigateToChat(chatId))
            } catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка создания группы")) }
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    fun updateProfile(newName: String, uri: Uri?, ctx: Context) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            val ok = repo.updateProfile(uid, newName, uri)
            if (ok) {
                prefs.saveUser(uid, userId.value, newName, avatarUrl.value, isAdmin.value)
                _uiEvent.emit(UiEvent.Success("Профиль обновлён"))
            } else _uiEvent.emit(UiEvent.Error("Ошибка обновления профиля"))
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun setTheme(t: AppTheme)          = viewModelScope.launch { prefs.saveTheme(t) }
    fun setNotifMsg(v: Boolean)        = viewModelScope.launch { prefs.saveNotifMsg(v) }
    fun setNotifSys(v: Boolean)        = viewModelScope.launch { prefs.saveNotifSys(v) }
    fun setNotifErr(v: Boolean)        = viewModelScope.launch { prefs.saveNotifErr(v) }
    fun setNotifSound(v: Boolean)      = viewModelScope.launch { prefs.saveNotifSound(v) }
    fun setNotifVib(v: Boolean)        = viewModelScope.launch { prefs.saveNotifVib(v) }

    fun getCacheSize(ctx: Context): String {
        val bytes = ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024*1024 -> "${bytes/1024}KB"
            else -> "${bytes/1024/1024}MB"
        }
    }

    fun clearCache(ctx: Context) {
        ctx.cacheDir.deleteRecursively()
        viewModelScope.launch { _uiEvent.emit(UiEvent.Success("Кэш очищен")) }
    }

    fun exportChats(ctx: Context) {
        viewModelScope.launch {
            try {
                val sb = StringBuilder("[")
                _chats.value.forEach { chat -> sb.append("""{"id":"${chat.id}","name":"${chat.name}"},""") }
                if (sb.endsWith(",")) sb.deleteCharAt(sb.length - 1)
                sb.append("]")
                val file = java.io.File(ctx.getExternalFilesDir(null), "veryschool_export_${System.currentTimeMillis()}.json")
                file.writeText(sb.toString())
                _uiEvent.emit(UiEvent.Success("Экспортировано: ${file.absolutePath}"))
            } catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка экспорта")) }
        }
    }

    // ── Admin helpers ─────────────────────────────────────────────────────────

    fun adminBan(targetId: String, reason: String)   = viewModelScope.launch { repo.banUser(repo.currentUid ?: return@launch, targetId, reason) }
    fun adminUnban(targetId: String)                 = viewModelScope.launch { repo.unbanUser(repo.currentUid ?: return@launch, targetId) }
    fun adminFreeze(targetId: String)                = viewModelScope.launch { repo.freezeUser(repo.currentUid ?: return@launch, targetId) }
    fun adminUnfreeze(targetId: String)              = viewModelScope.launch { repo.unfreezeUser(repo.currentUid ?: return@launch, targetId) }
    fun adminDeleteMsg(chatId: String, msgId: String) = viewModelScope.launch { repo.adminDeleteMessage(repo.currentUid ?: return@launch, chatId, msgId) }
    fun adminBotBroadcast(text: String)              = viewModelScope.launch { repo.broadcastBotMessage(repo.currentUid ?: return@launch, text) }
    fun adminBotToUser(uid: String, text: String)    = viewModelScope.launch { repo.sendBotMessage(uid, text) }
}

class MainViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(mc: Class<T>): T {
        val prefs = PrefsManager(ctx)
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(prefs) as T
    }
}
