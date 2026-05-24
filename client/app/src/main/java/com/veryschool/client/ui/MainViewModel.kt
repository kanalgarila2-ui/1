package com.veryschool.client.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
    data class Error(val msg: String)   : UiEvent()
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

    // НОВАЯ ФИЧА #11: typing indicator
    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers: StateFlow<List<String>> = _typingUsers

    // НОВАЯ ФИЧА #15: сортировка чатов — закреплённые + непрочитанные сверху
    val sortedChats: StateFlow<List<ChatModel>> get() = _chats

    private var listenersStarted = false
    private var chatMessagesJob: Job? = null
    private var typingJob: Job? = null
    private var listenersJob: Job? = null

    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts

    private val _searchResults = MutableStateFlow<List<MessageUiModel>>(emptyList())
    val searchResults: StateFlow<List<MessageUiModel>> = _searchResults

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts

    // НОВАЯ ФИЧА #18: архивированные чаты
    private val _archivedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedChatIds: StateFlow<Set<String>> = _archivedChatIds
    // Добавь после других MutableStateFlow:

    // Для избранных сообщений
    private val _starredMessages = MutableStateFlow<List<StarredMessage>>(emptyList())
    val starredMessages: StateFlow<List<StarredMessage>> = _starredMessages.asStateFlow()

    // Для медиа-галереи
    private val _mediaMessages = MutableStateFlow<List<MessageModel>>(emptyList())
    val mediaMessages: StateFlow<List<MessageModel>> = _mediaMessages.asStateFlow()

    // Для глобального поиска
    private val _globalSearchResults = MutableStateFlow<List<Pair<ChatModel, MessageUiModel>>>(emptyList())
    val globalSearchResults: StateFlow<List<Pair<ChatModel, MessageUiModel>>> = _globalSearchResults.asStateFlow()
    
    init {
        _authState.value = if (repo.isLoggedIn) AuthState.Auth else AuthState.NotAuth
        if (repo.isLoggedIn) startListeners()
    }

    override fun onCleared() {
        super.onCleared()
        chatMessagesJob?.cancel()
        typingJob?.cancel()
        listenersJob?.cancel()
        repo.cleanup()
    }

    private fun startListeners() {
        if (listenersStarted) return
        listenersStarted = true
        val uid = repo.currentUid ?: return

        listenersJob = viewModelScope.launch {
            launch {
                repo.getChatsFlow(uid).collect { list ->
                    // НОВАЯ ФИЧА #15: сортировка — закреплённые + непрочитанные сверху
                    _chats.value = list.sortedWith(
                        compareByDescending<ChatModel> { it.pinned }
                            .thenByDescending { (_unreadCounts.value[it.id] ?: 0) > 0 }
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
            typingJob?.cancel(); typingJob = null
            listenersJob?.cancel(); listenersJob = null
            listenersStarted = false
            repo.logout()
            prefs.clear()
            _authState.value = AuthState.NotAuth
            _chats.value = emptyList(); _users.value = emptyList()
            _messages.value = emptyList(); _optimisticMessages.value = emptyList()
            _currentChat.value = null; _drafts.value = emptyMap()
            _unreadCounts.value = emptyMap(); _typingUsers.value = emptyList()
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
        typingJob?.cancel(); typingJob = null
        _currentChat.value = chat
        _optimisticMessages.value = emptyList()
        _messages.value = emptyList()
        _searchResults.value = emptyList()
        _unreadCounts.value = _unreadCounts.value.toMutableMap().apply { put(chat.id, 0) }
        _typingUsers.value = emptyList()

        chatMessagesJob = viewModelScope.launch {
            repo.getMessagesFlow(chat.id).collect { list ->
                val deleted = _deletedMessageIds.value
                val filtered = list.filter { it.id !in deleted }
                _messages.value = filtered

                // НОВАЯ ФИЧА #13: авто-удаление истёкших из Firestore
                val now = System.currentTimeMillis()
                filtered.filter { it.expiresAt != null && it.expiresAt.toDate().time <= now }
                    .forEach { expired -> launch { repo.deleteExpiredMessage(chat.id, expired.id) } }

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

        // НОВАЯ ФИЧА #11: слушаем typing indicator
        typingJob = viewModelScope.launch {
            repo.getTypingFlow(chat.id).collect { typingUids ->
                val uid = repo.currentUid ?: return@collect
                val others = typingUids.filter { it != uid }
                val names = others.mapNotNull { id -> _users.value.firstOrNull { it.id == id }?.displayName }
                _typingUsers.value = names
            }
        }
    }

    // BUG-12 FIX: sendMessage передаёт replyToId корректно
    fun sendMessage(chatId: String, text: String, replyToId: String = "", replyToText: String = "") {
        val uid = repo.currentUid ?: return
        val tempId = "pending_${UUID.randomUUID()}"
        _optimisticMessages.value = _optimisticMessages.value + MessageUiModel(
            id = tempId, chatId = chatId, senderId = uid,
            senderName = displayName.value, senderAvatarUrl = avatarUrl.value,
            text = text, imageUrl = "", imageBase64 = "",
            replyToId = replyToId, replyToText = replyToText,
            reactions = emptyMap(), readBy = emptyList(),
            isDeleted = false, isPinned = false,
            timestamp = System.currentTimeMillis(), isPending = true
        )
        saveDraft(chatId, "")
        viewModelScope.launch {
            try {
                repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, text,
                    replyToId = replyToId, replyToText = replyToText)
                _optimisticMessages.value = _optimisticMessages.value.filter { it.id != tempId }
                // Сбрасываем typing после отправки
                repo.setTypingStatus(chatId, uid, false)
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
                        _uiEvent.emit(UiEvent.Error("Аватар слишком большой (макс. ~150KB)")); return@launch
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

    fun saveDraft(chatId: String, text: String) {
        _drafts.value = _drafts.value.toMutableMap().apply {
            if (text.isBlank()) remove(chatId) else put(chatId, text)
        }
    }
    fun getDraft(chatId: String) = _drafts.value[chatId] ?: ""

    fun searchMessages(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        _searchResults.value = _messages.value.map { it.toUi() }.filter {
            it.text.contains(query, ignoreCase = true) || it.senderName.contains(query, ignoreCase = true)
        }
    }

    fun markAllRead(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            _messages.value.filter { uid !in it.readBy }.forEach { msg ->
                try { repo.markAsRead(chatId, msg.id, uid) } catch (_: Exception) {}
            }
            _unreadCounts.value = _unreadCounts.value.toMutableMap().apply { put(chatId, 0) }
        }
    }

    // BUG-13 FIX: forwardMessage правильно передаёт toChatId
    fun forwardMessage(msg: MessageUiModel, toChatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.sendMessage(toChatId, uid, displayName.value, avatarUrl.value,
                    "↩️ ${msg.senderName}:\n${msg.text}")
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

    // НОВАЯ ФИЧА #17: typing indicator
    private var typingDebounceJob: Job? = null
    fun onUserTyping(chatId: String) {
        val uid = repo.currentUid ?: return
        typingDebounceJob?.cancel()
        typingDebounceJob = viewModelScope.launch {
            repo.setTypingStatus(chatId, uid, true)
            delay(4000) // автосброс через 4 секунды
            repo.setTypingStatus(chatId, uid, false)
        }
    }
    fun onUserStoppedTyping(chatId: String) {
        val uid = repo.currentUid ?: return
        typingDebounceJob?.cancel()
        viewModelScope.launch { repo.setTypingStatus(chatId, uid, false) }
    }

    // НОВАЯ ФИЧА #18: архивирование чата
    fun archiveChat(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.archiveChat(chatId, uid)
                _archivedChatIds.value = _archivedChatIds.value + chatId
                _uiEvent.emit(UiEvent.Success("Чат заархивирован"))
            } catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка архивирования")) }
        }
    }
    fun unarchiveChat(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.unarchiveChat(chatId, uid)
                _archivedChatIds.value = _archivedChatIds.value - chatId
            } catch (_: Exception) {}
        }
    }

    fun setTheme(v: AppTheme)          = viewModelScope.launch { prefs.saveTheme(v) }
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

    // BUG-25 FIX: getCacheSize перенесён в репозиторий, nid() в NotificationHelper исправлен отдельно
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

    // ── Избранные сообщения ────────────────────────────────────────────────
    fun loadStarredMessages() {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.getStarredMessagesFlow(uid).collect { messages ->
                    _starredMessages.value = messages
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadStarredMessages error", e)
            }
        }
    }

    fun starMessage(chatId: String, msg: MessageUiModel) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val originalMsg = _messages.value.firstOrNull { it.id == msg.id }
                if (originalMsg == null) {
                    _uiEvent.emit(UiEvent.Error("Сообщение не найдено"))
                    return@launch
                }
                if (uid in msg.starredBy) {
                    repo.unstarMessage(chatId, msg.id, uid)
                    _uiEvent.emit(UiEvent.Success("Убрано из избранного"))
                } else {
                    repo.starMessage(chatId, msg.id, uid, originalMsg)
                    _uiEvent.emit(UiEvent.Success("⭐ Добавлено в избранное"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

// ── Медиа галерея ────────────────────────────────────────────────────
    fun loadMediaGallery(chatId: String) {
        viewModelScope.launch {
            try {
                val media = repo.getMediaMessages(chatId)
                _mediaMessages.value = media
            } catch (e: Exception) {
                Log.e(TAG, "loadMediaGallery error", e)
                _mediaMessages.value = emptyList()
            }
        }
    }

// ── Глобальный поиск ─────────────────────────────────────────────────
    fun globalSearch(query: String) {
        if (query.isBlank()) {
            _globalSearchResults.value = emptyList()
            return
        }
        val q = query.lowercase()
        val results = mutableListOf<Pair<ChatModel, MessageUiModel>>()
    
    // Поиск в чатах по имени
        _chats.value.forEach { chat ->
            if (chat.name.contains(q, ignoreCase = true)) {
            // Создаем заглушку сообщения для отображения чата в результатах
                val dummyMsg = MessageUiModel(
                    id = "dummy_${chat.id}",
                    text = "Чат: ${chat.name}",
                    chatId = chat.id,
                    senderId = "",
                    senderName = "",
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.SYSTEM,
                    starredBy = emptyList()
                )
                results.add(chat to dummyMsg)
            }
        }
    
    // Поиск в сообщениях
        _messages.value.forEach { msg ->
            if (msg.text.contains(q, ignoreCase = true)) {
                val chat = _chats.value.firstOrNull { it.id == msg.chatId }
                if (chat != null) {
                    results.add(chat to msg.toUi())
                }
            }
        }
    
        _globalSearchResults.value = results
    }

// ── Редактирование сообщения ─────────────────────────────────────────
    fun editMessage(chatId: String, messageId: String, newText: String) {
        viewModelScope.launch {
            try {
                repo.editMessage(chatId, messageId, newText)
                _uiEvent.emit(UiEvent.Success("Сообщение изменено"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

// ── Кто прочитал ─────────────────────────────────────────────────────
    private val _readByUsers = MutableStateFlow<List<UserModel>>(emptyList())
    val readByUsers: StateFlow<List<UserModel>> = _readByUsers

    fun loadReadBy(chatId: String, messageId: String) {
        viewModelScope.launch {
            try {
                val userIds = repo.getReadByUsers(chatId, messageId)
                val users = _users.value.filter { it.id in userIds }
                _readByUsers.value = users
            } catch (e: Exception) {
                Log.e(TAG, "loadReadBy error", e)
            }
        }
    }

// ── Блокировка пользователя ──────────────────────────────────────────
    fun blockUser(targetUid: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.blockUser(uid, targetUid)
                _uiEvent.emit(UiEvent.Success("Пользователь заблокирован"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun unblockUser(targetUid: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.unblockUser(uid, targetUid)
                _uiEvent.emit(UiEvent.Success("Пользователь разблокирован"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

// ── Верификация (admin) ──────────────────────────────────────────────
    fun setVerified(targetUid: String, verified: Boolean) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.setVerified(uid, targetUid, verified)
                val msg = if (verified) "Пользователь верифицирован" else "Верификация снята"
                _uiEvent.emit(UiEvent.Success(msg))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

// ── Мьют чата ────────────────────────────────────────────────────────
    fun muteChat(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.muteChat(uid, chatId)
                _uiEvent.emit(UiEvent.Success("Уведомления отключены"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun unmuteChat(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.unmuteChat(uid, chatId)
                _uiEvent.emit(UiEvent.Success("Уведомления включены"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

// ── Закреплённые ссылки ──────────────────────────────────────────────
    fun addPinnedLink(chatId: String, link: String) {
        viewModelScope.launch {
            try {
                repo.addPinnedLink(chatId, link)
                _uiEvent.emit(UiEvent.Success("Ссылка закреплена"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

    fun removePinnedLink(chatId: String, link: String) {
        viewModelScope.launch {
            try {
                repo.removePinnedLink(chatId, link)
                _uiEvent.emit(UiEvent.Success("Ссылка откреплена"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }

// ── Покинуть группу ──────────────────────────────────────────────────
    fun leaveGroup(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.leaveGroup(chatId, uid)
                _chats.value = _chats.value.filter { it.id != chatId }
                _uiEvent.emit(UiEvent.Success("Вы покинули группу"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}"))
            }
        }
    }
}

fun MessageModel.toUi(): MessageUiModel {
    return MessageUiModel(
        id = this.id,
        text = this.text,
        chatId = this.chatId,
        senderId = this.senderId,
        senderName = this.senderName,
        senderAvatar = this.senderAvatar,
        timestamp = this.timestamp,
        type = this.type,
        mediaUrl = this.mediaUrl,
        starredBy = this.starredBy ?: emptyList(),
        replyToId = this.replyToId,
        replyToText = this.replyToText
    )
}

class MainViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(mc: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(PrefsManager(ctx)) as T
    }
}

    // ── ФИЧА #1: редактирование сообщения ────────────────────────────────────
    fun editMessage(chatId: String, messageId: String, newText: String) {
        viewModelScope.launch {
            try { repo.editMessage(chatId, messageId, newText); _uiEvent.emit(UiEvent.Success("Изменено")) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка редактирования")) }
        }
    }

    // ── ФИЧА #3: звёздочка ────────────────────────────────────────────────────
    private val _starredMessages = MutableStateFlow<List<StarredMessage>>(emptyList())
    val starredMessages: StateFlow<List<StarredMessage>> = _starredMessages

    fun loadStarredMessages() {
        val uid = repo.currentUid ?: return
        viewModelScope.launch { repo.getStarredMessagesFlow(uid).collect { _starredMessages.value = it } }
    }

    fun starMessage(chatId: String, msg: MessageUiModel) {
        val uid = repo.currentUid ?: return
        val isStarred = uid in msg.starredBy
        viewModelScope.launch {
            try {
                val model = _messages.value.firstOrNull { it.id == msg.id }
                if (model == null) {
                    _uiEvent.emit(UiEvent.Error("Сообщение не найдено"))
                    return@launch
                }
                if (isStarred) repo.unstarMessage(chatId, msg.id, uid)
                else repo.starMessage(chatId, msg.id, uid, model) // ← model должен быть MessageModel
                _uiEvent.emit(UiEvent.Success(if (isStarred) "Убрано из избранного" else "⭐ Добавлено в избранное"))
            } catch (_: Exception) { 
                _uiEvent.emit(UiEvent.Error("Ошибка")) 
            }
        }
    }

    // ── ФИЧА #4: медиа-галерея ────────────────────────────────────────────────
    private val _mediaMessages = MutableStateFlow<List<MessageModel>>(emptyList())
    val mediaMessages: StateFlow<List<MessageModel>> = _mediaMessages

    fun loadMediaGallery(chatId: String) {
        viewModelScope.launch {
            try { _mediaMessages.value = repo.getMediaMessages(chatId) }
            catch (_: Exception) { _mediaMessages.value = emptyList() }
        }
    }

    // ── ФИЧА #6: голосовые сообщения ─────────────────────────────────────────
    fun sendVoiceMessage(chatId: String, base64: String, durationSec: Int) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, "", voiceBase64 = base64, voiceDurationSec = durationSec) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}")) }
        }
    }

    // ── ФИЧА #7: GIF ──────────────────────────────────────────────────────────
    fun sendGif(chatId: String, gifUrl: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.sendMessage(chatId, uid, displayName.value, avatarUrl.value, "", gifUrl = gifUrl) }
            catch (e: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка: ${e.message}")) }
        }
    }

    // ── ФИЧА #8: блокировка пользователя ─────────────────────────────────────
    fun blockUser(targetUid: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.blockUser(uid, targetUid); _uiEvent.emit(UiEvent.Success("Пользователь заблокирован")) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }
    fun unblockUser(targetUid: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.unblockUser(uid, targetUid); _uiEvent.emit(UiEvent.Success("Разблокировано")) }
            catch (_: Exception) {}
        }
    }

    // ── ФИЧА #9: поиск пользователей ─────────────────────────────────────────
    private val _foundUser = MutableStateFlow<UserModel?>(null)
    val foundUser: StateFlow<UserModel?> = _foundUser

    fun searchUserById(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _foundUser.value = repo.findUserByNumericIdOrUsername(query)
            _isLoading.value = false
            if (_foundUser.value == null) _uiEvent.emit(UiEvent.Error("Пользователь не найден"))
        }
    }
    fun clearFoundUser() { _foundUser.value = null }

    // ── ФИЧА #12: invite link ─────────────────────────────────────────────────
    fun joinByInviteCode(code: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val chatId = repo.joinByInviteCode(uid, code)
                if (chatId != null) _uiEvent.emit(UiEvent.NavigateToChat(chatId))
                else _uiEvent.emit(UiEvent.Error("Неверный код приглашения"))
            } catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }

    // ── ФИЧА #13: покинуть группу ─────────────────────────────────────────────
    fun leaveGroup(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                repo.leaveGroup(chatId, uid)
                _chats.value = _chats.value.filter { it.id != chatId }
                _uiEvent.emit(UiEvent.Success("Вы покинули группу"))
            } catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }

    // ── ФИЧА #15: кто прочитал ────────────────────────────────────────────────
    private val _readByUsers = MutableStateFlow<List<UserModel>>(emptyList())
    val readByUsers: StateFlow<List<UserModel>> = _readByUsers

    fun loadReadBy(chatId: String, messageId: String) {
        viewModelScope.launch {
            try {
                val uids = repo.getReadByUsers(chatId, messageId)
                _readByUsers.value = _users.value.filter { it.id in uids }
            } catch (_: Exception) {}
        }
    }

    // ── ФИЧА #16: DND ─────────────────────────────────────────────────────────
    fun setDnd(enabled: Boolean, from: String = "23:00", to: String = "07:00") {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.setDnd(uid, enabled, from, to) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }

    // ── ФИЧА #17: мьют чата ──────────────────────────────────────────────────
    fun muteChat(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.muteChat(uid, chatId); _uiEvent.emit(UiEvent.Success("Уведомления отключены")) }
            catch (_: Exception) {}
        }
    }
    fun unmuteChat(chatId: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch { try { repo.unmuteChat(uid, chatId) } catch (_: Exception) {} }
    }

    // ── ФИЧА #18: история имён ────────────────────────────────────────────────
    fun updateDisplayNameWithHistory(newName: String) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try {
                val me = _users.value.firstOrNull { it.id == uid }
                repo.updateDisplayName(uid, newName.trim(), me?.nameHistory ?: emptyList())
                prefs.saveUser(uid, userId.value, newName.trim(), avatarUrl.value, isAdmin.value)
                _uiEvent.emit(UiEvent.Success("Имя обновлено"))
            } catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }

    // ── ФИЧА #20: верификация (только admin) ─────────────────────────────────
    fun setVerified(targetUid: String, verified: Boolean) {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            try { repo.setVerified(uid, targetUid, verified); _uiEvent.emit(UiEvent.Success(if (verified) "✓ Верифицирован" else "Верификация снята")) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }

    // ── ФИЧА #24: глобальный поиск по всем чатам ─────────────────────────────
    private val _globalSearchResults = MutableStateFlow<List<Pair<ChatModel, MessageUiModel>>>(emptyList())
    val globalSearchResults: StateFlow<List<Pair<ChatModel, MessageUiModel>>> = _globalSearchResults

    fun globalSearch(query: String) {
        if (query.isBlank()) { _globalSearchResults.value = emptyList(); return }
        val q = query.lowercase()
        val results = mutableListOf<Pair<ChatModel, MessageUiModel>>()
        _chats.value.forEach { chat: ChatModel ->
            // Ищем в имени чата
            if (chat.name.contains(q, ignoreCase = true)) {
                // добавляем заглушку
            }
        }
        // Ищем в текущих загруженных сообщениях
        _messages.value.forEach { msg ->
            if (msg.text.contains(q, ignoreCase = true)) {
                val chat = _chats.value.firstOrNull { it.id == msg.chatId }
                if (chat != null) results.add(chat to msg.toUi())
            }
        }
        _globalSearchResults.value = results
    }

    // ── ФИЧА #11: прикреплённые ссылки ───────────────────────────────────────
    fun addPinnedLink(chatId: String, link: String) {
        viewModelScope.launch {
            try { repo.addPinnedLink(chatId, link); _uiEvent.emit(UiEvent.Success("Ссылка прикреплена")) }
            catch (_: Exception) { _uiEvent.emit(UiEvent.Error("Ошибка")) }
        }
    }
    fun removePinnedLink(chatId: String, link: String) {
        viewModelScope.launch { try { repo.removePinnedLink(chatId, link) } catch (_: Exception) {} }
    }

    // ── Архив ────────────────────────────────────────────────────────────────
