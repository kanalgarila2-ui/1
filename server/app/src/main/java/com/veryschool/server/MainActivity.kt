package com.veryschool.server

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veryschool.server.core.ServerService
import com.veryschool.server.ui.AdminViewModel
import com.veryschool.server.ui.AdminViewModelFactory
import com.veryschool.server.ui.screens.AdminDashboard
import com.veryschool.server.ui.theme.VSAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        startForegroundService(Intent(this, ServerService::class.java).apply { action = ServerService.ACTION_START })
        setContent {
            VSAdminTheme {
                val vm: AdminViewModel = viewModel(factory = AdminViewModelFactory(applicationContext))
                val running by vm.serverRunning.collectAsStateWithLifecycle()
                val logs by vm.logs.collectAsStateWithLifecycle()
                val localIp by vm.localIp.collectAsStateWithLifecycle()
                val publicIp by vm.publicIp.collectAsStateWithLifecycle()
                val onlineCount by vm.onlineCount.collectAsStateWithLifecycle()
                val users by vm.users.collectAsStateWithLifecycle()
                val chats by vm.chats.collectAsStateWithLifecycle()
                val msgCount by vm.msgCount.collectAsStateWithLifecycle()
                AdminDashboard(
                    serverRunning = running,
                    localIp = localIp,
                    publicIp = publicIp,
                    onlineCount = onlineCount,
                    userCount = users.size,
                    chatCount = chats.size,
                    msgCount = msgCount,
                    logs = logs,
                    users = users,
                    chats = chats,
                    onStart = vm::startServer,
                    onStop = vm::stopServer,
                    onRestart = vm::restartServer,
                    onDeleteUser = vm::deleteUser,
                    onSetAdmin = vm::setAdmin,
                    onBanUser = vm::banUser,
                    onUnbanUser = vm::unbanUser,
                    onBlockDm = vm::blockDm,
                    onUnblockDm = vm::unblockDm,
                    onSendBotMessage = vm::sendBotMessage,
                    onDeleteChat = vm::deleteChat,
                    onClearLogs = vm::clearLogs
                )
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
