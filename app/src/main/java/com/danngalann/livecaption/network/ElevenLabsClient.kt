package com.danngalann.livecaption.network

import android.util.Base64
import com.danngalann.livecaption.BuildConfig
import com.danngalann.livecaption.asr.ProviderConnectionState
import com.danngalann.livecaption.asr.ProviderEventListener
import com.danngalann.livecaption.asr.ProviderId
import com.danngalann.livecaption.asr.SpeechRecognitionProvider
import com.danngalann.livecaption.asr.TranscriptionEvent
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class ElevenLabsClient(
    private val apiKeyProvider: () -> String = { BuildConfig.ELEVENLABS_API_KEY }
) : SpeechRecognitionProvider {
    override val id = ProviderId.ELEVENLABS
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val pending = ArrayDeque<ByteArray>()
    private var socket: WebSocket? = null
    private var listener: ProviderEventListener? = null
    private var ready = false

    override fun prepare() = Unit

    override fun start(listener: ProviderEventListener) {
        stop()
        this.listener = listener
        listener.onEvent(TranscriptionEvent.State(ProviderConnectionState.CONNECTING))
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            listener.onEvent(TranscriptionEvent.Error("ElevenLabs API key is not configured", true))
            return
        }
        val request = Request.Builder()
            .url("wss://api.elevenlabs.io/v1/speech-to-text/realtime?model_id=scribe_v2_realtime&language_code=es&commit_strategy=vad&vad_silence_threshold_secs=1.5&vad_threshold=0.4&min_speech_duration_ms=100&min_silence_duration_ms=100")
            .addHeader("xi-api-key", apiKey)
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                val json = JSONObject(text)
                val messageType = json.optString("message_type")

                when (messageType) {
                    "session_started" -> {
                        synchronized(pending) {
                            ready = true
                            while (pending.isNotEmpty()) sendAudio(pending.removeFirst())
                        }
                        listener.onEvent(
                            TranscriptionEvent.State(ProviderConnectionState.READY)
                        )
                    }
                    "partial_transcript" -> {
                        listener.onEvent(
                            TranscriptionEvent.Partial(json.optString("text", ""))
                        )
                    }
                    "committed_transcript" -> {
                        listener.onEvent(
                            TranscriptionEvent.Final(json.optString("text", ""))
                        )
                    }
                    "committed_transcript_with_timestamps" -> {
                        listener.onEvent(
                            TranscriptionEvent.Final(json.optString("text", ""))
                        )
                    }
                    "input_error" -> {
                        val error = json.optString("error", "Unknown error")
                        listener.onEvent(TranscriptionEvent.Error(error, true))
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                listener.onEvent(
                    TranscriptionEvent.Error(t.message ?: "ElevenLabs connection failed", true)
                )
            }
        })
    }

    override fun sendAudio(pcm: ByteArray) {
        synchronized(pending) {
            if (!ready) {
                pending.addLast(pcm.copyOf())
                return
            }
        }
        val payload = JSONObject()
            .put("message_type", "input_audio_chunk")
            .put("audio_base_64", Base64.encodeToString(pcm, Base64.NO_WRAP))
            .put("sample_rate", 16000)
            .put("language_code", "es")

        socket?.send(payload.toString())
    }

    override fun stop() {
        synchronized(pending) {
            ready = false
            pending.clear()
        }
        socket?.close(1000, null)
        socket = null
        listener = null
    }

    override fun checkAvailability(callback: (Boolean, Long?) -> Unit) {
        callback(apiKeyProvider().isNotBlank(), null)
    }
}
