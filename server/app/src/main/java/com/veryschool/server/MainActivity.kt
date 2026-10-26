package com.veryschool.server

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veryschool.server.ui.AdminViewModel
import com.veryschool.server.ui.AdminViewModelFactory
import com.veryschool.server.ui.AdminEvent
import com.veryschool.server.ui.screens.AdminDashboard
import com.veryschool.server.ui.screens.AdminLoginScreen
import com.veryschool.server.ui.theme.VSAdminTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VSAdminTheme {
                val vm: AdminViewModel = viewModel(factory = AdminViewModelFactory())
                val isLoggedIn      by vm.isLoggedIn.collectAsStateWithLifecycle()
                val isLoading       by vm.isLoading.collectAsStateWithLifecycle()
                val users           by vm.users.collectAsStateWithLifecycle()
                val chats           by vm.chats.collectAsStateWithLifecycle()
                val logs            by vm.logs.collectAsStateWithLifecycle()
                val passphrases     by vm.passphrases.collectAsStateWithLifecycle()
                val globalSettings  by vm.globalSettings.collectAsStateWithLifecycle()
                val userStats       by vm.userStats.collectAsStateWithLifecycle()
                val chatStats       by vm.chatStats.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    vm.event.collectLatest { event ->
                        when (event) {
                            is AdminEvent.Error   -> Toast.makeText(this@MainActivity, event.msg, Toast.LENGTH_LONG).show()
                            is AdminEvent.Success -> Toast.makeText(this@MainActivity, event.msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                if (!isLoggedIn) {
                    AdminLoginScreen(
                        onLogin   = { e, p -> vm.login(e, p) },
                        isLoading = isLoading
                    )
                } else {
                    AdminDashboard(
                        users           = users,
                        chats           = chats,
                        logs            = logs,
                        passphrases     = passphrases,
                        globalSettings  = globalSettings,
                        userStats       = userStats,
                        chatStats       = chatStats,
                        onBan           = { uid, reason -> vm.ban(uid, reason) },
                        onUnban         = vm::unban,
                        onFreeze        = vm::freeze,
                        onUnfreeze      = vm::unfreeze,
                        onDeleteUser    = vm::deleteUser,
                        onUpdateUser    = vm::updateUser,
                        onSetVerified   = vm::setVerified,
                        onDeleteMsg     = vm::deleteMessage,
                        onDeleteChat    = vm::deleteChat,
                        onBotToUser     = vm::sendBotToUser,
                        onBotBroadcast  = vm::broadcastBot,
                        onSavePassphrases    = vm::savePassphrases,
                        onSaveGlobalSettings = vm::saveGlobalSettings,
                        onOpenChat      = { vm.openChatMessages(it) },
                        onRefreshStats  = vm::refreshStats
                    )
                }
            }
        }
    }
}
