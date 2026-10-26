package com.veryschool.client

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.veryschool.client.notifications.NotificationHelper
import com.veryschool.client.ui.*
import com.veryschool.client.ui.screens.*
import com.veryschool.client.ui.theme.VerySchoolTheme
import kotlinx.coroutines.flow.collectLatest

object Nav {
    const val AUTH        = "auth"
    const val CHAT_LIST   = "chat_list"
    const val PROFILE     = "profile"
    const val SETTINGS    = "settings"
    const val CHAT        = "chat/{chatId}"
    const val USER_PROF   = "user/{userId}"
    const val GROUP_INFO  = "group_info/{chatId}"
    const val MEDIA       = "media/{chatId}"
    const val STARRED     = "starred"
    const val GLOBAL_SEARCH = "global_search"
    const val ABOUT       = "about"
    const val PRIVACY     = "privacy"
    const val TERMS       = "terms"
    const val GUIDELINES  = "guidelines"
    fun chat(id: String)      = "chat/$id"
    fun user(id: String)      = "user/$id"
    fun groupInfo(id: String) = "group_info/$id"
    fun media(id: String)     = "media/$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper(this).createChannels()
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModelFactory(applicationContext))
            val theme by vm.theme.collectAsStateWithLifecycle()
            VerySchoolTheme(appTheme = theme) {
                val nav = rememberNavController()

                val authState    by vm.authState.collectAsStateWithLifecycle()
                val isLoading    by vm.isLoading.collectAsStateWithLifecycle()
                val currentUid   by vm.userId.collectAsStateWithLifecycle()
                val displayName  by vm.displayName.collectAsStateWithLifecycle()
                val avatarUrl    by vm.avatarUrl.collectAsStateWithLifecycle()
                val isAdmin      by vm.isAdmin.collectAsStateWithLifecycle()
                val chats        by vm.chats.collectAsStateWithLifecycle()
                val users        by vm.users.collectAsStateWithLifecycle()
                val messages     by vm.messages.collectAsStateWithLifecycle()
                val optimistic   by vm.optimisticMessages.collectAsStateWithLifecycle()
                val currentChat  by vm.currentChat.collectAsStateWithLifecycle()
                val appTheme     by vm.theme.collectAsStateWithLifecycle()
                val notifMsg     by vm.notifMsg.collectAsStateWithLifecycle()
                val notifSys     by vm.notifSys.collectAsStateWithLifecycle()
                val notifErr     by vm.notifErr.collectAsStateWithLifecycle()
                val notifSound   by vm.notifSound.collectAsStateWithLifecycle()
                val notifVib     by vm.notifVib.collectAsStateWithLifecycle()
                val notifPreview by vm.notifPreview.collectAsStateWithLifecycle()
                val notifGroups  by vm.notifGroups.collectAsStateWithLifecycle()
                val bubbleStyle  by vm.bubbleStyle.collectAsStateWithLifecycle()
                val fontSize     by vm.fontSize.collectAsStateWithLifecycle()
                val chatBg       by vm.chatBg.collectAsStateWithLifecycle()
                val timeFormat   by vm.timeFormat.collectAsStateWithLifecycle()
                val compactMode  by vm.compactMode.collectAsStateWithLifecycle()
                val hideOnline   by vm.hideOnline.collectAsStateWithLifecycle()
                val hideRead     by vm.hideRead.collectAsStateWithLifecycle()
                val hideStatus   by vm.hideStatus.collectAsStateWithLifecycle()
                val autoDownload by vm.autoDownload.collectAsStateWithLifecycle()
                val sendQuality  by vm.sendQuality.collectAsStateWithLifecycle()
                val unreadCounts by vm.unreadCounts.collectAsStateWithLifecycle()
                val drafts by vm.drafts.collectAsStateWithLifecycle()
                val typingUsers by vm.typingUsers.collectAsStateWithLifecycle()
                val starred by vm.starredMessages.collectAsStateWithLifecycle()  // ← теперь List<StarredMessage>
                val mediaMessages by vm.mediaMessages.collectAsStateWithLifecycle()  // ← List<MessageModel>
                val globalResults by vm.globalSearchResults.collectAsStateWithLifecycle()  // ← List<Pair<ChatModel, MessageUiModel>>

                // Текущий пользователь
                val me = users.firstOrNull { it.id == currentUid }
                val blockedUserIds = (me?.blockedUsers ?: emptyList()).toSet()

                LaunchedEffect(Unit) {
                    vm.uiEvent.collectLatest { event ->
                        when (event) {
                            is UiEvent.Error   -> Toast.makeText(this@MainActivity, event.msg, Toast.LENGTH_LONG).show()
                            is UiEvent.Success -> Toast.makeText(this@MainActivity, event.msg, Toast.LENGTH_SHORT).show()
                            is UiEvent.NavigateToChat -> {
                                val chat = chats.firstOrNull { it.id == event.chatId }
                                if (chat != null) {
                                    vm.openChat(chat)
                                    nav.navigate(Nav.chat(event.chatId))
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(authState) {
                    when (authState) {
                        AuthState.Auth    -> nav.navigate(Nav.CHAT_LIST) { popUpTo(0) { inclusive = true } }
                        AuthState.NotAuth -> nav.navigate(Nav.AUTH)      { popUpTo(0) { inclusive = true } }
                        AuthState.Unknown -> {}
                    }
                }

                NavHost(nav, startDestination = Nav.AUTH) {

                    // ── Авторизация ───────────────────────────────────────────
                    composable(Nav.AUTH) {
                        AuthScreen(
                            onLogin    = { e, p, ph -> vm.login(e, p, ph) },
                            onRegister = { e, p, u, dn, ph -> vm.register(e, p, u, dn, ph) },
                            isLoading  = isLoading
                        )
                    }

                    // ── Список чатов ──────────────────────────────────────────
                    composable(Nav.CHAT_LIST) {
                        ChatListScreen(
                            chats        = chats,
                            users        = users,
                            currentUserId = currentUid,
                            displayName  = displayName,
                            avatarUrl    = avatarUrl,
                            unreadCounts = unreadCounts,
                            onChatClick  = { chat -> vm.openChat(chat); nav.navigate(Nav.chat(chat.id)) },
                            onNewDm      = { user -> vm.startDm(user) },
                            onNewGroup   = { name, ids -> vm.createGroup(name, ids) },
                            onProfile    = { nav.navigate(Nav.PROFILE) },
                            onSettings   = { nav.navigate(Nav.SETTINGS) },
                            onAbout      = { nav.navigate(Nav.ABOUT) },
                            onPrivacy    = { nav.navigate(Nav.PRIVACY) },
                            onTerms      = { nav.navigate(Nav.TERMS) },
                            onGuidelines = { nav.navigate(Nav.GUIDELINES) },
                            onGlobalSearch = { nav.navigate(Nav.GLOBAL_SEARCH) },
                            onStarred    = { vm.loadStarredMessages(); nav.navigate(Nav.STARRED) },
                            onMuteChat   = { chatId -> vm.muteChat(chatId) },
                            onArchiveChat = { chatId -> vm.archiveChat(chatId) }
                        )
                    }

                    // ── Чат ───────────────────────────────────────────────────
                    composable(Nav.CHAT) { back ->
                        val chatId = back.arguments?.getString("chatId") ?: return@composable
                        val chat = currentChat ?: chats.firstOrNull { it.id == chatId } ?: return@composable
                        ChatScreen(
                            chat = chat,
                            messages = messages,
                            optimisticMessages = optimistic,
                            currentUserId = currentUid,
                            currentUserFrozen = me?.isFrozen ?: false,
                            users = users,
                            isAdmin = isAdmin,
                            typingUsers = typingUsers,
                            bubbleStyle = bubbleStyle,
                            fontSize = fontSize,
                            chatBg = chatBg,
                            timeFormat = timeFormat,
                            blockedUserIds = blockedUserIds,
                            onBack = { nav.popBackStack() },
                            onSendText = { txt, rId, rText -> vm.sendMessage(chatId, txt, rId, rText) },
                            onSendImage = { uri -> vm.sendImageBase64(chatId, uri, this@MainActivity) },
                            onSendPoll = { q, opts -> vm.sendPoll(chatId, q, opts) },
                            onSendWithExpiry = { txt, sec -> vm.sendMessageWithExpiry(chatId, txt, sec) },
                            onReact = { msgId, emoji ->
                                if (emoji.startsWith("poll:")) {
                                    val idx = emoji.removePrefix("poll:").toIntOrNull() ?: return@ChatScreen
                                    vm.votePoll(chatId, msgId, idx)
                                } else vm.addReaction(chatId, msgId, emoji)
                            },
                            onDelete  = { msgId -> vm.deleteMessage(chatId, msgId) },
                            onPin     = { msgId, text -> vm.pinMessage(chatId, msgId, text) },
                            onEdit    = { msgId, newText -> vm.editMessage(chatId, msgId, newText) },
                            onStar    = { msg -> vm.starMessage(chatId, msg) },
                            onReadBy  = { msgId -> vm.loadReadBy(chatId, msgId) },
                            onForward = { msg ->
                                val target = chats.firstOrNull { it.id != chatId }
                                if (target != null) vm.forwardMessage(msg, target.id)
                                else Toast.makeText(this@MainActivity, "Нет других чатов", Toast.LENGTH_SHORT).show()
                            },
                            onUserClick = { uid -> nav.navigate(Nav.user(uid)) },
                            onOpenGroupInfo = { nav.navigate(Nav.groupInfo(chatId)) },
                            onOpenMedia = { vm.loadMediaGallery(chatId); nav.navigate(Nav.media(chatId)) },
                            onTyping  = { vm.onUserTyping(chatId) },
                            onSaveDraft = { vm.saveDraft(chatId, it) },
                            draft = drafts[chatId] ?: ""
                        )
                    }

                    // ── Информация о группе ───────────────────────────────────
                    composable(Nav.GROUP_INFO) { back ->
                        val chatId = back.arguments?.getString("chatId") ?: return@composable
                        val chat = chats.firstOrNull { it.id == chatId } ?: return@composable
                        GroupInfoScreen(
                            chat = chat,
                            users = users,
                            currentUserId = currentUid,
                            isAdmin = isAdmin,
                            isMuted = me?.mutedChats?.contains(chatId) == true,
                            onBack = { nav.popBackStack() },
                            onUserClick = { uid -> nav.navigate(Nav.user(uid)) },
                            onLeaveGroup = { vm.leaveGroup(chatId); nav.navigate(Nav.CHAT_LIST) { popUpTo(Nav.CHAT) { inclusive = true } } },
                            onMuteToggle = {
                                if (me?.mutedChats?.contains(chatId) == true) vm.unmuteChat(chatId)
                                else vm.muteChat(chatId)
                            },
                            onAddLink    = { link -> vm.addPinnedLink(chatId, link) },
                            onRemoveLink = { link -> vm.removePinnedLink(chatId, link) },
                            onOpenMedia  = { vm.loadMediaGallery(chatId); nav.navigate(Nav.media(chatId)) }
                        )
                    }

                    // ── Медиагалерея ──────────────────────────────────────────
                    composable(Nav.MEDIA) { back ->
                        val chatId = back.arguments?.getString("chatId") ?: return@composable
                        val chat = chats.firstOrNull { it.id == chatId }
                        MediaGalleryScreen(
                            chatName = chat?.name ?: "",
                            mediaMessages = mediaMessages,
                            onBack = { nav.popBackStack() },
                            onImageClick = {}
                        )
                    }

                    // ── Избранные сообщения ───────────────────────────────────
                    composable(Nav.STARRED) {
                        StarredMessagesScreen(
                            starred = starred,
                            onBack  = { nav.popBackStack() },
                            onNavigateToChat = { chatId, _ ->
                                val chat = chats.firstOrNull { it.id == chatId }
                                if (chat != null) { vm.openChat(chat); nav.navigate(Nav.chat(chatId)) }
                            }
                        )
                    }

                    // ── Глобальный поиск ─────────────────────────────────────
                    composable(Nav.GLOBAL_SEARCH) {
                        GlobalSearchScreen(
                            results = globalResults,
                            users   = users,
                            chats   = chats,
                            onQuery = { vm.globalSearch(it) },
                            onNavigateToChat = { chatId ->
                                val chat = chats.firstOrNull { it.id == chatId }
                                if (chat != null) { vm.openChat(chat); nav.navigate(Nav.chat(chatId)) }
                            },
                            onUserClick = { uid -> nav.navigate(Nav.user(uid)) },
                            onBack = { nav.popBackStack() }
                        )
                    }

                    // ── Профиль (свой) ────────────────────────────────────────
                    composable(Nav.PROFILE) {
                        ProfileScreen(
                            userId       = currentUid,
                            username     = me?.username ?: "",
                            displayName  = me?.displayName ?: displayName,
                            avatarUrl    = me?.avatarUrl ?: avatarUrl,
                            isAdmin      = isAdmin,
                            statusEmoji  = me?.statusEmoji ?: "",
                            statusText   = me?.statusText ?: "",
                            msgSentCount = me?.msgSentCount ?: 0L,
                            nameHistory  = me?.nameHistory ?: emptyList(),
                            onBack           = { nav.popBackStack() },
                            onSave           = { dn, uri -> vm.updateProfile(dn, uri, this@MainActivity) },
                            onSaveStatus     = { emoji, text -> vm.updateStatus(emoji, text) },
                            onChangePassword = { cur, nw -> vm.changePassword(cur, nw) },
                            onLogout         = { vm.logout() },
                            onSettings       = { nav.navigate(Nav.SETTINGS) },
                            onStarred        = { vm.loadStarredMessages(); nav.navigate(Nav.STARRED) }
                        )
                    }

                    // ── Профиль другого пользователя ──────────────────────────
                    composable(Nav.USER_PROF) { back ->
                        val uid = back.arguments?.getString("userId") ?: return@composable
                        val user = users.firstOrNull { it.id == uid } ?: return@composable
                        UserProfileScreen(
                            user          = user,
                            isAdmin       = isAdmin,
                            currentUserId = currentUid,
                            isBlocked     = uid in blockedUserIds,
                            onBack        = { nav.popBackStack() },
                            onSendMessage = { vm.startDm(user) },
                            onBan    = { if (user.isBanned) vm.adminUnban(uid) else vm.adminBan(uid, "Нарушение правил") },
                            onFreeze = { if (user.isFrozen) vm.adminUnfreeze(uid) else vm.adminFreeze(uid) },
                            onBlock  = { vm.blockUser(uid) },
                            onUnblock = { vm.unblockUser(uid) },
                            onVerify = { vm.setVerified(uid, !user.isVerified) }
                        )
                    }

                    // ── Настройки ─────────────────────────────────────────────
                    composable(Nav.SETTINGS) {
                        SettingsScreen(
                            theme = appTheme, bubbleStyle = bubbleStyle, fontSize = fontSize,
                            chatBg = chatBg, timeFormat = timeFormat, compactMode = compactMode,
                            notifMsg = notifMsg, notifSys = notifSys, notifErr = notifErr,
                            notifSound = notifSound, notifVib = notifVib,
                            notifPreview = notifPreview, notifGroups = notifGroups,
                            hideOnline = hideOnline, hideRead = hideRead, hideStatus = hideStatus,
                            autoDownload = autoDownload, sendQuality = sendQuality,
                            cacheSize      = vm.getCacheSize(this@MainActivity),
                            onTheme        = vm::setTheme,
                            onBubbleStyle  = vm::setBubbleStyle,
                            onFontSize     = vm::setFontSize,
                            onChatBg       = vm::setChatBg,
                            onTimeFormat   = vm::setTimeFormat,
                            onCompactMode  = vm::setCompactMode,
                            onNotifMsg     = vm::setNotifMsg,    onNotifSys     = vm::setNotifSys,
                            onNotifErr     = vm::setNotifErr,    onNotifSound   = vm::setNotifSound,
                            onNotifVib     = vm::setNotifVib,    onNotifPreview = vm::setNotifPreview,
                            onNotifGroups  = vm::setNotifGroups,
                            onHideOnline   = vm::setHideOnline,  onHideRead     = vm::setHideRead,
                            onHideStatus   = vm::setHideStatus,
                            onAutoDownload = vm::setAutoDownload, onSendQuality = vm::setSendQuality,
                            onClearCache   = { vm.clearCache(this@MainActivity) },
                            onExportChats  = { vm.exportChats(this@MainActivity) },
                            onAbout        = { nav.navigate(Nav.ABOUT) },
                            onPrivacy      = { nav.navigate(Nav.PRIVACY) },
                            onTerms        = { nav.navigate(Nav.TERMS) },
                            onGuidelines   = { nav.navigate(Nav.GUIDELINES) },
                            onBack         = { nav.popBackStack() }
                        )
                    }

                    // ── О приложении ──────────────────────────────────────────
                    composable(Nav.ABOUT) {
                        AboutScreen(
                            onBack       = { nav.popBackStack() },
                            onPrivacy    = { nav.navigate(Nav.PRIVACY) },
                            onTerms      = { nav.navigate(Nav.TERMS) },
                            onGuidelines = { nav.navigate(Nav.GUIDELINES) }
                        )
                    }

                    composable(Nav.PRIVACY)    { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
                    composable(Nav.TERMS)      { TermsOfServiceScreen(onBack = { nav.popBackStack() }) }
                    composable(Nav.GUIDELINES) { CommunityGuidelinesScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
