package com.veryschool.client.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import com.veryschool.client.data.models.*
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val REACTIONS_LIST = listOf("👍","❤️","😂","😮","😢","🔥","🎉","👀")
private val VS_LINK_RE = Regex("""vs:///id=(\w+)""")

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
    onUserClick: (String) -> Unit,
    onForward: (MessageUiModel) -> Unit = {},
    onSaveDraft: (String) -> Unit = {},
    draft: String = ""
) {
    val tc = LocalTC.current
    val listState = rememberLazyListState()
    // ФИЧА: черновик из VM
    var text by remember(draft) { mutableStateOf(draft) }
    val selected = remember { mutableStateListOf<String>() }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var replyToMsg by remember { mutableStateOf<MessageModel?>(null) }
    // ФИЧА: поиск в чате
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onSendImage) }

    val allMessages: List<MessageUiModel> by remember(messages, optimisticMessages) {
        derivedStateOf {
            val realIds = messages.map { it.id }.toSet()
            messages.map { it.toUi() } + optimisticMessages.filter { it.id !in realIds }
        }
    }

    val displayedMessages = if (searchQuery.isBlank()) allMessages
    else allMessages.filter { it.text.contains(searchQuery, ignoreCase = true) || it.senderName.contains(searchQuery, ignoreCase = true) }

    val msgCount = allMessages.size
    LaunchedEffect(msgCount) { if (msgCount > 0 && searchQuery.isBlank()) listState.animateScrollToItem(msgCount - 1) }

    // Сохраняем черновик при изменении текста
    LaunchedEffect(text) { onSaveDraft(text) }

    fullscreenImageUrl?.let { url ->
        FullscreenImageViewer(url = url, onClose = { fullscreenImageUrl = null }); return
    }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (selected.isNotEmpty()) selected.clear() else onBack() }) {
                        Icon(if (selected.isNotEmpty()) Icons.Default.Close else Icons.Default.ArrowBack, null, tint = tc.on)
                    }
                },
                title = {
                    if (selected.isNotEmpty()) {
                        Text("${selected.size} выбрано", color = tc.on, fontWeight = FontWeight.SemiBold)
                    } else if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск в чате...", color = tc.muted) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on),
                            shape = RoundedCornerShape(12.dp), singleLine = true
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(url = chat.avatarUrl, name = chat.name, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                when { chat.isGroup -> Text("${chat.members.size} участников", color = tc.muted, fontSize = 11.sp); chat.isBot -> Text("Бот", color = tc.muted, fontSize = 11.sp) }
                            }
                        }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        // FIX #1: правильная проверка что все выбранные — свои
                        val canDelete = isAdmin || selected.all { id -> allMessages.firstOrNull { it.id == id }?.senderId == currentUserId }
                        // ФИЧА: кнопка "Переслать"
                        IconButton(onClick = {
                            selected.mapNotNull { id -> allMessages.firstOrNull { it.id == id } }.forEach { onForward(it) }
                            selected.clear()
                        }) { Icon(Icons.Default.Forward, null, tint = tc.on) }
                        if (canDelete) {
                            IconButton(onClick = { selected.toList().forEach { onDelete(it) }; selected.clear() }) {
                                Icon(Icons.Default.Delete, null, tint = VSRed)
                            }
                        }
                    } else {
                        // ФИЧА: поиск в чате
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                            Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, null, tint = tc.on)
                        }
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
                            Text("❄️", fontSize = 18.sp); Spacer(Modifier.width(8.dp))
                            Text("Аккаунт заморожен. Отправка недоступна.", color = VSFrozen, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Surface(color = tc.surf, tonalElevation = 4.dp) {
                    Column {
                        replyToMsg?.let { reply ->
                            Row(Modifier.fillMaxWidth().background(tc.card).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(32.dp).background(VSPrimary, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(reply.senderName, color = VSPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(reply.text.take(60), color = tc.muted, fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { replyToMsg = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, tint = tc.muted, modifier = Modifier.size(16.dp))
                                }
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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
                                    focusedTextColor = tc.on, unfocusedTextColor = tc.on,
                                    cursorColor = VSPrimary, focusedContainerColor = tc.card, unfocusedContainerColor = tc.card
                                ), maxLines = 5
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = { if (text.isNotBlank()) { onSendText(text.trim()); text = ""; replyToMsg = null } },
                                modifier = Modifier.size(46.dp), containerColor = VSPrimary, shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val pinnedMsg = if (chat.pinnedMessageId.isNotEmpty()) allMessages.firstOrNull { it.id == chat.pinnedMessageId } else null
        Column(Modifier.fillMaxSize().padding(padding)) {
            pinnedMsg?.let { pinned ->
                Row(Modifier.fillMaxWidth().background(tc.card).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, null, tint = VSYellow, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Закреплено", color = VSYellow, fontSize = 10.sp)
                        Text(pinned.text.take(60), color = tc.muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            // ФИЧА: счётчик результатов поиска
            if (searchQuery.isNotBlank()) {
                Box(Modifier.fillMaxWidth().background(VSPrimary.copy(0.15f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Найдено: ${displayedMessages.size}", color = VSPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayedMessages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isOwn = msg.senderId == currentUserId, // FIX #1
                        isSelected = msg.id in selected,
                        isAdmin = isAdmin,
                        currentUserId = currentUserId, // передаём для правильной проверки
                        users = users,
                        searchQuery = searchQuery,
                        onLongPress = { if (msg.id in selected) selected.remove(msg.id) else selected.add(msg.id) },
                        onReact = { emoji -> onReact(msg.id, emoji) },
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onReply = { replyToMsg = messages.firstOrNull { it.id == msg.id } },
                        onPin = { onPin(msg.id, msg.text) },
                        onDelete = { onDelete(msg.id) },
                        onForward = { onForward(msg) },
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
    currentUserId: String, // FIX #1
    users: List<UserModel>, searchQuery: String,
    onLongPress: () -> Unit, onReact: (String) -> Unit,
    onImageClick: (String) -> Unit, onReply: () -> Unit, onPin: () -> Unit,
    onDelete: () -> Unit, onForward: () -> Unit, onUserClick: (String) -> Unit
) {
    val tc = LocalTC.current
    var showReactions by remember { mutableStateOf(false) }
    // FIX #15: showMenu привязан к конкретному id, сбрасывается при реконпозиции
    var showMenu by remember(msg.id) { mutableStateOf(false) }

    if (msg.isDeleted) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start) {
            Text("🚫 Сообщение удалено", color = tc.muted, fontSize = 12.sp,
                modifier = Modifier.background(tc.card, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        return
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
        if (!isOwn && msg.senderId != "BOT") {
            Text(msg.senderName, color = VSSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (isOwn) 4.dp else 18.dp, bottomStart = if (isOwn) 18.dp else 4.dp))
                .background(when { isSelected -> VSPrimary.copy(0.3f); isOwn -> VSBubbleOwn; msg.chatId.startsWith("bot_") -> VSBubbleBot; else -> VSBubbleOther })
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress(); showMenu = true },
                        onDoubleTap = { showReactions = true }
                    )
                }
                .padding(10.dp)
        ) {
            Column {
                if (msg.replyToId.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(6.dp)) {
                        Box(Modifier.width(2.dp).height(30.dp).background(VSPrimaryLite, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(msg.replyToText.take(60), color = Color.White.copy(0.8f), fontSize = 12.sp, maxLines = 2)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Картинка
                val imageData = msg.imageBase64.ifEmpty { msg.imageUrl }
                if (imageData.isNotEmpty()) {
                    var scale by remember { mutableStateOf(1f) }
                    if (imageData.startsWith("avatar://")) {
                        Base64ChatImage(dataUri = imageData, scale = scale, onZoom = { z -> scale = (scale * z).coerceIn(1f, 4f) }, onClick = {})
                    } else {
                        AsyncImage(
                            model = imageData, contentDescription = null,
                            modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> scale = (scale * z).coerceIn(1f, 4f) } }
                                .combinedClickable(onClick = { onImageClick(imageData) }),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (msg.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
                }
                // Текст с подсветкой поиска и vs:/// ссылками
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
                        if (last < msg.text.length) {
                            // ФИЧА: подсветка найденного текста
                            val remaining = msg.text.substring(last)
                            if (searchQuery.isNotBlank()) {
                                val idx = remaining.indexOf(searchQuery, ignoreCase = true)
                                if (idx >= 0) {
                                    withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(remaining.substring(0, idx)) }
                                    withStyle(SpanStyle(color = Color.Black, background = VSYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)) { append(remaining.substring(idx, idx + searchQuery.length)) }
                                    withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(remaining.substring(idx + searchQuery.length)) }
                                } else withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(remaining) }
                            } else withStyle(SpanStyle(color = Color.White, fontSize = 15.sp)) { append(remaining) }
                        }
                    }
                    ClickableText(text = annotated, onClick = { offset ->
                        annotated.getStringAnnotations("USER", offset, offset).firstOrNull()?.let { onUserClick(it.item) }
                    })
                }
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
        val reactions = msg.reactions.filter { it.value.isNotEmpty() }
        if (reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.forEach { (emoji, uids) ->
                    Surface(shape = RoundedCornerShape(50), color = tc.card, modifier = Modifier.clickable { onReact(emoji) }) {
                        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji, fontSize = 13.sp); Spacer(Modifier.width(3.dp)); Text("${uids.size}", color = tc.on, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }

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

    // FIX #1 + #15: правильные проверки владельца, menu сбрасывается при потере фокуса
    if (showMenu) {
        val canDeleteThis = isAdmin || msg.senderId == currentUserId // FIX #1
        DropdownMenu(expanded = true, onDismissRequest = { showMenu = false }, modifier = Modifier.background(tc.card)) {
            DropdownMenuItem(text = { Text("↩️ Ответить", color = tc.on) }, onClick = { onReply(); showMenu = false })
            DropdownMenuItem(text = { Text("😀 Реакция", color = tc.on) }, onClick = { showReactions = true; showMenu = false })
            DropdownMenuItem(text = { Text("↪️ Переслать", color = tc.on) }, onClick = { onForward(); showMenu = false })
            if (isAdmin) {
                DropdownMenuItem(text = { Text("📌 Закрепить", color = tc.on) }, onClick = { onPin(); showMenu = false })
            }
            if (canDeleteThis) {
                DropdownMenuItem(text = { Text("🗑️ Удалить", color = VSRed) }, onClick = { onDelete(); showMenu = false })
            }
        }
    }
}

@Composable
private fun Base64ChatImage(dataUri: String, scale: Float, onZoom: (Float) -> Unit, onClick: () -> Unit) {
    val bitmap = remember(dataUri) {
        try {
            val parts = dataUri.removePrefix("avatar://").split("/", limit = 2)
            if (parts.size == 2) {
                val bytes = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } else null
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap, contentDescription = null,
            modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp)
                .clip(RoundedCornerShape(10.dp))
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> onZoom(z) } }
                .clickable { onClick() },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun FullscreenImageViewer(url: String, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTransformGestures { _, pan, z, _ -> scale = (scale * z).coerceIn(0.5f, 5f); offsetX += pan.x; offsetY += pan.y } }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f },
                    onTap = { if (scale == 1f) onClose() }
                )
            }
    ) {
        AsyncImage(model = url, contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
            contentScale = ContentScale.Fit)
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
