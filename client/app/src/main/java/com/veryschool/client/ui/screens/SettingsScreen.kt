package com.veryschool.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.prefs.*
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // Внешний вид
    theme: AppTheme, bubbleStyle: BubbleStyle, fontSize: FontSize,
    chatBg: ChatBg, timeFormat: TimeFormat, compactMode: Boolean,
    // Уведомления
    notifMsg: Boolean, notifSys: Boolean, notifErr: Boolean,
    notifSound: Boolean, notifVib: Boolean, notifPreview: Boolean, notifGroups: Boolean,
    // Приватность
    hideOnline: Boolean, hideRead: Boolean, hideStatus: Boolean,
    // Медиа
    autoDownload: Boolean, sendQuality: String,
    // Данные
    cacheSize: String,
    // Колбэки — внешний вид
    onTheme: (AppTheme) -> Unit,
    onBubbleStyle: (BubbleStyle) -> Unit,
    onFontSize: (FontSize) -> Unit,
    onChatBg: (ChatBg) -> Unit,
    onTimeFormat: (TimeFormat) -> Unit,
    onCompactMode: (Boolean) -> Unit,
    // Уведомления
    onNotifMsg: (Boolean) -> Unit, onNotifSys: (Boolean) -> Unit,
    onNotifErr: (Boolean) -> Unit, onNotifSound: (Boolean) -> Unit,
    onNotifVib: (Boolean) -> Unit, onNotifPreview: (Boolean) -> Unit,
    onNotifGroups: (Boolean) -> Unit,
    // Приватность
    onHideOnline: (Boolean) -> Unit, onHideRead: (Boolean) -> Unit,
    onHideStatus: (Boolean) -> Unit,
    // Медиа
    onAutoDownload: (Boolean) -> Unit, onSendQuality: (String) -> Unit,
    // Данные
    onClearCache: () -> Unit, onExportChats: () -> Unit,
    onAbout: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onTerms: () -> Unit = {},
    onGuidelines: () -> Unit = {},
    onBack: () -> Unit
) {
    val tc = LocalTC.current

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("Настройки", color = tc.on, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(6.dp)) }

            // ── ВНЕШНИЙ ВИД ───────────────────────────────────────────────────
            item { SLabel("🎨 Внешний вид", tc.muted) }
            item {
                SCard(tc.surf) {
                    // Тема
                    SubLabel("Тема приложения", tc.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(AppTheme.DARK to "🌙 Тёмная", AppTheme.LIGHT to "☀️ Светлая", AppTheme.SYSTEM to "⚙️ Авто")
                            .forEach { (t, label) ->
                                FilterChip(selected = theme == t, onClick = { onTheme(t) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSPrimary, selectedLabelColor = Color.White))
                            }
                    }
                    Div(tc); SubLabel("Стиль пузырей", tc.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(BubbleStyle.ROUND to "● Круглый", BubbleStyle.SHARP to "◆ Острый", BubbleStyle.RECT to "■ Прямой")
                            .forEach { (s, label) ->
                                FilterChip(selected = bubbleStyle == s, onClick = { onBubbleStyle(s) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSSecondary, selectedLabelColor = Color.White))
                            }
                    }
                    Div(tc); SubLabel("Размер шрифта в чате", tc.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(FontSize.SMALL to "A" , FontSize.MEDIUM to "A", FontSize.LARGE to "A")
                            .forEachIndexed { i, (s, label) ->
                                FilterChip(selected = fontSize == s, onClick = { onFontSize(s) },
                                    label = { Text(label, fontSize = (11 + i * 2).sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSPrimary, selectedLabelColor = Color.White))
                            }
                    }
                    Div(tc); SubLabel("Фон чата", tc.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ChatBg.NONE to "Нет", ChatBg.DOTS to "⸱⸱⸱", ChatBg.GRID to "▦", ChatBg.WAVES to "∿", ChatBg.STARS to "✦", ChatBg.GRADIENT to "▓")
                            .forEach { (bg, label) ->
                                FilterChip(selected = chatBg == bg, onClick = { onChatBg(bg) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSPrimary, selectedLabelColor = Color.White))
                            }
                    }
                    Div(tc)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            SubLabel("Формат времени", tc.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(TimeFormat.H24 to "24ч", TimeFormat.H12 to "12ч").forEach { (f, l) ->
                                    FilterChip(selected = timeFormat == f, onClick = { onTimeFormat(f) },
                                        label = { Text(l, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSPrimary, selectedLabelColor = Color.White))
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            SubLabel("Компактный режим", tc.muted)
                            Switch(checked = compactMode, onCheckedChange = onCompactMode,
                                colors = SwitchDefaults.colors(checkedTrackColor = VSPrimary))
                        }
                    }
                }
            }

            // ── УВЕДОМЛЕНИЯ ───────────────────────────────────────────────────
            item { SLabel("🔔 Уведомления", tc.muted) }
            item {
                SCard(tc.surf) {
                    Tog("Сообщения в личке",       Icons.Default.Message,         notifMsg,     tc, onNotifMsg)
                    Tog("Сообщения в группах",     Icons.Default.Group,           notifGroups,  tc, onNotifGroups)
                    Tog("Системные (бан, заморозка)", Icons.Default.Shield,       notifSys,     tc, onNotifSys)
                    Tog("Ошибки синхронизации",    Icons.Default.Error,           notifErr,     tc, onNotifErr)
                    Div(tc)
                    Tog("Звук уведомлений",        Icons.Default.VolumeUp,        notifSound,   tc, onNotifSound)
                    Tog("Вибрация",                Icons.Default.Vibration,       notifVib,     tc, onNotifVib)
                    Tog("Показывать текст сообщения", Icons.Default.Visibility,   notifPreview, tc, onNotifPreview)
                }
            }

            // ── ПРИВАТНОСТЬ ───────────────────────────────────────────────────
            item { SLabel("🔒 Приватность", tc.muted) }
            item {
                SCard(tc.surf) {
                    Tog("Скрыть статус «онлайн»",     Icons.Default.VisibilityOff, hideOnline, tc, onHideOnline)
                    Tog("Скрыть ✓✓ прочитано",        Icons.Default.DoneAll,       hideRead,   tc, onHideRead)
                    Tog("Скрыть мой статус",          Icons.Default.Info,          hideStatus, tc, onHideStatus)
                    Div(tc)
                    // Описание
                    Text("Другие пользователи не увидят скрытые данные.", color = tc.muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            // ── МЕДИА ─────────────────────────────────────────────────────────
            item { SLabel("🖼️ Медиа", tc.muted) }
            item {
                SCard(tc.surf) {
                    Tog("Автозагрузка фото", Icons.Default.Download, autoDownload, tc, onAutoDownload)
                    Div(tc)
                    SubLabel("Качество отправляемых фото", tc.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LOW" to "🔹 Низкое", "MEDIUM" to "🔷 Среднее", "HIGH" to "💎 Высокое")
                            .forEach { (q, label) ->
                                FilterChip(selected = sendQuality == q, onClick = { onSendQuality(q) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSPrimary, selectedLabelColor = Color.White))
                            }
                    }
                    Div(tc)
                    Text("Высокое качество = больший объём сообщения (~600KB макс).", color = tc.muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            // ── ДАННЫЕ ────────────────────────────────────────────────────────
            item { SLabel("💾 Данные и хранилище", tc.muted) }
            item {
                SCard(tc.surf) {
                    Act("Очистить кэш ($cacheSize)", Icons.Default.Delete,   VSRed,     tc.on, onClearCache)
                    Div(tc)
                    Act("Экспорт чатов (JSON)",      Icons.Default.Download, VSPrimary, tc.on, onExportChats)
                }
            }

            // ── О ПРИЛОЖЕНИИ ──────────────────────────────────────────────────
            item { SLabel("ℹ️ О приложении", tc.muted) }
            item {
                SCard(tc.surf) {
                    Act("О приложении", Icons.Default.Info, VSPrimary, tc.on, onAbout)
                    Div(tc)
                    Act("Политика конфиденциальности", Icons.Default.Lock, VSSecondary, tc.on, onPrivacy)
                    Div(tc)
                    Act("Правила использования", Icons.Default.Description, tc.muted, tc.on, onTerms)
                    Div(tc)
                    Act("Правила сообщества", Icons.Default.Group, tc.muted, tc.on, onGuidelines)
                    Div(tc)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = VSGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("VerySchool v2.1.0", color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Данные в Firebase Firestore • Kotlin + Jetpack Compose", color = tc.muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── helpers ───────────────────────────────────────────────────────────────────
@Composable fun SCard(surf: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = surf), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), content = content)
    }
}
@Composable fun SLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}
@Composable fun SubLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
}
@Composable fun Div(tc: TC) {
    HorizontalDivider(color = tc.border, modifier = Modifier.padding(vertical = 6.dp))
}
@Composable fun Tog(label: String, icon: ImageVector, value: Boolean, tc: TC, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tc.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = tc.on, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VSPrimary))
    }
}
@Composable fun Act(label: String, icon: ImageVector, iconColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = textColor.copy(0.4f))
    }
}
