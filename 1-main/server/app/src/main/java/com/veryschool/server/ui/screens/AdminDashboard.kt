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
fun AdminLoginScreen(onLogin: (String, String) -> Unit, isLoading: Boolean = false) {
    val tc = LocalAdminTC.current
    var email by remember { mutableStateOf("") }
    var pass  by remember { mutableStateOf("") }
    val fc = fc(tc)
    Box(Modifier.fillMaxSize().background(tc.bg), contentAlignment = Alignment.Center) {
        Card(Modifier.width(320.dp), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = tc.surf)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VS", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = AdminPrimary)
                Text("Admin Panel", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("VerySchool Administration", color = tc.muted, fontSize = 12.sp)
                HorizontalDivider(color = tc.border)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
                    colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = tc.muted) })
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Пароль") },
                    colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp), visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = tc.muted) })
                Button(onClick = { onLogin(email, pass) }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary),
                    enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Войти", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(
    users: List<UserModel>,
    chats: List<ChatModel>,
    logs: List<LogModel>,
    passphrases: List<String>,
    globalSettings: GlobalSettings,
    userStats: Map<String, Int>,
    chatStats: Map<String, Int>,
    onBan: (String, String) -> Unit,
    onUnban: (String) -> Unit,
    onFreeze: (String) -> Unit,
    onUnfreeze: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onUpdateUser: (String, Map<String, Any>) -> Unit,
    onSetVerified: (String, Boolean) -> Unit,
    onDeleteMsg: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onBotToUser: (String, String) -> Unit,
    onBotBroadcast: (String) -> Unit,
    onSavePassphrases: (List<String>) -> Unit,
    onSaveGlobalSettings: (GlobalSettings) -> Unit,
    onOpenChat: (String) -> Unit,
    onRefreshStats: () -> Unit
) {
    val tc = LocalAdminTC.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "📊" to "Обзор",
        "👥" to "Юзеры",
        "💬" to "Чаты",
        "📋" to "Логи",
        "🤖" to "БОТ",
        "🔑" to "Фразы",
        "⚙️" to "Настройки"
    )

    // Статус режима обслуживания в топбаре
    val isMaintenance = globalSettings.maintenanceMode

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VS", fontWeight = FontWeight.ExtraBold, color = AdminPrimary, fontSize = 22.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Admin", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        if (isMaintenance) {
                            Surface(shape = RoundedCornerShape(50), color = AdminRed.copy(0.2f)) {
                                Text("⚠️ Обслуживание", color = AdminRed, fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(50), color = AdminPrimary.copy(0.2f)) {
                                Text("${userStats["online"] ?: 0} онлайн", color = AdminPrimary, fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshStats) { Icon(Icons.Default.Refresh, null, tint = tc.on) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = tc.surf,
                contentColor = AdminPrimary, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, (emoji, label) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji, fontSize = 14.sp)
                            Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                        }
                    })
                }
            }
            when (tab) {
                0 -> OverviewTab(users, chats, userStats, chatStats, globalSettings)
                1 -> UsersTab(users, onBan, onUnban, onFreeze, onUnfreeze, onDeleteUser, onUpdateUser, onSetVerified)
                2 -> ChatsTab(chats, onOpenChat, onDeleteMsg, onDeleteChat)
                3 -> LogsTab(logs)
                4 -> BotTab(users, onBotToUser, onBotBroadcast)
                5 -> PassphrasesTab(passphrases, onSavePassphrases)
                6 -> GlobalSettingsScreen(globalSettings, onSaveGlobalSettings)
            }
        }
    }
}

