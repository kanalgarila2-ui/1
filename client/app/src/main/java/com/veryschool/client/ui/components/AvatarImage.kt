package com.veryschool.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veryschool.client.data.models.UserModel
import com.veryschool.client.ui.theme.VSFrozen
import com.veryschool.client.ui.theme.VSPrimary
import com.veryschool.client.ui.theme.VSRed

@Composable
fun AvatarImage(
    url: String,
    name: String,
    size: Dp = 48.dp,
    isFrozen: Boolean = false,
    isDeleted: Boolean = false,
    isBanned: Boolean = false,
    showOnline: Boolean = false,
    isOnline: Boolean = false
) {
    Box(modifier = Modifier.size(size)) {
        if (isDeleted || isBanned) {
            // "УДАЛЁННЫЙ АККАУНТ" аватарка
            Box(
                Modifier.fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF2D1B1B)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌", fontSize = (size.value * 0.3f).sp)
                    Text("УДАЛЁН", color = VSRed, fontSize = (size.value * 0.18f).sp,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        } else if (url.isNotEmpty()) {
            AsyncImage(
                model = url, contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
                    .then(if (isFrozen) Modifier.border(2.dp, VSFrozen, CircleShape) else Modifier),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .background(VSPrimary.copy(0.25f))
                    .then(if (isFrozen) Modifier.border(2.dp, VSFrozen, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    color = VSPrimary, fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        }
        // Снежинка при заморозке
        if (isFrozen && !isDeleted) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .background(Color(0x4467E8F9)),
                contentAlignment = Alignment.Center
            ) { Text("❄️", fontSize = (size.value * 0.35f).sp) }
        }
        // Онлайн индикатор
        if (showOnline) {
            Box(
                Modifier.size(size * 0.28f).align(Alignment.BottomEnd)
                    .background(if (isOnline) VSFrozen else Color.Gray, CircleShape)
                    .border(1.5.dp, Color.Black, CircleShape)
            )
        }
    }
}
