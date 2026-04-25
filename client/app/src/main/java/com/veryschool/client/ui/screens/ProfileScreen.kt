package com.veryschool.client.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String, username: String, displayName: String,
    avatarUrl: String, isAdmin: Boolean,
    onBack: () -> Unit,
    onSave: (String, Uri?) -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit
) {
    val tc = LocalTC.current
    var newName by remember(displayName) { mutableStateOf(displayName) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }
    val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = VSPrimary)

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("Мой профиль", color = tc.on, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = tc.on) }
                    TextButton(onClick = { onSave(newName, avatarUri) }) { Text("Сохранить", color = VSPrimary, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clickable { picker.launch("image/*") }) {
                AvatarImage(url = avatarUri?.toString() ?: avatarUrl, name = displayName, size = 100.dp)
                Box(Modifier.size(30.dp).align(Alignment.BottomEnd), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = VSPrimary) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(5.dp).size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("@$username", color = tc.muted, fontSize = 13.sp)
            if (isAdmin) { Spacer(Modifier.height(4.dp)); Surface(shape = RoundedCornerShape(6.dp), color = VSYellow.copy(0.2f)) { Text("  ADMIN  ", color = VSYellow, fontSize = 11.sp, modifier = Modifier.padding(2.dp)) } }
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, null, tint = VSSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text("UID: $userId", color = VSSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = tc.border)
                    Text("Отображаемое имя", color = tc.muted, fontSize = 12.sp)
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, colors = fc, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { showLogout = true }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = VSRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, VSRed)
            ) { Icon(Icons.Default.Logout, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Выйти из аккаунта", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showLogout) AlertDialog(
        onDismissRequest = { showLogout = false }, title = { Text("Выход", color = tc.on) },
        text = { Text("Вы уверены?", color = tc.muted) },
        confirmButton = { TextButton(onClick = onLogout) { Text("Выйти", color = VSRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(user: com.veryschool.client.data.models.UserModel, isAdmin: Boolean, onBack: () -> Unit, onSendMessage: () -> Unit, onBan: () -> Unit, onFreeze: () -> Unit) {
    val tc = LocalTC.current
    Scaffold(containerColor = tc.bg, topBar = {
        TopAppBar(navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
            title = { Text("Профиль", color = tc.on, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            AvatarImage(url = user.avatarUrl, name = user.displayName, size = 100.dp,
                isFrozen = user.isFrozen, isDeleted = user.isDeleted || user.isBanned,
                showOnline = true, isOnline = user.online)
            Spacer(Modifier.height(12.dp))
            Text(user.displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("@${user.username}", color = tc.muted)
            Text(if (user.online) "🟢 онлайн" else "⚫ оффлайн", color = if (user.online) VSGreen else tc.muted, fontSize = 13.sp)
            if (user.isFrozen) Text("❄️ Аккаунт заморожен", color = VSFrozen, fontSize = 12.sp)
            if (user.isBanned) Text("🚫 Заблокирован: ${user.banReason}", color = VSRed, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Упоминание в чате:", color = tc.muted, fontSize = 12.sp)
                    Text("vs:///id=${user.id}", color = VSSecondary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
            if (isAdmin) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Действия администратора", color = tc.muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onFreeze, colors = ButtonDefaults.outlinedButtonColors(contentColor = VSFrozen), border = androidx.compose.foundation.BorderStroke(1.dp, VSFrozen), modifier = Modifier.weight(1f)) {
                                Text(if (user.isFrozen) "Разморозить" else "Заморозить", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = onBan, colors = ButtonDefaults.outlinedButtonColors(contentColor = VSRed), border = androidx.compose.foundation.BorderStroke(1.dp, VSRed), modifier = Modifier.weight(1f)) {
                                Text(if (user.isBanned) "Разбанить" else "Забанить", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSendMessage, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Написать сообщение", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
