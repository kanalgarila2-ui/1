package com.veryschool.client.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import com.veryschool.client.data.models.*
import com.veryschool.client.data.prefs.*
import com.veryschool.client.ui.components.AvatarImage
import com.veryschool.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private val VS_LINK_RE = Regex("""vs:///(?:id=(\d+)|u=([a-z0-9_.]+))""")

// ФИЧА #23: метка даты для sticky headers
private fun dateLabel(ts: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Сегодня"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Вчера"
        else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(ts))
    }
}

private fun groupMessagesByDate(messages: List<MessageUiModel>): List<Any> {
    val result = mutableListOf<Any>()
    var lastLabel = ""
    messages.forEach { msg ->
        val label = dateLabel(msg.timestamp)
        if (label != lastLabel) { result.add(label); lastLabel = label }
        result.add(msg)
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chat: ChatModel,
    messages: List<MessageModel>,
    optimisticMessages: List<MessageUiModel> = emptyList(),
    currentUserId: String,
    currentUserFrozen: Boolean = false,
    users: List<UserModel> = emptyList(),
    isAdmin: Boolean = false,
    typingUsers: List<String> = emptyList(),
    bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
    fontSize: FontSize = FontSize.MEDIUM,
    chatBg: ChatBg = ChatBg.NONE,
    timeFormat: TimeFormat = TimeFormat.H24,
    onBack: () -> Unit,
    onSendText: (text: String, replyToId: String, replyToText: String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendPoll: ((question: String, options: List<String>) -> Unit)? = null,
    onSendWithExpiry: ((text: String, expirySec: Int) -> Unit)? = null,
    onReact: (msgId: String, emoji: String) -> Unit,
    onDelete: (msgId: String) -> Unit,
    onPin: (msgId: String, text: String) -> Unit,
    onForward: (MessageUiModel) -> Unit,
    onEdit: ((msgId: String, newText: String) -> Unit)? = null,   // ФИЧА #1
    onStar: ((MessageUiModel) -> Unit)? = null,                    // ФИЧА #3
    onReadBy: ((msgId: String) -> Unit)? = null,                   // ФИЧА #15
    onUserClick: (userId: String) -> Unit,
    onOpenGroupInfo: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,                             // ФИЧА #4
    onTyping: (() -> Unit)? = null,
    onSaveDraft: (String) -> Unit = {},
    draft: String = "",
    blockedUserIds: Set<String> = emptySet()                       // ФИЧА #8
) {
    val tc = LocalTC.current
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onSendImage) }

    var replyToMsg by remember { mutableStateOf<MessageModel?>(null) }
    var editingMsg by remember { mutableStateOf<MessageUiModel?>(null) }  // ФИЧА #1
    var text by remember(draft) { mutableStateOf(draft) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selfDestructSec by remember { mutableStateOf<Int?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }

    val realMessages = messages.map { it.toUi() }
    val allMessages = (realMessages + optimisticMessages)
        .distinctBy { it.id }
        .filter { it.senderId !in blockedUserIds }  // ФИЧА #8
        .sortedBy { it.timestamp }
    val displayedMessages = if (searchQuery.isBlank()) allMessages
        else allMessages.filter { it.text.contains(searchQuery, ignoreCase = true) }

    // ФИЧА #23: группировка по дате для sticky headers
    val groupedItems = remember(displayedMessages) { groupMessagesByDate(displayedMessages) }

    val msgCount = groupedItems.size
    LaunchedEffect(msgCount) {
        if (allMessages.isNotEmpty() && searchQuery.isBlank()) listState.animateScrollToItem(msgCount - 1)
    }
    LaunchedEffect(text) { onSaveDraft(text) }

    // ФИЧА #21: показывать кнопку "вниз" когда прокрутили вверх
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < info.totalItemsCount - 3
        }
    }

    if (fullscreenImageUrl != null) {
        FullscreenImageViewer(url = fullscreenImageUrl!!, onClose = { fullscreenImageUrl = null })
        return
    }

    Scaffold(
        containerColor = tc.bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { selected.clear(); onBack() }) { Icon(Icons.Default.ArrowBack, null, tint = tc.on) }
                },
                title = {
                    if (showSearch) {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск в чате...", color = tc.muted) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border, focusedTextColor = tc.on, unfocusedTextColor = tc.on),
                            shape = RoundedCornerShape(12.dp), singleLine = true)
                    } else {
                        Row(Modifier.clickable { if (chat.isGroup) onOpenGroupInfo?.invoke() },
                            verticalAlignment = Alignment.CenterVertically) {
                            val chatUser = if (chat.isDm) users.firstOrNull { it.id != currentUserId && it.id in chat.members } else null
                            AvatarImage(url = chat.avatarUrl, name = chat.name, size = 36.dp, showOnline = chat.isDm, isOnline = chatUser?.online == true)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(chat.name, color = tc.on, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    // ФИЧА #5: онлайн счётчик в группе
                                    if (chat.isGroup) {
                                        val onlineCount = users.count { it.id in chat.members && it.online }
                                        if (onlineCount > 0) Text("·$onlineCount онлайн", color = VSGreen, fontSize = 10.sp)
                                    }
                                }
                                if (typingUsers.isNotEmpty()) {
                                    Text("${typingUsers.joinToString(", ")} печатает...", color = VSPrimary, fontSize = 11.sp)
                                } else if (chat.isDm && chatUser != null) {
                                    // ФИЧА #10: умный lastSeen
                                    Text(chatUser.lastSeenText(), color = if (chatUser.online) VSGreen else tc.muted, fontSize = 11.sp)
                                } else if (chat.isGroup) {
                                    Text("${chat.members.size} участников", color = tc.muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, null, tint = tc.on) }
                        IconButton(onClick = { displayedMessages.firstOrNull { it.id == selected.firstOrNull() }?.let { onForward(it) }; selected.clear() }) {
                            Icon(Icons.Default.Forward, null, tint = tc.on)
                        }
                        if (isAdmin) {
                            IconButton(onClick = { selected.forEach { onDelete(it) }; selected.clear() }) {
                                Icon(Icons.Default.Delete, null, tint = VSRed)
                            }
                        }
                    } else {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                            Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, null, tint = tc.on)
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = tc.on) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (chat.isGroup) DropdownMenuItem(text = { Text("👥 Участники", color = tc.on) }, onClick = { showMenu = false; onOpenGroupInfo?.invoke() })
                                DropdownMenuItem(text = { Text("🖼 Медиафайлы", color = tc.on) }, onClick = { showMenu = false; onOpenMedia?.invoke() })
                                if (chat.pinnedMessageId.isNotEmpty()) DropdownMenuItem(text = { Text("📌 Закреплённое", color = tc.on) }, onClick = { showMenu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tc.surf)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(tc.surf)) {
                // Reply / Edit preview
                val previewMsg = editingMsg?.text ?: replyToMsg?.text
                val previewLabel = if (editingMsg != null) "✏️ Редактирование" else if (replyToMsg != null) "↩️ ${replyToMsg!!.senderName}" else null
                AnimatedVisibility(visible = previewLabel != null) {
                    Row(Modifier.fillMaxWidth().background(tc.card).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(36.dp).background(VSPrimary, RoundedCornerShape(1.5.dp)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            if (previewLabel != null) Text(previewLabel, color = VSPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(previewMsg?.take(60) ?: "", color = tc.muted, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { replyToMsg = null; editingMsg = null; text = "" }) {
                            Icon(Icons.Default.Close, null, tint = tc.muted)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !currentUserFrozen) {
                        Icon(Icons.Default.AttachFile, null, tint = tc.muted)
                    }
                    var showTimerMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showTimerMenu = true }, enabled = !currentUserFrozen) {
                            Icon(Icons.Default.Timer, null, tint = if (selfDestructSec != null) VSPrimary else tc.muted, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showTimerMenu, onDismissRequest = { showTimerMenu = false }) {
                            listOf(null to "Выкл", 30 to "30 сек", 60 to "1 мин", 300 to "5 мин").forEach { (sec, label) ->
                                DropdownMenuItem(text = { Text(label, color = if (selfDestructSec == sec) VSPrimary else tc.on) },
                                    onClick = { selfDestructSec = sec; showTimerMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; if (it.isNotEmpty()) onTyping?.invoke() },
                        placeholder = { Text(if (currentUserFrozen) "❄️ Аккаунт заморожен" else "Сообщение...", color = tc.muted) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VSPrimary, unfocusedBorderColor = tc.border,
                            focusedTextColor = tc.on, unfocusedTextColor = tc.on,
                            cursorColor = VSPrimary, focusedContainerColor = tc.card, unfocusedContainerColor = tc.card
                        ), maxLines = 5, enabled = !currentUserFrozen
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (text.isNotBlank() && !currentUserFrozen) {
                                val trimmed = text.trim()
                                // ФИЧА #1: режим редактирования
                                if (editingMsg != null) {
                                    onEdit?.invoke(editingMsg!!.id, trimmed)
                                    editingMsg = null; text = ""; return@FloatingActionButton
                                }
                                val rId = replyToMsg?.id ?: ""; val rText = replyToMsg?.text?.take(60) ?: ""
                                when {
                                    trimmed.startsWith("/poll ") && onSendPoll != null -> {
                                        val parts = trimmed.removePrefix("/poll ").split("|").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (parts.size >= 2) { onSendPoll(parts[0], parts.drop(1)); text = "" }
                                        else onSendText(trimmed, rId, rText)
                                    }
                                    selfDestructSec != null && onSendWithExpiry != null -> { onSendWithExpiry(trimmed, selfDestructSec!!); text = ""; replyToMsg = null }
                                    else -> { onSendText(trimmed, rId, rText); text = ""; replyToMsg = null }
                                }
                            }
                        },
                        modifier = Modifier.size(46.dp),
                        containerColor = if (currentUserFrozen) tc.muted else VSPrimary,
                        shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val bgMod = when (chatBg) {
                ChatBg.DOTS -> Modifier.fillMaxSize().drawBehind { drawDots() }
                ChatBg.GRID -> Modifier.fillMaxSize().drawBehind { drawGrid() }
                ChatBg.STARS -> Modifier.fillMaxSize().drawBehind { drawStars() }
                ChatBg.GRADIENT -> Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1035), Color(0xFF0D0A20))))
                ChatBg.WAVES -> Modifier.fillMaxSize().drawBehind { drawWaves() }
                else -> Modifier.fillMaxSize()
            }
            LazyColumn(state = listState, modifier = bgMod, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(groupedItems, key = { if (it is String) "hdr_$it" else (it as MessageUiModel).id }) { item ->
                    when (item) {
                        // ФИЧА #23: sticky date header
                        is String -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(shape = RoundedCornerShape(12.dp), color = tc.card.copy(0.85f)) {
                                    Text(item, color = tc.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }
                        is MessageUiModel -> {
                            SwipeableMessageItem(onSwipeRight = { replyToMsg = messages.firstOrNull { it.id == item.id } }) {
                                // ФИЧА #22: анимация появления нового пузыря
                                val isNew = item.isPending || (System.currentTimeMillis() - item.timestamp < 2000)
                                AnimatedVisibility(
                                    visible = true,
                                    enter = if (isNew && item.senderId == currentUserId) slideInHorizontally { it } + fadeIn()
                                            else fadeIn(tween(150))
                                ) {
                                    MessageBubble(
                                        msg = item,
                                        isOwn = item.senderId == currentUserId,
                                        isSelected = item.id in selected,
                                        isAdmin = isAdmin, currentUserId = currentUserId,
                                        users = users, searchQuery = searchQuery,
                                        bubbleStyle = bubbleStyle, fontSize = fontSize, timeFormat = timeFormat,
                                        onLongPress = { if (item.id in selected) selected.remove(item.id) else selected.add(item.id) },
                                        onReact = { emoji -> onReact(item.id, emoji) },
                                        onImageClick = { url -> fullscreenImageUrl = url },
                                        onReply = { replyToMsg = messages.firstOrNull { it.id == item.id } },
                                        onPin = { onPin(item.id, item.text) },
                                        onDelete = { onDelete(item.id) },
                                        onForward = { onForward(item) },
                                        onEdit = { editingMsg = item; text = item.text },  // ФИЧА #1
                                        onStar = { onStar?.invoke(item) },                 // ФИЧА #3
                                        onReadBy = { onReadBy?.invoke(item.id) },          // ФИЧА #15
                                        onUserClick = onUserClick
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ФИЧА #21: scroll-to-bottom кнопка
            AnimatedVisibility(visible = showScrollToBottom, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
                enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                val unreadCount = allMessages.count { currentUserId !in it.readBy && it.senderId != currentUserId }
                FloatingActionButton(
                    onClick = { /* scroll to bottom */ },
                    modifier = Modifier.size(42.dp), containerColor = tc.surf, shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = VSPrimary)
                        if (unreadCount > 0) {
                            Surface(shape = CircleShape, color = VSPrimary, modifier = Modifier.size(16.dp).offset(4.dp, (-4).dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("$unreadCount", color = Color.White, fontSize = 9.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Swipe to reply ────────────────────────────────────────────────────────────
// (ФИЧА #12 из прошлой серии)
@Composable
private fun SwipeableMessageItem(onSwipeRight: () -> Unit, content: @Composable () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animOffset by animateFloatAsState(targetValue = offsetX, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    var triggered by remember { mutableStateOf(false) }
    Box(Modifier.offset { IntOffset(animOffset.roundToInt(), 0) }.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { if (offsetX > 80f && !triggered) { triggered = true; onSwipeRight() }; triggered = false; offsetX = 0f },
            onHorizontalDrag = { _, delta -> if (delta > 0) offsetX = (offsetX + delta).coerceIn(0f, 100f) }
        )
    }) {
        content()
        if (offsetX > 20f) Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            Icon(Icons.Default.Reply, null, tint = VSPrimary.copy(alpha = (offsetX / 80f).coerceIn(0f, 1f)), modifier = Modifier.size(22.dp))
        }
    }
}

// ── MessageBubble ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MessageUiModel, isOwn: Boolean, isSelected: Boolean, isAdmin: Boolean,
    currentUserId: String, users: List<UserModel>, searchQuery: String,
    bubbleStyle: BubbleStyle, fontSize: FontSize, timeFormat: TimeFormat,
    onLongPress: () -> Unit, onReact: (String) -> Unit,
    onImageClick: (String) -> Unit, onReply: () -> Unit, onPin: () -> Unit,
    onDelete: () -> Unit, onForward: () -> Unit,
    onEdit: () -> Unit = {},     // ФИЧА #1
    onStar: () -> Unit = {},     // ФИЧА #3
    onReadBy: () -> Unit = {},   // ФИЧА #15
    onUserClick: (String) -> Unit
) {
    val tc = LocalTC.current
    var showReactions by remember { mutableStateOf(false) }
    var showMenu by remember(msg.id) { mutableStateOf(false) }
    var showMiniProfile by remember { mutableStateOf(false) }
    var showReadBy by remember { mutableStateOf(false) }  // ФИЧА #15
    val sender = users.firstOrNull { it.id == msg.senderId }

    val textSizeSp = when (fontSize) { FontSize.SMALL -> 13.sp; FontSize.LARGE -> 17.sp; else -> 15.sp }
    val timeFmt = if (timeFormat == TimeFormat.H12) "hh:mm a" else "HH:mm"
    val bubbleShape: Shape = when (bubbleStyle) {
        BubbleStyle.SHARP -> RoundedCornerShape(4.dp)
        BubbleStyle.RECT  -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
            bottomEnd = if (isOwn) 4.dp else 18.dp, bottomStart = if (isOwn) 18.dp else 4.dp)
    }

    if (msg.isDeleted) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start) {
            Text("🚫 Сообщение удалено", color = tc.muted, fontSize = 12.sp,
                modifier = Modifier.background(tc.card, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        return
    }

    // Мини-профиль
    if (showMiniProfile && sender != null) {
        Dialog(onDismissRequest = { showMiniProfile = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = tc.surf, tonalElevation = 8.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarImage(url = sender.avatarUrl, name = sender.displayName, size = 64.dp, showOnline = true, isOnline = sender.online)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sender.displayName, color = tc.on, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (sender.isVerified) Text("✓", color = VSPrimary, fontWeight = FontWeight.Bold)
                    }
                    if (sender.username.isNotEmpty()) Text("@${sender.username}", color = tc.muted, fontSize = 13.sp)
                    if (sender.statusDisplay().isNotEmpty()) Text(sender.statusDisplay(), color = tc.muted)
                    // ФИЧА #10: умный lastSeen
                    Text(sender.lastSeenText(), color = if (sender.online) VSGreen else tc.muted, fontSize = 12.sp)
                    Button(onClick = { showMiniProfile = false; onUserClick(sender.id) },
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = VSPrimary),
                        modifier = Modifier.fillMaxWidth()) { Text("Написать", color = Color.White) }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
        if (!isOwn && msg.senderId != "BOT") {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.clickable { showMiniProfile = true }) {
                    AvatarImage(url = msg.senderAvatarUrl, name = msg.senderName, size = 28.dp)
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg.senderName, color = VSSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (sender?.isVerified == true) Text("✓", color = VSPrimary, fontSize = 10.sp)
                    }
                    BubbleBox(msg, isOwn, isSelected, bubbleShape, textSizeSp, timeFmt, searchQuery, users,
                        onLongPress = { onLongPress(); showMenu = true }, onDoubleTap = { showReactions = true },
                        onReact = onReact, onImageClick = onImageClick, onUserClick = onUserClick)
                }
            }
        } else {
            BubbleBox(msg, isOwn, isSelected, bubbleShape, textSizeSp, timeFmt, searchQuery, users,
                onLongPress = { onLongPress(); showMenu = true }, onDoubleTap = { showReactions = true },
                onReact = onReact, onImageClick = onImageClick, onUserClick = onUserClick)
        }

        // Строка под пузырём: реакции + звёздочка
        Row(Modifier.padding(top = 2.dp, start = if (isOwn) 0.dp else 40.dp), verticalAlignment = Alignment.CenterVertically) {
            val reactions = msg.reactions.filter { it.value.isNotEmpty() }
            reactions.forEach { (emoji, uids) ->
                Surface(shape = RoundedCornerShape(50), color = tc.card, modifier = Modifier.padding(end = 4.dp).clickable { onReact(emoji) }) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 13.sp); Spacer(Modifier.width(3.dp)); Text("${uids.size}", color = tc.on, fontSize = 11.sp)
                    }
                }
            }
            // ФИЧА #3: звёздочка
            if (currentUserId in msg.starredBy) {
                Text("⭐", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
            // ФИЧА #15: "прочитано X" для своих сообщений
            if (isOwn && msg.readBy.size > 1) {
                Spacer(Modifier.width(4.dp))
                Text("✓✓ ${msg.readBy.size - 1}", color = VSGreen, fontSize = 10.sp,
                    modifier = Modifier.clickable { showReadBy = true; onReadBy() })
            }
        }
        Spacer(Modifier.height(2.dp))
    }

    // Контекстное меню
    if (showMenu) {
        Popup(onDismissRequest = { showMenu = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = tc.surf, shadowElevation = 12.dp) {
                Column(Modifier.padding(8.dp).width(200.dp)) {
                    MenuRow(Icons.Default.Reply, "Ответить", tc.on) { onReply(); showMenu = false }
                    MenuRow(Icons.Default.EmojiEmotions, "Реакция", tc.on) { showReactions = true; showMenu = false }
                    MenuRow(Icons.Default.Forward, "Переслать", tc.on) { onForward(); showMenu = false }
                    MenuRow(Icons.Default.PushPin, "Закрепить", tc.on) { onPin(); showMenu = false }
                    MenuRow(if (currentUserId in msg.starredBy) Icons.Default.StarBorder else Icons.Default.Star,
                        if (currentUserId in msg.starredBy) "Убрать из избранного" else "В избранное", VSYellow) { onStar(); showMenu = false }
                    // ФИЧА #1: редактировать (только свои)
                    if (msg.senderId == currentUserId) MenuRow(Icons.Default.Edit, "Редактировать", tc.on) { onEdit(); showMenu = false }
                    if (msg.senderId == currentUserId || isAdmin) {
                        HorizontalDivider(color = tc.border)
                        MenuRow(Icons.Default.Delete, "Удалить", VSRed) { onDelete(); showMenu = false }
                    }
                }
            }
        }
    }

    if (showReactions) {
        Popup(onDismissRequest = { showReactions = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = tc.surf, shadowElevation = 8.dp) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("👍","❤️","😂","😮","😢","🔥","👏","🎉").forEach { emoji ->
                        Text(emoji, fontSize = 26.sp, modifier = Modifier.clickable { onReact(emoji); showReactions = false }.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val tc = LocalTC.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, color = color, fontSize = 14.sp)
    }
}

// ── BubbleBox ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleBox(
    msg: MessageUiModel, isOwn: Boolean, isSelected: Boolean, bubbleShape: Shape,
    textSizeSp: TextUnit, timeFmt: String, searchQuery: String, users: List<UserModel>,
    onLongPress: () -> Unit, onDoubleTap: () -> Unit,
    onReact: (String) -> Unit, onImageClick: (String) -> Unit = {}, onUserClick: (String) -> Unit = {}
) {
    val tc = LocalTC.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(
        Modifier.widthIn(max = 280.dp).clip(bubbleShape)
            .background(when { isSelected -> VSPrimary.copy(0.3f); isOwn -> VSBubbleOwn; msg.chatId.startsWith("bot_") -> VSBubbleBot; else -> VSBubbleOther })
            .combinedClickable(onLongClick = onLongPress, onDoubleClick = onDoubleTap, onClick = {})
            .padding(10.dp)
    ) {
        Column {
            // Reply
            if (msg.replyToId.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(6.dp)) {
                    Box(Modifier.width(2.dp).height(30.dp).background(VSPrimaryLite, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(msg.replyToText.take(60), color = Color.White.copy(0.8f), fontSize = 12.sp, maxLines = 2)
                }
                Spacer(Modifier.height(4.dp))
            }

            // Изображение
            val imageData = msg.imageBase64.ifEmpty { msg.imageUrl }
            if (imageData.isNotEmpty()) {
                var scale by remember { mutableFloatStateOf(1f) }
                if (imageData.startsWith("avatar://")) {
                    Base64ChatImage(imageData, scale, { z -> scale = (scale * z).coerceIn(1f, 4f) }) { onImageClick(imageData) }
                } else {
                    AsyncImage(model = imageData, contentDescription = null,
                        modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp).clip(RoundedCornerShape(10.dp))
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> scale = (scale * z).coerceIn(1f, 4f) } }
                            .combinedClickable(onClick = { onImageClick(imageData) }),
                        contentScale = ContentScale.Fit)
                }
                if (msg.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
            }

            // ФИЧА #7: GIF
            if (msg.gifUrl.isNotEmpty()) {
                AsyncImage(model = msg.gifUrl, contentDescription = "GIF",
                    modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 180.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { onImageClick(msg.gifUrl) }, contentScale = ContentScale.Fit)
                if (msg.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
            }

            // ФИЧА #6: голосовое сообщение
            if (msg.voiceBase64.isNotEmpty()) {
                VoiceMessageBubble(durationSec = msg.voiceDurationSec, isOwn = isOwn)
            }

            // Текст
            if (msg.text.isNotEmpty() && !msg.isPoll) {
                val httpRe = Regex("""https?://\S+""")
                data class Span(val range: IntRange, val tag: String, val item: String, val display: String)
                val spans = mutableListOf<Span>()
                VS_LINK_RE.findAll(msg.text).forEach {
                    val uid = it.groupValues[1].ifEmpty { it.groupValues[2] }
                    val user = users.firstOrNull { u -> u.id == uid || u.username == uid }
                    spans.add(Span(it.range, "USER", uid, "@${user?.displayName ?: uid}"))
                }
                httpRe.findAll(msg.text).forEach { spans.add(Span(it.range, "URL", it.value, it.value)) }
                spans.sortBy { it.range.first }
                val annotated = buildAnnotatedString {
                    var last = 0
                    spans.forEach { span ->
                        if (span.range.first > last) withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(msg.text.substring(last, span.range.first)) }
                        pushStringAnnotation(span.tag, span.item)
                        withStyle(SpanStyle(color = Color(0xFF93C5FD), fontSize = textSizeSp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) { append(span.display) }
                        pop(); last = span.range.last + 1
                    }
                    if (last < msg.text.length) {
                        val rem = msg.text.substring(last)
                        if (searchQuery.isNotBlank()) {
                            val idx = rem.indexOf(searchQuery, ignoreCase = true)
                            if (idx >= 0) {
                                withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(rem.substring(0, idx)) }
                                withStyle(SpanStyle(color = Color.Black, background = VSYellow, fontSize = textSizeSp, fontWeight = FontWeight.Bold)) { append(rem.substring(idx, idx + searchQuery.length)) }
                                withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(rem.substring(idx + searchQuery.length)) }
                            } else withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(rem) }
                        } else withStyle(SpanStyle(color = Color.White, fontSize = textSizeSp)) { append(rem) }
                    }
                }
                ClickableText(text = annotated, onClick = { offset ->
                    annotated.getStringAnnotations("USER", offset, offset).firstOrNull()?.let { onUserClick(it.item) }
                        ?: annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))) }
                        } ?: run { clipboard.setText(AnnotatedString(msg.text)) }
                })
            }

            // Poll
            if (msg.isPoll && msg.pollQuestion.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("📊 ${msg.pollQuestion}", color = Color.White, fontSize = textSizeSp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val total = msg.pollVotes.size
                msg.pollOptions.forEachIndexed { idx, opt ->
                    val cnt = msg.pollVotes.values.count { it == idx.toString() }
                    val frac = if (total > 0) cnt.toFloat() / total else 0f
                    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.15f)).clickable { onReact("poll:$idx") }) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(32.dp).background(VSPrimary.copy(0.5f)).clip(RoundedCornerShape(8.dp)))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(opt, color = Color.White, fontSize = 13.sp); Text("$cnt", color = Color.White.copy(0.8f), fontSize = 12.sp)
                        }
                    }
                }
                if (total > 0) Text("Всего: $total", color = Color.White.copy(0.6f), fontSize = 10.sp)
            }

            // Таймер самоудаления
            if (msg.expiresAt != null) {
                var remaining by remember(msg.id) { mutableLongStateOf(((msg.expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)) }
                LaunchedEffect(msg.id) { while (remaining > 0) { kotlinx.coroutines.delay(1000); remaining = ((msg.expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0) } }
                Text("⏱ ${remaining}с", color = Color.White.copy(0.7f), fontSize = 10.sp)
            }

            // Статус + время
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                // ФИЧА #1: метка "изменено"
                if (msg.isEdited) Text("изменено", color = Color.White.copy(0.5f), fontSize = 9.sp)
                Spacer(Modifier.width(4.dp))
                if (msg.isPending) Text("⏳", fontSize = 9.sp)
                else if (isOwn) Text(if (msg.readBy.size > 1) "✓✓" else "✓", color = Color.White.copy(0.7f), fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text(SimpleDateFormat(timeFmt, Locale.getDefault()).format(Date(msg.timestamp)), color = Color.White.copy(0.6f), fontSize = 10.sp)
            }
        }
    }
}

