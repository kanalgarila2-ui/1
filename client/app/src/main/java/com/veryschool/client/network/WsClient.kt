package com.veryschool.client.network

import android.util.Log
import com.veryschool.client.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class WsClient {
    private val TAG = "WsClient"
    private val httpClient = HttpClient(Android) {
        install(WebSockets)
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(Logging) { level = LogLevel.NONE }
    }
    private var session: DefaultWebSocketSession? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _incomingMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<WsMessage> = _incomingMessages.asSharedFlow()

    suspend fun connect(serverUrl: String, token: String): Boolean {
        return try {
            val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
            val fullUrl = if (wsUrl.endsWith("/")) "${wsUrl}ws" else "$wsUrl/ws"
            Log.d(TAG, "Connecting to $fullUrl")
            httpClient.webSocket(fullUrl) {
                session = this
                _connected.value = true
                Log.d(TAG, "Connected!")
                send(Frame.Text(Json.encodeToString(WsMessage(WsTypes.AUTH, token))))
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    try {
                        val msg = Json { ignoreUnknownKeys = true }.decodeFromString<WsMessage>(frame.readText())
                        _incomingMessages.emit(msg)
                    } catch (e: Exception) { Log.e(TAG, "Parse: ${e.message}") }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            _connected.value = false
            false
        }
    }

    suspend fun send(message: WsMessage) {
        try { session?.send(Frame.Text(Json.encodeToString(message))) }
        catch (e: Exception) { Log.e(TAG, "Send: ${e.message}") }
    }

    fun disconnect() { _connected.value = false; session = null }
}

class ApiClient(private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun login(req: AuthRequest): AuthResponse = try {
        client.post("$baseUrl/auth/login") { contentType(ContentType.Application.Json); setBody(req) }.body()
    } catch (e: Exception) { AuthResponse(false, error = e.message ?: "Ошибка сети") }

    suspend fun register(req: RegisterRequest): AuthResponse = try {
        client.post("$baseUrl/auth/register") { contentType(ContentType.Application.Json); setBody(req) }.body()
    } catch (e: Exception) { AuthResponse(false, error = e.message ?: "Ошибка сети") }

    suspend fun checkUsername(username: String): Boolean = try {
        client.get("$baseUrl/auth/check/$username").body<CheckUsernameResponse>().exists
    } catch (_: Exception) { false }

    suspend fun updateProfile(token: String, req: UpdateProfileRequest): Boolean = try {
        client.put("$baseUrl/user/profile") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(req)
        }.status.value == 200
    } catch (_: Exception) { false }

    suspend fun changePassword(token: String, req: ChangePasswordRequest): Boolean = try {
        val res: Map<String, Boolean> = client.post("$baseUrl/user/password") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
        res["success"] == true
    } catch (_: Exception) { false }

    suspend fun getUsers(token: String): List<UserDto> = try {
        client.get("$baseUrl/users") { header(HttpHeaders.Authorization, "Bearer $token") }.body()
    } catch (_: Exception) { emptyList() }

    suspend fun ping(url: String): Boolean = try {
        client.get("$url/ping").status.isSuccess()
    } catch (_: Exception) { false }
}
