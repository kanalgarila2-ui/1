package com.veryschool.client.data

import android.util.Log
import com.veryschool.client.data.db.*
import com.veryschool.client.data.models.*
import com.veryschool.client.data.prefs.PrefsManager
import com.veryschool.client.network.ApiClient
import com.veryschool.client.network.WsClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "AppRepo"

class AppRepository(
    private val db: AppDatabase,
    private val prefs: PrefsManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Один WsClient живёт всё время — channel не умирает
    private val wsClient = WsClient()
    private var apiClient: ApiClient? = null

    // Scope для WS цикла — отменяется при logout
    private var wsScope: CoroutineScope? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val chats: Flow<List<ChatEntity>> = db.chatDao().getAllChats()
    val users: Flow<List<UserEntity>> = db.userDao().getAllUsers()

    fun getMessages(chatId: String) = db.messageDao().getMessages(chatId)

    fun initClient(serverUrl: String) {
        Log.i(TAG, "initClient: $serverUrl")
        apiClient = ApiClient(serverUrl)
    }

    fun getApiClient() = apiClient

    /**
     * Запускает WS с авто-переподключением.
     * Возвращает Job — отмени чтобы остановить.
     */
    fun startWsConnection(serverUrl: String, token: String): Job {
        Log.i(TAG, "startWsConnection → $serverUrl  token=${token.take(8)}...")

        wsScope?.cancel()
        _connected.value = false

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        wsScope = scope

        return scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                Log.i(TAG, "WS attempt #$attempt")
                try {
                    wsClient.connect(
                        serverUrl = serverUrl,
                        token = token,
                        onConnected = {
                            _connected.value = true
                            Log.i(TAG, "✓ WS CONNECTED")
                        },
                        onDisconnected = {
                            _connected.value = false
                            Log.w(TAG, "✗ WS DISCONNECTED")
                        },
                        onMessage = { msg -> handleIncoming(msg) }
                    )
                } catch (e: CancellationException) {
                    Log.i(TAG, "WS loop cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "WS loop error: ${e.message}")
                }

                if (!isActive) break
                Log.i(TAG, "Reconnecting in 3s... (attempt $attempt)")
                delay(3000)
            }
            Log.i(TAG, "WS loop ended")
        }
    }

    private suspend fun handleIncoming(msg: WsMessage) {
        Log.i(TAG, "INCOMING type=${msg.type} len=${msg.payload.length}")
        try {
            when (msg.type) {

                WsTypes.AUTH_OK -> {
                    Log.i(TAG, "✓ AUTH_OK")
                }

                WsTypes.AUTH_FAIL -> {
                    Log.e(TAG, "✗ AUTH_FAIL: ${msg.payload}")
                }

                WsTypes.USER_LIST -> {
                    val list = json.decodeFromString<List<UserDto>>(msg.payload)
                    Log.i(TAG, "USER_LIST: ${list.size} users → saving to DB")
                    db.userDao().upsertUsers(list.map { it.toEntity() })
                    val saved = db.userDao().countAll()
                    Log.i(TAG, "DB now has $saved users")
                }

                WsTypes.CHAT_LIST -> {
                    val list = json.decodeFromString<List<ChatDto>>(msg.payload)
                    Log.i(TAG, "CHAT_LIST: ${list.size} chats → saving to DB")
                    db.chatDao().upsertChats(list.map { it.toEntity() })
                }

                WsTypes.NEW_MESSAGE -> {
                    val m = json.decodeFromString<MessageDto>(msg.payload)
                    Log.i(TAG, "NEW_MESSAGE in chat=${m.chatId} from=${m.senderId}")
                    db.messageDao().upsertMessage(m.toEntity())
                    db.chatDao().updateLastMessage(m.chatId, m.text.ifEmpty { "📷 Фото" }, m.timestamp)
                }

                WsTypes.MESSAGE_HISTORY -> {
                    val list = json.decodeFromString<List<MessageDto>>(msg.payload)
                    Log.i(TAG, "MESSAGE_HISTORY: ${list.size} msgs")
                    if (list.isNotEmpty()) db.messageDao().upsertMessages(list.map { it.toEntity() })
                }

                WsTypes.REACTION_UPDATE -> {
                    val m = json.decodeFromString<MessageDto>(msg.payload)
                    db.messageDao().updateReactions(m.id, json.encodeToString(m.reactions))
                }

                WsTypes.USER_ONLINE -> {
                    val u = json.decodeFromString<UserDto>(msg.payload)
                    Log.i(TAG, "USER_ONLINE: @${u.username}")
                    db.userDao().upsertUser(u.toEntity())
                }

                WsTypes.USER_OFFLINE -> {
                    val u = json.decodeFromString<UserDto>(msg.payload)
                    Log.i(TAG, "USER_OFFLINE: @${u.username}")
                    db.userDao().setOnline(u.id, false)
                }

                WsTypes.PROFILE_UPDATED -> {
                    val u = json.decodeFromString<UserDto>(msg.payload)
                    db.userDao().upsertUser(u.toEntity())
                }

                WsTypes.GROUP_CREATED -> {
                    val c = json.decodeFromString<ChatDto>(msg.payload)
                    Log.i(TAG, "GROUP_CREATED: ${c.name} id=${c.id}")
                    db.chatDao().upsertChat(c.toEntity())
                }

                WsTypes.USER_UPDATED -> {
                    val u = json.decodeFromString<UserDto>(msg.payload)
                    db.userDao().upsertUser(u.toEntity())
                }

                WsTypes.BANNED -> {
                    Log.w(TAG, "BANNED: ${msg.payload}")
                }

                WsTypes.ERROR -> {
                    Log.e(TAG, "SERVER ERROR: ${msg.payload}")
                }

                WsTypes.PING -> {
                    Log.d(TAG, "PING → PONG")
                    wsClient.send(WsMessage(WsTypes.PONG))
                }

                else -> Log.w(TAG, "UNKNOWN msg type: ${msg.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleIncoming CRASH [${msg.type}]: ${e.message}\npayload=${msg.payload.take(200)}")
        }
    }

    // ── Outgoing ──────────────────────────────────────────────────────────────

    fun sendMessage(chatId: String, text: String, imageBase64: String = "") {
        Log.i(TAG, "sendMessage → chatId=$chatId text=${text.take(40)}")
        wsClient.send(WsMessage(WsTypes.SEND_MESSAGE, json.encodeToString(
            SendMessageRequest(chatId, text, imageBase64)
        )))
    }

    fun sendReaction(messageId: String, chatId: String, emoji: String) {
        wsClient.send(WsMessage(WsTypes.REACTION, json.encodeToString(
            ReactionRequest(messageId, chatId, emoji)
        )))
    }

    fun requestMessages(chatId: String) {
        Log.i(TAG, "requestMessages chatId=$chatId")
        wsClient.send(WsMessage(WsTypes.MESSAGE_HISTORY, chatId))
        CoroutineScope(Dispatchers.IO).launch { db.chatDao().clearUnread(chatId) }
    }

    fun createDm(otherUserId: String, currentUserId: String, otherDisplayName: String) {
        Log.i(TAG, "createDm → otherUserId=$otherUserId name=$otherDisplayName")
        wsClient.send(WsMessage(WsTypes.CREATE_GROUP, json.encodeToString(
            CreateGroupRequest(name = otherDisplayName, memberIds = listOf(otherUserId), isDm = true)
        )))
    }

    fun createGroup(name: String, memberIds: List<String>) {
        Log.i(TAG, "createGroup → name=$name members=$memberIds")
        wsClient.send(WsMessage(WsTypes.CREATE_GROUP, json.encodeToString(
            CreateGroupRequest(name = name, memberIds = memberIds, isDm = false)
        )))
    }

    fun sendTyping(chatId: String) = wsClient.send(WsMessage(WsTypes.TYPING, chatId))
    fun sendTypingStop(chatId: String) = wsClient.send(WsMessage(WsTypes.TYPING_STOP, chatId))

    fun disconnect() {
        Log.i(TAG, "disconnect()")
        wsScope?.cancel()
        wsScope = null
        wsClient.close()
        _connected.value = false
    }

    // ── Converters ────────────────────────────────────────────────────────────
    private fun ChatDto.toEntity() = ChatEntity(
        id, name, isGroup, json.encodeToString(members),
        avatarBase64, lastMessage, lastMessageTime, 0, isBot
    )
    private fun MessageDto.toEntity() = MessageEntity(
        id, chatId, senderId, senderName, text, timestamp,
        json.encodeToString(reactions), imageBase64
    )
    private fun UserDto.toEntity() = UserEntity(id, username, displayName, avatarBase64, online)
}