// ── Обзор ────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    users: List<UserModel>, chats: List<ChatModel>,
    userStats: Map<String, Int>, chatStats: Map<String, Int>,
    settings: GlobalSettings
) {
    val tc = LocalAdminTC.current
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Статус системы
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.maintenanceMode) AdminRed.copy(0.1f) else AdminGreen.copy(0.1f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (settings.maintenanceMode) "⚠️" else "✅", fontSize = 28.sp)
                    Column {
                        Text(if (settings.maintenanceMode) "Режим обслуживания" else "Система работает нормально",
                            color = if (settings.maintenanceMode) AdminRed else AdminGreen,
                            fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("v${settings.appVersion} · Мин: v${settings.minAppVersion}", color = tc.muted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Объявление
        if (settings.announcementEnabled && settings.announcementText.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AdminYellow.copy(0.1f))) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📢", fontSize = 20.sp)
                        Text(settings.announcementText, color = AdminYellow, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Статы пользователей
        item {
            Text("Пользователи", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Всего", userStats["total"] ?: users.size, AdminPrimary, Modifier.weight(1f))
                StatCard("Онлайн", userStats["online"] ?: 0, AdminGreen, Modifier.weight(1f))
                StatCard("Бан", userStats["banned"] ?: 0, AdminRed, Modifier.weight(1f))
                StatCard("❄️", userStats["frozen"] ?: 0, AdminBorder, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("✓ Верифиц.", userStats["verified"] ?: 0, AdminYellow, Modifier.weight(1f))
                StatCard("Админов", userStats["admin"] ?: 0, AdminPrimary, Modifier.weight(1f))
                StatCard("Макс.", settings.maxUsersTotal, tc.muted, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }

        // Статы чатов
        item {
            Text("Чаты", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Всего", chatStats["total"] ?: chats.size, AdminPrimary, Modifier.weight(1f))
                StatCard("Группы", chatStats["groups"] ?: 0, AdminGreen, Modifier.weight(1f))
                StatCard("DM", chatStats["dm"] ?: 0, AdminBorder, Modifier.weight(1f))
                StatCard("BOT", chatStats["bot"] ?: 0, AdminYellow, Modifier.weight(1f))
            }
        }

        // Лимиты
        item {
            Text("Активные лимиты", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LimitRow("Макс. символов в сообщении", settings.maxMessageLength)
                    LimitRow("Макс. фото (KB)", settings.maxImageSizeKb)
                    LimitRow("Макс. участников в группе", settings.maxGroupMembers)
                    LimitRow("Задержка отправки (ms)", settings.messageCooldownMs.toInt())
                    LimitRow("История сообщений", settings.messagesHistoryLimit)
                    LimitRow("Окно редактирования (сек)", settings.editMessageWindowSec)
                }
            }
        }

        // Включённые функции
        item {
            Text("Функции", color = tc.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeatureRow("📝 Регистрация", settings.registrationEnabled)
                    FeatureRow("📷 Изображения", settings.imageMessagesEnabled)
                    FeatureRow("🎤 Голосовые", settings.voiceMessagesEnabled)
                    FeatureRow("🎬 GIF", settings.gifEnabled)
                    FeatureRow("📊 Опросы", settings.pollsEnabled)
                    FeatureRow("⏱ Самоудаление", settings.selfDestructEnabled)
                    FeatureRow("✏️ Редактирование", settings.editMessageEnabled)
                    FeatureRow("↩️ Пересылка", settings.forwardEnabled)
                    FeatureRow("👥 Создание групп", settings.groupCreationEnabled)
                    FeatureRow("🔗 Invite ссылки", settings.inviteLinksEnabled)
                    FeatureRow("🔔 Push уведомления", settings.pushNotificationsEnabled)
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val tc = LocalAdminTC.current
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.12f))) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(label, color = tc.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LimitRow(label: String, value: Int) {
    val tc = LocalAdminTC.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = tc.muted, fontSize = 12.sp)
        Text("$value", color = tc.on, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FeatureRow(label: String, enabled: Boolean) {
    val tc = LocalAdminTC.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = tc.on, fontSize = 13.sp)
        Text(if (enabled) "ВКЛ" else "ВЫКЛ",
            color = if (enabled) AdminGreen else AdminRed,
            fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Users ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersTab(
    users: List<UserModel>,
    onBan: (String, String) -> Unit, onUnban: (String) -> Unit,
    onFreeze: (String) -> Unit, onUnfreeze: (String) -> Unit,
    onDelete: (String) -> Unit, onUpdate: (String, Map<String, Any>) -> Unit,
    onSetVerified: (String, Boolean) -> Unit
) {
    val tc = LocalAdminTC.current
    var search by remember { mutableStateOf("") }
    var editUser by remember { mutableStateOf<UserModel?>(null) }
    var banTarget by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") } // all / banned / frozen / online / admin

    val base = if (search.isBlank()) users
        else users.filter { it.username.contains(search, true) || it.displayName.contains(search, true) || it.id.contains(search) || it.numericId.toString().contains(search) }
    val filtered = when (filter) {
        "banned"  -> base.filter { it.isBanned }
        "frozen"  -> base.filter { it.isFrozen }
        "online"  -> base.filter { it.online }
        "admin"   -> base.filter { it.isAdmin }
        "verified"-> base.filter { it.isVerified }
        else      -> base
    }.sortedWith(compareByDescending<UserModel> { it.online }.thenBy { it.displayName })

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = search, onValueChange = { search = it },
            placeholder = { Text("Поиск по имени, username, ID...", color = tc.muted) },
            modifier = Modifier.fillMaxWidth().padding(12.dp), colors = fc(tc),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = tc.muted) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null, tint = tc.muted) } })

        // Фильтры
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "Все", "online" to "Онлайн", "banned" to "Бан", "frozen" to "Заморожены",
                "admin" to "Админы", "verified" to "Верифиц.").forEach { (f, label) ->
                FilterChip(selected = filter == f, onClick = { filter = f },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AdminPrimary, selectedLabelColor = Color.White))
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("${filtered.size} из ${users.size}", color = tc.muted, fontSize = 12.sp) }
            items(filtered, key = { it.id }) { user ->
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = when {
                        user.isBanned  -> AdminRed.copy(0.08f)
                        user.isFrozen  -> AdminBorder.copy(0.1f)
                        user.isAdmin   -> AdminYellow.copy(0.06f)
                        else           -> tc.surf
                    }),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Заголовок
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(AdminPrimary.copy(0.18f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(user.displayName.firstOrNull()?.uppercase() ?: "?",
                                    color = AdminPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.displayName, color = tc.on, fontWeight = FontWeight.SemiBold)
                                    if (user.isAdmin) Chip("ADMIN", AdminYellow)
                                    if (user.isVerified) Chip("✓", AdminPrimary)
                                    if (user.isBanned) Chip("BAN", AdminRed)
                                    if (user.isFrozen) Chip("❄️", AdminBorder)
                                }
                                Text("@${user.username} · #${user.numericId}", color = tc.muted, fontSize = 11.sp)
                                Text("ID: ${user.id}", color = tc.muted, fontSize = 9.sp)
                            }
                            // Онлайн индикатор
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(10.dp).background(if (user.online) AdminGreen else Color.Gray, CircleShape))
                                Text(if (user.online) "online" else "offline", color = tc.muted, fontSize = 8.sp)
                            }
                        }

                        if (user.isBanned && user.banReason.isNotEmpty()) {
                            Text("Причина бана: ${user.banReason}", color = AdminRed, fontSize = 11.sp)
                        }
                        if (user.statusDisplay().isNotEmpty()) {
                            Text(user.statusDisplay(), color = tc.muted, fontSize = 11.sp)
                        }

                        // Кнопки
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            ABtn("✏️ Редактировать", AdminPrimary) { editUser = user }
                            if (user.isBanned) ABtn("✅ Разбан", AdminGreen) { onUnban(user.id) }
                            else ABtn("🚫 Бан", AdminRed) { banTarget = user.id }
                            if (user.isFrozen) ABtn("🔥 Размороз", AdminGreen) { onUnfreeze(user.id) }
                            else ABtn("❄️ Заморозить", AdminBorder) { onFreeze(user.id) }
                            ABtn(if (user.isVerified) "✓ Снять" else "✓ Верифиц.", AdminYellow) { onSetVerified(user.id, !user.isVerified) }
                            ABtn("🗑️ Удалить", AdminRed) { onDelete(user.id) }
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

