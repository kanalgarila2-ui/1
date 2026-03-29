package com.veryschool.client.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String, username: String, displayName: String, avatarBase64: String, serverUrl: String,
    onBack: () -> Unit, onSave: (String, Uri?) -> Unit,
    onChangePassword: (String, String) -> Unit, onLogout: () -> Unit
) {
    var newDisplayName by remember(displayName) { mutableStateOf(displayName) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedAvatarUri = uri }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder,
        focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface, cursorColor = VSPrimary
    )

    Scaffold(
        containerColor = VSBackground,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VSOnSurface) } },
                title = { Text("Профиль", color = VSOnSurface, fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = { onSave(newDisplayName, selectedAvatarUri) }) { Text("Сохранить", color = VSPrimary, fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VSSurface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            // Avatar
            Box(Modifier.size(110.dp).clickable { imagePicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (selectedAvatarUri != null) {
                    AsyncImage(model = selectedAvatarUri, contentDescription = null, modifier = Modifier.size(110.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    AvatarImage(avatarBase64, displayName, 110)
                }
                Box(Modifier.align(Alignment.BottomEnd).size(32.dp).background(VSPrimary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

            // Info card
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = VSSurface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, null, tint = VSSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ID: $userId", color = VSSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.AlternateEmail, null, tint = VSOnSurfaceMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("@$username", color = VSOnSurfaceMuted, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = VSBorder)
                    Text("Отображаемое имя", color = VSOnSurfaceMuted, fontSize = 12.sp)
                    OutlinedTextField(value = newDisplayName, onValueChange = { newDisplayName = it },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Server info
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = VSSurface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, null, tint = VSOnSurfaceMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Сервер", color = VSOnSurfaceMuted, fontSize = 12.sp)
                        Text(serverUrl, color = VSOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Change password
            OutlinedButton(onClick = { showPasswordDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VSPrimary), border = androidx.compose.foundation.BorderStroke(1.dp, VSPrimary)) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Изменить пароль", fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))

            // Logout
            OutlinedButton(onClick = { showLogoutDialog = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VSRed), border = androidx.compose.foundation.BorderStroke(1.dp, VSRed)) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Выйти из аккаунта", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выход", color = VSOnSurface) },
            text = { Text("Вы уверены?", color = VSOnSurfaceMuted) },
            confirmButton = { TextButton(onClick = onLogout) { Text("Выйти", color = VSRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена", color = VSOnSurfaceMuted) } },
            containerColor = VSSurface)
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onConfirm = { old, new -> onChangePassword(old, new); showPasswordDialog = false },
            onDismiss = { showPasswordDialog = false }
        )
    }
}

@Composable
fun ChangePasswordDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder,
        focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface, cursorColor = VSPrimary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить пароль", color = VSOnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = oldPass, onValueChange = { oldPass = it; error = "" },
                    label = { Text("Текущий пароль") }, colors = fieldColors, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = newPass, onValueChange = { newPass = it; error = "" },
                    label = { Text("Новый пароль") }, colors = fieldColors, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = confirmPass, onValueChange = { confirmPass = it; error = "" },
                    label = { Text("Повторите пароль") }, colors = fieldColors, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                if (error.isNotEmpty()) Text(error, color = VSRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (oldPass.isBlank() || newPass.isBlank()) { error = "Заполните все поля"; return@TextButton }
                if (newPass != confirmPass) { error = "Пароли не совпадают"; return@TextButton }
                if (newPass.length < 6) { error = "Минимум 6 символов"; return@TextButton }
                onConfirm(oldPass, newPass)
            }) { Text("Изменить", color = VSPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = VSOnSurfaceMuted) } },
        containerColor = VSSurface
    )
}