// ФИЧА #6: UI голосового сообщения
@Composable
private fun VoiceMessageBubble(durationSec: Int, isOwn: Boolean) {
    val tc = LocalTC.current
    var playing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = { playing = !playing }, modifier = Modifier.size(36.dp)) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
        }
        // Волновая форма (имитация)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val heights = remember { List(20) { (8..32).random().dp } }
            heights.forEach { h -> Box(Modifier.width(3.dp).height(h).background(Color.White.copy(0.8f), RoundedCornerShape(2.dp))) }
        }
        val min = durationSec / 60; val sec = durationSec % 60
        Text("%d:%02d".format(min, sec), color = Color.White.copy(0.8f), fontSize = 11.sp)
    }
}

// ── Фоны ──────────────────────────────────────────────────────────────────────
private fun DrawScope.drawDots() { val s=28.dp.toPx();val r=2.dp.toPx();var y=s/2;while(y<size.height){var x=s/2;while(x<size.width){drawCircle(Color.Gray.copy(0.18f),r, Offset(x,y));x+=s};y+=s} }
private fun DrawScope.drawGrid() { val s=40.dp.toPx();val c=Color.Gray.copy(0.12f);var x=0f;while(x<=size.width){drawLine(c, Offset(x,0f), Offset(x,size.height),1f);x+=s};var y=0f;while(y<=size.height){drawLine(c, Offset(0f,y), Offset(size.width,y),1f);y+=s} }
private fun DrawScope.drawStars() { listOf(0.1f to 0.05f,0.3f to 0.15f,0.6f to 0.08f,0.8f to 0.2f,0.15f to 0.4f,0.5f to 0.35f,0.9f to 0.5f,0.25f to 0.7f,0.7f to 0.65f,0.45f to 0.85f).forEach{(rx,ry)->drawCircle(Color.White.copy(0.25f),2.5f, Offset(rx*size.width,ry*size.height))} }
private fun DrawScope.drawWaves() { val p=Path();val c=Color(0xFF8B5CF6).copy(0.08f);listOf(0.3f,0.55f,0.8f).forEach{fy->p.reset();p.moveTo(0f,fy*size.height);p.cubicTo(size.width*0.25f,fy*size.height-30f,size.width*0.5f,fy*size.height+30f,size.width,fy*size.height);p.lineTo(size.width,size.height);p.lineTo(0f,size.height);p.close();drawPath(p,c)} }

