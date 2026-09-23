package com.danngalann.livecaption.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresPermission

class AudioRecorder(private val context: Context) : AudioSource {

    private var record: AudioRecord? = null
    @Volatile
    private var recording = false
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingThread: Thread? = null

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override fun start(onAudio: (ByteArray) -> Unit) {
        if (recording) return
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

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        record = audioRecord

        audioRecord.startRecording()

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)
            noiseSuppressor?.enabled = true
        }

        recording = true

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (recording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) onAudio(buffer.copyOf(read))
            }
        }.also {
            it.name = "livecaption-audio"
            it.start()
        }
    }

    override fun stop() {
        if (!recording) return
        recording = false
        noiseSuppressor?.release()
        noiseSuppressor = null
        record?.stop()
        recordingThread?.join(1_000)
        recordingThread = null
        record?.release()
        record = null
    }
}
