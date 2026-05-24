package com.veryschool.client.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.models.UserModel
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    user: UserModel,
    isAdmin: Boolean,
    currentUserId: String = "",
    isBlocked: Boolean = false,
    onBack: () -> Unit,
    onSendMessage: () -> Unit,
    onBan: () -> Unit,
    onFreeze: () -> Unit,
    onBlock: () -> Unit = {},     // ФИЧА #8
    onUnblock: () -> Unit = {},
    onVerify: () -> Unit = {}     // ФИЧА #20
) {
    val tc = LocalTC.current
    var showBanDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    if (showBanDialog) {
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            containerColor = tc.surf,
            title = { Text(if (user.isBanned) "Разбанить?" else "Заблокировать?", color = tc.on) },
            text = { Text(if (user.isBanned) "Пользователь снова получит доступ." else "Пользователь потеряет доступ к приложению.", color = tc.muted) },
            confirmButton = {
                TextButton(onClick = { showBanDialog = false; onBan() }) {
                    Text(if (user.isBanned) "Разбанить" else "Заблокировать", color = VSRed)
                }
            },
            dismissButton = { TextButton(onClick = { showBanDialog = false }) { Text("Отмена", color = tc.muted) } }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            containerColor = tc.surf,
            title = { Text(if (isBlocked) "Разблокировать?" else "Заблокировать?", color = tc.on) },
            text = { Text(if (isBlocked) "Вы снова будете видеть сообщения этого пользователя." else "Вы не будете видеть сообщения этого пользователя.", color = tc.muted) },
            confirmButton = {
                TextButton(onClick = { showBlockDialog = false; if (isBlocked) onUnblock() else onBlock() }) {
                    Text(if (isBlocked) "Разблокировать" else "Заблокировать", color = VSRed)
                }
            },
            dismissButton = { TextButton(onClick = { showBlockDialog = false }) { Text("Отмена", color = tc.muted) } }
        )
    }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("Профиль", color = tc.on, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Аватар + имя
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            AvatarImage(url = user.avatarUrl, name = user.displayName, size = 84.dp,
                                isFrozen = user.isFrozen, isDeleted = user.isDeleted, isBanned = user.isBanned,
                                showOnline = true, isOnline = user.online)
                            // ФИЧА #20: бейдж верификации
                            if (user.isVerified) {
                                Surface(modifier = Modifier.align(Alignment.BottomEnd).size(22.dp),
                                    shape = CircleShape, color = VSPrimary) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(user.displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            if (user.isVerified) Text("✓", color = VSPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        if (user.username.isNotEmpty()) Text("@${user.username}", color = tc.muted, fontSize = 14.sp)
                        if (user.statusDisplay().isNotEmpty()) Text(user.statusDisplay(), color = tc.muted, fontSize = 13.sp)

                        // Онлайн / lastSeen (ФИЧА #10)
                        Surface(shape = RoundedCornerShape(20.dp), color = if (user.online) VSGreen.copy(0.15f) else tc.card) {
                            Text(user.lastSeenText().ifEmpty { if (user.online) "онлайн" else "оффлайн" },
                                color = if (user.online) VSGreen else tc.muted, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }

                        // ФИЧА #19: статистика
                        if (user.msgSentCount > 0) {
                            Text("📨 ${user.msgSentCount} сообщений отправлено", color = tc.muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ID и ссылка
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Числовой ID", color = tc.muted, fontSize = 12.sp)
                            Text("#${user.numericId}", color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        if (user.isBanned) {
                            HorizontalDivider(color = tc.border)
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Статус", color = tc.muted, fontSize = 12.sp)
                                Text("🚫 Заблокирован", color = VSRed, fontSize = 12.sp)
                            }
                            if (user.banReason.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Причина", color = tc.muted, fontSize = 12.sp)
                                    Text(user.banReason, color = VSRed, fontSize = 12.sp)
                                }
                            }
                        }
                        if (user.isFrozen) {
                            HorizontalDivider(color = tc.border)
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Статус", color = tc.muted, fontSize = 12.sp)
                                Text("❄️ Заморожен", color = VSFrozen, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ФИЧА #18: история имён
            if (user.nameHistory.size > 1) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("История имён", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            user.nameHistory.takeLast(5).reversed().forEach { name ->
                                Text(name, color = tc.on, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Действия
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column {
                        if (currentUserId != user.id) {
                            // Написать
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = onSendMessage, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)) {
                                    Icon(Icons.Default.Chat, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Написать", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(color = tc.border)
                            // ФИЧА #8: блокировка
                            Row(Modifier.fillMaxWidth().clickable { showBlockDialog = true }.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block, null,
                                    tint = if (isBlocked) VSGreen else VSRed, modifier = Modifier.size(20.dp))
                                Text(if (isBlocked) "Разблокировать" else "Заблокировать пользователя",
                                    color = if (isBlocked) VSGreen else VSRed, fontSize = 14.sp)
                            }
                        }

                        // Админ-действия
                        if (isAdmin) {
                            HorizontalDivider(color = tc.border)
                            Row(Modifier.fillMaxWidth().clickable { showBanDialog = true }.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Block, null, tint = VSRed, modifier = Modifier.size(20.dp))
                                Text(if (user.isBanned) "Снять блокировку" else "Заблокировать (бан)", color = VSRed, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = tc.border)
                            Row(Modifier.fillMaxWidth().clickable(onClick = onFreeze).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AcUnit, null, tint = VSFrozen, modifier = Modifier.size(20.dp))
                                Text(if (user.isFrozen) "Снять заморозку" else "Заморозить аккаунт", color = VSFrozen, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = tc.border)
                            // ФИЧА #20: верификация
                            Row(Modifier.fillMaxWidth().clickable(onClick = onVerify).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = VSPrimary, modifier = Modifier.size(20.dp))
                                Text(if (user.isVerified) "Снять верификацию" else "Верифицировать ✓", color = VSPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

