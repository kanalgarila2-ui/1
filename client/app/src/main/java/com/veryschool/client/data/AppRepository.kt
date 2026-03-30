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

class AppRepository(
    private val db: AppDatabase,
    private val prefs: PrefsManager
) {
    private val TAG = "AppRepo"
    private var wsClient: WsClient? = null
    private var apiClient: ApiClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Единый flow статуса — создаём один раз и держим
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val chats = db.chatDao().getAllChats()
    val users = db.userDao().getAllUsers()

    fun getMessages(chatId: String) = db.messageDao().getMessages(chatId)

    fun initClient(serverUrl: String) {
        apiClient = ApiClient(serverUrl)
    }

    fun getApiClient() = apiClient

    /**
     * Запускает WebSocket соединение с авто-переподключением.
     * Возвращает Job который можно отменить.
     */
    fun startWsConnection(serverUrl: String, token: String): Job {
        // Отключаем старый клиент
        wsClient?.disconnect()
        _connected.value = false

        val client = WsClient()
        wsClient = client

        // Подписываемся на статус подключения нового клиента
        scope.launch {
            client.connected.collect { isConnected ->
                _connected.value = isConnected
                Log.d(TAG, "Connection status: $isConnected")
            }
        }

        // Подписываемся на входящие сообщения
        scope.launch {
            client.incomingMessages.collect { msg ->
                handleIncoming(msg)
            }
        }

        // Запускаем соединение с авто-переподключением
        return scope.launch {
            while (isActive) {
                Log.d(TAG, "Starting WS connection to $serverUrl")
                try {
                    client.connect(serverUrl, token)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "WS connect error: ${e.message}")
                }

                if (!isActive) break

                // Ждём перед переподключением
                Log.d(TAG, "WS disconnected, reconnecting in 3s...")
                delay(3000)
            }
        }
    }

    private suspend fun handleIncoming(msg: WsMessage) {
        try {
            Log.d(TAG, "Handling: ${msg.type}")
            when (msg.type) {
                WsTypes.USER_LIST -> {
                    val users = Json.decodeFromString<List<UserDto>>(msg.payload)
                    Log.d(TAG, "Received ${users.size} users")
                    db.userDao().upsertUsers(users.map { it.toEntity() })
                }
                WsTypes.CHAT_LIST -> {
                    val chats = Json.decodeFromString<List<ChatDto>>(msg.payload)
                    Log.d(TAG, "Received ${chats.size} chats")
                    db.chatDao().upsertChats(chats.map { it.toEntity() })
                }
                WsTypes.MESSAGE_HISTORY -> {
                    val msgs = Json.decodeFromString<List<MessageDto>>(msg.payload)
                    Log.d(TAG, "Received ${msgs.size} messages history")
                    if (msgs.isNotEmpty()) db.messageDao().upsertMessages(msgs.map { it.toEntity() })
                }
                WsTypes.NEW_MESSAGE -> {
                    val m = Json.decodeFromString<MessageDto>(msg.payload)
                    db.messageDao().upsertMessage(m.toEntity())
                    db.chatDao().updateLastMessage(m.chatId, m.text.ifEmpty { "📷 Фото" }, m.timestamp)
                }
                WsTypes.REACTION_UPDATE -> {
                    val m = Json.decodeFromString<MessageDto>(msg.payload)
                    db.messageDao().updateReactions(m.id, Json.encodeToString(m.reactions))
                }
                WsTypes.USER_ONLINE -> {
                    val u = Json.decodeFromString<UserDto>(msg.payload)
                    db.userDao().upsertUser(u.toEntity())
                }
                WsTypes.USER_OFFLINE -> {
                    val u = Json.decodeFromString<UserDto>(msg.payload)
                    db.userDao().setOnline(u.id, false)
                }
                WsTypes.PROFILE_UPDATED -> {
                    val u = Json.decodeFromString<UserDto>(msg.payload)
                    db.userDao().upsertUser(u.toEntity())
                }
                WsTypes.GROUP_CREATED -> {
                    val c = Json.decodeFromString<ChatDto>(msg.payload)
                    Log.d(TAG, "New chat created: ${c.name}")
                    db.chatDao().upsertChat(c.toEntity())
                }
                WsTypes.USER_UPDATED -> {
                    val u = Json.decodeFromString<UserDto>(msg.payload)
                    db.userDao().upsertUser(u.toEntity())
                }
                WsTypes.BANNED -> {
                    Log.w(TAG, "Banned: ${msg.payload}")
                }
                WsTypes.ERROR -> {
                    Log.e(TAG, "Server error: ${msg.payload}")
                }
                WsTypes.PING -> sendWs(WsMessage(WsTypes.PONG))
                WsTypes.AUTH_OK -> {
                    Log.d(TAG, "Auth OK!")
                }
                WsTypes.AUTH_FAIL -> {
                    Log.e(TAG, "Auth FAILED: ${msg.payload}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handle error [${msg.type}]: ${e.message}")
        }
    }

    suspend fun sendMessage(chatId: String, text: String, imageBase64: String = "") {
        sendWs(WsMessage(WsTypes.SEND_MESSAGE, Json.encodeToString(SendMessageRequest(chatId, text, imageBase64))))
    }

    suspend fun sendReaction(messageId: String, chatId: String, emoji: String) {
        sendWs(WsMessage(WsTypes.REACTION, Json.encodeToString(ReactionRequest(messageId, chatId, emoji))))
    }

    suspend fun requestMessages(chatId: String) {
        sendWs(WsMessage(WsTypes.MESSAGE_HISTORY, chatId))
        db.chatDao().clearUnread(chatId)
    }

    suspend fun createDm(otherUserId: String, currentUserId: String, otherDisplayName: String) {
        sendWs(WsMessage(WsTypes.CREATE_GROUP, Json.encodeToString(
            CreateGroupRequest(name = otherDisplayName, memberIds = listOf(otherUserId), isDm = true)
        )))
    }

    suspend fun createGroup(name: String, memberIds: List<String>) {
        sendWs(WsMessage(WsTypes.CREATE_GROUP, Json.encodeToString(
            CreateGroupRequest(name = name, memberIds = memberIds, isDm = false)
        )))
    }

    suspend fun sendTyping(chatId: String) = sendWs(WsMessage(WsTypes.TYPING, chatId))
    suspend fun sendTypingStop(chatId: String) = sendWs(WsMessage(WsTypes.TYPING_STOP, chatId))

    private suspend fun sendWs(msg: WsMessage) {
        wsClient?.send(msg)
    }

    fun disconnect() {
        wsClient?.disconnect()
        _connected.value = false
    }

    // ── Converters ────────────────────────────────────────────────────────────
    private fun ChatDto.toEntity() = ChatEntity(id, name, isGroup, Json.encodeToString(members), avatarBase64, lastMessage, lastMessageTime, 0, isBot)
    private fun MessageDto.toEntity() = MessageEntity(id, chatId, senderId, senderName, text, timestamp, Json.encodeToString(reactions), imageBase64)
    private fun UserDto.toEntity() = UserEntity(id, username, displayName, avatarBase64, online)
}
