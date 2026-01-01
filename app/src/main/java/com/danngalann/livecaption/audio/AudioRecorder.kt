package com.danngalann.livecaption.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresPermission

class AudioRecorder(private val context: Context) {

    private lateinit var record: AudioRecord
    private var recording = false
    private var noiseSuppressor: NoiseSuppressor? = null

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start(onAudio: (ByteArray) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        record.startRecording()

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)
            noiseSuppressor?.enabled = true
        }

        recording = true

        Thread {
            val buffer = ByteArray(bufferSize)
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) onAudio(buffer.copyOf(read))
            }
        }.start()
    }

    fun stop() {
        recording = false
        noiseSuppressor?.release()
        noiseSuppressor = null
        record.stop()
        record.release()
    }
}
