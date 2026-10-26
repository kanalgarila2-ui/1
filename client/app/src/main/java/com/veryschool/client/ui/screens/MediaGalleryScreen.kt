package com.veryschool.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veryschool.client.data.models.MessageModel
import com.veryschool.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    chatName: String,
    mediaMessages: List<MessageModel>,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val tc = LocalTC.current
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }

    if (fullscreenUrl != null) {
        FullscreenImageViewer(url = fullscreenUrl!!, onClose = { fullscreenUrl = null })
        return
    }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Column {
                    Text("Медиа", color = tc.on, fontWeight = FontWeight.Bold)
                    Text(chatName, color = tc.muted, fontSize = 11.sp)
                }},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        if (mediaMessages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🖼", fontSize = 48.sp)
                    Text("Нет медиафайлов", color = tc.muted)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(mediaMessages, key = { it.id }) { msg ->
                    val data = msg.imageBase64.ifEmpty { msg.imageUrl }
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                            .background(tc.card).clickable { fullscreenUrl = data }
                    ) {
                        if (data.startsWith("avatar://")) {
                            val bm = remember(data) {
                                try {
                                    val parts = data.removePrefix("avatar://").split("/", limit = 2)
                                    if (parts.size == 2) {
                                        val b = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                                    } else null
                                } catch (_: Exception) { null }
                            }
                            if (bm != null) Image(bm, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            AsyncImage(model = data, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}
