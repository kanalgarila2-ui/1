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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.veryschool.client.data.prefs.*
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
    onSendPoll: ((question: String, options: List<String>) -> Unit)? = null,
    onSendWithExpiry: ((text: String, expirySec: Int) -> Unit)? = null,
    onReact: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, String) -> Unit,
    onUserClick: (String) -> Unit,
    onForward: (MessageUiModel) -> Unit = {},
    onSaveDraft: (String) -> Unit = {},
    draft: String = "",
    bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
    fontSize: FontSize = FontSize.MEDIUM,
    chatBg: ChatBg = ChatBg.NONE,
    timeFormat: TimeFormat = TimeFormat.H24
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
    // ФИЧА: самоудаляющиеся сообщения
    var selfDestructSec by remember { mutableStateOf<Int?>(null) } // null = выкл, 30/60/300

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
                            // Таймер самоудаления
                            var showTimerMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showTimerMenu = true }) {
                                    Icon(Icons.Default.Timer, null, tint = if (selfDestructSec != null) VSPrimary else tc.muted, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = showTimerMenu, onDismissRequest = { showTimerMenu = false }, containerColor = tc.card) {
                                    listOf(null to "Выкл", 30 to "30 сек", 60 to "1 мин", 300 to "5 мин").forEach { (sec, label) ->
                                        DropdownMenuItem(text = { Text(label, color = if (selfDestructSec == sec) VSPrimary else tc.on) }, onClick = { selfDestructSec = sec; showTimerMenu = false })
                                    }
                                }
                            }
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
                                onClick = {
                                    if (text.isNotBlank()) {
                                        val trimmed = text.trim()
                                        when {
                                            trimmed.startsWith("/poll ") && onSendPoll != null -> {
                                                val parts = trimmed.removePrefix("/poll ").split("|").map { it.trim() }.filter { it.isNotEmpty() }
                                                if (parts.size >= 2) { onSendPoll(parts[0], parts.drop(1)); text = "" }
                                                else onSendText(trimmed)
                                            }
                                            selfDestructSec != null && onSendWithExpiry != null -> {
                                                onSendWithExpiry(trimmed, selfDestructSec!!); text = ""; replyToMsg = null
                                            }
                                            else -> { onSendText(trimmed); text = ""; replyToMsg = null }
                                        }
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
            val bgModifier = when (chatBg) {
                ChatBg.DOTS -> Modifier.fillMaxSize().drawBehind {
                    val step = 28.dp.toPx(); val dotR = 2.dp.toPx()
                    var y = step / 2
                    while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.18f), dotR, androidx.compose.ui.geometry.Offset(x, y)); x += step }; y += step }
                }
                ChatBg.GRID -> Modifier.fillMaxSize().drawBehind {
                    val step = 40.dp.toPx(); val c = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.12f)
                    var x = 0f; while (x <= size.width) { drawLine(c, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f); x += step }
                    var y = 0f; while (y <= size.height) { drawLine(c, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f); y += step }
                }
                ChatBg.STARS -> Modifier.fillMaxSize().drawBehind {
                    val stars = listOf(0.1f to 0.05f, 0.3f to 0.15f, 0.6f to 0.08f, 0.8f to 0.2f, 0.15f to 0.4f, 0.5f to 0.35f, 0.9f to 0.5f, 0.25f to 0.7f, 0.7f to 0.65f, 0.45f to 0.85f, 0.85f to 0.9f, 0.05f to 0.95f)
                    val c = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f)
                    stars.forEach { (rx, ry) -> drawCircle(c, 2.5f, androidx.compose.ui.geometry.Offset(rx * size.width, ry * size.height)) }
                }
                ChatBg.GRADIENT -> Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1E1035), androidx.compose.ui.graphics.Color(0xFF0D0A20))))
                ChatBg.WAVES -> Modifier.fillMaxSize().drawBehind {
                    val path = androidx.compose.ui.graphics.Path(); val w = size.width; val h = size.height; val c = androidx.compose.ui.graphics.Color(0xFF8B5CF6).copy(alpha = 0.08f)
                    listOf(0.3f, 0.55f, 0.8f).forEach { fy ->
                        path.reset(); path.moveTo(0f, fy * h)
                        path.cubicTo(w * 0.25f, fy * h - 30f, w * 0.5f, fy * h + 30f, w, fy * h)
                        path.lineTo(w, h); path.lineTo(0f, h); path.close()
                        drawPath(path, c)
                    }
                }
                else -> Modifier.fillMaxSize()
            }
            LazyColumn(
                state = listState, modifier = bgModifier,
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
                        bubbleStyle = bubbleStyle,
                        fontSize = fontSize,
                        timeFormat = timeFormat,
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
    bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
    fontSize: FontSize = FontSize.MEDIUM,
    timeFormat: TimeFormat = TimeFormat.H24,
    onLongPress: () -> Unit, onReact: (String) -> Unit,
    onImageClick: (String) -> Unit, onReply: () -> Unit, onPin: () -> Unit,
    onDelete: () -> Unit, onForward: () -> Unit, onUserClick: (String) -> Unit
) {
    val tc = LocalTC.current
    var showReactions by remember { mutableStateOf(false) }
    // FIX #15: showMenu привязан к конкретному id, сбрасывается при реконпозиции
    var showMenu by remember(msg.id) { mutableStateOf(false) }

    val textSizeSp = when (fontSize) { FontSize.SMALL -> 13.sp; FontSize.LARGE -> 17.sp; else -> 15.sp }
    val timeFmt = if (timeFormat == TimeFormat.H12) "hh:mm a" else "HH:mm"
    val bubbleShape = when (bubbleStyle) {
        BubbleStyle.SHARP -> RoundedCornerShape(4.dp)
        BubbleStyle.RECT  -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (isOwn) 4.dp else 18.dp, bottomStart = if (isOwn) 18.dp else 4.dp)
    }

    if (msg.isDeleted) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start) {
            Text("🚫 Сообщение удалено", color = tc.muted, fontSize = 12.sp,
                modifier = Modifier.background(tc.card, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        return
    }

    // ФИЧА: мини-профиль по тапу на аватарку
    var showMiniProfile by remember { mutableStateOf(false) }
    val sender = users.firstOrNull { it.id == msg.senderId }

    if (showMiniProfile && sender != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showMiniProfile = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = tc.surf, tonalElevation = 8.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarImage(url = sender.avatarUrl, name = sender.displayName, size = 64.dp)
                    Text(sender.displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (sender.username.isNotEmpty()) Text("@${sender.username}", color = tc.muted, fontSize = 13.sp)
                    if (sender.statusDisplay().isNotEmpty()) Text(sender.statusDisplay(), color = tc.muted, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val onlineColor = if (sender.online) Color(0xFF22C55E) else tc.muted
                        Text(if (sender.online) "● онлайн" else "● оффлайн", color = onlineColor, fontSize = 12.sp)
                    }
                    if (sender.id != msg.senderId || true) {
                        Button(onClick = { showMiniProfile = false; onUserClick(sender.id) },
                            shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = VSPrimary),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Написать", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
        if (!isOwn && msg.senderId != "BOT") {
            Row(verticalAlignment = Alignment.Bottom) {
                AvatarImage(url = msg.senderAvatarUrl, name = msg.senderName, size = 28.dp,
                    modifier = Modifier.clickable { showMiniProfile = true })
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(msg.senderName, color = VSSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp))
                    BubbleBox(msg, isOwn, isSelected, bubbleShape, textSizeSp, timeFmt, searchQuery, users, onReact, onImageClick, onUserClick)
                }
            }
        } else {
            BubbleBox(msg, isOwn, isSelected, bubbleShape, textSizeSp, timeFmt, searchQuery, users, onReact, onImageClick, onUserClick)
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
private fun BubbleBox(
    msg: MessageUiModel, isOwn: Boolean, isSelected: Boolean,
    bubbleShape: androidx.compose.ui.graphics.Shape,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    timeFmt: String, searchQuery: String,
    users: List<UserModel>,
    onReact: (String) -> Unit,
    onImageClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val tc = LocalTC.current
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(bubbleShape)
            .background(when { isSelected -> VSPrimary.copy(0.3f); isOwn -> VSBubbleOwn; msg.chatId.startsWith("bot_") -> VSBubbleBot; else -> VSBubbleOther })
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
            val imageData = msg.imageBase64.ifEmpty { msg.imageUrl }
            if (imageData.isNotEmpty()) {
                var scale by remember { mutableStateOf(1f) }
                if (imageData.startsWith("avatar://")) {
                    Base64ChatImage(dataUri = imageData, scale = scale, onZoom = { z -> scale = (scale * z).coerceIn(1f, 4f) }, onClick = { onImageClick(imageData) })
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
            if (msg.text.isNotEmpty() && !msg.isPoll) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val annotated = buildAnnotatedString {
                    var last = 0
                    // HTTP/HTTPS ссылки
                    val httpRe = Regex("""https?://\S+""")
                    val vsRe = VS_LINK_RE
                    data class LinkSpan(val range: IntRange, val tag: String, val item: String, val display: String)
                    val spans = mutableListOf<LinkSpan>()
                    vsRe.findAll(msg.text).forEach { spans.add(LinkSpan(it.range, "USER", it.groupValues[1], "@${users.firstOrNull { u -> u.id == it.groupValues[1] }?.displayName ?: it.groupValues[1]}")) }
                    httpRe.findAll(msg.text).forEach { spans.add(LinkSpan(it.range, "URL", it.value, it.value)) }
                    spans.sortBy { it.range.first }
                    spans.forEach { span ->
                        if (span.range.first > last) withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(msg.text.substring(last, span.range.first)) }
                        pushStringAnnotation(span.tag, span.item)
                        withStyle(SpanStyle(color = Color(0xFF93C5FD), fontSize = textSizeSp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) { append(span.display) }
                        pop()
                        last = span.range.last + 1
                    }
                    if (last < msg.text.length) {
                        val remaining = msg.text.substring(last)
                        if (searchQuery.isNotBlank()) {
                            val idx = remaining.indexOf(searchQuery, ignoreCase = true)
                            if (idx >= 0) {
                                withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(remaining.substring(0, idx)) }
                                withStyle(SpanStyle(color = Color.Black, background = VSYellow, fontSize = textSizeSp, fontWeight = FontWeight.Bold)) { append(remaining.substring(idx, idx + searchQuery.length)) }
                                withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(remaining.substring(idx + searchQuery.length)) }
                            } else withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(remaining) }
                        } else withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(remaining) }
                    }
                }
                ClickableText(text = annotated, onClick = { offset ->
                    annotated.getStringAnnotations("USER", offset, offset).firstOrNull()?.let { onUserClick(it.item) }
                        ?: annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ann.item))
                            context.startActivity(intent)
                        }
                        ?: run { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.text)) }
                })
            }
            if (msg.isPoll && msg.pollQuestion.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("📊 ${msg.pollQuestion}", color = Color.White, fontSize = textSizeSp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val totalVotes = msg.pollVotes.size
                msg.pollOptions.forEachIndexed { idx, option ->
                    val voteCount = msg.pollVotes.values.count { it == idx.toString() }
                    val fraction = if (totalVotes > 0) voteCount.toFloat() / totalVotes else 0f
                    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.15f)).clickable { onReact("poll:$idx") }) {
                        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(32.dp).background(VSPrimary.copy(0.5f)).clip(RoundedCornerShape(8.dp)))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option, color = Color.White, fontSize = 13.sp)
                            Text("$voteCount", color = Color.White.copy(0.8f), fontSize = 12.sp)
                        }
                    }
                }
                if (totalVotes > 0) Text("Всего голосов: $totalVotes", color = Color.White.copy(0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (msg.expiresAt != null) {
                var remaining by remember(msg.id) { mutableStateOf(((msg.expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)) }
                LaunchedEffect(msg.id) {
                    while (remaining > 0) { kotlinx.coroutines.delay(1000); remaining = ((msg.expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0) }
                }
                Text("⏱ ${remaining}с", color = Color.White.copy(0.7f), fontSize = 10.sp)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (msg.isPending) Text("⏳", fontSize = 9.sp)
                else if (isOwn) {
                    val readCount = msg.readBy.filter { it != msg.senderId }.size
                    Text(if (readCount > 0) "✓✓" else "✓", color = Color.White.copy(0.7f), fontSize = 10.sp)
                }
                Spacer(Modifier.width(4.dp))
                Text(SimpleDateFormat(timeFmt, Locale.getDefault()).format(Date(msg.timestamp)), color = Color.White.copy(0.6f), fontSize = 10.sp)
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
