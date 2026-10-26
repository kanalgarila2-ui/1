package com.veryschool.client.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veryschool.client.ui.theme.VSFrozen
import com.veryschool.client.ui.theme.VSPrimary
import com.veryschool.client.ui.theme.VSRed

// Добавлен цвет VSGreen
private val VSGreen = Color(0xFF00C853)

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
        when {
            isDeleted || isBanned -> DeletedAvatar(size)
            url.startsWith("avatar://") -> Base64Avatar(url, name, size, isFrozen)
            url.isNotEmpty() -> CoilAvatar(url, name, size, isFrozen)
            else -> InitialsAvatar(name, size, isFrozen)
        }
        if (isFrozen && !isDeleted && !isBanned) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0x4467E8F9)), contentAlignment = Alignment.Center) {
                Text("❄️", fontSize = (size.value * 0.35f).sp)
            }
        }
        if (showOnline) {
            Box(
                Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .background(if (isOnline) VSGreen else Color.Gray, CircleShape)
                    .border(1.5.dp, Color.Black, CircleShape)
            )
        }
    }
}

@Composable private fun DeletedAvatar(size: Dp) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2D1B1B)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌", fontSize = (size.value * 0.3f).sp)
            Text("УДАЛЁН", color = VSRed, fontSize = (size.value * 0.17f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable private fun CoilAvatar(url: String, name: String, size: Dp, isFrozen: Boolean) {
    AsyncImage(model = url, contentDescription = name,
        modifier = Modifier.fillMaxSize().clip(CircleShape).then(if (isFrozen) Modifier.border(2.dp, VSFrozen, CircleShape) else Modifier),
        contentScale = ContentScale.Crop)
}

@Composable private fun Base64Avatar(dataUri: String, name: String, size: Dp, isFrozen: Boolean) {
    val bitmap = remember(dataUri) {
        try {
            val parts = dataUri.removePrefix("avatar://").split("/", limit = 2)
            if (parts.size == 2) { val b = Base64.decode(parts[1], Base64.NO_WRAP); BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } else null
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = name,
            modifier = Modifier.fillMaxSize().clip(CircleShape).then(if (isFrozen) Modifier.border(2.dp, VSFrozen, CircleShape) else Modifier),
            contentScale = ContentScale.Crop)
    } else InitialsAvatar(name, size, isFrozen)
}

@Composable private fun InitialsAvatar(name: String, size: Dp, isFrozen: Boolean) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(VSPrimary.copy(0.25f)).then(if (isFrozen) Modifier.border(2.dp, VSFrozen, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = VSPrimary, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
    }
}