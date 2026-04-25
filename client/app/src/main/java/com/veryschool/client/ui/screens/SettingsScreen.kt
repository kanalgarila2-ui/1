package com.veryschool.client.ui.screens

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
import com.veryschool.client.data.prefs.AppTheme
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: AppTheme, notifMsg: Boolean, notifSys: Boolean, notifErr: Boolean,
    notifSound: Boolean, notifVib: Boolean, cacheSize: String,
    onTheme: (AppTheme) -> Unit, onNotifMsg: (Boolean) -> Unit, onNotifSys: (Boolean) -> Unit,
    onNotifErr: (Boolean) -> Unit, onNotifSound: (Boolean) -> Unit, onNotifVib: (Boolean) -> Unit,
    onClearCache: () -> Unit, onExportChats: () -> Unit, onBack: () -> Unit
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
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(8.dp)) }

            item { SectionLabel("🎨 Тема", tc.muted) }
            item {
                SCard(tc.surf) {
                    Text("Выберите тему", color = tc.on, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AppTheme.DARK to "Тёмная", AppTheme.LIGHT to "Светлая", AppTheme.SYSTEM to "Авто").forEach { (t, label) ->
                            FilterChip(selected = theme == t, onClick = { onTheme(t) }, label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VSPrimary, selectedLabelColor = Color.White))
                        }
                    }
                }
            }

            item { SectionLabel("🔔 Уведомления", tc.muted) }
            item {
                SCard(tc.surf) {
                    TogRow("Новые сообщения",       Icons.Default.Message,   notifMsg,   tc.on, tc.muted, onNotifMsg)
                    TogRow("Системные (бан, заморозка)", Icons.Default.Shield, notifSys, tc.on, tc.muted, onNotifSys)
                    TogRow("Ошибки синхронизации",  Icons.Default.Error,     notifErr,   tc.on, tc.muted, onNotifErr)
                    HorizontalDivider(color = tc.border, modifier = Modifier.padding(vertical = 4.dp))
                    TogRow("Звук",      Icons.Default.VolumeUp,  notifSound, tc.on, tc.muted, onNotifSound)
                    TogRow("Вибрация",  Icons.Default.Vibration, notifVib,   tc.on, tc.muted, onNotifVib)
                }
            }

            item { SectionLabel("💾 Данные", tc.muted) }
            item {
                SCard(tc.surf) {
                    ActRow("Очистить кэш ($cacheSize)", Icons.Default.Delete,   VSRed,     tc.on, onClearCache)
                    HorizontalDivider(color = tc.border, modifier = Modifier.padding(vertical = 4.dp))
                    ActRow("Экспорт чатов (JSON)",      Icons.Default.Download, VSPrimary, tc.on, onExportChats)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable private fun SCard(surf: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = surf), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
@Composable private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}
@Composable private fun TogRow(label: String, icon: ImageVector, value: Boolean, on: Color, muted: Color, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
        Text(label, color = on, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VSPrimary))
    }
}
@Composable private fun ActRow(label: String, icon: ImageVector, iconColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = textColor.copy(0.4f))
    }
}
