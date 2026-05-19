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
import java.util.UUID

sealed class AuthState { object Unknown : AuthState(); object NotAuth : AuthState(); object Auth : AuthState() }
sealed class UiEvent {
    data class Error(val msg: String) : UiEvent()
    data class Success(val msg: String) : UiEvent()
    data class NavigateToChat(val chatId: String) : UiEvent()
}

class MainViewModel(private val prefs: PrefsManager) : ViewModel() {
    private val repo = FirebaseRepository()
    private val TAG  = "MainVM"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    // Индикатор загрузки (FIX #14)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val userId      = prefs.userId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val displayName = prefs.displayName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val avatarUrl   = prefs.avatarUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val isAdmin     = prefs.isAdmin.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val theme        = prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.DARK)
    val bubbleStyle  = prefs.bubbleStyle.stateIn(viewModelScope, SharingStarted.Eagerly, BubbleStyle.ROUND)
    val fontSize     = prefs.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, FontSize.MEDIUM)
    val chatBg       = prefs.chatBg.stateIn(viewModelScope, SharingStarted.Eagerly, ChatBg.NONE)
    val timeFormat   = prefs.timeFormat.stateIn(viewModelScope, SharingStarted.Eagerly, TimeFormat.H24)
    val compactMode  = prefs.compactMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifMsg     = prefs.notifMsg.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSys     = prefs.notifSys.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifErr     = prefs.notifErr.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSound   = prefs.notifSound.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifVib     = prefs.notifVib.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifPreview = prefs.notifPreview.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifGroups  = prefs.notifGroups.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hideOnline   = prefs.hideOnline.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hideRead     = prefs.hideRead.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hideStatus   = prefs.hideStatus.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoDownload = prefs.autoDownload.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val sendQuality  = prefs.sendQuality.stateIn(viewModelScope, SharingStarted.Eagerly, "MEDIUM")

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

    // FIX #2: не запускать listeners дважды
    private var listenersStarted = false
    private var chatMessagesJob: Job? = null
    private var listenersJob: Job? = null

    // ФИЧА: черновики сообщений
    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts

    // ФИЧА: поиск по сообщениям
    private val _searchResults = MutableStateFlow<List<MessageUiModel>>(emptyList())
    val searchResults: StateFlow<List<MessageUiModel>> = _searchResults

    // ФИЧА: непрочитанные счётчики
    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts

    init {
        _authState.value = if (repo.isLoggedIn) AuthState.Auth else AuthState.NotAuth
        if (repo.isLoggedIn) startListeners()
    }

    // FIX #3: отменяем всё при уничтожении VM
    override fun onCleared() {
        super.onCleared()
        chatMessagesJob?.cancel()
        listenersJob?.cancel()
        repo.cleanup()
    }

    private fun startListeners() {
        // FIX #2: строго один раз
        if (listenersStarted) return
        listenersStarted = true
        val uid = repo.currentUid ?: return

        listenersJob = viewModelScope.launch {
            launch {
                repo.getChatsFlow(uid).collect { list ->
                    _chats.value = list.sortedWith(
                        compareByDescending<ChatModel> { it.pinned }
                            .thenByDescending { it.lastMessageTime?.toDate()?.time ?: 0L }
                    )
                }
            }
            launch { repo.getAllUsersFlow().collect { _users.value = it } }
            launch { repo.getDeletedMessagesFlow().collect { ids -> _deletedMessageIds.value = ids.toSet() } }
            launch {
                repo.getUserFlow(uid).collect { user ->
                    if (user != null && user.isBanned)
                        _uiEvent.emit(UiEvent.Error("Аккаунт заблокирован: ${user.banReason}"))
                }
            }
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun register(email: String, password: String, username: String, displayName: String, passphrase: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repo.register(email.trim(), password, username.trim(), displayName.trim(), passphrase)
            _isLoading.value = false
            if (res.success && res.user != null) {
                prefs.saveUser(res.user.id, res.user.username, res.user.displayName, res.user.avatarUrl, res.user.isAdmin)
                _authState.value = AuthState.Auth
                startListeners()
                _uiEvent.emit(UiEvent.Success("Добро пожаловать, ${res.user.displayName}!"))
            } else _uiEvent.emit(UiEvent.Error(res.error))
        }
    }

    fun login(email: String, password: String, passphrase: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repo.login(email.trim(), password, passphrase)
            _isLoading.value = false
            if (res.success && res.user != null) {
                prefs.saveUser(res.user.id, res.user.username, res.user.displayName, res.user.avatarUrl, res.user.isAdmin)
                _authState.value = AuthState.Auth
                startListeners()
                _uiEvent.emit(UiEvent.Success("Добро пожаловать, ${res.user.displayName}!"))
            } else _uiEvent.emit(UiEvent.Error(res.error))
        }
    }

    fun logout() {
        viewModelScope.launch {
            chatMessagesJob?.cancel(); chatMessagesJob = null
            listenersJob?.cancel(); listenersJob = null
            listenersStarted = false
            repo.logout()
            prefs.clear()
            _authState.value = AuthState.NotAuth
            _chats.value = emptyList(); _users.value = emptyList()
            _messages.value = emptyList(); _optimisticMessages.value = emptyList()
            _currentChat.value = null; _drafts.value = emptyMap()
            _unreadCounts.value = emptyMap()
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fbUser = FirebaseAuth.getInstance().currentUser
                    ?: return@launch _uiEvent.emit(UiEvent.Error("Не авторизован"))
                val email = fbUser.email
                    ?: return@launch _uiEvent.emit(UiEvent.Error("Email не найден"))
                if (newPassword.length < 6) { _uiEvent.emit(UiEvent.Error("Пароль минимум 6 символов")); return@launch }
                fbUser.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
                fbUser.updatePassword(newPassword).await()
                _uiEvent.emit(UiEvent.Success("Пароль успешно изменён"))
            } catch (e: Exception) {
                val msg = when {
                    "wrong-password" in (e.message ?: "") || "INVALID_LOGIN" in (e.message ?: "") || "credential" in (e.message ?: "") -> "Неверный текущий пароль"
                    "weak-password" in (e.message ?: "") -> "Пароль слишком простой"
                    else -> "Ошибка: ${e.message?.take(80)}"
                }
                _uiEvent.emit(UiEvent.Error(msg))
            } finally { _isLoading.value = false }
        }
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    fun openChat(chat: ChatModel) {
        chatMessagesJob?.cancel(); chatMessagesJob = null
        _currentChat.value = chat
        _optimisticMessages.value = emptyList()
        _messages.value = emptyList()
        _searchResults.value = emptyList()
        _unreadCounts.value = _unreadCounts.value.toMutableMap().apply { put(chat.id, 0) }

        chatMessagesJob = viewModelScope.launch {
            repo.getMessagesFlow(chat.id).collect { list ->
                val deleted = _deletedMessageIds.value
                val filtered = list.filter { it.id !in deleted }
                _messages.value = filtered
                val realIds = filtered.map { it.id }.toSet()
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id !in realIds }
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
        // FIX #11: UUID гарантирует уникальность
        val tempId = "pending_${UUID.randomUUID()}"
        _optimisticMessages.value = _optimisticMessages.value + MessageUiModel(
            id = tempId, chatId = chatId, senderId = uid,
            senderName = displayName.value, senderAvatarUrl = avatarUrl.value,
            text = text, imageUrl = "", imageBase64 = "",
            replyToId = "", replyToText = "",
            reactions = emptyMap(), readBy = emptyList(),
            isDeleted = false, isPinned = false,
            timestamp = System.currentTimeMillis(), isPending = true
        )
        saveDraft(chatId, "")
        viewModelScope.launch {
            try {
                repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, text)
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id != tempId }
            } catch (e: Exception) {
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id != tempId }
                _uiEvent.emit(UiEvent.Error("Ошибка отправки: ${e.message}"))
            }
        }
    }

    fun sendImageBase64(chatId: String, uri: Uri, ctx: Context) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                    ?: return@launch _uiEvent.emit(UiEvent.Error("Не удалось прочитать файл"))
                if (bytes.size > 600_000) { _uiEvent.emit(UiEvent.Error("Файл слишком большой (макс. ~600KB)")); return@launch }
                val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when { "png" in mime -> "png"; "gif" in mime -> "gif"; "webp" in mime -> "webp"; else -> "jpg" }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, "", imageBase64 = "avatar://$ext/$b64")
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка отправки фото: ${e.message}"))
            } finally { _isLoading.value = false }
        }
    }

    // FIX #8: конвертируем URI в base64 перед отправкой
    fun updateProfile(newName: String, uri: Uri?, ctx: Context) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updates = mutableMapOf<String, Any>("displayName" to newName.trim())
                if (uri != null) {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null && bytes.size <= 200_000) {
                        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                        val ext = if ("png" in mime) "png" else "jpg"
                        updates["avatarUrl"] = "avatar://$ext/${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
                    } else if (bytes != null) {
                        _uiEvent.emit(UiEvent.Error("Аватар слишком большой (макс. ~150KB)"))
                        return@launch
                    }
                }
                repo.updateProfileMap(uid, updates)
                prefs.saveUser(uid, userId.value, newName.trim(), updates["avatarUrl"] as? String ?: avatarUrl.value, isAdmin.value)
                _uiEvent.emit(UiEvent.Success("Профиль обновлён"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            } finally { _isLoading.value = false }
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
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка удаления")) }
        }
    }

    fun pinMessage(chatId: String, messageId: String, text: String) =
        viewModelScope.launch { repo.pinMessage(chatId, messageId, text) }

    fun startDm(otherUser: UserModel) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { _uiEvent.emit(UiEvent.NavigateToChat(repo.createDm(uid, otherUser.id, otherUser.displayName))) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка создания чата")) }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { _uiEvent.emit(UiEvent.NavigateToChat(repo.createGroup(name, memberIds, uid))) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка создания группы")) }
        }
    }

    // ФИЧА: черновики
    fun saveDraft(chatId: String, text: String) {
        _drafts.value = _drafts.value.toMutableMap().apply {
            if (text.isBlank()) remove(chatId) else put(chatId, text)
        }
    }
    fun getDraft(chatId: String) = _drafts.value[chatId] ?: ""

    // ФИЧА: поиск по сообщениям
    fun searchMessages(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        _searchResults.value = _messages.value.map { it.toUi() }.filter {
            it.text.contains(query, ignoreCase = true) || it.senderName.contains(query, ignoreCase = true)
        }
    }

    // ФИЧА: пометить все прочитанными
    fun markAllRead(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            _messages.value.filter { uid !in it.readBy }.forEach { msg ->
                try { repo.markAsRead(chatId, msg.id, uid) } catch (_: Exception) {}
            }
            _unreadCounts.value = _unreadCounts.value.toMutableMap().apply { put(chatId, 0) }
        }
    }

    // ФИЧА: переслать сообщение
    fun forwardMessage(msg: MessageUiModel, toChatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.sendMessage(toChatId, uid, displayName.value, avatarUrl.value, "↩️ ${msg.senderName}:\n${msg.text}")
                _uiEvent.emit(UiEvent.Success("Переслано"))
            } catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка пересылки")) }
        }
    }

    fun updateStatus(emoji: String, text: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.updateProfileMap(uid, mapOf("statusEmoji" to emoji, "statusText" to text))
                _uiEvent.emit(UiEvent.Success("Статус обновлён"))
            } catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}")) }
        }
    }

    fun sendPoll(chatId: String, question: String, options: List<String>) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, "",
                isPoll = true, pollQuestion = question, pollOptions = options) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка отправки опроса: ${e.message}")) }
        }
    }

    fun sendMessageWithExpiry(chatId: String, text: String, expirySec: Int) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, text, expirySec = expirySec) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка отправки: ${e.message}")) }
        }
    }

    fun votePoll(chatId: String, messageId: String, optionIndex: Int) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.votePoll(chatId, messageId, uid, optionIndex) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка голосования")) }
        }
    }
    fun setBubbleStyle(v: BubbleStyle) = viewModelScope.launch { prefs.saveBubbleStyle(v) }
    fun setFontSize(v: FontSize)       = viewModelScope.launch { prefs.saveFontSize(v) }
    fun setChatBg(v: ChatBg)           = viewModelScope.launch { prefs.saveChatBg(v) }
    fun setTimeFormat(v: TimeFormat)   = viewModelScope.launch { prefs.saveTimeFormat(v) }
    fun setCompactMode(v: Boolean)     = viewModelScope.launch { prefs.saveCompactMode(v) }
    fun setNotifMsg(v: Boolean)        = viewModelScope.launch { prefs.saveNotifMsg(v) }
    fun setNotifSys(v: Boolean)        = viewModelScope.launch { prefs.saveNotifSys(v) }
    fun setNotifErr(v: Boolean)        = viewModelScope.launch { prefs.saveNotifErr(v) }
    fun setNotifSound(v: Boolean)      = viewModelScope.launch { prefs.saveNotifSound(v) }
    fun setNotifVib(v: Boolean)        = viewModelScope.launch { prefs.saveNotifVib(v) }
    fun setNotifPreview(v: Boolean)    = viewModelScope.launch { prefs.saveNotifPreview(v) }
    fun setNotifGroups(v: Boolean)     = viewModelScope.launch { prefs.saveNotifGroups(v) }
    fun setHideOnline(v: Boolean)      = viewModelScope.launch { prefs.saveHideOnline(v) }
    fun setHideRead(v: Boolean)        = viewModelScope.launch { prefs.saveHideRead(v) }
    fun setHideStatus(v: Boolean)      = viewModelScope.launch { prefs.saveHideStatus(v) }
    fun setAutoDownload(v: Boolean)    = viewModelScope.launch { prefs.saveAutoDownload(v) }
    fun setSendQuality(v: String)      = viewModelScope.launch { prefs.saveSendQuality(v) }

    fun getCacheSize(ctx: Context): String {
        val b = ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return when { b < 1024 -> "${b}B"; b < 1024*1024 -> "${b/1024}KB"; else -> "${b/1024/1024}MB" }
    }
    fun clearCache(ctx: Context) {
        ctx.cacheDir.deleteRecursively()
        viewModelScope.launch { _uiEvent.emit(UiEvent.Success("Кэш очищен")) }
    }
    fun exportChats(ctx: Context) {
        viewModelScope.launch {
            try {
                val json = "[" + _chats.value.joinToString(",") { """{"id":"${it.id}","name":"${it.name}"}""" } + "]"
                val f = java.io.File(ctx.getExternalFilesDir(null), "vs_export_${System.currentTimeMillis()}.json")
                f.writeText(json)
                _uiEvent.emit(UiEvent.Success("Экспортировано: ${f.name}"))
            } catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка экспорта")) }
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
