@file:OptIn(ExperimentalStdlibApi::class)

package com.veryschool.server.core

import android.util.Log
import at.favre.lib.crypto.bcrypt.BCrypt
import com.veryschool.server.data.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// ─── Protocol models ────────────────────────────────────────────────────────
@Serializable data class WsMessage(val type: String, val payload: String = "")
@Serializable data class UserDto(val id: String, val username: String, val displayName: String, val avatarBase64: String = "", val online: Boolean = false, val isBanned: Boolean = false, val dmBlocked: Boolean = false)
@Serializable data class MessageDto(val id: String, val chatId: String, val senderId: String, val senderName: String, val text: String, val timestamp: Long, val reactions: Map<String, List<String>> = emptyMap(), val imageBase64: String = "")
@Serializable data class ChatDto(val id: String, val name: String, val isGroup: Boolean, val members: List<String>, val avatarBase64: String = "", val lastMessage: String = "", val lastMessageTime: Long = 0L, val isBot: Boolean = false)
@Serializable data class AuthRequest(val username: String, val password: String, val passphrase: String)
@Serializable data class RegisterRequest(val username: String, val password: String, val displayName: String, val passphrase: String)
@Serializable data class AuthResponse(val success: Boolean, val token: String = "", val user: UserDto? = null, val error: String = "")
@Serializable data class SendMessageRequest(val chatId: String, val text: String, val imageBase64: String = "")
@Serializable data class ReactionRequest(val messageId: String, val chatId: String, val emoji: String)
@Serializable data class CreateGroupRequest(val name: String, val memberIds: List<String>, val isDm: Boolean = false)
@Serializable data class UpdateProfileRequest(val displayName: String, val avatarBase64: String = "")
@Serializable data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
@Serializable data class CheckUsernameResponse(val exists: Boolean)
@Serializable data class ServerStatsDto(val users: Int, val chats: Int, val messages: Int, val online: Int)
@Serializable data class BanRequest(val userId: String, val durationMinutes: Long = 0, val reason: String = "")
@Serializable data class BotMessageRequest(val text: String, val targetUserId: String = "")

object WsTypes {
    const val AUTH = "auth"; const val AUTH_OK = "auth_ok"; const val AUTH_FAIL = "auth_fail"
    const val NEW_MESSAGE = "new_message"; const val SEND_MESSAGE = "send_message"
    const val REACTION = "reaction"; const val REACTION_UPDATE = "reaction_update"
    const val CHAT_LIST = "chat_list"; const val USER_LIST = "user_list"
    const val MESSAGE_HISTORY = "message_history"
    const val USER_ONLINE = "user_online"; const val USER_OFFLINE = "user_offline"
    const val CREATE_GROUP = "create_group"; const val GROUP_CREATED = "group_created"
    const val PROFILE_UPDATED = "profile_updated"
    const val ERROR = "error"; const val PING = "ping"; const val PONG = "pong"
    const val TYPING = "typing"; const val TYPING_STOP = "typing_stop"
    const val BANNED = "banned"; const val USER_UPDATED = "user_updated"
}

// ─── Connection manager ──────────────────────────────────────────────────────
data class ActiveSession(val userId: String, val session: DefaultWebSocketSession)

class ConnectionManager {
    private val sessions = ConcurrentHashMap<String, ActiveSession>()
    private val mutex = Mutex()
    suspend fun add(u: String, s: DefaultWebSocketSession) = mutex.withLock { sessions[u] = ActiveSession(u, s) }
    suspend fun remove(u: String) = mutex.withLock { sessions.remove(u) }
    fun isOnline(u: String) = sessions.containsKey(u)
    fun onlineUsers() = sessions.keys.toSet()
    fun count() = sessions.size
    suspend fun sendTo(userId: String, msg: WsMessage) {
        try { sessions[userId]?.session?.send(Frame.Text(Json.encodeToString(msg))) }
        catch (e: Exception) { Log.e("ConnMgr", "Send error $userId: ${e.message}") }
    }
    suspend fun broadcast(msg: WsMessage, exceptUserId: String? = null) {
        sessions.values.filter { it.userId != exceptUserId }.forEach {
            try { it.session.send(Frame.Text(Json.encodeToString(msg))) } catch (_: Exception) {}
        }
    }
    suspend fun sendToMembers(members: List<String>, msg: WsMessage) { members.forEach { sendTo(it, msg) } }
}

