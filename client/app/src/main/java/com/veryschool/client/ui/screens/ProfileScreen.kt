package com.veryschool.client.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.models.UserModel
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String, username: String, displayName: String,
    avatarUrl: String, isAdmin: Boolean,
    statusEmoji: String = "", statusText: String = "",
    msgSentCount: Long = 0L,
    nameHistory: List<String> = emptyList(),
    onBack: () -> Unit,
    onSave: (String, Uri?) -> Unit,
    onSaveStatus: ((emoji: String, text: String) -> Unit)? = null,
    onChangePassword: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    onStarred: (() -> Unit)? = null
) {
    val tc = LocalTC.current
    var newName by remember(displayName) { mutableStateOf(displayName) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var editStatusEmoji by remember(statusEmoji) { mutableStateOf(statusEmoji) }
    var editStatusText by remember(statusText) { mutableStateOf(statusText) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val fc = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
        focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = VSPrimary
    )

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("Мой профиль", color = tc.on, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = tc.on) }
                    TextButton(onClick = { onSave(newName, avatarUri) }) {
                        Text("Сохранить", color = VSPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.clickable { picker.launch("image/*") }) {
                AvatarImage(url = avatarUri?.toString() ?: avatarUrl, name = displayName, size = 100.dp)
                Box(Modifier.size(30.dp).align(Alignment.BottomEnd), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = VSPrimary) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color.White,
                            modifier = Modifier.padding(5.dp).size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("@$username", color = tc.muted, fontSize = 13.sp)
            if (isAdmin) {
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = VSYellow.copy(0.2f)) {
                    Text("  ADMIN  ", color = VSYellow, fontSize = 11.sp, modifier = Modifier.padding(2.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, null, tint = VSSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("UID: $userId", color = VSSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = tc.border)
                    Text("Отображаемое имя", color = tc.muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        colors = fc, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = tc.muted) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Row(
                    Modifier.fillMaxWidth().clickable { showPasswordDialog = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, tint = VSPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Изменить пароль", color = tc.on, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = tc.muted)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Статистика (ФИЧА #19)
            if (msgSentCount > 0 || nameHistory.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$msgSentCount", color = VSPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("сообщений", color = tc.muted, fontSize = 11.sp)
                        }
                        if (nameHistory.isNotEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${nameHistory.size}", color = VSSecondary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("имён", color = tc.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Избранные сообщения (ФИЧА #3)
            if (onStarred != null) {
                Card(Modifier.fillMaxWidth().clickable { onStarred() }, shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = VSYellow, modifier = Modifier.size(22.dp))
                        Text("Избранные сообщения", color = tc.on, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = tc.muted)
                    }
                }
            }

            // ФИЧА: ссылка-упоминание (тап копирует в буфер)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Моя ссылка-упоминание", color = tc.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    val link = "vs:///id=$userId"
                    Text(link, color = VSSecondary, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        modifier = Modifier.clickable { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(link)) })
                    Text("Нажми чтобы скопировать. Другие нажмут и увидят твой профиль.", color = tc.muted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ФИЧА: статус пользователя
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Мой статус", color = tc.muted, fontSize = 12.sp)
                    val statusEmojis = listOf("","🎮","🎵","📚","💤","🏃","🍕","✈️","💻","🎯","😎","🤔")
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(statusEmojis.size) { idx ->
                            val e = statusEmojis[idx]
                            Surface(shape = RoundedCornerShape(8.dp),
                                color = if (editStatusEmoji == e) VSPrimary.copy(0.3f) else tc.card,
                                modifier = Modifier.clickable { editStatusEmoji = e }.size(36.dp),
                                contentColor = tc.on) {
                                Box(contentAlignment = Alignment.Center) { Text(if (e.isEmpty()) "✕" else e, fontSize = 18.sp) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editStatusText, onValueChange = { editStatusText = it },
                        placeholder = { Text("Текст статуса...", color = tc.muted) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = VSPrimary)
                    )
                    Button(onClick = { onSaveStatus?.invoke(editStatusEmoji, editStatusText) },
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)) {
                        Text("Сохранить статус", color = Color.White)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VSRed),
                border = BorderStroke(1.dp, VSRed)
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Выйти из аккаунта", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Выход", color = tc.on) },
            text = { Text("Вы уверены что хотите выйти?", color = tc.muted) },
            confirmButton = { TextButton(onClick = onLogout) { Text("Выйти", color = VSRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Отмена", color = tc.muted) } },
            containerColor = tc.surf
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            tc = tc,
            onConfirm = { cur, nw -> onChangePassword(cur, nw); showPasswordDialog = false },
            onDismiss = { showPasswordDialog = false }
        )
    }
}

@Composable
private fun ChangePasswordDialog(tc: TC, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showCur by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val fc = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
        focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = VSPrimary
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить пароль", color = tc.on, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPass, onValueChange = { currentPass = it; error = "" },
                    label = { Text("Текущий пароль") }, colors = fc,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showCur) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showCur = !showCur }) { Icon(if (showCur) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = tc.muted) } }
                )
                OutlinedTextField(
                    value = newPass, onValueChange = { newPass = it; error = "" },
                    label = { Text("Новый пароль") }, colors = fc,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showNew = !showNew }) { Icon(if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = tc.muted) } }
                )
                OutlinedTextField(
                    value = confirmPass, onValueChange = { confirmPass = it; error = "" },
                    label = { Text("Повторите пароль") }, colors = fc,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error.isNotEmpty()) Text(error, color = VSRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    currentPass.isBlank() -> error = "Введите текущий пароль"
                    newPass.length < 6    -> error = "Минимум 6 символов"
                    newPass != confirmPass -> error = "Пароли не совпадают"
                    else -> onConfirm(currentPass, newPass)
                }
            }) { Text("Сохранить", color = VSPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = tc.muted) } },
        containerColor = tc.surf
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    user: UserModel, isAdmin: Boolean,
    onBack: () -> Unit, onSendMessage: () -> Unit,
    onBan: () -> Unit, onFreeze: () -> Unit
) {
    val tc = LocalTC.current
    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("Профиль", color = tc.on, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            AvatarImage(url = user.avatarUrl, name = user.displayName, size = 100.dp,
                isFrozen = user.isFrozen, isDeleted = user.isDeleted || user.isBanned,
                showOnline = true, isOnline = user.online)
            Spacer(Modifier.height(12.dp))
            Text(user.displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("@${user.username}", color = tc.muted)
            Text(if (user.online) "🟢 онлайн" else "⚫ оффлайн",
                color = if (user.online) VSGreen else tc.muted, fontSize = 13.sp)
            if (user.isFrozen) Text("❄️ Заморожен", color = VSFrozen, fontSize = 12.sp)
            if (user.isBanned) Text("🚫 Заблокирован: ${user.banReason}", color = VSRed, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Упоминание:", color = tc.muted, fontSize = 12.sp)
                    Text("vs:///id=${user.id}", color = VSSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
            if (isAdmin) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = tc.surf)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Действия администратора", color = tc.muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onFreeze, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VSFrozen),
                                border = BorderStroke(1.dp, VSFrozen)) {
                                Text(if (user.isFrozen) "Разморозить" else "Заморозить", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = onBan, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VSRed),
                                border = BorderStroke(1.dp, VSRed)) {
                                Text(if (user.isBanned) "Разбанить" else "Забанить", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSendMessage, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Написать сообщение", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
