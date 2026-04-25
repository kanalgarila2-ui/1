package com.veryschool.client.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.models.*
import com.google.firebase.Timestamp
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatModel>,
    users: List<UserModel>,
    currentUserId: String,
    displayName: String,
    avatarUrl: String,
    onChatClick: (ChatModel) -> Unit,
    onNewDm: (UserModel) -> Unit,
    onNewGroup: (String, List<String>) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    val tc = LocalTC.current
    var showNewChat by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filteredChats = if (searchQuery.isBlank()) chats
    else chats.filter { it.name.contains(searchQuery, ignoreCase = true) || it.lastMessage.contains(searchQuery, ignoreCase = true) }

    val matchingUsers = if (searchQuery.length >= 2)
        users.filter { it.id != currentUserId && !it.isBanned && !it.isDeleted &&
            (it.username.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true)) }
    else emptyList()

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск...", color = tc.muted) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on),
                            shape = RoundedCornerShape(12.dp), singleLine = true
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VerySchool", fontWeight = FontWeight.ExtraBold, color = VSPrimary, fontSize = 20.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, null, tint = tc.on)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = tc.on) }
                    IconButton(onClick = onProfile) {
                        AvatarImage(url = avatarUrl, name = displayName, size = 32.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = { showNewGroup = true }, containerColor = VSSecondary, shape = CircleShape, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Default.Group, null, tint = Color.White)
                }
                FloatingActionButton(onClick = { showNewChat = true }, containerColor = VSPrimary, shape = CircleShape) {
                    Icon(Icons.Default.Edit, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // User search results
            if (matchingUsers.isNotEmpty()) {
                item {
                    Text("Пользователи", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                items(matchingUsers) { user ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onNewDm(user) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImage(url = user.avatarUrl, name = user.displayName, size = 44.dp,
                            isFrozen = user.isFrozen, isDeleted = user.isDeleted || user.isBanned,
                            showOnline = true, isOnline = user.online)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, color = tc.on, fontWeight = FontWeight.Medium)
                            Text("@${user.username}", color = tc.muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Send, null, tint = VSPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                item { HorizontalDivider(color = tc.border, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }

            if (filteredChats.isEmpty() && searchQuery.isBlank()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💬", fontSize = 48.sp)
                            Text("Нет чатов", color = tc.muted, fontWeight = FontWeight.SemiBold)
                            Text("Нажми ✏️ чтобы написать", color = tc.muted, fontSize = 13.sp)
                        }
                    }
                }
            }

            items(filteredChats, key = { it.id }) { chat ->
                ChatListItem(chat = chat, currentUserId = currentUserId, onClick = { onChatClick(chat) })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // New DM dialog
    if (showNewChat) {
        val otherUsers = users.filter { it.id != currentUserId && !it.isBanned && !it.isDeleted }
        var dmSearch by remember { mutableStateOf("") }
        val filtered = if (dmSearch.isBlank()) otherUsers else otherUsers.filter {
            it.username.contains(dmSearch, ignoreCase = true) || it.displayName.contains(dmSearch, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showNewChat = false; dmSearch = "" },
            title = { Text("Написать сообщение", color = tc.on) },
            text = {
                Column {
                    OutlinedTextField(value = dmSearch, onValueChange = { dmSearch = it },
                        placeholder = { Text("Поиск...", color = tc.muted) }, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on),
                        shape = RoundedCornerShape(10.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = tc.muted) })
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(if (otherUsers.isEmpty()) "Нет других пользователей" else "Ничего не найдено", color = tc.muted)
                    }
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(filtered) { user ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onNewDm(user); showNewChat = false; dmSearch = "" }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarImage(url = user.avatarUrl, name = user.displayName, size = 40.dp,
                                    showOnline = true, isOnline = user.online)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.displayName, color = tc.on, fontWeight = FontWeight.Medium)
                                    Text("@${user.username}", color = tc.muted, fontSize = 12.sp)
                                }
                                if (user.online) Box(Modifier.size(8.dp).background(VSFrozen, CircleShape))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNewChat = false; dmSearch = "" }) { Text("Отмена", color = tc.muted) } },
            containerColor = tc.surf
        )
    }

    // New Group dialog
    if (showNewGroup) {
        CreateGroupDialog(
            users = users.filter { it.id != currentUserId && !it.isBanned && !it.isDeleted },
            onCreate = { name, ids -> onNewGroup(name, ids); showNewGroup = false },
            onDismiss = { showNewGroup = false }
        )
    }
}

@Composable
private fun ChatListItem(chat: ChatModel, currentUserId: String, onClick: () -> Unit) {
    val tc = LocalTC.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            AvatarImage(url = chat.avatarUrl, name = chat.name, size = 52.dp)
            if (chat.pinned) Box(Modifier.size(16.dp).background(VSYellow, CircleShape).align(Alignment.TopEnd), contentAlignment = Alignment.Center) {
                Text("📌", fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (chat.isGroup) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Group, null, tint = tc.muted, modifier = Modifier.size(14.dp)) }
                    if (chat.isBot) { Spacer(Modifier.width(4.dp)); Text("🤖", fontSize = 12.sp) }
                }
                Text(formatChatTime(chat.lastMessageTime?.toDate()?.time ?: 0L), color = tc.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(chat.lastMessage.ifEmpty { "Нет сообщений" }, color = tc.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = tc.border.copy(0.4f), modifier = Modifier.padding(start = 80.dp))
}

@Composable
fun CreateGroupDialog(users: List<UserModel>, onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    val tc = LocalTC.current
    var groupName by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isBlank()) users else users.filter { it.displayName.contains(search, ignoreCase = true) || it.username.contains(search, ignoreCase = true) }
    val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on)

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Создать группу", color = tc.on) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("Название группы") }, colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Поиск...", color = tc.muted) }, colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp), leadingIcon = { Icon(Icons.Default.Search, null, tint = tc.muted) })
                Text("Выбрано: ${selected.size}", color = tc.muted, fontSize = 12.sp)
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(filtered) { user ->
                        Row(Modifier.fillMaxWidth().clickable { if (user.id in selected) selected.remove(user.id) else selected.add(user.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = user.id in selected, onCheckedChange = { c -> if (c) selected.add(user.id) else selected.remove(user.id) }, colors = CheckboxDefaults.colors(checkedColor = VSPrimary))
                            Spacer(Modifier.width(8.dp))
                            AvatarImage(url = user.avatarUrl, name = user.displayName, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Column { Text(user.displayName, color = tc.on, fontSize = 14.sp); Text("@${user.username}", color = tc.muted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (groupName.isNotBlank() && selected.isNotEmpty()) onCreate(groupName, selected.toList()) }) { Text("Создать", color = VSPrimary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf
    )
}

private fun formatChatTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "сейчас"
        diff < 3600_000 -> "${diff / 60_000}м"
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ts))
    }
}
