package com.veryschool.client.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import com.veryschool.client.data.db.MessageEntity
import com.veryschool.client.data.db.UserEntity
import com.veryschool.client.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

val REACTIONS = listOf("👍", "❤️", "😂")

// Parse vs:///id=XXXXXX links
private val VS_LINK_REGEX = Regex("vs:///id=(\\d{6})")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatName: String, chatAvatar: String, isGroup: Boolean,
    messages: List<MessageEntity>, currentUserId: String, users: List<UserEntity>,
    onBack: () -> Unit, onSendText: (String) -> Unit, onSendImage: (Uri) -> Unit,
    onReact: (String, String) -> Unit, onTyping: () -> Unit, onTypingStop: () -> Unit,
    onUserProfileClick: ((String) -> Unit)? = null
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var typingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { onSendImage(it) } }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        containerColor = VSBackground,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VSOnSurface) } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(chatAvatar, chatName, 38); Spacer(Modifier.width(10.dp))
                        Column {
                            Text(chatName, color = VSOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (isGroup) Text("группа", color = VSOnSurfaceMuted, fontSize = 11.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VSSurface)
            )
        },
        bottomBar = {
            Surface(color = VSSurface, tonalElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.AttachFile, null, tint = VSOnSurfaceMuted) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { newText ->
                            text = newText
                            typingJob?.cancel()
                            if (newText.isNotEmpty()) {
                                onTyping()
                                typingJob = scope.launch { delay(2000); onTypingStop() }
                            } else onTypingStop()
                        },
                        placeholder = { Text("Сообщение...", color = VSOnSurfaceMuted) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = VSBorder, focusedTextColor = VSOnSurface, unfocusedTextColor = VSOnSurface, cursorColor = VSPrimary, focusedContainerColor = VSBackground, unfocusedContainerColor = VSBackground),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { if (text.isNotBlank()) { onSendText(text.trim()); text = ""; onTypingStop(); typingJob?.cancel() } },
                        modifier = Modifier.size(46.dp), containerColor = VSPrimary, shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(message = msg, isOwn = msg.senderId == currentUserId, users = users,
                    onReact = { emoji -> onReact(msg.id, emoji) },
                    onUserClick = { userId -> onUserProfileClick?.invoke(userId) })
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity, isOwn: Boolean, users: List<UserEntity>, onReact: (String) -> Unit, onUserClick: (String) -> Unit) {
    var showReactions by remember { mutableStateOf(false) }
    val reactions = remember(message.reactions) {
        try { Json.parseToJsonElement(message.reactions).jsonObject.entries.associate { (e, arr) -> e to arr.jsonArray.size }.filter { it.value > 0 } }
        catch (_: Exception) { emptyMap() }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
        if (!isOwn) Text(message.senderName, color = VSSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))

        Box {
            Column(
                modifier = Modifier.widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = if (isOwn) 4.dp else 20.dp, bottomStart = if (isOwn) 20.dp else 4.dp))
                    .background(if (isOwn) VSMessageOwn else VSMessageOther)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { showReactions = true }) }
                    .padding(10.dp)
            ) {
                if (message.imageBase64.isNotEmpty()) {
                    val bytes = try { android.util.Base64.decode(message.imageBase64, android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
                    if (bytes != null) { AsyncImage(model = bytes, contentDescription = null, modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit); if (message.text.isNotEmpty()) Spacer(Modifier.height(4.dp)) }
                }
                if (message.text.isNotEmpty()) {
                    // Parse vs:/// links
                    val vsMatches = VS_LINK_REGEX.findAll(message.text)
                    if (vsMatches.none()) {
                        Text(message.text, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                    } else {
                        val annotated = buildAnnotatedString {
                            var lastEnd = 0
                            vsMatches.forEach { match ->
                                val userId = match.groupValues[1]
                                val user = users.firstOrNull { it.id == userId }
                                if (match.range.first > lastEnd) withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(message.text.substring(lastEnd, match.range.first)) }
                                pushStringAnnotation("USER_LINK", userId)
                                withStyle(SpanStyle(color = Color(0xFF74B9FF), fontSize = 15.sp, fontWeight = FontWeight.Medium)) {
                                    append("@${user?.displayName ?: userId}")
                                }
                                pop()
                                lastEnd = match.range.last + 1
                            }
                            if (lastEnd < message.text.length) withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(message.text.substring(lastEnd)) }
                        }
                        androidx.compose.foundation.text.ClickableText(text = annotated, onClick = { offset ->
                            annotated.getStringAnnotations("USER_LINK", offset, offset).firstOrNull()?.let { onUserClick(it.item) }
                        })
                    }
                }
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)), color = Color.White.copy(0.6f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
            }
            if (showReactions) {
                Popup(onDismissRequest = { showReactions = false }) {
                    Card(shape = RoundedCornerShape(50.dp), colors = CardDefaults.cardColors(containerColor = VSSurfaceVariant), elevation = CardDefaults.cardElevation(12.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            REACTIONS.forEach { emoji -> Text(emoji, fontSize = 26.sp, modifier = Modifier.padding(horizontal = 6.dp).clickable { onReact(emoji); showReactions = false }) }
                        }
                    }
                }
            }
        }
        if (reactions.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.forEach { (emoji, count) ->
                    Surface(shape = RoundedCornerShape(50.dp), color = VSBorder, modifier = Modifier.clickable { onReact(emoji) }) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji, fontSize = 14.sp); Spacer(Modifier.width(4.dp)); Text("$count", color = VSOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
