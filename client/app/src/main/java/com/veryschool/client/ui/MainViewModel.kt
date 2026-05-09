package com.veryschool.client.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.veryschool.client.data.models.*
import com.veryschool.client.data.prefs.*
import com.veryschool.client.data.repo.FirebaseRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

sealed class AuthState { object Unknown : AuthState(); object NotAuth : AuthState(); object Auth : AuthState() }
sealed class UiEvent { data class Error(val msg: String) : UiEvent(); data class Success(val msg: String) : UiEvent(); data class NavigateToChat(val chatId: String) : UiEvent() }

class MainViewModel(private val prefs: PrefsManager) : ViewModel() {
    private val repo = FirebaseRepository()
    private val TAG  = "MainVM"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    val userId      = prefs.userId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val displayName = prefs.displayName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val avatarUrl   = prefs.avatarUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val isAdmin     = prefs.isAdmin.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val theme       = prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.DARK)
    val notifMsg    = prefs.notifMsg.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSys    = prefs.notifSys.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifErr    = prefs.notifErr.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSound  = prefs.notifSound.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifVib    = prefs.notifVib.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _chats = MutableStateFlow<List<ChatModel>>(emptyList())
    val chats: StateFlow<List<ChatModel>> = _chats

    private val _users = MutableStateFlow<List<UserModel>>(emptyList())
    val users: StateFlow<List<UserModel>> = _users

    private val _messages = MutableStateFlow<List<MessageModel>>(emptyList())
    val messages: StateFlow<List<MessageModel>> = _messages

    private val _optimisticMessages = MutableStateFlow<List<MessageUiModel>>(emptyList())
    val optimisticMessages: StateFlow<List<MessageUiModel>> = _optimisticMessages

    private val _currentChat = MutableStateFlow<ChatModel?>(null)
    val currentChat: StateFlow<ChatModel?> = _currentChat

    private val _deletedMessageIds = MutableStateFlow<Set<String>>(emptySet())

    // БАГ ДУБЛИРОВАНИЯ FIX: единственный job для сообщений текущего чата
    private var chatMessagesJob: Job? = null

    init {
        _authState.value = if (repo.isLoggedIn) AuthState.Auth else AuthState.NotAuth
        if (repo.isLoggedIn) startListeners()
    }

    private fun startListeners() {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            repo.getChatsFlow(uid).collect { list ->
                _chats.value = list.sortedWith(
                    compareByDescending<ChatModel> { it.pinned }
                        .thenByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
                )
            }
        }
        viewModelScope.launch { repo.getAllUsersFlow().collect { _users.value = it } }
        viewModelScope.launch {
            repo.getDeletedMessagesFlow().collect { ids -> _deletedMessageIds.value = ids.toSet() }
        }
        viewModelScope.launch {
            repo.getUserFlow(uid).collect { user ->
                if (user != null && user.isBanned)
                    _uiEvent.emit(UiEvent.Error("Аккаунт заблокирован: ${user.banReason}"))
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
            chatMessagesJob?.cancel()
            chatMessagesJob = null
            repo.logout()
            prefs.clear()
            _authState.value = AuthState.NotAuth
            _chats.value = emptyList(); _users.value = emptyList()
            _messages.value = emptyList(); _optimisticMessages.value = emptyList()
            _currentChat.value = null
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val fbUser = FirebaseAuth.getInstance().currentUser
                    ?: return@launch _uiEvent.emit(UiEvent.Error("Не авторизован"))
                val email = fbUser.email
                    ?: return@launch _uiEvent.emit(UiEvent.Error("Email не найден"))
                if (newPassword.length < 6) {
                    _uiEvent.emit(UiEvent.Error("Пароль минимум 6 символов")); return@launch
                }
                val cred = EmailAuthProvider.getCredential(email, currentPassword)
                fbUser.reauthenticate(cred).await()
                fbUser.updatePassword(newPassword).await()
                _uiEvent.emit(UiEvent.Success("Пароль успешно изменён"))
            } catch (e: Exception) {
                val msg = when {
                    "wrong-password" in (e.message ?: "") || "credential" in (e.message ?: "") -> "Неверный текущий пароль"
                    "weak-password" in (e.message ?: "") -> "Пароль слишком простой"
                    else -> "Ошибка: ${e.message?.take(80) ?: "неизвестно"}"
                }
                _uiEvent.emit(UiEvent.Error(msg))
            }
        }
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    fun openChat(chat: ChatModel) {
        // ГЛАВНЫЙ ФИК ДУБЛИРОВАНИЯ: отменяем старый job, чистим стейт
        chatMessagesJob?.cancel()
        chatMessagesJob = null
        _currentChat.value = chat
        _optimisticMessages.value = emptyList()
        _messages.value = emptyList()

        chatMessagesJob = viewModelScope.launch {
            repo.getMessagesFlow(chat.id).collect { list ->
                val deleted = _deletedMessageIds.value
                val filtered = list.filter { it.id !in deleted }
                _messages.value = filtered
                // Убираем pending у которых реальный id уже пришёл
                val realIds = filtered.map { it.id }.toSet()
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id !in realIds }
                // Отмечаем прочитанным
                repo.currentUid?.let { uid ->
                    filtered.lastOrNull()?.let { msg ->
                        if (uid !in msg.readBy) {
                            try { repo.markAsRead(chat.id, msg.id, uid) } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        val uid = repo.currentUid ?: return
        // Уникальный tempId чтобы не было коллизий
        val tempId = "pending_${System.currentTimeMillis()}_${uid.take(4)}"
        val optimistic = MessageUiModel(
            id = tempId, chatId = chatId, senderId = uid,
            senderName = displayName.value, senderAvatarUrl = avatarUrl.value,
            text = text, imageUrl = "", imageBase64 = "",
            replyToId = "", replyToText = "",
            reactions = emptyMap(), readBy = emptyList(),
            isDeleted = false, isPinned = false,
            timestamp = System.currentTimeMillis(), isPending = true
        )
        _optimisticMessages.value = _optimisticMessages.value + optimistic
        viewModelScope.launch {
            try {
                repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, text)
                // Убираем pending — Firestore вернёт реальное
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id != tempId }
            } catch (e: Exception) {
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id != tempId }
                _uiEvent.emit(UiEvent.Error("Ошибка отправки: ${e.message}"))
            }
        }
    }

    // Отправка изображения как base64 — не использует Firebase Storage
    fun sendImageBase64(chatId: String, uri: Uri, ctx: Context) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                    ?: return@launch _uiEvent.emit(UiEvent.Error("Не удалось прочитать файл"))
                if (bytes.size > 600_000) {
                    _uiEvent.emit(UiEvent.Error("Файл слишком большой (макс. ~600KB)"))
                    return@launch
                }
                val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when { "png" in mime -> "png"; "gif" in mime -> "gif"; "webp" in mime -> "webp"; else -> "jpg" }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                // Формат: avatar://ext/base64data
                val dataUri = "avatar://$ext/$b64"
                repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, "", imageBase64 = dataUri)
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка отправки фото: ${e.message}"))
            }
        }
    }

    fun updateProfile(newName: String, uri: Uri?, ctx: Context) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>("displayName" to newName.trim())
                if (uri != null) {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null && bytes.size <= 200_000) {
                        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                        val ext = if ("png" in mime) "png" else "jpg"
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        updates["avatarUrl"] = "avatar://$ext/$b64"
                    } else if (bytes != null) {
                        _uiEvent.emit(UiEvent.Error("Аватар слишком большой (макс. ~150KB)"))
                        return@launch
                    }
                }
                repo.updateProfileMap(uid, updates)
                prefs.saveUser(uid, userId.value, newName.trim(),
                    updates["avatarUrl"] as? String ?: avatarUrl.value, isAdmin.value)
                _uiEvent.emit(UiEvent.Success("Профиль обновлён"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
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

    fun pinMessage(chatId: String, messageId: String, text: String) =
        viewModelScope.launch { repo.pinMessage(chatId, messageId, text) }

    fun startDm(otherUser: UserModel) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { _uiEvent.emit(UiEvent.NavigateToChat(repo.createDm(uid, otherUser.id, otherUser.displayName))) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка создания чата")) }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { _uiEvent.emit(UiEvent.NavigateToChat(repo.createGroup(name, memberIds, uid))) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка создания группы")) }
        }
    }

    fun setTheme(t: AppTheme)     = viewModelScope.launch { prefs.saveTheme(t) }
    fun setNotifMsg(v: Boolean)   = viewModelScope.launch { prefs.saveNotifMsg(v) }
    fun setNotifSys(v: Boolean)   = viewModelScope.launch { prefs.saveNotifSys(v) }
    fun setNotifErr(v: Boolean)   = viewModelScope.launch { prefs.saveNotifErr(v) }
    fun setNotifSound(v: Boolean) = viewModelScope.launch { prefs.saveNotifSound(v) }
    fun setNotifVib(v: Boolean)   = viewModelScope.launch { prefs.saveNotifVib(v) }

    fun getCacheSize(ctx: Context): String {
        val b = ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return when { b < 1024 -> "${b}B"; b < 1024*1024 -> "${b/1024}KB"; else -> "${b/1024/1024}MB" }
    }
    fun clearCache(ctx: Context) { ctx.cacheDir.deleteRecursively(); viewModelScope.launch { _uiEvent.emit(UiEvent.Success("Кэш очищен")) } }
    fun exportChats(ctx: Context) {
        viewModelScope.launch {
            try {
                val json = "[" + _chats.value.joinToString(",") { """{"id":"${it.id}","name":"${it.name}"}""" } + "]"
                val f = java.io.File(ctx.getExternalFilesDir(null), "vs_export_${System.currentTimeMillis()}.json")
                f.writeText(json)
                _uiEvent.emit(UiEvent.Success("Экспортировано: ${f.name}"))
            } catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка экспорта")) }
        }
    }

    fun adminBan(targetId: String, reason: String)    = viewModelScope.launch { repo.banUser(repo.currentUid ?: return@launch, targetId, reason) }
    fun adminUnban(targetId: String)                  = viewModelScope.launch { repo.unbanUser(repo.currentUid ?: return@launch, targetId) }
    fun adminFreeze(targetId: String)                 = viewModelScope.launch { repo.freezeUser(repo.currentUid ?: return@launch, targetId) }
    fun adminUnfreeze(targetId: String)               = viewModelScope.launch { repo.unfreezeUser(repo.currentUid ?: return@launch, targetId) }
    fun adminDeleteMsg(chatId: String, msgId: String) = viewModelScope.launch { repo.adminDeleteMessage(repo.currentUid ?: return@launch, chatId, msgId) }
    fun adminBotBroadcast(text: String)               = viewModelScope.launch { repo.broadcastBotMessage(repo.currentUid ?: return@launch, text) }
    fun adminBotToUser(uid: String, text: String)     = viewModelScope.launch { repo.sendBotMessage(uid, text) }
}

class MainViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(mc: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(PrefsManager(ctx)) as T
    }
}
