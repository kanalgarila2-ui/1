package com.veryschool.client.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.models.*
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    results: List<Pair<ChatModel, MessageUiModel>>,
    users: List<UserModel>,
    chats: List<ChatModel>,
    onQuery: (String) -> Unit,
    onNavigateToChat: (chatId: String) -> Unit,
    onUserClick: (userId: String) -> Unit,
    onBack: () -> Unit
) {
    val tc = LocalTC.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Три таба: Сообщения / Чаты / Люди
    var tab by remember { mutableStateOf(0) }

    val filteredChats = if (query.isBlank()) emptyList()
        else chats.filter { it.name.contains(query, ignoreCase = true) }
    val filteredUsers = if (query.isBlank()) emptyList()
        else users.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.username.contains(query, ignoreCase = true) ||
            it.numericId.toString().contains(query)
        }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; onQuery(it) },
                        placeholder = { Text("Поиск по всему...", color = tc.muted) },
                        modifier = Modifier.fillMaxWidth().height(50.dp).focusRequester(focusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
                            focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = VSPrimary
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; onQuery("") }) {
                                Icon(Icons.Default.Clear, null, tint = tc.muted)
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Табы
            TabRow(selectedTabIndex = tab, containerColor = tc.surf, contentColor = VSPrimary) {
                listOf("Сообщения ${results.size}", "Чаты ${filteredChats.size}", "Люди ${filteredUsers.size}").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(title, fontSize = 12.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }

            if (query.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 48.sp)
                        Text("Введите запрос для поиска", color = tc.muted)
                        Text("Поиск по сообщениям, чатам и пользователям", color = tc.muted, fontSize = 12.sp)
                    }
                }
                return@Scaffold
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (tab) {
                    // ── Сообщения ─────────────────────────────────────────────
                    0 -> {
                        if (results.isEmpty()) item { EmptyResult("Сообщений не найдено") }
                        else items(results, key = { it.second.id }) { (chat, msg) ->
                            Card(
                                onClick = { onNavigateToChat(chat.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = tc.surf)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Chat, null, tint = VSPrimary, modifier = Modifier.size(14.dp))
                                        Text(chat.name, color = VSPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(msg.senderName, color = tc.muted, fontSize = 11.sp)
                                    // Подсветка совпадения
                                    val idx = msg.text.indexOf(query, ignoreCase = true)
                                    if (idx >= 0) {
                                        Text(buildAnnotatedString {
                                            append(msg.text.substring(0, idx))
                                            withStyle(SpanStyle(background = VSYellow, color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)) {
                                                append(msg.text.substring(idx, idx + query.length))
                                            }
                                            if (idx + query.length < msg.text.length) append(msg.text.substring(idx + query.length))
                                        }, color = tc.on, fontSize = 13.sp, maxLines = 2)
                                    } else Text(msg.text.take(100), color = tc.on, fontSize = 13.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                    // ── Чаты ─────────────────────────────────────────────────
                    1 -> {
                        if (filteredChats.isEmpty()) item { EmptyResult("Чатов не найдено") }
                        else items(filteredChats, key = { it.id }) { chat ->
                            Card(
                                onClick = { onNavigateToChat(chat.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = tc.surf)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AvatarImage(url = chat.avatarUrl, name = chat.name, size = 44.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        val sub = when { chat.isGroup -> "${chat.members.size} участников"; chat.isDm -> "Личный чат"; chat.isBot -> "BOT"; else -> "" }
                                        Text(sub, color = tc.muted, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = tc.muted)
                                }
                            }
                        }
                    }
                    // ── Люди ─────────────────────────────────────────────────
                    2 -> {
                        if (filteredUsers.isEmpty()) item { EmptyResult("Пользователей не найдено") }
                        else items(filteredUsers, key = { it.id }) { user ->
                            Card(
                                onClick = { onUserClick(user.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = tc.surf)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AvatarImage(url = user.avatarUrl, name = user.displayName, size = 44.dp,
                                        showOnline = true, isOnline = user.online)
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(user.displayName, color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            if (user.isVerified) Text("✓", color = VSPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("@${user.username} · #${user.numericId}", color = tc.muted, fontSize = 12.sp)
                                        if (user.statusDisplay().isNotEmpty()) Text(user.statusDisplay(), color = tc.muted, fontSize = 11.sp)
                                    }
                                    Text(user.lastSeenText(), color = tc.muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyResult(text: String) {
    val tc = LocalTC.current
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = tc.muted, fontSize = 14.sp)
    }
}
