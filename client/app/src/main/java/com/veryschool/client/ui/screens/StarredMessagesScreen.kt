package com.veryschool.client.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veryschool.client.data.models.StarredMessage
import com.veryschool.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    starred: List<StarredMessage>,
    onBack: () -> Unit,
    onNavigateToChat: (chatId: String, messageId: String) -> Unit
) {
    val tc = LocalTC.current
    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = { Text("⭐ Избранные сообщения", color = tc.on, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        }
    ) { padding ->
        if (starred.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⭐", fontSize = 48.sp)
                    Text("Нет избранных сообщений", color = tc.muted, fontSize = 15.sp)
                    Text("Зажмите сообщение → ⭐ чтобы добавить", color = tc.muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(starred, key = { it.id }) { msg ->
                    Card(
                        onClick = { onNavigateToChat(msg.chatId, msg.messageId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tc.surf)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Star, null, tint = VSYellow, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(msg.senderName, color = VSSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(msg.text.take(120), color = tc.on, fontSize = 13.sp)
                                msg.starredAt?.let {
                                    Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it.toDate()),
                                        color = tc.muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
