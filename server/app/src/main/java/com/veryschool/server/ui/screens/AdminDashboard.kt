package com.veryschool.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.server.data.models.*
import com.veryschool.server.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(onLogin: (String, String) -> Unit) {
    val tc = LocalAdminTC.current
    var email by remember { mutableStateOf("") }
    var pass  by remember { mutableStateOf("") }
    val fc = fc(tc)
    Box(Modifier.fillMaxSize().background(tc.bg), contentAlignment = Alignment.Center) {
        Card(Modifier.width(320.dp), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VS Admin", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = AdminPrimary)
                Text("Панель администратора", color = tc.muted, fontSize = 13.sp)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, colors = fc,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Пароль") }, colors = fc,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation())
                Button(onClick = { onLogin(email, pass) }, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary)) {
                    Text("Войти", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(
    users: List<UserModel>, chats: List<ChatModel>, logs: List<LogModel>, passphrases: List<String>,
    onBan: (String, String) -> Unit, onUnban: (String) -> Unit,
    onFreeze: (String) -> Unit, onUnfreeze: (String) -> Unit,
    onDeleteUser: (String) -> Unit, onUpdateUser: (String, Map<String, Any>) -> Unit,
    onDeleteMsg: (String, String) -> Unit,
    onBotToUser: (String, String) -> Unit, onBotBroadcast: (String) -> Unit,
    onSavePassphrases: (List<String>) -> Unit,
    onOpenChat: (String) -> Unit
) {
    val tc = LocalAdminTC.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Юзеры", "Чаты", "Логи", "Бот", "Фразы")
    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VS", fontWeight = FontWeight.ExtraBold, color = AdminPrimary, fontSize = 22.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Admin", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Surface(shape = RoundedCornerShape(50), color = AdminPrimary.copy(0.2f)) {
                            Text(" ${users.size} 👥  ${chats.size} 💬 ", color = AdminPrimary, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = tc.surf,
                contentColor = AdminPrimary, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) })
                }
            }
            when (tab) {
                0 -> UsersTab(users, onBan, onUnban, onFreeze, onUnfreeze, onDeleteUser, onUpdateUser)
                1 -> ChatsTab(chats, onOpenChat, onDeleteMsg)
                2 -> LogsTab(logs)
                3 -> BotTab(users, onBotToUser, onBotBroadcast)
                4 -> PassphrasesTab(passphrases, onSavePassphrases)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersTab(
    users: List<UserModel>, onBan: (String, String) -> Unit, onUnban: (String) -> Unit,
    onFreeze: (String) -> Unit, onUnfreeze: (String) -> Unit,
    onDelete: (String) -> Unit, onUpdate: (String, Map<String, Any>) -> Unit
) {
    val tc = LocalAdminTC.current
    var search by remember { mutableStateOf("") }
    var editUser by remember { mutableStateOf<UserModel?>(null) }
    var banTarget by remember { mutableStateOf<String?>(null) }
    val filtered = if (search.isBlank()) users
    else users.filter { it.username.contains(search, true) || it.displayName.contains(search, true) || it.id.contains(search) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = search, onValueChange = { search = it },
            placeholder = { Text("Поиск...", color = tc.muted) },
            modifier = Modifier.fillMaxWidth().padding(12.dp), colors = fc(tc),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = tc.muted) })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("${filtered.size} из ${users.size}", color = tc.muted, fontSize = 12.sp) }
            items(filtered, key = { it.id }) { user ->
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (user.isBanned) AdminRed.copy(0.08f) else tc.surf),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(AdminPrimary.copy(0.2f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(user.displayName.firstOrNull()?.uppercase() ?: "?",
                                    color = AdminPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(user.displayName, color = tc.on, fontWeight = FontWeight.SemiBold)
                                    if (user.isAdmin) Chip("ADMIN", AdminYellow)
                                    if (user.isBanned) Chip("BAN", AdminRed)
                                    if (user.isFrozen) Chip("❄️", AdminBorder)
                                }
                                Text("@${user.username} • ${user.id.take(8)}...", color = tc.muted, fontSize = 11.sp)
                            }
                            Box(Modifier.size(8.dp).background(if (user.online) AdminGreen else Color.Gray, CircleShape))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            ABtn("✏️", AdminPrimary) { editUser = user }
                            if (user.isBanned) ABtn("✅ Разбан", AdminGreen) { onUnban(user.id) }
                            else ABtn("🚫 Бан", AdminRed) { banTarget = user.id }
                            if (user.isFrozen) ABtn("🔥 Размороз", AdminGreen) { onUnfreeze(user.id) }
                            else ABtn("❄️ Заморозить", AdminBorder) { onFreeze(user.id) }
                            ABtn("🗑️", AdminRed) { onDelete(user.id) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    editUser?.let { u ->
        EditUserDialog(u, onSave = { upd -> onUpdate(u.id, upd); editUser = null }, onDismiss = { editUser = null })
    }
    banTarget?.let { uid ->
        BanDialog(onBan = { r -> onBan(uid, r); banTarget = null }, onDismiss = { banTarget = null })
    }
}

@Composable
private fun ChatsTab(chats: List<ChatModel>, onOpen: (String) -> Unit, onDeleteMsg: (String, String) -> Unit) {
    val tc = LocalAdminTC.current
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${chats.size} чатов", color = tc.muted, fontSize = 12.sp) }
        items(chats, key = { it.id }) { chat ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = tc.surf),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(chat.id) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (chat.isBot) Icons.Default.SmartToy else if (chat.isGroup) Icons.Default.Group else Icons.Default.Person,
                        null, tint = if (chat.isBot) AdminYellow else AdminPrimary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold)
                        Text("${chat.members.size} уч. • ${chat.id.take(8)}...", color = tc.muted, fontSize = 11.sp)
                        if (chat.lastMessage.isNotEmpty())
                            Text(chat.lastMessage.take(50), color = tc.muted, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = tc.muted)
                }
            }
        }
    }
}

