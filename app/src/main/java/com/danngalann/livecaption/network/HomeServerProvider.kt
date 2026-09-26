package com.danngalann.livecaption.network

import android.os.SystemClock
import com.danngalann.livecaption.asr.ProviderConnectionState
import com.danngalann.livecaption.asr.ProviderEventListener
import com.danngalann.livecaption.asr.ProviderId
import com.danngalann.livecaption.asr.SpeechRecognitionProvider
import com.danngalann.livecaption.asr.TranscriptionEvent
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

class HomeServerProvider(
    private val endpointProvider: () -> String,
    private val tokenProvider: () -> String
) : SpeechRecognitionProvider {
    override val id = ProviderId.HOME_SERVER

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val pending = ArrayDeque<ByteArray>()
    private var socket: WebSocket? = null
    private var listener: ProviderEventListener? = null
    private var ready = false
    private var model: String? = null
    private var runtime: String? = null
    private var connectionStartedAtMs = 0L

    override fun prepare() = Unit

    override fun start(listener: ProviderEventListener) {
        stop()
        this.listener = listener
        connectionStartedAtMs = SystemClock.elapsedRealtime()
        listener.onEvent(TranscriptionEvent.State(ProviderConnectionState.CONNECTING))
        val url = websocketUrl(endpointProvider())
        if (url == null) {
            listener.onEvent(TranscriptionEvent.Error("Home server URL is not configured", true))
            return
        }
        val request = Request.Builder().url(url).apply {
            tokenProvider().takeIf(String::isNotBlank)?.let {
                addHeader("Authorization", "Bearer $it")
            }
        }.build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "server.ready" -> {
                        if (!json.optBoolean("asr_available", false)) {
                            listener.onEvent(TranscriptionEvent.Error("Home ASR is unavailable", true))
                            return
                        }
                        model = json.optString("model").takeIf(String::isNotBlank)
                        runtime = json.optString("runtime").takeIf(String::isNotBlank)
                        webSocket.send(
                            JSONObject()
                                .put("type", "session.init")
                                .put("protocol_version", 1)
                                .put("session_id", UUID.randomUUID().toString())
                                .put("sample_rate", 16000)
                                .put("channels", 1)
                                .put("encoding", "pcm_s16le")
                                .put("language", "es")
                                .toString()
                        )
                    }
                    "session.started" -> {
                        synchronized(pending) {
                            ready = true
                            while (pending.isNotEmpty()) {
                                webSocket.send(ByteString.of(*pending.removeFirst()))
                            }
                        }
                        listener.onEvent(
                            TranscriptionEvent.State(
                                state = ProviderConnectionState.READY,
                                model = model,
                                runtime = runtime,
                                serverLatencyMs =
                                    SystemClock.elapsedRealtime() - connectionStartedAtMs
                            )
                        )
                    }
                    "transcript.partial" -> listener.onEvent(
                        TranscriptionEvent.Partial(
                            text = json.optString("text"),
                            audioPositionMs = json.optLongOrNull("audio_position_ms"),
                            latencyMs = json.optLongOrNull("partial_update_interval_ms"),
                            backlogMs = json.optLongOrNull("backlog_ms")
                        )
                    )
                    "transcript.final" -> listener.onEvent(
                        TranscriptionEvent.Final(
                            text = json.optString("text"),
                            audioPositionMs = json.optLongOrNull("audio_position_ms"),
                            backlogMs = json.optLongOrNull("backlog_ms")
                        )
                    )
                    "error" -> listener.onEvent(
                        TranscriptionEvent.Error(
                            json.optString("message", "Home server error"),
                            json.optBoolean("retryable", true)
                        )
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onEvent(
                    TranscriptionEvent.Error(t.message ?: "Home server connection failed", true)
                )
            }
        })
    }

    override fun sendAudio(pcm: ByteArray) {
        synchronized(pending) {
            if (!ready) {
                pending.addLast(pcm.copyOf())
            } else {
                socket?.send(ByteString.of(*pcm))
            }
        }
    }

    override fun stop() {
        synchronized(pending) {
            ready = false
            pending.clear()
        }
        socket?.close(1000, null)
        socket = null
        listener = null
        model = null
        runtime = null
    }

    override fun checkAvailability(callback: (Boolean, Long?) -> Unit) {
        val baseUrl = httpUrl(endpointProvider()) ?: run {
            callback(false, null)
            return
        }
        val request = Request.Builder().url("$baseUrl/health").apply {
            tokenProvider().takeIf(String::isNotBlank)?.let {
                addHeader("Authorization", "Bearer $it")
            }
        }.build()
        val started = SystemClock.elapsedRealtime()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    val healthy = it.isSuccessful &&
                        runCatching { JSONObject(body).optBoolean("asr_available") }.getOrDefault(false)
                    callback(healthy, SystemClock.elapsedRealtime() - started)
                }
            }
        })
    }

    private fun websocketUrl(value: String): String? {
        if (value.isBlank()) return null
        val base = value.trimEnd('/')
            .replace(Regex("^http://"), "ws://")
            .replace(Regex("^https://"), "wss://")
        return if (base.startsWith("ws://") || base.startsWith("wss://")) {
            "$base/v1/transcribe"
        } else {
            null
        }
    }

    private fun httpUrl(value: String): String? {
        if (value.isBlank()) return null
        val base = value.trimEnd('/')
            .replace(Regex("^ws://"), "http://")
            .replace(Regex("^wss://"), "https://")
        return if (base.startsWith("http://") || base.startsWith("https://")) base else null
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
