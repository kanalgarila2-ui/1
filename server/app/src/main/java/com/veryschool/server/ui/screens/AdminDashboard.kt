package com.veryschool.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.server.data.ChatEntity
import com.veryschool.server.data.UserEntity
import com.veryschool.server.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(
    serverRunning: Boolean, localIp: String, publicIp: String, onlineCount: Int,
    userCount: Int, chatCount: Int, msgCount: Int, logs: List<String>,
    users: List<UserEntity>, chats: List<ChatEntity>,
    onStart: () -> Unit, onStop: () -> Unit, onRestart: () -> Unit,
    onDeleteUser: (String) -> Unit, onSetAdmin: (String, Boolean) -> Unit,
    onBanUser: (String, Long, String) -> Unit, onUnbanUser: (String) -> Unit,
    onBlockDm: (String, Long) -> Unit, onUnblockDm: (String) -> Unit,
    onSendBotMessage: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit, onClearLogs: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Сервер", "Логи", "Юзеры", "Чаты", "Бот")

    Scaffold(containerColor = AdminBackground, topBar = {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VS", fontWeight = FontWeight.ExtraBold, color = AdminPrimary, fontSize = 22.sp)
                Text("Admin", fontWeight = FontWeight.ExtraBold, color = AdminOnSurface, fontSize = 22.sp)
                Box(Modifier.size(10.dp).background(if (serverRunning) AdminGreen else AdminRed, CircleShape))
                if (onlineCount > 0) Surface(shape = RoundedCornerShape(50), color = AdminPrimary.copy(0.2f)) {
                    Text(" $onlineCount онлайн ", color = AdminPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AdminSurface))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = AdminSurface, contentColor = AdminPrimary, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(label, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) })
                }
            }
            when (selectedTab) {
                0 -> ServerTab(serverRunning, localIp, publicIp, onlineCount, userCount, chatCount, msgCount, onStart, onStop, onRestart)
                1 -> LogsTab(logs, onClearLogs)
                2 -> UsersTab(users, onDeleteUser, onSetAdmin, onBanUser, onUnbanUser, onBlockDm, onUnblockDm)
                3 -> ChatsTab(chats, onDeleteChat)
                4 -> BotTab(users, onSendBotMessage)
            }
        }
    }
}

@Composable
fun ServerTab(running: Boolean, localIp: String, publicIp: String, onlineCount: Int, userCount: Int, chatCount: Int, msgCount: Int, onStart: () -> Unit, onStop: () -> Unit, onRestart: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AdminSurface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (running) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (running) AdminGreen else AdminRed, modifier = Modifier.size(32.dp))
                    Column { Text(if (running) "Сервер работает" else "Остановлен", color = AdminOnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Порт 8080 • WebSocket + HTTP", color = AdminOnSurfaceMuted, fontSize = 12.sp) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("👥", "$userCount", "Юзеры", Modifier.weight(1f))
                StatCard("💬", "$chatCount", "Чаты", Modifier.weight(1f))
                StatCard("📨", "$msgCount", "Сообщ.", Modifier.weight(1f))
                StatCard("🟢", "$onlineCount", "Онлайн", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !running, colors = ButtonDefaults.buttonColors(containerColor = AdminGreen, disabledContainerColor = AdminBorder), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Старт", fontSize = 13.sp) }
                Button(onClick = onStop, enabled = running, colors = ButtonDefaults.buttonColors(containerColor = AdminRed, disabledContainerColor = AdminBorder), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Стоп", fontSize = 13.sp) }
                Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = AdminYellow), modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp), tint = Color.Black); Spacer(Modifier.width(4.dp)); Text("Рестарт", fontSize = 13.sp, color = Color.Black) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AdminSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Адреса", color = AdminOnSurface, fontWeight = FontWeight.Bold)
                    IpRow("🏠 Локальная сеть", localIp) { clipboard.setText(AnnotatedString(localIp)) }
                    IpRow("🌍 Публичный IP", publicIp) { clipboard.setText(AnnotatedString(publicIp)) }
                    HorizontalDivider(color = AdminBorder)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null, tint = AdminPrimary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Column { Text("Ключевая фраза", color = AdminOnSurfaceMuted, fontSize = 12.sp); Text("22sch", color = AdminPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, fontFamily = FontFamily.Monospace) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString("22sch")) }) { Icon(Icons.Default.ContentCopy, null, tint = AdminOnSurfaceMuted) }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AdminSurface)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = AdminPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(label, color = AdminOnSurfaceMuted, fontSize = 9.sp)
        }
    }
}