@Composable
private fun LogsTab(logs: List<LogModel>) {
    val tc = LocalAdminTC.current
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        Text("${logs.size} записей", color = tc.muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(logs) { log ->
                val color = when (log.action) {
                    "BAN", "DELETE_MSG", "DELETE_USER" -> AdminRed
                    "UNBAN", "UNFREEZE" -> AdminGreen
                    "FREEZE" -> AdminBorder
                    "LOGIN" -> AdminPrimary
                    else -> tc.on
                }
                Text(
                    "[${log.timestamp?.toDate()?.let { sdf.format(it) } ?: "?"}][${log.action}] " +
                    "uid=${log.userId.take(6)} tgt=${log.targetId.take(6)} ${log.details.take(40)}",
                    color = color, fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth()
                        .background(tc.card.copy(0.5f), RoundedCornerShape(3.dp)).padding(6.dp, 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotTab(users: List<UserModel>, onToUser: (String, String) -> Unit, onBroadcast: (String) -> Unit) {
    val tc = LocalAdminTC.current
    var text by remember { mutableStateOf("") }
    var toAll by remember { mutableStateOf(true) }
    var selectedUid by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val fc = fc(tc)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row { Text("🤖", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                    Text("VerySchool BOT", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = toAll, onCheckedChange = { toAll = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AdminPrimary))
                    Spacer(Modifier.width(8.dp))
                    Text(if (toAll) "Всем пользователям" else "Конкретному", color = tc.on)
                }
                if (!toAll) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = users.firstOrNull { it.id == selectedUid }?.let { "@${it.username}" } ?: "Выберите...",
                            onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, colors = fc,
                            shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            users.forEach { user ->
                                DropdownMenuItem(
                                    text = { Text("@${user.username} (${user.displayName})", color = tc.on) },
                                    onClick = { selectedUid = user.id; expanded = false })
                            }
                        }
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Сообщение") },
                    colors = fc, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 4)
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            if (toAll) onBroadcast(text.trim())
                            else if (selectedUid.isNotEmpty()) onToUser(selectedUid, text.trim())
                            text = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary)) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Отправить")
                }
            }
        }
    }
}

@Composable
private fun PassphrasesTab(passphrases: List<String>, onSave: (List<String>) -> Unit) {
    val tc = LocalAdminTC.current
    val list = remember(passphrases) { passphrases.toMutableStateList() }
    var newPhrase by remember { mutableStateOf("") }
    val fc = fc(tc)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ключевые фразы входа", color = tc.on, fontWeight = FontWeight.Bold)
        Text("Пользователи вводят одну из этих фраз при регистрации.", color = tc.muted, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list) { phrase ->
                Row(Modifier.fillMaxWidth().background(tc.card, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(phrase, color = AdminPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { list.remove(phrase) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = AdminRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newPhrase, onValueChange = { newPhrase = it },
                placeholder = { Text("Новая фраза...", color = tc.muted) }, colors = fc,
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp))
            Button(onClick = { if (newPhrase.isNotBlank()) { list.add(newPhrase.trim()); newPhrase = "" } },
                colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary),
                shape = RoundedCornerShape(10.dp)) { Text("Добавить") }
        }
        Button(onClick = { onSave(list.toList()) }, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Сохранить", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable private fun fc(tc: AdminTC) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AdminPrimary, unfocusedBorderColor = tc.border,
    focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = AdminPrimary)

@Composable private fun Chip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.2f)) {
        Text(text, color = color, fontSize = 8.sp, modifier = Modifier.padding(3.dp, 1.dp))
    }
}
@Composable private fun ABtn(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(30.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditUserDialog(user: UserModel, onSave: (Map<String, Any>) -> Unit, onDismiss: () -> Unit) {
    val tc = LocalAdminTC.current
    var dn by remember(user) { mutableStateOf(user.displayName) }
    var uname by remember(user) { mutableStateOf(user.username) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Редактировать @${user.username}", color = tc.on) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = dn, onValueChange = { dn = it }, label = { Text("Имя") }, colors = fc(tc),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = uname, onValueChange = { uname = it }, label = { Text("Юзернейм") },
                    colors = fc(tc), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(mapOf("displayName" to dn.trim(), "username" to uname.trim().lowercase())) }) {
            Text("Сохранить", color = AdminPrimary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BanDialog(onBan: (String) -> Unit, onDismiss: () -> Unit) {
    val tc = LocalAdminTC.current
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Заблокировать?", color = tc.on) },
        text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Причина") },
            colors = fc(tc), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) },
        confirmButton = { TextButton(onClick = { onBan(reason) }) {
            Text("Заблокировать", color = AdminRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf)
}
