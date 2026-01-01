package com.danngalann.livecaption.network

import android.util.Base64
import androidx.annotation.RequiresPermission
import com.danngalann.livecaption.BuildConfig
import com.danngalann.livecaption.audio.AudioRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class ElevenLabsClient(
    private val audioRecorder: AudioRecorder
) {
    private lateinit var socket: WebSocket

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun connect(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url("wss://api.elevenlabs.io/v1/speech-to-text/realtime?model_id=scribe_v2_realtime&language_code=es&commit_strategy=vad&vad_silence_threshold_secs=1.5&vad_threshold=0.4&min_speech_duration_ms=100&min_silence_duration_ms=100")
            .addHeader("xi-api-key", BuildConfig.ELEVENLABS_API_KEY)
            .build()

        val client = OkHttpClient()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                val json = JSONObject(text)
                val messageType = json.optString("message_type")

                when (messageType) {
                    "session_started" -> {
                        // Connection established, ready to send audio
                    }
                    "partial_transcript" -> {
                        val transcriptText = json.optString("text", "")
                        onPartial(transcriptText)
                    }
                    "committed_transcript" -> {
                        val transcriptText = json.optString("text", "")
                        onFinal(transcriptText)
                    }
                    "committed_transcript_with_timestamps" -> {
                        // If timestamps are needed, can be extracted here
                        val transcriptText = json.optString("text", "")
                        onFinal(transcriptText)
                    }
                    "input_error" -> {
                        val error = json.optString("error", "Unknown error")
                        // Log error or handle it
                        android.util.Log.e("ElevenLabsClient", "Input error: $error")
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                android.util.Log.e("ElevenLabsClient", "WebSocket failure", t)
            }
        })

        audioRecorder.start { pcm ->
            sendAudio(pcm)
        }
    }

    fun sendAudio(pcm: ByteArray) {
        val payload = JSONObject()
            .put("message_type", "input_audio_chunk")
            .put("audio_base_64", Base64.encodeToString(pcm, Base64.NO_WRAP))
            .put("sample_rate", 16000)
            .put("language_code", "es")

        socket.send(payload.toString())
    }

    fun stop() {
        audioRecorder.stop()
        socket.close(1000, null)
    }
}
