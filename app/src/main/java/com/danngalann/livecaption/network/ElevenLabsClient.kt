package com.danngalann.livecaption.network

import android.util.Base64
import androidx.annotation.RequiresPermission
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
            .url("wss://api.elevenlabs.io/v1/speech-to-text/realtime?model_id=scribe_v2_realtime")
            .addHeader("xi-api-key", BuildConfig.ELEVENLABS_API_KEY)
            .build()

        val client = OkHttpClient()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                // parse JSON
                // route to onPartial / onFinal
            }
        })

        audioRecorder.start { pcm ->
            sendAudio(pcm)
        }
    }

    fun sendAudio(pcm: ByteArray) {
        val payload = JSONObject()
            .put("audio_base_64", Base64.encodeToString(pcm, Base64.NO_WRAP))
            .put("sample_rate", 16000)

        socket.send(payload.toString())
    }

    fun stop() {
        audioRecorder.stop()
        socket.close(1000, null)
    }
}
