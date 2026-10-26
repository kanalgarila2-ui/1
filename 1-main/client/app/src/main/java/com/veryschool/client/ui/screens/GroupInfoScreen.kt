package com.veryschool.client.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.models.*
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chat: ChatModel,
    users: List<UserModel>,
    currentUserId: String,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onUserClick: (userId: String) -> Unit,
    onLeaveGroup: () -> Unit,          // ФИЧА #13
    onMuteToggle: () -> Unit,          // ФИЧА #17
    isMuted: Boolean,
    onAddLink: (String) -> Unit,       // ФИЧА #11
    onRemoveLink: (String) -> Unit,
    onOpenMedia: () -> Unit            // ФИЧА #4
) {
    val tc = LocalTC.current
    val clipboard = LocalClipboardManager.current
    val members = users.filter { it.id in chat.members }
    val admins = chat.adminIds
    var showAddLink by remember { mutableStateOf(false) }
    var newLink by remember { mutableStateOf("") }
    var showLeaveDialog by remember { mutableStateOf(false) }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Покинуть группу?", color = tc.on) },
            text = { Text("Вы больше не сможете видеть сообщения этой группы.", color = tc.muted) },
            containerColor = tc.surf,
            confirmButton = {
                TextButton(onClick = { showLeaveDialog = false; onLeaveGroup() }) {
                    Text("Покинуть", color = VSRed)
                }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Отмена", color = tc.muted) } }
        )
    }

    if (showAddLink) {
        AlertDialog(
            onDismissRequest = { showAddLink = false; newLink = "" },
            title = { Text("Прикрепить ссылку", color = tc.on) },
            containerColor = tc.surf,
            text = {
                OutlinedTextField(
                    value = newLink, onValueChange = { newLink = it },
                    placeholder = { Text("https://...", color = tc.muted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { if (newLink.isNotBlank()) { onAddLink(newLink.trim()); newLink = ""; showAddLink = false } }) {
                    Text("Прикрепить", color = VSPrimary)
                }
            },
            dismissButton = { TextButton(onClick = { showAddLink = false; newLink = "" }) { Text("Отмена", color = tc.muted) } }
        )
    }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("Информация о группе", color = tc.on, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Шапка группы
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvatarImage(url = chat.avatarUrl, name = chat.name, size = 72.dp)
                        Text(chat.name, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (chat.description.isNotEmpty()) Text(chat.description, color = tc.muted, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${chat.members.size}", color = VSPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("участников", color = tc.muted, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${chat.messageCount}", color = VSSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("сообщений", color = tc.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ФИЧА #12: Invite код
            if (chat.inviteCode.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Код приглашения", color = tc.muted, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.inviteCode, color = VSPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(chat.inviteCode)) }) {
                                    Icon(Icons.Default.ContentCopy, null, tint = VSPrimary)
                                }
                            }
                            Text("Поделитесь кодом — участники смогут вступить через «Войти по коду»", color = tc.muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Быстрые действия
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenMedia).padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = VSPrimary, modifier = Modifier.size(22.dp))
                            Text("Медиафайлы", color = tc.on, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = tc.muted)
                        }
                        HorizontalDivider(color = tc.border)
                        Row(Modifier.fillMaxWidth().clickable { onMuteToggle() }.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                null, tint = if (isMuted) VSRed else VSGreen, modifier = Modifier.size(22.dp))
                            Text(if (isMuted) "Включить уведомления" else "Отключить уведомления",
                                color = tc.on, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = tc.border)
                        if (chat.isGroup && currentUserId !in chat.adminIds) {
                            Row(Modifier.fillMaxWidth().clickable { showLeaveDialog = true }.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ExitToApp, null, tint = VSRed, modifier = Modifier.size(22.dp))
                                Text("Покинуть группу", color = VSRed, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // ФИЧА #11: Прикреплённые ссылки
            if (chat.pinnedLinks.isNotEmpty() || isAdmin) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📌 Прикреплённые ссылки", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (isAdmin) TextButton(onClick = { showAddLink = true }) { Text("+ Добавить", color = VSPrimary, fontSize = 12.sp) }
                        }
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                            Column {
                                chat.pinnedLinks.forEachIndexed { i, link ->
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Link, null, tint = VSSecondary, modifier = Modifier.size(18.dp))
                                        Text(link, color = VSSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        if (isAdmin) IconButton(onClick = { onRemoveLink(link) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, null, tint = tc.muted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (i < chat.pinnedLinks.lastIndex) HorizontalDivider(color = tc.border)
                                }
                            }
                        }
                    }
                }
            }

            // Участники
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Участники (${members.size})", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    // ФИЧА #5: онлайн счётчик
                    val onlineCount = members.count { it.online }
                    if (onlineCount > 0) Text("$onlineCount онлайн", color = VSGreen, fontSize = 12.sp)
                }
            }

            items(members.sortedWith(compareByDescending<UserModel> { it.id in admins }.thenBy { it.displayName }), key = { it.id }) { user ->
                Card(
                    onClick = { onUserClick(user.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = tc.surf)
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(url = user.avatarUrl, name = user.displayName, size = 40.dp, showOnline = true, isOnline = user.online)
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(user.displayName, color = tc.on, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                if (user.isVerified) Text("✓", color = VSPrimary, fontSize = 12.sp)
                            }
                            val sub = buildString {
                                if (user.id in admins) append("Администратор")
                                else append("@${user.username}")
                                if (user.statusDisplay().isNotEmpty()) append(" · ${user.statusDisplay()}")
                            }
                            Text(sub, color = tc.muted, fontSize = 11.sp)
                        }
                        Text(user.lastSeenText(), color = tc.muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