// ── Chats ────────────────────────────────────────────────────────────────────

@Composable
private fun ChatsTab(
    chats: List<ChatModel>, onOpen: (String) -> Unit,
    onDeleteMsg: (String, String) -> Unit, onDeleteChat: (String) -> Unit
) {
    val tc = LocalAdminTC.current
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isBlank()) chats
        else chats.filter { it.name.contains(search, true) || it.id.contains(search) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = search, onValueChange = { search = it },
            placeholder = { Text("Поиск чата...", color = tc.muted) },
            modifier = Modifier.fillMaxWidth().padding(12.dp), colors = fc(tc),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = tc.muted) })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("${filtered.size} чатов", color = tc.muted, fontSize = 12.sp) }
            items(filtered, key = { it.id }) { chat ->
                var showMenu by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = tc.surf),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(chat.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(when { chat.isBot -> "🤖"; chat.isGroup -> "👥"; else -> "💌" }, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold)
                            Text("${chat.members.size} участников · ${chat.id.take(8)}...", color = tc.muted, fontSize = 11.sp)
                            if (chat.lastMessage.isNotEmpty())
                                Text(chat.lastMessage.take(50), color = tc.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = tc.muted) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("📂 Открыть", color = tc.on) }, onClick = { showMenu = false; onOpen(chat.id) })
                                DropdownMenuItem(text = { Text("🗑️ Удалить чат", color = AdminRed) }, onClick = { showMenu = false; onDeleteChat(chat.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Logs ────────────────────────────────────────────────────────────────────

@Composable
private fun LogsTab(logs: List<LogModel>) {
    val tc = LocalAdminTC.current
    val sdf = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    var filter by remember { mutableStateOf("") }
    val filtered = if (filter.isBlank()) logs else logs.filter { it.action.contains(filter, true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to "Все", "BAN" to "Бан", "FREEZE" to "Заморозка", "LOGIN" to "Вход",
                "DELETE" to "Удаление", "BOT" to "Бот", "SETTINGS" to "Настройки").forEach { (f, label) ->
                FilterChip(selected = filter == f, onClick = { filter = f },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AdminPrimary, selectedLabelColor = Color.White))
            }
        }
        Text("${filtered.size} из ${logs.size} записей", color = tc.muted, fontSize = 11.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(filtered) { log ->
                val color = when (log.action) {
                    "BAN", "DELETE_MSG", "DELETE_USER", "DELETE_CHAT" -> AdminRed
                    "UNBAN", "UNFREEZE", "MAINTENANCE_OFF" -> AdminGreen
                    "FREEZE", "MAINTENANCE_ON" -> AdminBorder
                    "LOGIN" -> AdminPrimary
                    "BOT_BROADCAST" -> AdminYellow
                    "UPDATE_GLOBAL_SETTINGS" -> AdminPrimary
                    else -> tc.on
                }
                Row(Modifier.fillMaxWidth().background(tc.card.copy(0.5f), RoundedCornerShape(3.dp)).padding(6.dp, 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(log.timestamp?.toDate()?.let { sdf.format(it) } ?: "?", color = tc.muted, fontSize = 9.sp, modifier = Modifier.width(80.dp))
                    Text("[${log.action}]", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                    Text("${log.targetId.take(8)} ${log.details.take(35)}", color = tc.on, fontSize = 9.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Bot ──────────────────────────────────────────────────────────────────────

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                    Text("VerySchool BOT", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = toAll, onCheckedChange = { toAll = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AdminPrimary))
                    Spacer(Modifier.width(8.dp))
                    Text(if (toAll) "📣 Всем пользователям (${users.size})" else "👤 Конкретному пользователю", color = tc.on, fontSize = 14.sp)
                }
                if (!toAll) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = users.firstOrNull { it.id == selectedUid }?.let { "@${it.username} (${it.displayName})" } ?: "Выберите пользователя...",
                            onValueChange = {}, readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            colors = fc, shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            users.sortedBy { it.displayName }.forEach { user ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(if (user.online) AdminGreen else Color.Gray, CircleShape))
                                            Text("@${user.username} — ${user.displayName}", color = tc.on, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = { selectedUid = user.id; expanded = false })
                            }
                        }
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it },
                    label = { Text("Текст сообщения") }, colors = fc,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 6)
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            if (toAll) onBroadcast(text.trim())
                            else if (selectedUid.isNotEmpty()) onToUser(selectedUid, text.trim())
                            text = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary),
                    enabled = text.isNotBlank() && (toAll || selectedUid.isNotEmpty())) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (toAll) "Отправить всем (${users.size})" else "Отправить", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Passphrases ──────────────────────────────────────────────────────────────

@Composable
private fun PassphrasesTab(passphrases: List<String>, onSave: (List<String>) -> Unit) {
    val tc = LocalAdminTC.current
    val list = remember(passphrases) { passphrases.toMutableStateList() }
    var newPhrase by remember { mutableStateOf("") }
    val fc = fc(tc)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ключевые фразы входа", color = tc.on, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Пользователи вводят одну из этих фраз при регистрации и входе.", color = tc.muted, fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list) { phrase ->
                Row(Modifier.fillMaxWidth().background(tc.card, RoundedCornerShape(10.dp)).padding(14.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, tint = AdminPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(phrase, color = AdminPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    IconButton(onClick = { list.remove(phrase) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = AdminRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (list.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Нет фраз. Добавьте хотя бы одну.", color = AdminRed)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newPhrase, onValueChange = { newPhrase = it },
                placeholder = { Text("Новая фраза...", color = tc.muted) }, colors = fc,
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.Add, null, tint = AdminPrimary) })
            Button(onClick = { if (newPhrase.isNotBlank()) { list.add(newPhrase.trim()); newPhrase = "" } },
                colors = ButtonDefaults.buttonColors(containerColor = AdminPrimary),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.height(56.dp)) { Text("Добавить") }
        }
        Button(onClick = { onSave(list.toList()) }, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp), enabled = list.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable fun fc(tc: AdminTC) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AdminPrimary, unfocusedBorderColor = tc.border,
    focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = AdminPrimary,
    focusedLabelColor = AdminPrimary, unfocusedLabelColor = tc.muted)

@Composable fun Chip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.2f)) {
        Text(text, color = color, fontSize = 9.sp, modifier = Modifier.padding(4.dp, 2.dp), fontWeight = FontWeight.Bold)
    }
}
@Composable fun ABtn(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditUserDialog(user: UserModel, onSave: (Map<String, Any>) -> Unit, onDismiss: () -> Unit) {
    val tc = LocalAdminTC.current
    var dn by remember(user) { mutableStateOf(user.displayName) }
    var uname by remember(user) { mutableStateOf(user.username) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Редактировать @${user.username}", color = tc.on) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = dn, onValueChange = { dn = it }, label = { Text("Отображаемое имя") },
                    colors = fc(tc), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = uname, onValueChange = { uname = it.lowercase() }, label = { Text("Юзернейм") },
                    colors = fc(tc), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mapOf("displayName" to dn.trim(), "username" to uname.trim().lowercase())) }) {
                Text("Сохранить", color = AdminPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BanDialog(onBan: (String) -> Unit, onDismiss: () -> Unit) {
    val tc = LocalAdminTC.current
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Причина блокировки", color = tc.on) },
        text = {
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Причина (необязательно)") },
                colors = fc(tc), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        },
        confirmButton = {
            TextButton(onClick = { onBan(reason) }) {
                Text("Заблокировать", color = AdminRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf)
}