@Composable fun IpRow(label: String, ip: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, color = AdminOnSurfaceMuted, fontSize = 11.sp); Text(ip, color = AdminOnSurface, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, null, tint = AdminPrimary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun LogsTab(logs: List<String>, onClear: () -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) state.animateScrollToItem(logs.size - 1) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${logs.size} записей", color = AdminOnSurfaceMuted, fontSize = 12.sp)
            TextButton(onClick = onClear) { Text("Очистить", color = AdminRed, fontSize = 12.sp) }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(logs) { log ->
                val color = when { "[ERROR]" in log -> AdminRed; "[WARN]" in log || "❌" in log || "🚫" in log -> AdminYellow; "✅" in log || "👤" in log -> AdminGreen; "💬" in log || "🤖" in log -> AdminPrimary; else -> AdminOnSurface }
                Text(log, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().background(AdminSurface.copy(0.4f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun UsersTab(users: List<UserEntity>, onDelete: (String) -> Unit, onSetAdmin: (String, Boolean) -> Unit, onBan: (String, Long, String) -> Unit, onUnban: (String) -> Unit, onBlockDm: (String, Long) -> Unit, onUnblockDm: (String) -> Unit) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var showBanDialog by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = users.filter { searchQuery.isBlank() || it.username.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск юзеров...", color = AdminOnSurfaceMuted) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AdminPrimary, unfocusedBorderColor = AdminBorder, focusedTextColor = AdminOnSurface, unfocusedTextColor = AdminOnSurface),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AdminOnSurfaceMuted) })

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("${filtered.size} из ${users.size} пользователей", color = AdminOnSurfaceMuted, fontSize = 12.sp) }
            items(filtered, key = { it.id }) { user ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (user.isBanned) AdminRed.copy(0.1f) else AdminSurface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(AdminSecondary.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(user.displayName.firstOrNull()?.uppercase() ?: "?", color = AdminSecondary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(user.displayName, color = AdminOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (user.isAdmin) Surface(shape = RoundedCornerShape(4.dp), color = AdminYellow.copy(0.2f)) { Text("ADMIN", color = AdminYellow, fontSize = 8.sp, modifier = Modifier.padding(2.dp, 1.dp)) }
                                    if (user.isBanned) Surface(shape = RoundedCornerShape(4.dp), color = AdminRed.copy(0.2f)) { Text("BAN", color = AdminRed, fontSize = 8.sp, modifier = Modifier.padding(2.dp, 1.dp)) }
                                    if (user.dmBlocked) Surface(shape = RoundedCornerShape(4.dp), color = AdminYellow.copy(0.2f)) { Text("DM⛔", color = AdminYellow, fontSize = 8.sp, modifier = Modifier.padding(2.dp, 1.dp)) }
                                }
                                Text("@${user.username} • ID: ${user.id}", color = AdminOnSurfaceMuted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Admin toggle
                            SmallActionBtn(if (user.isAdmin) "−Admin" else "+Admin", AdminYellow) { onSetAdmin(user.id, !user.isAdmin) }
                            // Ban/unban
                            if (user.isBanned) SmallActionBtn("Разбан", AdminGreen) { onUnban(user.id) }
                            else SmallActionBtn("Бан", AdminRed) { showBanDialog = user.id }
                            // DM block/unblock
                            if (user.dmBlocked) SmallActionBtn("DM ✓", AdminGreen) { onUnblockDm(user.id) }
                            else SmallActionBtn("DM ⛔", AdminYellow) { onBlockDm(user.id, 0L) }
                            // Delete
                            SmallActionBtn("Удалить", AdminRed.copy(0.7f)) { confirmDelete = user.id }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (confirmDelete != null) {
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Удалить?", color = AdminOnSurface) },
            text = { Text("Нельзя отменить", color = AdminOnSurfaceMuted) },
            confirmButton = { TextButton(onClick = { onDelete(confirmDelete!!); confirmDelete = null }) { Text("Удалить", color = AdminRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена", color = AdminOnSurfaceMuted) } },
            containerColor = AdminSurface)
    }

    if (showBanDialog != null) {
        BanDialog(
            onBan = { mins, reason -> onBan(showBanDialog!!, mins, reason); showBanDialog = null },
            onDismiss = { showBanDialog = null }
        )
    }
}

@Composable
fun SmallActionBtn(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BanDialog(onBan: (Long, String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("0") }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AdminPrimary, unfocusedBorderColor = AdminBorder, focusedTextColor = AdminOnSurface, unfocusedTextColor = AdminOnSurface)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Бан пользователя", color = AdminOnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Причина") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Минуты (0 = навсегда)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onBan(duration.toLongOrNull() ?: 0L, reason) }) { Text("Заблокировать", color = AdminRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = AdminOnSurfaceMuted) } },
        containerColor = AdminSurface)
}

@Composable
fun BotTab(users: List<UserEntity>, onSendBot: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var targetAll by remember { mutableStateOf(true) }
    var selectedUser by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AdminPrimary, unfocusedBorderColor = AdminBorder, focusedTextColor = AdminOnSurface, unfocusedTextColor = AdminOnSurface)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AdminSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", fontSize = 24.sp); Spacer(Modifier.width(8.dp))
                    Text("VerySchool BOT", color = AdminOnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("Отправить сообщение от имени бота", color = AdminOnSurfaceMuted, fontSize = 12.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = targetAll, onCheckedChange = { targetAll = it }, colors = SwitchDefaults.colors(checkedThumbColor = AdminPrimary, checkedTrackColor = AdminPrimary.copy(0.3f)))
                    Spacer(Modifier.width(8.dp))
                    Text(if (targetAll) "Всем пользователям" else "Конкретному пользователю", color = AdminOnSurface)
                }

                if (!targetAll) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = users.firstOrNull { it.id == selectedUser }?.let { "@${it.username}" } ?: "Выбери пользователя",
                            onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            colors = fieldColors, shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            users.forEach { user ->
                                DropdownMenuItem(text = { Text("@${user.username} (${user.displayName})", color = AdminOnSurface) }, onClick = { selectedUser = user.id; expanded = false })
                            }
                        }
                    }
                }

                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Сообщение") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 4)

                Button(onClick = {
                    if (text.isNotBlank()) {
                        onSendBot(text.trim(), if (targetAll) "" else selectedUser)
                        text = ""
                    }
                }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary)) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Отправить от BOT")
                }
            }
        }
    }
}

