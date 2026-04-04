package com.veryschool.client.network

import android.util.Log
import com.veryschool.client.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit

private const val TAG = "WsClient"

/**
 * WsClient на OkHttp — без конфликтов Ktor scope.
 * connect() блокирует suspend функцию пока соединение живо.
 */
class WsClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val okClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // бесконечный таймаут для WS
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var ws: WebSocket? = null

    suspend fun connect(
        serverUrl: String,
        token: String,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onMessage: suspend (WsMessage) -> Unit
    ) {
        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val fullUrl = if (wsUrl.endsWith("/")) "${wsUrl}ws" else "$wsUrl/ws"

        Log.i(TAG, "Connecting to $fullUrl")

        // CompletableDeferred — разблокируется когда соединение закрывается
        val done = CompletableDeferred<Unit>()

        val request = Request.Builder().url(fullUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ws = webSocket
                Log.i(TAG, "Connected! Sending AUTH...")
                val authMsg = json.encodeToString(WsMessage(WsTypes.AUTH, token))
                webSocket.send(authMsg)
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "< raw: ${text.substring(0, minOf(120, text.length))}")
                try {
                    val msg = json.decodeFromString<WsMessage>(text)
                    Log.i(TAG, "< type=${msg.type} len=${msg.payload.length}")
                    // Запускаем suspend обработчик в IO
                    CoroutineScope(Dispatchers.IO).launch {
                        try { onMessage(msg) }
                        catch (e: Exception) { Log.e(TAG, "onMessage handler error: ${e.message}") }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Closed: $code $reason")
                ws = null
                onDisconnected()
                done.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure: ${t.message}")
                ws = null
                onDisconnected()
                done.complete(Unit)
            }
        }

        okClient.newWebSocket(request, listener)

        // Ждём пока соединение закроется (блокируем корутину)
        try {
            done.await()
        } catch (e: CancellationException) {
            Log.i(TAG, "connect cancelled — closing WS")
            ws?.close(1000, "cancelled")
            ws = null
            throw e
        }
    }

    fun send(message: WsMessage) {
        val text = json.encodeToString(message)
        val socket = ws
        if (socket != null) {
            val ok = socket.send(text)
            if (!ok) Log.e(TAG, "send() failed — WS closed? msg=${message.type}")
            else Log.d(TAG, "> sent: ${message.type}")
        } else {
            Log.w(TAG, "send() skipped — no active connection. msg=${message.type}")
        }
    }

    fun close() {
        ws?.close(1000, "logout")
        ws = null
    }
}

class ApiClient(private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun login(req: AuthRequest): AuthResponse = try {
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    } catch (e: Exception) { AuthResponse(false, error = "Ошибка сети: ${e.message}") }

    suspend fun register(req: RegisterRequest): AuthResponse = try {
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    } catch (e: Exception) { AuthResponse(false, error = "Ошибка сети: ${e.message}") }

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
}
