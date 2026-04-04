package com.veryschool.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String, displayName: String, username: String,
    avatarBase64: String, online: Boolean,
    onBack: () -> Unit, onSendMessage: () -> Unit
) {
    Scaffold(
        containerColor = VSBackground,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VSOnSurface) } },
                title = { Text("Профиль", color = VSOnSurface, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VSSurface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box {
                AvatarImage(avatarBase64, displayName, 100)
                if (online) Box(Modifier.size(18.dp).background(VSGreen, CircleShape).align(Alignment.BottomEnd))
            }
            Spacer(Modifier.height(16.dp))
            Text(displayName, color = VSOnSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("@$username", color = VSOnSurfaceMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(if (online) "🟢 онлайн" else "⚫ оффлайн", color = if (online) VSGreen else VSOnSurfaceMuted, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VSSurface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tag, null, tint = VSSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Уникальный ID", color = VSOnSurfaceMuted, fontSize = 12.sp)
                        Text(userId, color = VSSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VSSurface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = VSOnSurfaceMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Как упомянуть пользователя", color = VSOnSurfaceMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Напишите в чате:", color = VSOnSurfaceMuted, fontSize = 12.sp)
                    Text("vs:///id=$userId", color = VSSecondary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("Текст станет синей кнопкой-ссылкой", color = VSOnSurfaceMuted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSendMessage,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VSPrimary)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Написать сообщение", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