@Composable
fun ChatsTab(chats: List<ChatEntity>, onDelete: (String) -> Unit) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${chats.size} чатов", color = AdminOnSurfaceMuted, fontSize = 12.sp) }
        items(chats, key = { it.id }) { chat ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AdminSurface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (chat.isBot) Icons.Default.SmartToy else if (chat.isGroup) Icons.Default.Group else Icons.Default.Person, null,
                        tint = if (chat.isBot) AdminYellow else if (chat.isGroup) AdminPrimary else AdminSecondary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, color = AdminOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${if (chat.isBot) "BOT" else if (chat.isGroup) "Группа" else "Личный"} • ${chat.id.take(8)}...", color = AdminOnSurfaceMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { confirmDelete = chat.id }) { Icon(Icons.Default.Delete, null, tint = AdminRed.copy(0.7f), modifier = Modifier.size(18.dp)) }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
    if (confirmDelete != null) {
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Удалить чат?", color = AdminOnSurface) },
            text = { Text("Все сообщения удалятся", color = AdminOnSurfaceMuted) },
            confirmButton = { TextButton(onClick = { onDelete(confirmDelete!!); confirmDelete = null }) { Text("Удалить", color = AdminRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена", color = AdminOnSurfaceMuted) } },
            containerColor = AdminSurface)
    }
}
