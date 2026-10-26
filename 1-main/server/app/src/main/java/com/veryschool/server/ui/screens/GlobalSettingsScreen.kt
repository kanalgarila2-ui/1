package com.veryschool.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.veryschool.server.data.models.GlobalSettings
import com.veryschool.server.ui.theme.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.server.data.models.GlobalSettings
import com.veryschool.server.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    settings: GlobalSettings,
    onSave: (GlobalSettings) -> Unit
) {
    val tc = LocalAdminTC.current

    // Локальные состояния всех полей — инициализируем из settings
    // ── Регистрация ──────────────────────────────────────────────────────────
    var registrationEnabled  by remember(settings) { mutableStateOf(settings.registrationEnabled) }
    var maxUsersTotal        by remember(settings) { mutableStateOf(settings.maxUsersTotal.toString()) }
    var requirePassphrase    by remember(settings) { mutableStateOf(settings.requirePassphrase) }
    var minPasswordLength    by remember(settings) { mutableStateOf(settings.minPasswordLength.toString()) }
    var minUsernameLength    by remember(settings) { mutableStateOf(settings.minUsernameLength.toString()) }
    var maxUsernameLength    by remember(settings) { mutableStateOf(settings.maxUsernameLength.toString()) }
    var minDisplayNameLength by remember(settings) { mutableStateOf(settings.minDisplayNameLength.toString()) }
    var maxDisplayNameLength by remember(settings) { mutableStateOf(settings.maxDisplayNameLength.toString()) }

    // ── Сообщения ─────────────────────────────────────────────────────────────
    var maxMessageLength     by remember(settings) { mutableStateOf(settings.maxMessageLength.toString()) }
    var maxImageSizeKb       by remember(settings) { mutableStateOf(settings.maxImageSizeKb.toString()) }
    var maxAvatarSizeKb      by remember(settings) { mutableStateOf(settings.maxAvatarSizeKb.toString()) }
    var messageCooldownMs    by remember(settings) { mutableStateOf(settings.messageCooldownMs.toString()) }
    var maxPollOptions       by remember(settings) { mutableStateOf(settings.maxPollOptions.toString()) }
    var maxPinnedLinks       by remember(settings) { mutableStateOf(settings.maxPinnedLinks.toString()) }
    var selfDestructEnabled  by remember(settings) { mutableStateOf(settings.selfDestructEnabled) }
    var pollsEnabled         by remember(settings) { mutableStateOf(settings.pollsEnabled) }
    var voiceMessagesEnabled by remember(settings) { mutableStateOf(settings.voiceMessagesEnabled) }
    var gifEnabled           by remember(settings) { mutableStateOf(settings.gifEnabled) }
    var imageMessagesEnabled by remember(settings) { mutableStateOf(settings.imageMessagesEnabled) }
    var editMessageEnabled   by remember(settings) { mutableStateOf(settings.editMessageEnabled) }
    var editWindowSec        by remember(settings) { mutableStateOf(settings.editMessageWindowSec.toString()) }
    var forwardEnabled       by remember(settings) { mutableStateOf(settings.forwardEnabled) }

    // ── Чаты ─────────────────────────────────────────────────────────────────
    var maxGroupMembers      by remember(settings) { mutableStateOf(settings.maxGroupMembers.toString()) }
    var maxGroupsPerUser     by remember(settings) { mutableStateOf(settings.maxGroupsPerUser.toString()) }
    var maxDmChatsPerUser    by remember(settings) { mutableStateOf(settings.maxDmChatsPerUser.toString()) }
    var groupCreationEnabled by remember(settings) { mutableStateOf(settings.groupCreationEnabled) }
    var dmCreationEnabled    by remember(settings) { mutableStateOf(settings.dmCreationEnabled) }
    var inviteLinksEnabled   by remember(settings) { mutableStateOf(settings.inviteLinksEnabled) }

    // ── Уведомления ──────────────────────────────────────────────────────────
    var pushEnabled          by remember(settings) { mutableStateOf(settings.pushNotificationsEnabled) }
    var botMessagesEnabled   by remember(settings) { mutableStateOf(settings.botMessagesEnabled) }

    // ── Модерация ─────────────────────────────────────────────────────────────
    var autoFreeze           by remember(settings) { mutableStateOf(settings.autoFreezeOnReports) }
    var reportsToFreeze      by remember(settings) { mutableStateOf(settings.reportsToAutoFreeze.toString()) }
    var allowBlocking        by remember(settings) { mutableStateOf(settings.allowUserBlocking) }

    // ── Технические ───────────────────────────────────────────────────────────
    var maintenanceMode      by remember(settings) { mutableStateOf(settings.maintenanceMode) }
    var maintenanceMsg       by remember(settings) { mutableStateOf(settings.maintenanceMessage) }
    var appVersion           by remember(settings) { mutableStateOf(settings.appVersion) }
    var minAppVersion        by remember(settings) { mutableStateOf(settings.minAppVersion) }
    var forceUpdateMsg       by remember(settings) { mutableStateOf(settings.forceUpdateMessage) }
    var announcementEnabled  by remember(settings) { mutableStateOf(settings.announcementEnabled) }
    var announcementText     by remember(settings) { mutableStateOf(settings.announcementText) }
    var historyLimit         by remember(settings) { mutableStateOf(settings.messagesHistoryLimit.toString()) }
    var logsRetentionDays    by remember(settings) { mutableStateOf(settings.logsRetentionDays.toString()) }

    fun buildSettings() = settings.copy(
        registrationEnabled = registrationEnabled,
        maxUsersTotal = maxUsersTotal.toIntOrNull() ?: settings.maxUsersTotal,
        requirePassphrase = requirePassphrase,
        minPasswordLength = minPasswordLength.toIntOrNull() ?: settings.minPasswordLength,
        minUsernameLength = minUsernameLength.toIntOrNull() ?: settings.minUsernameLength,
        maxUsernameLength = maxUsernameLength.toIntOrNull() ?: settings.maxUsernameLength,
        minDisplayNameLength = minDisplayNameLength.toIntOrNull() ?: settings.minDisplayNameLength,
        maxDisplayNameLength = maxDisplayNameLength.toIntOrNull() ?: settings.maxDisplayNameLength,
        maxMessageLength = maxMessageLength.toIntOrNull() ?: settings.maxMessageLength,
        maxImageSizeKb = maxImageSizeKb.toIntOrNull() ?: settings.maxImageSizeKb,
        maxAvatarSizeKb = maxAvatarSizeKb.toIntOrNull() ?: settings.maxAvatarSizeKb,
        messageCooldownMs = messageCooldownMs.toLongOrNull() ?: settings.messageCooldownMs,
        maxPollOptions = maxPollOptions.toIntOrNull() ?: settings.maxPollOptions,
        maxPinnedLinks = maxPinnedLinks.toIntOrNull() ?: settings.maxPinnedLinks,
        selfDestructEnabled = selfDestructEnabled,
        pollsEnabled = pollsEnabled,
        voiceMessagesEnabled = voiceMessagesEnabled,
        gifEnabled = gifEnabled,
        imageMessagesEnabled = imageMessagesEnabled,
        editMessageEnabled = editMessageEnabled,
        editMessageWindowSec = editWindowSec.toIntOrNull() ?: settings.editMessageWindowSec,
        forwardEnabled = forwardEnabled,
        maxGroupMembers = maxGroupMembers.toIntOrNull() ?: settings.maxGroupMembers,
        maxGroupsPerUser = maxGroupsPerUser.toIntOrNull() ?: settings.maxGroupsPerUser,
        maxDmChatsPerUser = maxDmChatsPerUser.toIntOrNull() ?: settings.maxDmChatsPerUser,
        groupCreationEnabled = groupCreationEnabled,
        dmCreationEnabled = dmCreationEnabled,
        inviteLinksEnabled = inviteLinksEnabled,
        pushNotificationsEnabled = pushEnabled,
        botMessagesEnabled = botMessagesEnabled,
        autoFreezeOnReports = autoFreeze,
        reportsToAutoFreeze = reportsToFreeze.toIntOrNull() ?: settings.reportsToAutoFreeze,
        allowUserBlocking = allowBlocking,
        maintenanceMode = maintenanceMode,
        maintenanceMessage = maintenanceMsg,
        appVersion = appVersion,
        minAppVersion = minAppVersion,
        forceUpdateMessage = forceUpdateMsg,
        announcementEnabled = announcementEnabled,
        announcementText = announcementText,
        messagesHistoryLimit = historyLimit.toIntOrNull() ?: settings.messagesHistoryLimit,
        logsRetentionDays = logsRetentionDays.toIntOrNull() ?: settings.logsRetentionDays
    )

    val fc = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AdminPrimary, unfocusedBorderColor = tc.border,
        focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = AdminPrimary,
        focusedLabelColor = AdminPrimary, unfocusedLabelColor = tc.muted
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Статус обновления ────────────────────────────────────────────────
        item {
            settings.updatedAt?.let { ts ->
                Text("Последнее обновление: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(ts.toDate())} · @${settings.updatedBy.take(8)}",
                    color = tc.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // ── КРИТИЧНЫЕ ПЕРЕКЛЮЧАТЕЛИ ───────────────────────────────────────────
        item {
            SSection("⚠️ Критичные настройки") {
                // Режим обслуживания
                SwitchRow("🔧 Режим обслуживания", maintenanceMode,
                    subtitle = "Все пользователи видят заглушку",
                    color = if (maintenanceMode) AdminRed else tc.muted
                ) { maintenanceMode = it }
                if (maintenanceMode) {
                    STextField("Сообщение обслуживания", maintenanceMsg, fc) { maintenanceMsg = it }
                }
                HorizontalDivider(color = tc.border)

                // Регистрация
                SwitchRow("📝 Регистрация открыта", registrationEnabled,
                    subtitle = "Отключить чтобы заморозить набор"
                ) { registrationEnabled = it }
                HorizontalDivider(color = tc.border)

                // Объявление
                SwitchRow("📢 Глобальное объявление", announcementEnabled,
                    subtitle = "Показывается всем пользователям"
                ) { announcementEnabled = it }
                if (announcementEnabled) {
                    STextField("Текст объявления", announcementText, fc) { announcementText = it }
                }
            }
        }

        // ── РЕГИСТРАЦИЯ И АУТЕНТИФИКАЦИЯ ─────────────────────────────────────
        item {
            SSection("🔐 Регистрация и аутентификация") {
                SNumberField("Макс. пользователей", maxUsersTotal, fc) { maxUsersTotal = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("Требовать ключевую фразу", requirePassphrase) { requirePassphrase = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Мин. длина пароля", minPasswordLength, fc) { minPasswordLength = it }
                HorizontalDivider(color = tc.border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SNumberField("Мин. username", minUsernameLength, fc, Modifier.weight(1f)) { minUsernameLength = it }
                    SNumberField("Макс. username", maxUsernameLength, fc, Modifier.weight(1f)) { maxUsernameLength = it }
                }
                HorizontalDivider(color = tc.border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SNumberField("Мин. имя", minDisplayNameLength, fc, Modifier.weight(1f)) { minDisplayNameLength = it }
                    SNumberField("Макс. имя", maxDisplayNameLength, fc, Modifier.weight(1f)) { maxDisplayNameLength = it }
                }
            }
        }

        // ── СООБЩЕНИЯ ─────────────────────────────────────────────────────────
        item {
            SSection("💬 Сообщения") {
                SNumberField("Макс. символов в сообщении", maxMessageLength, fc) { maxMessageLength = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. размер фото (KB)", maxImageSizeKb, fc) { maxImageSizeKb = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. размер аватара (KB)", maxAvatarSizeKb, fc) { maxAvatarSizeKb = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Задержка между сообщениями (ms)", messageCooldownMs, fc) { messageCooldownMs = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. вариантов в опросе", maxPollOptions, fc) { maxPollOptions = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. прикреплённых ссылок", maxPinnedLinks, fc) { maxPinnedLinks = it }
            }
        }

        // ── ФУНКЦИИ СООБЩЕНИЙ ─────────────────────────────────────────────────
        item {
            SSection("🎛️ Функции сообщений") {
                SwitchRow("📷 Изображения", imageMessagesEnabled) { imageMessagesEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("🎤 Голосовые сообщения", voiceMessagesEnabled) { voiceMessagesEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("🎬 GIF", gifEnabled) { gifEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("📊 Голосования (опросы)", pollsEnabled) { pollsEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("⏱ Самоудаляющиеся", selfDestructEnabled) { selfDestructEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("✏️ Редактирование", editMessageEnabled) { editMessageEnabled = it }
                if (editMessageEnabled) {
                    SNumberField("Окно редактирования (сек)", editWindowSec, fc) { editWindowSec = it }
                }
                HorizontalDivider(color = tc.border)
                SwitchRow("↩️ Пересылка", forwardEnabled) { forwardEnabled = it }
            }
        }

        // ── ЧАТЫ ─────────────────────────────────────────────────────────────
        item {
            SSection("💬 Чаты и группы") {
                SwitchRow("👥 Создание групп", groupCreationEnabled) { groupCreationEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("🔗 Invite ссылки", inviteLinksEnabled) { inviteLinksEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("💌 Личные чаты (DM)", dmCreationEnabled) { dmCreationEnabled = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. участников в группе", maxGroupMembers, fc) { maxGroupMembers = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. групп на пользователя", maxGroupsPerUser, fc) { maxGroupsPerUser = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Макс. DM чатов", maxDmChatsPerUser, fc) { maxDmChatsPerUser = it }
            }
        }

        // ── УВЕДОМЛЕНИЯ ──────────────────────────────────────────────────────
        item {
            SSection("🔔 Уведомления") {
                SwitchRow("Push уведомления", pushEnabled) { pushEnabled = it }
                HorizontalDivider(color = tc.border)
                SwitchRow("🤖 Сообщения от BOT", botMessagesEnabled) { botMessagesEnabled = it }
            }
        }

        // ── МОДЕРАЦИЯ ─────────────────────────────────────────────────────────
        item {
            SSection("🛡️ Автомодерация") {
                SwitchRow("Авто-заморозка при жалобах", autoFreeze) { autoFreeze = it }
                if (autoFreeze) {
                    SNumberField("Жалоб до заморозки", reportsToFreeze, fc) { reportsToFreeze = it }
                }
                HorizontalDivider(color = tc.border)
                SwitchRow("Блокировка пользователей друг другом", allowBlocking) { allowBlocking = it }
            }
        }

        // ── ВЕРСИИ ────────────────────────────────────────────────────────────
        item {
            SSection("📦 Версии приложения") {
                STextField("Текущая версия", appVersion, fc) { appVersion = it }
                HorizontalDivider(color = tc.border)
                STextField("Минимальная версия (force update)", minAppVersion, fc) { minAppVersion = it }
                HorizontalDivider(color = tc.border)
                STextField("Сообщение force update", forceUpdateMsg, fc) { forceUpdateMsg = it }
            }
        }

        // ── ТЕХНИЧЕСКИЕ ──────────────────────────────────────────────────────
        item {
            SSection("⚙️ Технические") {
                SNumberField("Лимит истории сообщений", historyLimit, fc) { historyLimit = it }
                HorizontalDivider(color = tc.border)
                SNumberField("Хранить логи (дней)", logsRetentionDays, fc) { logsRetentionDays = it }
            }
        }

        // ── Кнопка сохранения ─────────────────────────────────────────────────
        item {
            Button(
                onClick = { onSave(buildSettings()) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdminGreen)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Сохранить в Firebase", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ── DSL компоненты ──────────────────────────────────────────────────────────

@Composable
private fun SSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val tc = LocalAdminTC.current
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, color = AdminPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = tc.surf),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String, value: Boolean,
    subtitle: String = "",
    color: Color = Color.Unspecified,
    onChange: (Boolean) -> Unit
) {
    val tc = LocalAdminTC.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (color != Color.Unspecified) color else tc.on, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) Text(subtitle, color = tc.muted, fontSize = 11.sp)
        }
        Switch(checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AdminGreen,
                uncheckedTrackColor = tc.border
            ))
    }
}

@Composable
private fun SNumberField(
    label: String, value: String,
    colors: TextFieldColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    val tc = LocalAdminTC.current
    OutlinedTextField(
        value = value, onValueChange = { if (it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text(label, fontSize = 12.sp) }, colors = colors,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        singleLine = true, shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun STextField(
    label: String, value: String,
    colors: TextFieldColors,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) }, colors = colors,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        singleLine = true, shape = RoundedCornerShape(10.dp)
    )
}
