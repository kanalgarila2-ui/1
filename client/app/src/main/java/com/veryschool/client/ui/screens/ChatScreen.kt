package com.veryschool.client.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.derivedStateOf
import com.veryschool.client.data.models.*
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val REACTIONS_LIST = listOf("👍","❤️","😂","😮","😢","🔥")
private val VS_LINK_RE = Regex("""vs:///id=(\d{6})""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatModel,
    messages: List<MessageModel>,
    optimisticMessages: List<MessageUiModel>,
    currentUserId: String,
    currentUserFrozen: Boolean,
    users: List<UserModel>,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onReact: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, String) -> Unit,
    onUserClick: (String) -> Unit
) {
    val tc = LocalTC.current
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var replyToMsg by remember { mutableStateOf<MessageModel?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onSendImage) }

    // Объединяем реальные + оптимистичные
    val allMessages: List<MessageUiModel> by remember(messages, optimisticMessages) {
        derivedStateOf {
            val realIds = messages.map { it.id }.toSet()
            messages.map { it.toUi() } + optimisticMessages.filter { it.id !in realIds }
        }
    }

    LaunchedEffect(allMessages.size) { if (allMessages.isNotEmpty()) listState.animateScrollToItem(allMessages.size - 1) }

    // Полноэкранный просмотр
    fullscreenImageUrl?.let { url ->
        FullscreenImageViewer(url = url, onClose = { fullscreenImageUrl = null })
        return
    }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { if (selected.isNotEmpty()) selected.clear() else onBack() }) { Icon(if (selected.isNotEmpty()) Icons.Default.Close else Icons.Default.ArrowBack, null, tint = tc.on) } },
                title = {
                    if (selected.isNotEmpty()) {
                        Text("${selected.size} выбрано", color = tc.on, fontWeight = FontWeight.SemiBold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(url = chat.avatarUrl, name = chat.name, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (chat.isGroup) Text("${chat.members.size} участников", color = tc.muted, fontSize = 11.sp)
                                else if (chat.isBot) Text("Бот", color = tc.muted, fontSize = 11.sp)
                            }
                        }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        // Меню при выделении
                        val canDeleteAll = isAdmin || selected.all { msgId -> allMessages.firstOrNull { it.id == msgId }?.senderId == currentUserId }
                        IconButton(onClick = {
                            val text2 = allMessages.filter { it.id in selected }.joinToString("\n") { it.text }
                            // Copy to clipboard handled in MainActivity
                        }) { Icon(Icons.Default.ContentCopy, null, tint = tc.on) }
                        if (canDeleteAll) IconButton(onClick = { selected.toList().forEach { onDelete(it) }; selected.clear() }) { Icon(Icons.Default.Delete, null, tint = VSRed) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        },
        bottomBar = {
            if (currentUserFrozen) {
                Surface(color = tc.surf) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("❄️", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Аккаунт заморожен. Отправка недоступна.", color = VSFrozen, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Surface(color = tc.surf, tonalElevation = 4.dp) {
                    Column {
                        // Reply preview
                        replyToMsg?.let { reply ->
                            Row(
                                Modifier.fillMaxWidth().background(tc.card).padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(32.dp).background(VSPrimary, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(reply.senderName, color = VSPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(reply.text.take(60), color = tc.muted, fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { replyToMsg = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = tc.muted, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).navigationBarsPadding().imePadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.AttachFile, null, tint = tc.muted) }
                            OutlinedTextField(
                                value = text, onValueChange = { text = it },
                                placeholder = { Text("Сообщение...", color = tc.muted) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on, cursorColor = VSPrimary, focusedContainerColor = tc.card, unfocusedContainerColor = tc.card),
                                maxLines = 5
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (text.isNotBlank()) {
                                        onSendText(text.trim())
                                        text = ""; replyToMsg = null
                                    }
                                },
                                modifier = Modifier.size(46.dp), containerColor = VSPrimary, shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Pinned message banner
        val pinnedMsg = if (chat.pinnedMessageId.isNotEmpty()) allMessages.firstOrNull { it.id == chat.pinnedMessageId } else null
        Column(Modifier.fillMaxSize().padding(padding)) {
            pinnedMsg?.let { pinned ->
                Row(
                    Modifier.fillMaxWidth().background(tc.card).padding(horizontal = 12.dp, vertical = 6.dp).clickable { /* scroll to pinned */ },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PushPin, null, tint = VSYellow, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Закреплено", color = VSYellow, fontSize = 10.sp)
                        Text(pinned.text.take(60), color = tc.muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(allMessages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg, isOwn = msg.senderId == currentUserId,
                        isSelected = msg.id in selected,
                        isAdmin = isAdmin,
                        users = users,
                        onLongPress = { if (msg.id in selected) selected.remove(msg.id) else selected.add(msg.id) },
                        onReact = { emoji -> onReact(msg.id, emoji) },
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onReply = { replyToMsg = messages.firstOrNull { it.id == msg.id } },
                        onPin = { onPin(msg.id, msg.text) },
                        onDelete = { onDelete(msg.id) },
                        onUserClick = onUserClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MessageUiModel, isOwn: Boolean, isSelected: Boolean, isAdmin: Boolean,
    users: List<UserModel>, onLongPress: () -> Unit, onReact: (String) -> Unit,
    onImageClick: (String) -> Unit, onReply: () -> Unit, onPin: () -> Unit,
    onDelete: () -> Unit, onUserClick: (String) -> Unit
) {
    val tc = LocalTC.current
    var showReactions by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (msg.isDeleted) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start) {
            Text("🚫 Сообщение удалено", color = tc.muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp).background(tc.card, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        return
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
        if (!isOwn) Text(msg.senderName, color = VSSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (isOwn) 4.dp else 18.dp, bottomStart = if (isOwn) 18.dp else 4.dp))
                .background(if (isSelected) VSPrimary.copy(0.3f) else if (isOwn) VSBubbleOwn else if (msg.chatId.startsWith("bot_")) VSBubbleBot else VSBubbleOther)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress(); showMenu = true },
                        onDoubleTap = { showReactions = true }
                    )
                }
                .padding(10.dp)
        ) {
            Column {
                // Reply preview
                if (msg.replyToId.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(6.dp)) {
                        Box(Modifier.width(2.dp).height(30.dp).background(VSPrimaryLite, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column { Text(msg.replyToText.take(60), color = Color.White.copy(0.8f), fontSize = 12.sp, maxLines = 2) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Image
                if (msg.imageUrl.isNotEmpty()) {
                    var scale by remember { mutableStateOf(1f) }
                    AsyncImage(
                        model = msg.imageUrl, contentDescription = null,
                        modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f) }
                            }
                            .combinedClickable(onClick = { onImageClick(msg.imageUrl) }),
                        contentScale = ContentScale.Fit
                    )
                    if (msg.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
                }
                // Text with vs:/// links
                if (msg.text.isNotEmpty()) {
                    val annotated = buildAnnotatedString {
                        var last = 0
                        VS_LINK_RE.findAll(msg.text).forEach { match ->
                            val uid = match.groupValues[1]
                            val user = users.firstOrNull { it.id == uid }
                            if (match.range.first > last) withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(msg.text.substring(last, match.range.first)) }
                            pushStringAnnotation("USER", uid)
                            withStyle(SpanStyle(color = Color(0xFF93C5FD), fontSize = 15.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) { append("@${user?.displayName ?: uid}") }
                            pop()
                            last = match.range.last + 1
                        }
                        if (last < msg.text.length) withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(msg.text.substring(last)) }
                    }
                    ClickableText(text = annotated, onClick = { offset ->
                        annotated.getStringAnnotations("USER", offset, offset).firstOrNull()?.let { onUserClick(it.item) }
                    })
                }
                // Timestamp + pending
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (msg.isPending) Text("⏳", fontSize = 9.sp)
                    else if (isOwn) {
                        val readCount = msg.readBy.filter { it != msg.senderId }.size
                        Text(if (readCount > 0) "✓✓" else "✓", color = Color.White.copy(0.7f), fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), color = Color.White.copy(0.6f), fontSize = 10.sp)
                }
            }
        }

        // Reactions display
        val reactions = msg.reactions.filter { it.value.isNotEmpty() }
        if (reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.forEach { (emoji, uids) ->
                    Surface(shape = RoundedCornerShape(50), color = tc.card, modifier = Modifier.clickable { onReact(emoji) }) {
                        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji, fontSize = 13.sp)
                            Spacer(Modifier.width(3.dp))
                            Text("${uids.size}", color = tc.on, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }

    // Reaction picker
    if (showReactions) {
        Popup(onDismissRequest = { showReactions = false }) {
            Card(shape = RoundedCornerShape(50.dp), colors = CardDefaults.cardColors(containerColor = tc.card), elevation = CardDefaults.cardElevation(12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    REACTIONS_LIST.forEach { emoji ->
                        Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(4.dp).clickable { onReact(emoji); showReactions = false })
                    }
                }
            }
        }
    }

    // Context menu
    if (showMenu) {
        DropdownMenu(expanded = true, onDismissRequest = { showMenu = false }, modifier = Modifier.background(tc.card)) {
            DropdownMenuItem(text = { Text("↩️ Ответить", color = tc.on) }, onClick = { onReply(); showMenu = false })
            DropdownMenuItem(text = { Text("😀 Реакция", color = tc.on) }, onClick = { showReactions = true; showMenu = false })
            if (isAdmin) {
                DropdownMenuItem(text = { Text("📌 Закрепить", color = tc.on) }, onClick = { onPin(); showMenu = false })
            }
            if (isAdmin) {
                DropdownMenuItem(text = { Text("🗑️ Удалить", color = VSRed) }, onClick = { onDelete(); showMenu = false })
            }
        }
    }
}

@Composable
private fun FullscreenImageViewer(url: String, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x; offsetY += pan.y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f },
                    onTap = { if (scale == 1f) onClose() }
                )
            }
    ) {
        AsyncImage(
            model = url, contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
            },
            contentScale = ContentScale.Fit
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