// ─── Main server ─────────────────────────────────────────────────────────────
class VerySchoolServer(private val db: ServerDatabase, private val port: Int = 8080) {
    private val TAG = "VSServer"
    private var engine: NettyApplicationEngine? = null
    val connections = ConnectionManager()
    val logs = mutableListOf<String>()
    private val logMutex = Mutex()
    var isRunning = false
        private set
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private var ipLogFile: File? = null

    fun log(msg: String, level: String = "INFO") {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$ts][$level] $msg"
        Log.d(TAG, entry)
        runBlocking { logMutex.withLock { logs.add(entry); if (logs.size > 2000) logs.removeAt(0) } }
    }

    fun setLogDir(dir: File) {
        ipLogFile = File(dir, "ip_access.log")
    }

    private fun logIp(ip: String, userId: String, action: String) {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$ts | $ip | $userId | $action\n"
        try { ipLogFile?.appendText(line) } catch (_: Exception) {}
        runBlocking {
            try { db.ipLogDao().insert(IpLogEntity(ip = ip, userId = userId, action = action)) } catch (_: Exception) {}
        }
    }

    fun start() {
        if (isRunning) { log("Server already running"); return }
        try {
            engine = embeddedServer(Netty, port = port, configure = {
                connectionGroupSize = 2; workerGroupSize = 4; callGroupSize = 8; requestQueueLimit = 64
            }) {
                install(ContentNegotiation) { json(json) }
                install(CORS) {
                    anyHost()
                    allowMethod(HttpMethod.Get); allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Put); allowMethod(HttpMethod.Delete)
                    allowHeader(HttpHeaders.ContentType); allowHeader(HttpHeaders.Authorization)
                }
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15)
                    timeout = Duration.ofSeconds(30)
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                routing { setupRestRoutes(); setupWsRoute() }
            }.start(wait = false)
            isRunning = true
            log("✅ Server started on port $port")
        } catch (e: Exception) { log("❌ Failed to start: ${e.message}", "ERROR") }
    }

    fun stop() { engine?.stop(500, 1000); isRunning = false; log("🛑 Server stopped") }
    fun restart() { log("🔄 Restarting..."); stop(); Thread.sleep(500); start() }

    // ─── REST ────────────────────────────────────────────────────────────────
    private fun Routing.setupRestRoutes() {

        get("/ping") { call.respond(mapOf("status" to "ok")) }

        get("/stats") {
            call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@get }
            call.respond(ServerStatsDto(db.userDao().count(), db.chatDao().count(), db.messageDao().count(), connections.count()))
        }

        // ── Register
        post("/auth/register") {
            val ip = call.request.local.remoteAddress ?: "unknown"
            val req = call.receive<RegisterRequest>()
            log("🔌 Register attempt from $ip: @${req.username}")
            if (req.passphrase != "22sch") { call.respond(AuthResponse(false, error = "Неверная ключевая фраза")); return@post }
            if (req.username.isBlank() || req.password.isBlank() || req.displayName.isBlank()) { call.respond(AuthResponse(false, error = "Заполните все поля")); return@post }
            if (req.username.length < 3) { call.respond(AuthResponse(false, error = "Имя пользователя минимум 3 символа")); return@post }
            if (db.userDao().countByUsername(req.username.lowercase()) > 0) { call.respond(AuthResponse(false, error = "Имя уже занято")); return@post }
            val userId = generateUserId()
            val hash = BCrypt.withDefaults().hashToString(10, req.password.toCharArray())
            val user = UserEntity(userId, req.username.lowercase().trim(), hash, req.displayName.trim())
            db.userDao().upsert(user)
            val token = generateToken()
            db.tokenDao().insert(TokenEntity(token, userId))
            createBotChat(userId)
            logIp(ip, userId, "REGISTER")
            log("👤 Registered: @${req.username} ID=$userId from $ip")
            val dto = user.toDto()
            connections.broadcast(WsMessage(WsTypes.USER_ONLINE, json.encodeToString(dto)))
            call.respond(AuthResponse(true, token, dto))
        }

        // ── Login
        post("/auth/login") {
            val ip = call.request.local.remoteAddress ?: "unknown"
            val req = call.receive<AuthRequest>()
            log("🔐 Login attempt from $ip: @${req.username}")
            if (req.passphrase != "22sch") { call.respond(AuthResponse(false, error = "Неверная ключевая фраза")); return@post }
            val user = db.userDao().getByUsername(req.username.lowercase().trim())
            if (user == null || !BCrypt.verifyer().verify(req.password.toCharArray(), user.passwordHash).verified) {
                logIp(ip, req.username, "LOGIN_FAIL")
                log("❌ Failed login: @${req.username} from $ip", "WARN")
                call.respond(AuthResponse(false, error = "Неверный логин или пароль")); return@post
            }
            if (user.isBanned && (user.banUntil == 0L || user.banUntil > System.currentTimeMillis())) {
                val msg = if (user.banUntil == 0L) "Вы заблокированы навсегда. Причина: ${user.banReason}"
                          else "Вы заблокированы до ${java.text.SimpleDateFormat("dd.MM HH:mm").format(Date(user.banUntil))}. Причина: ${user.banReason}"
                call.respond(AuthResponse(false, error = msg)); return@post
            }
            val token = generateToken()
            db.tokenDao().insert(TokenEntity(token, user.id))
            logIp(ip, user.id, "LOGIN")
            log("✅ Login: @${user.username} from $ip")
            call.respond(AuthResponse(true, token, user.toDto(connections.isOnline(user.id))))
        }

        get("/auth/check/{username}") {
            val username = call.parameters["username"] ?: ""
            call.respond(CheckUsernameResponse(db.userDao().countByUsername(username.lowercase()) > 0))
        }

        post("/auth/logout") {
            val token = call.token() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            db.tokenDao().delete(token)
            call.respond(HttpStatusCode.OK)
        }

        get("/users") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@get }
            val users = db.userDao().getAll().map { it.toDto(connections.isOnline(it.id)) }
            log("📋 User list requested by $userId (${users.size} users)")
            call.respond(users)
        }

        get("/users/search") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@get }
            val q = call.request.queryParameters["q"]?.lowercase() ?: ""
            val users = db.userDao().getAll()
                .filter { it.username.contains(q) || it.displayName.lowercase().contains(q) || it.id.contains(q) }
                .map { it.toDto(connections.isOnline(it.id)) }
            call.respond(users)
        }

        get("/user/{id}") {
            call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@get }
            val targetId = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest); return@get }
            val user = db.userDao().getById(targetId) ?: run { call.respond(HttpStatusCode.NotFound); return@get }
            call.respond(user.toDto(connections.isOnline(user.id)))
        }

        put("/user/profile") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@put }
            val req = call.receive<UpdateProfileRequest>()
            if (req.displayName.isBlank()) { call.respond(HttpStatusCode.BadRequest); return@put }
            db.userDao().updateProfile(userId, req.displayName.trim(), req.avatarBase64)
            val user = db.userDao().getById(userId)!!
            val dto = user.toDto(connections.isOnline(userId))
            connections.broadcast(WsMessage(WsTypes.PROFILE_UPDATED, json.encodeToString(dto)))
            log("✏️ Profile updated: @${user.username}")
            call.respond(HttpStatusCode.OK)
        }

        post("/user/password") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            val req = call.receive<ChangePasswordRequest>()
            val user = db.userDao().getById(userId)!!
            if (!BCrypt.verifyer().verify(req.oldPassword.toCharArray(), user.passwordHash).verified) {
                call.respond(mapOf("success" to false, "error" to "Неверный текущий пароль")); return@post
            }
            db.userDao().updatePassword(userId, BCrypt.withDefaults().hashToString(10, req.newPassword.toCharArray()))
            log("🔑 Password changed: @${user.username}")
            call.respond(mapOf("success" to true))
        }

        get("/messages/{chatId}") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@get }
            val chatId = call.parameters["chatId"] ?: run { call.respond(HttpStatusCode.BadRequest); return@get }
            val chat = db.chatDao().getById(chatId) ?: run { call.respond(HttpStatusCode.NotFound); return@get }
            val members = try { json.decodeFromString<List<String>>(chat.members) } catch (_: Exception) { emptyList() }
            if (userId !in members) { call.respond(HttpStatusCode.Forbidden); return@get }
            call.respond(db.messageDao().getForChat(chatId).map { it.toDto() })
        }

        get("/admin/iplogs") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@get }
            val user = db.userDao().getById(userId)
            if (user?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@get }
            val logs = db.ipLogDao().getRecent()
            call.respond(logs.map { mapOf("ip" to it.ip, "userId" to it.userId, "action" to it.action, "time" to it.timestamp) })
        }

        post("/admin/ban") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            val admin = db.userDao().getById(userId)
            if (admin?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@post }
            val req = call.receive<BanRequest>()
            val until = if (req.durationMinutes == 0L) 0L else System.currentTimeMillis() + req.durationMinutes * 60_000
            db.userDao().setBan(req.userId, true, until, req.reason)
            val target = db.userDao().getById(req.userId)
            val durStr = if (until == 0L) "навсегда" else "${req.durationMinutes} мин"
            log("🚫 @${target?.username} BANNED by admin for $durStr. Reason: ${req.reason}", "WARN")
            connections.sendTo(req.userId, WsMessage(WsTypes.BANNED, "Вы заблокированы. Причина: ${req.reason}"))
            call.respond(mapOf("success" to true))
        }

        post("/admin/unban") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            val admin = db.userDao().getById(userId)
            if (admin?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@post }
            val req = call.receive<BanRequest>()
            db.userDao().setBan(req.userId, false, 0L, "")
            val target = db.userDao().getById(req.userId)
            log("✅ @${target?.username} UNBANNED by admin")
            call.respond(mapOf("success" to true))
        }

        post("/admin/block-dm") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            val admin = db.userDao().getById(userId)
            if (admin?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@post }
            val req = call.receive<BanRequest>()
            val until = if (req.durationMinutes == 0L) 0L else System.currentTimeMillis() + req.durationMinutes * 60_000
            db.userDao().setDmBlock(req.userId, true, until)
            val target = db.userDao().getById(req.userId)
            log("🔇 @${target?.username} DMs BLOCKED")
            call.respond(mapOf("success" to true))
        }

        post("/admin/unblock-dm") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            val admin = db.userDao().getById(userId)
            if (admin?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@post }
            val req = call.receive<BanRequest>()
            db.userDao().setDmBlock(req.userId, false, 0L)
            val target = db.userDao().getById(req.userId)
            log("✅ @${target?.username} DMs UNBLOCKED")
            call.respond(mapOf("success" to true))
        }

        post("/admin/bot-message") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@post }
            val admin = db.userDao().getById(userId)
            if (admin?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@post }
            val req = call.receive<BotMessageRequest>()
            if (req.targetUserId.isEmpty()) {
                val allUsers = db.userDao().getAll()
                allUsers.forEach { sendBotMessage(it.id, req.text) }
                log("🤖 Bot broadcast to ${allUsers.size} users: ${req.text.take(50)}")
            } else {
                sendBotMessage(req.targetUserId, req.text)
                val target = db.userDao().getById(req.targetUserId)
                log("🤖 Bot message to @${target?.username}: ${req.text.take(50)}")
            }
            call.respond(mapOf("success" to true))
        }

        delete("/admin/user/{id}") {
            val userId = call.authedUserId() ?: run { call.respond(HttpStatusCode.Unauthorized); return@delete }
            val admin = db.userDao().getById(userId)
            if (admin?.isAdmin != true) { call.respond(HttpStatusCode.Forbidden); return@delete }
            val targetId = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest); return@delete }
            db.userDao().delete(targetId)
            db.tokenDao().deleteForUser(targetId)
            log("🗑️ Admin deleted user: $targetId", "WARN")
            call.respond(HttpStatusCode.OK)
        }
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────
    private fun Routing.setupWsRoute() {
        webSocket("/ws") {
            var authedUserId: String? = null
            var authedUser: UserEntity? = null
            val ip = call.request.local.remoteAddress ?: "unknown"
            log("🔌 New WS connection from $ip")

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = try { json.decodeFromString<WsMessage>(frame.readText()) } catch (e: Exception) {
                        log("⚠️ Bad frame from $ip: ${e.message}", "WARN"); continue
                    }
                    when (msg.type) {
                        WsTypes.AUTH -> {
                            val tokenEntity = db.tokenDao().get(msg.payload)
                            if (tokenEntity == null) {
                                send(Frame.Text(json.encodeToString(WsMessage(WsTypes.AUTH_FAIL, "Неверный токен"))))
                                close(); return@webSocket
                            }
                            val user = db.userDao().getById(tokenEntity.userId)
                            if (user == null) { close(); return@webSocket }
                            if (user.isBanned && (user.banUntil == 0L || user.banUntil > System.currentTimeMillis())) {
                                send(Frame.Text(json.encodeToString(WsMessage(WsTypes.BANNED, "Вы заблокированы: ${user.banReason}"))))
                                close(); return@webSocket
                            }
                            authedUserId = user.id
                            authedUser = user
                            connections.add(user.id, this)
                            logIp(ip, user.id, "WS_CONNECT")
                            log("✅ WS authed: @${user.username} (${user.id}) from $ip")

                            send(Frame.Text(json.encodeToString(WsMessage(WsTypes.AUTH_OK, json.encodeToString(user.toDto(true))))))

                            val allUsers = db.userDao().getAll().map { it.toDto(connections.isOnline(it.id)) }
                            log("📋 Sending ${allUsers.size} users to @${user.username}")
                            send(Frame.Text(json.encodeToString(WsMessage(WsTypes.USER_LIST, json.encodeToString(allUsers)))))

                            val chats = db.chatDao().getForUser(user.id).map { it.toDto() }
                            log("💬 Sending ${chats.size} chats to @${user.username}")
                            send(Frame.Text(json.encodeToString(WsMessage(WsTypes.CHAT_LIST, json.encodeToString(chats)))))

                            connections.broadcast(WsMessage(WsTypes.USER_ONLINE, json.encodeToString(user.toDto(true))), exceptUserId = user.id)
                        }

                        WsTypes.SEND_MESSAGE -> {
                            val uid = authedUserId ?: continue
                            val user = authedUser ?: continue
                            val req = try { json.decodeFromString<SendMessageRequest>(msg.payload) } catch (_: Exception) { continue }
                            val chat = db.chatDao().getById(req.chatId)
                            if (chat == null) {
                                sendError("Чат не найден")
                                continue
                            }
                            val members = try { json.decodeFromString<List<String>>(chat.members) } catch (_: Exception) { emptyList() }
                            if (uid !in members) { sendError("Нет доступа"); continue }
                            if (req.text.isBlank() && req.imageBase64.isBlank()) continue

                            val msgEntity = MessageEntity(
                                id = UUID.randomUUID().toString(), chatId = req.chatId,
                                senderId = uid, senderName = user.displayName,
                                text = req.text.trim(), imageBase64 = req.imageBase64
                            )
                            db.messageDao().upsert(msgEntity)
                            log("💬 @${user.username} → '${chat.name}': ${req.text.take(60)}")
                            connections.sendToMembers(members, WsMessage(WsTypes.NEW_MESSAGE, json.encodeToString(msgEntity.toDto())))
                        }

                        WsTypes.MESSAGE_HISTORY -> {
                            val uid = authedUserId ?: continue
                            val chatId = msg.payload.trim()
                            val chat = db.chatDao().getById(chatId) 
                            if (chat == null) continue
                            val members = try {
                                json.decodeFromString<List<String>>(chat.members) 
                            } catch (_: Exception) { 
                                emptyList() 
                            }
                            if (uid !in members) continue
                            val msgs = db.messageDao().getForChat(chatId).map { 
                                it.toDto() 
                            }
                            log("📜 History for '${chat.name}': ${msgs.size} messages → @${authedUser?.username}")
                            send(Frame.Text(json.encodeToString(WsMessage(WsTypes.MESSAGE_HISTORY, json.encodeToString(msgs)))))
                        }

                        WsTypes.REACTION -> {
                            val uid = authedUserId ?: continue
                            val req = try { json.decodeFromString<ReactionRequest>(msg.payload) } catch (_: Exception) { continue }
                            val msgEntity = db.messageDao().getById(req.messageId) ?: continue
                            val chat = db.chatDao().getById(req.chatId) ?: continue
                            val members = try { json.decodeFromString<List<String>>(chat.members) } catch (_: Exception) { emptyList() }
                            val reactions = try { json.decodeFromString<MutableMap<String, MutableList<String>>>(msgEntity.reactions) } catch (_: Exception) { mutableMapOf() }
                            val cur = reactions.getOrPut(req.emoji) { mutableListOf() }
                            if (uid in cur) cur.remove(uid) else cur.add(uid)
                            if (cur.isEmpty()) reactions.remove(req.emoji)
                            val newReactions = json.encodeToString(reactions)
                            db.messageDao().updateReactions(req.messageId, newReactions)
                            connections.sendToMembers(members, WsMessage(WsTypes.REACTION_UPDATE, json.encodeToString(msgEntity.copy(reactions = newReactions).toDto())))
                        }

                        WsTypes.CREATE_GROUP -> {
                            val uid = authedUserId ?: continue
                            val user = authedUser ?: continue
                            val req = try { 
                                json.decodeFromString<CreateGroupRequest>(msg.payload) 
                            } catch (_: Exception) {
                                continue
                            }
                            val allMembers = (req.memberIds + uid).distinct()
                            log("📁 CREATE_GROUP request by @${user.username}: name='${req.name}' members=$allMembers isDm=${req.isDm}")

                            if (req.isDm && allMembers.size == 2) {
                                val otherId = allMembers.firstOrNull { it != uid }
                                if (otherId != null) {
                                    val other = db.userDao().getById(otherId)
                                    if (other != null && other.dmBlocked &&
                                        (other.dmBlockUntil == 0L || other.dmBlockUntil > System.currentTimeMillis())) {
                                        sendError("Этому пользователю нельзя писать в личку")
                                        continue
                                    }
                                }
                                
                                var existing: ChatEntity? = null
                                for (c in db.chatDao().getAll()) {
                                    if (!c.isGroup) {
                                        val m = try { json.decodeFromString<List<String>>(c.members) } catch (_: Exception) { emptyList() }
                                        if (m.toSet() == allMembers.toSet()) {
                                            existing = c
                                            break
                                        }
                                    }
                                }
                                
                                if (existing != null) {
                                    log("📁 DM already exists: ${existing.id}")
                                    allMembers.forEach { memberId ->
                                        val otherId2 = allMembers.firstOrNull { it != memberId } ?: memberId
                                        val other = db.userDao().getById(otherId2)
                                        val dto = existing.toDto().copy(name = other?.displayName ?: existing.name)
                                        connections.sendTo(memberId, WsMessage(WsTypes.GROUP_CREATED, json.encodeToString(dto)))
                                    }
                                    continue
                                }
                            }

                            val chatId = UUID.randomUUID().toString()
                            val chatEntity = ChatEntity(id = chatId, name = req.name, isGroup = !req.isDm, members = json.encodeToString(allMembers), createdBy = uid)
                            db.chatDao().upsert(chatEntity)
                            log("✅ Chat created: '${req.name}' id=$chatId by @${user.username} members=$allMembers")

                            allMembers.forEach { memberId ->
                                val chatDto = if (req.isDm) {
                                    val otherId = allMembers.firstOrNull { it != memberId } ?: memberId
                                    val other = db.userDao().getById(otherId)
                                    chatEntity.toDto().copy(name = other?.displayName ?: req.name)
                                } else chatEntity.toDto()
                                connections.sendTo(memberId, WsMessage(WsTypes.GROUP_CREATED, json.encodeToString(chatDto)))
                            }
                        }

                        WsTypes.TYPING -> {
                            val uid = authedUserId ?: continue
                            val user = authedUser ?: continue
                            val chat = db.chatDao().getById(msg.payload) ?: continue
                            val members = try { json.decodeFromString<List<String>>(chat.members) } catch (_: Exception) { emptyList() }
                            val payload = json.encodeToString(mapOf("userId" to uid, "username" to user.displayName, "chatId" to msg.payload))
                            connections.sendToMembers(members.filter { it != uid }, WsMessage(WsTypes.TYPING, payload))
                        }

                        WsTypes.TYPING_STOP -> {
                            val uid = authedUserId ?: continue
                            val chat = db.chatDao().getById(msg.payload) ?: continue
                            val members = try { json.decodeFromString<List<String>>(chat.members) } catch (_: Exception) { emptyList() }
                            connections.sendToMembers(members.filter { it != uid }, WsMessage(WsTypes.TYPING_STOP, json.encodeToString(mapOf("userId" to uid, "chatId" to msg.payload))))
                        }

                        WsTypes.PING -> send(Frame.Text(json.encodeToString(WsMessage(WsTypes.PONG))))
                    }
                }
            } catch (e: Exception) {
                log("⚠️ WS error from $ip: ${e.message}", "ERROR")
            } finally {
                authedUserId?.let { uid ->
                    connections.remove(uid)
                    val user = db.userDao().getById(uid)
                    if (user != null) {
                        connections.broadcast(WsMessage(WsTypes.USER_OFFLINE, json.encodeToString(user.toDto(false))))
                        log("🔌 WS disconnected: @${user.username} from $ip")
                    }
                }
            }
        }
    }

    // ─── Bot ──────────────────────────────────────────────────────────────────
    suspend fun sendBotMessage(targetUserId: String, text: String) {
        var botChat = db.chatDao().getBotChat(targetUserId)
        if (botChat == null) {
            botChat = createBotChat(targetUserId)
        }
        val msgEntity = MessageEntity(
            id = UUID.randomUUID().toString(), chatId = botChat.id,
            senderId = "BOT", senderName = "VerySchool BOT", text = text
        )
        db.messageDao().upsert(msgEntity)
        connections.sendTo(targetUserId, WsMessage(WsTypes.NEW_MESSAGE, json.encodeToString(msgEntity.toDto())))
    }

    private suspend fun createBotChat(userId: String): ChatEntity {
        val existing = db.chatDao().getBotChat(userId)
        if (existing != null) return existing
        val botChat = ChatEntity(
            id = "bot_$userId", name = "VerySchool BOT",
            isGroup = false, members = json.encodeToString(listOf(userId, "BOT")),
            createdBy = "BOT", isBot = true
        )
        db.chatDao().upsert(botChat)
        return botChat
    }

    private suspend fun DefaultWebSocketSession.sendError(msg: String) {
        send(Frame.Text(Json.encodeToString(WsMessage(WsTypes.ERROR, msg))))
    }

    private fun generateUserId(): String {
        var id: String
        do { id = (100000..999999).random().toString() }
        while (runBlocking { db.userDao().getById(id) } != null)
        return id
    }

    private fun generateToken() = UUID.randomUUID().toString().replace("-", "")

    private suspend fun ApplicationCall.token(): String? {
        val auth = request.headers[HttpHeaders.Authorization] ?: return null
        return auth.removePrefix("Bearer ").trim()
    }

    private suspend fun ApplicationCall.authedUserId(): String? {
        val token = token() ?: return null
        return db.tokenDao().get(token)?.userId
    }

    fun UserEntity.toDto(online: Boolean = false) = UserDto(id, username, displayName, avatarBase64, online, isBanned, dmBlocked)
    fun ChatEntity.toDto() = ChatDto(id, name, isGroup, try { json.decodeFromString(members) } catch (_: Exception) { emptyList() }, avatarBase64, isBot = isBot)
    fun MessageEntity.toDto() = MessageDto(id, chatId, senderId, senderName, text, timestamp, try { json.decodeFromString(reactions) } catch (_: Exception) { emptyMap() }, imageBase64)
}
