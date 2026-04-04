package com.veryschool.client.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veryschool.client.data.db.ChatEntity
import com.veryschool.client.data.db.UserEntity
import com.veryschool.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatEntity>,
    users: List<UserEntity>,
    currentUserId: String,
    displayName: String,
    avatarBase64: String,
    connected: Boolean,
    onChatClick: (ChatEntity) -> Unit,
    onNewDm: (UserEntity) -> Unit,
    onNewGroup: (String, List<String>) -> Unit,
    onProfile: () -> Unit
) {
    var showNewChat by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filteredChats = if (searchQuery.isBlank()) chats
    else chats.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        containerColor = VSBackground,
        topBar = {
            if (showSearch) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { showSearch = false; searchQuery = "" }) { Icon(Icons.Default.ArrowBack, null, tint = VSOnSurface) } },
                    title = {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск чатов и людей...", color = VSOnSurfaceMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder, focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface),
                            shape = RoundedCornerShape(12.dp), singleLine = true
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VSSurface)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VerySchool", fontWeight = FontWeight.ExtraBold, color = VSPrimary, fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(8.dp).background(if (connected) VSGreen else VSRed, CircleShape))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, null, tint = VSOnSurface) }
                        IconButton(onClick = onProfile) { AvatarImage(avatarBase64 = avatarBase64, name = displayName, size = 36) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VSSurface)
                )
            }
        },
        floatingActionButton = {
            Column {
                SmallFloatingActionButton(onClick = { showNewGroup = true }, containerColor = VSSecondary, shape = CircleShape) {
                    Icon(Icons.Default.Group, null, tint = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                FloatingActionButton(onClick = { showNewChat = true }, containerColor = VSPrimary, shape = CircleShape) {
                    Icon(Icons.Default.Edit, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        val matchingUsers = if (searchQuery.isNotBlank())
            users.filter { it.id != currentUserId && (it.username.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery)) }
        else emptyList()

        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (matchingUsers.isNotEmpty()) {
                item {
                    Text("Пользователи", color = VSOnSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                items(matchingUsers) { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNewDm(user) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImage(user.avatarBase64, user.displayName, 46)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user.displayName, color = VSOnSurface, fontWeight = FontWeight.Medium)
                            Text("@${user.username} • ID: ${user.id}", color = VSOnSurfaceMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Send, null, tint = VSPrimary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = VSBorder.copy(0.3f), modifier = Modifier.padding(start = 74.dp))
                }
                item { HorizontalDivider(color = VSBorder, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                item { Text("Чаты", color = VSOnSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }

            if (filteredChats.isEmpty() && searchQuery.isBlank()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ChatBubbleOutline, null, tint = VSOnSurfaceMuted, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Нет чатов", color = VSOnSurfaceMuted)
                            Spacer(Modifier.height(4.dp))
                            Text("Нажми ✏\uFE0F чтобы написать кому-нибудь", color = VSOnSurfaceMuted, fontSize = 13.sp)
                            if (users.filter { it.id != currentUserId }.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("(Пока нет других пользователей)", color = VSOnSurfaceMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            items(filteredChats, key = { it.id }) { chat ->
                ChatListItem(chat = chat, onClick = { onChatClick(chat) })
            }
        }
    }

    if (showNewChat) {
        val others = users.filter { it.id != currentUserId }
        var dmSearch by remember { mutableStateOf("") }
        val filteredOthers = if (dmSearch.isBlank()) others
            else others.filter { it.username.contains(dmSearch, ignoreCase = true) || it.displayName.contains(dmSearch, ignoreCase = true) || it.id.contains(dmSearch) }

        AlertDialog(
            onDismissRequest = { showNewChat = false; dmSearch = "" },
            title = { Text("Написать сообщение", color = VSOnSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = dmSearch, onValueChange = { dmSearch = it },
                        placeholder = { Text("Поиск по имени или ID...", color = VSOnSurfaceMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder, focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface),
                        shape = RoundedCornerShape(10.dp), singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = VSOnSurfaceMuted) }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filteredOthers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text(if (others.isEmpty()) "Нет других пользователей" else "Ничего не найдено", color = VSOnSurfaceMuted)
                        }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filteredOthers) { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onNewDm(user); showNewChat = false; dmSearch = "" }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarImage(user.avatarBase64, user.displayName, 42)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.displayName, color = VSOnSurface, fontWeight = FontWeight.Medium)
                                    Text("@${user.username} • ${user.id}", color = VSOnSurfaceMuted, fontSize = 12.sp)
                                }
                                if (user.online) Box(Modifier.size(8.dp).background(VSGreen, CircleShape))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNewChat = false; dmSearch = "" }) { Text("Отмена", color = VSOnSurfaceMuted) } },
            containerColor = VSSurface
        )
    }

    if (showNewGroup) {
        CreateGroupDialog(
            users = users.filter { it.id != currentUserId },
            onCreate = { name, ids -> onNewGroup(name, ids); showNewGroup = false },
            onDismiss = { showNewGroup = false }
        )
    }
}

@Composable
fun ChatListItem(chat: ChatEntity, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            if (chat.avatarBase64.isNotEmpty()) AvatarImage(chat.avatarBase64, chat.name, 52)
            else Box(Modifier.size(52.dp).background(VSPrimary.copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (chat.isGroup) Icons.Default.Group else Icons.Default.Person, null, tint = VSPrimary, modifier = Modifier.size(28.dp))
            }
            if (chat.unreadCount > 0) Box(Modifier.align(Alignment.TopEnd).size(18.dp).background(VSRed, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chat.name, color = VSOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.lastMessageTime > 0) Text(formatTime(chat.lastMessageTime), color = VSOnSurfaceMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(chat.lastMessage.ifEmpty { "Нет сообщений" }, color = VSOnSurfaceMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = VSBorder.copy(alpha = 0.5f), modifier = Modifier.padding(start = 82.dp))
}

@Composable
fun CreateGroupDialog(users: List<UserEntity>, onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var groupName by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isBlank()) users else users.filter { it.displayName.contains(search, ignoreCase = true) || it.username.contains(search, ignoreCase = true) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Создать группу", color = VSOnSurface) },
        text = {
            Column {
                OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("Название группы") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder, focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Поиск...", color = VSOnSurfaceMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder, focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = VSOnSurfaceMuted) })
                Spacer(Modifier.height(4.dp))
                Text("Выбрано: ${selected.size}", color = VSOnSurfaceMuted, fontSize = 12.sp)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(filtered) { user ->
                        Row(Modifier.fillMaxWidth().clickable { if (user.id in selected) selected.remove(user.id) else selected.add(user.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = user.id in selected, onCheckedChange = { c -> if (c) selected.add(user.id) else selected.remove(user.id) }, colors = CheckboxDefaults.colors(checkedColor = VSPrimary))
                            Spacer(Modifier.width(8.dp))
                            AvatarImage(user.avatarBase64, user.displayName, 32)
                            Spacer(Modifier.width(8.dp))
                            Column { Text(user.displayName, color = VSOnSurface, fontSize = 14.sp); Text("@${user.username}", color = VSOnSurfaceMuted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (groupName.isNotBlank() && selected.isNotEmpty()) onCreate(groupName, selected.toList()) }) { Text("Создать", color = VSPrimary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = VSOnSurfaceMuted) } },
        containerColor = VSSurface)
}

@Composable
fun AvatarImage(avatarBase64: String, name: String, size: Int) {
    if (avatarBase64.isNotEmpty()) {
        val bytes = try { android.util.Base64.decode(avatarBase64, android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
        if (bytes != null) { AsyncImage(model = bytes, contentDescription = name, modifier = Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop); return }
    }
    Box(Modifier.size(size.dp).background(VSPrimary.copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = VSPrimary, fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp)
    }
}

private fun formatTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "сейчас"; diff < 3600_000 -> "${diff / 60_000} мин"
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ts))
    }
}