// ── Base64 картинка ────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Base64ChatImage(dataUri: String, scale: Float, onZoom: (Float)->Unit, onClick: ()->Unit) {
    val bm = remember(dataUri) { try { val p=dataUri.removePrefix("avatar://").split("/",limit=2);if(p.size==2){val b=android.util.Base64.decode(p[1],android.util.Base64.NO_WRAP);android.graphics.BitmapFactory.decodeByteArray(b,0,b.size)?.asImageBitmap()}else null }catch(_:Exception){null} }
    if (bm!=null) Image(bm,null,Modifier.widthIn(max=240.dp).heightIn(max=240.dp).clip(RoundedCornerShape(10.dp)).graphicsLayer{scaleX=scale;scaleY=scale}.pointerInput(Unit){detectTransformGestures{_,_,z,_->onZoom(z)}}.combinedClickable(onClick=onClick),contentScale=ContentScale.Fit)
}

// ── Fullscreen ────────────────────────────────────────────────────────────────
@Composable
fun FullscreenImageViewer(url: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale=(scale*zoom).coerceIn(1f,5f);offset=if(scale>1f)offset+pan else Offset.Zero } }) {
        if (url.startsWith("avatar://")) {
            val bm=remember(url){try{val p=url.removePrefix("avatar://").split("/",limit=2);if(p.size==2){val b=android.util.Base64.decode(p[1],android.util.Base64.NO_WRAP);android.graphics.BitmapFactory.decodeByteArray(b,0,b.size)?.asImageBitmap()}else null}catch(_:Exception){null}}
            if(bm!=null) Image(bm,null,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)
        } else AsyncImage(model=url,contentDescription=null,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)
        IconButton(
            onClick=onClose,
            Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) { 
            Icon (
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",  // ← не null!
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
