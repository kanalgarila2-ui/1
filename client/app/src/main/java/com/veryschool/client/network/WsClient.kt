package com.veryschool.client.network

import android.util.Log
import com.veryschool.client.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val TAG = "WsClient"

/**
 * Простой WS клиент. connect() блокирует корутину пока соединение живо.
 * Все flows живут снаружи — в AppRepository.
 */
class WsClient {

    private val httpClient = HttpClient(Android) {
        install(WebSockets)
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // Очередь исходящих сообщений
    private val outgoing = Channel<String>(Channel.UNLIMITED)

    private var _session: DefaultWebSocketSession? = null

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

        Log.i(TAG, "→ Connecting to $fullUrl")

        try {
            httpClient.webSocket(fullUrl) {
                _session = this
                onConnected()
                Log.i(TAG, "✓ Connected. Sending AUTH token...")
                send(Frame.Text(Json.encodeToString(WsMessage(WsTypes.AUTH, token))))

                // Запускаем отправку исходящих сообщений
                val sendJob = launch {
                    for (text in outgoing) {
                        try {
                            Log.d(TAG, "→ Sending: ${text.take(80)}")
                            send(Frame.Text(text))
                        } catch (e: Exception) {
                            Log.e(TAG, "Send error: ${e.message}")
                        }
                    }
                }

                // Читаем входящие
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val raw = frame.readText()
                                Log.d(TAG, "← raw: ${raw.take(120)}")
                                try {
                                    val msg = Json { ignoreUnknownKeys = true }
                                        .decodeFromString<WsMessage>(raw)
                                    Log.i(TAG, "← MSG type=${msg.type} payloadLen=${msg.payload.length}")
                                    onMessage(msg)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Parse error: ${e.message} | raw=$raw")
                                }
                            }
                            is Frame.Close -> {
                                Log.w(TAG, "← Close frame: ${(frame as? Frame.Close)?.readReason()}")
                            }
                            is Frame.Ping -> {
                                Log.d(TAG, "← Ping, sending Pong")
                                send(Frame.Pong(frame.data))
                            }
                            else -> {}
                        }
                    }
                } finally {
                    sendJob.cancel()
                }
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "WS coroutine cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "WS error: ${e::class.simpleName}: ${e.message}")
        } finally {
            _session = null
            onDisconnected()
            Log.i(TAG, "✗ Disconnected")
        }
    }

    fun send(message: WsMessage) {
        val text = Json.encodeToString(message)
        val result = outgoing.trySend(text)
        if (result.isFailure) {
            Log.e(TAG, "Send queue full! Dropped: ${message.type}")
        }
    }

    fun close() {
        outgoing.close()
        _session = null
    }
}

class ApiClient(private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun login(req: AuthRequest): AuthResponse = try {
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json); setBody(req)
        }.body()
    } catch (e: Exception) { AuthResponse(false, error = "Ошибка сети: ${e.message}") }

    suspend fun register(req: RegisterRequest): AuthResponse = try {
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json); setBody(req)
        }.body()
    } catch (e: Exception) { AuthResponse(false, error = "Ошибка сети: ${e.message}") }

    suspend fun checkUsername(username: String): Boolean = try {
        client.get("$baseUrl/auth/check/$username").body<CheckUsernameResponse>().exists
    } catch (_: Exception) { false }

    suspend fun updateProfile(token: String, req: UpdateProfileRequest): Boolean = try {
        client.put("$baseUrl/user/profile") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(req)
        }.status.value == 200
    } catch (_: Exception) { false }

    suspend fun changePassword(token: String, req: ChangePasswordRequest): Boolean = try {
        val res: Map<String, Boolean> = client.post("$baseUrl/user/password") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(req)
        }.body()
        res["success"] == true
    } catch (_: Exception) { false }
}
