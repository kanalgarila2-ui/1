package com.veryschool.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.veryschool.client.data.db.ChatEntity
import com.veryschool.client.service.WsConnectionService
import com.veryschool.client.ui.*
import com.veryschool.client.ui.screens.*
import com.veryschool.client.ui.theme.VerySchoolTheme
import kotlinx.coroutines.flow.collectLatest

object Routes {
    const val CONNECT = "connect"
    const val AUTH = "auth"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}"
    const val PROFILE = "profile"
    const val USER_PROFILE = "user_profile/{userId}"
    fun chat(chatId: String) = "chat/$chatId"
    fun userProfile(userId: String) = "user_profile/$userId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        // Запускаем foreground service для удержания WS в фоне
        WsConnectionService.start(this)

        setContent {
            VerySchoolTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModelFactory(applicationContext))
                val navController = rememberNavController()

                val authState by vm.authState.collectAsStateWithLifecycle()
                val currentUserId by vm.currentUserId.collectAsStateWithLifecycle()
                val username by vm.currentUsername.collectAsStateWithLifecycle()
                val displayName by vm.displayName.collectAsStateWithLifecycle()
                val avatar by vm.avatar.collectAsStateWithLifecycle()
                val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
                val chats by vm.chats.collectAsStateWithLifecycle()
                val users by vm.users.collectAsStateWithLifecycle()
                val messages by vm.selectedChatMessages.collectAsStateWithLifecycle()
                val connected by vm.connected.collectAsStateWithLifecycle(false)

                LaunchedEffect(Unit) {
                    vm.uiEvent.collectLatest { event ->
                        when (event) {
                            is UiEvent.Error -> Toast.makeText(this@MainActivity, event.msg, Toast.LENGTH_LONG).show()
                            is UiEvent.Success -> Toast.makeText(this@MainActivity, event.msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                LaunchedEffect(authState) {
                    val dest = when (authState) {
                        AuthState.NeedServer -> Routes.CONNECT
                        AuthState.NeedAuth -> Routes.AUTH
                        AuthState.Authenticated -> Routes.CHAT_LIST
                        AuthState.Unknown -> return@LaunchedEffect
                    }
                    navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                }

                var currentChat by remember { mutableStateOf<ChatEntity?>(null) }

                NavHost(navController = navController, startDestination = Routes.CONNECT) {
                    composable(Routes.CONNECT) {
                        ConnectScreen(onConnect = { ip, _ -> vm.saveServer(ip) })
                    }
                    composable(Routes.AUTH) {
                        AuthScreen(
                            onLogin = { u, p, ph -> vm.login(u, p, ph) },
                            onRegister = { u, p, dn, ph -> vm.register(u, p, dn, ph) }
                        )
                    }
                    composable(Routes.CHAT_LIST) {
                        ChatListScreen(
                            chats = chats, users = users, currentUserId = currentUserId,
                            displayName = displayName, avatarBase64 = avatar, connected = connected,
                            onChatClick = { chat ->
                                currentChat = chat
                                vm.openChat(chat.id)
                                navController.navigate(Routes.chat(chat.id))
                            },
                            onNewDm = { user ->
                                val existing = chats.firstOrNull { c ->
                                    !c.isGroup && c.members.contains(user.id) && c.members.contains(currentUserId)
                                }
                                if (existing != null) {
                                    currentChat = existing
                                    vm.openChat(existing.id)
                                    navController.navigate(Routes.chat(existing.id))
                                } else vm.startDm(user.id)
                            },
                            onNewGroup = { name, ids -> vm.createGroup(name, ids) },
                            onProfile = { navController.navigate(Routes.PROFILE) }
                        )
                    }
                    composable(Routes.CHAT) { backStack ->
                        val chatId = backStack.arguments?.getString("chatId") ?: return@composable
                        val chat = currentChat ?: chats.firstOrNull { it.id == chatId }
                        ChatScreen(
                            chatName = chat?.name ?: chatId,
                            chatAvatar = chat?.avatarBase64 ?: "",
                            isGroup = chat?.isGroup ?: false,
                            messages = messages,
                            currentUserId = currentUserId,
                            users = users,
                            onBack = { navController.popBackStack() },
                            onSendText = { text -> vm.sendMessage(chatId, text) },
                            onSendImage = { uri -> vm.sendImageMessage(chatId, uri, this@MainActivity) },
                            onReact = { msgId, emoji -> vm.sendReaction(msgId, chatId, emoji) },
                            onTyping = { vm.sendTyping(chatId) },
                            onTypingStop = { vm.sendTypingStop(chatId) },
                            onUserProfileClick = { userId -> navController.navigate(Routes.userProfile(userId)) }
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            userId = currentUserId, username = username,
                            displayName = displayName, avatarBase64 = avatar, serverUrl = serverUrl,
                            onBack = { navController.popBackStack() },
                            onSave = { dn, uri -> vm.updateProfile(dn, uri, this@MainActivity) },
                            onChangePassword = { old, new -> vm.changePassword(old, new) },
                            onLogout = {
                                vm.logout()
                                WsConnectionService.stop(this@MainActivity)
                                navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                            }
                        )
                    }
                    composable(Routes.USER_PROFILE) { backStack ->
                        val userId = backStack.arguments?.getString("userId") ?: return@composable
                        val user = users.firstOrNull { it.id == userId }
                        UserProfileScreen(
                            userId = userId,
                            displayName = user?.displayName ?: userId,
                            username = user?.username ?: "",
                            avatarBase64 = user?.avatarBase64 ?: "",
                            online = user?.online ?: false,
                            onBack = { navController.popBackStack() },
                            onSendMessage = {
                                val existing = chats.firstOrNull { c ->
                                    !c.isGroup && c.members.contains(userId) && c.members.contains(currentUserId)
                                }
                                if (existing != null) {
                                    currentChat = existing
                                    vm.openChat(existing.id)
                                    navController.navigate(Routes.chat(existing.id)) {
                                        popUpTo(Routes.CHAT_LIST)
                                    }
                                } else vm.startDm(userId)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Не останавливаем сервис при onDestroy — он должен жить в фоне
        // Останавливается только при logout
    }
}
