package com.danngalann.livecaption.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SoundEvent(val label: String)

interface SoundEventClassifier {
    val events: StateFlow<SoundEvent?>
    val error: StateFlow<String?>
    fun start()
    fun onAudio(pcm: ByteArray)
    fun stop()
    fun close()
}

class MediaPipeSoundClassifier(private val context: Context) : SoundEventClassifier {
    internal val classificationsCompleted = AtomicInteger()
    private val executor = Executors.newSingleThreadExecutor()
    private val _events = MutableStateFlow<SoundEvent?>(null)
    override val events = _events.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error = _error.asStateFlow()
    private val samples = ByteArray(WINDOW_BYTES)
    private var filled = 0
    private var running = false
    private var queued = false
    private var generation = 0L
    private var classifier: AudioClassifier? = null
    private var previousLabel: String? = null
    private var consecutive = 0
    private var lastDetectedAt = 0L

    @Synchronized
    override fun start() {
        if (running) return
        running = true
        filled = 0
        queued = false
        previousLabel = null
        consecutive = 0
        classificationsCompleted.set(0)
        _events.value = null
        _error.value = null
        val session = ++generation
        executor.execute {
            try {
                val options = AudioClassifier.AudioClassifierOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                    .setMaxResults(3)
                    .setScoreThreshold(0.3f)
                    .setCategoryAllowlist(LABELS.keys.toList())
                    .build()
                val created = AudioClassifier.createFromOptions(context, options)
                synchronized(this) {
                    if (running && session == generation) classifier = created else created.close()
                }
            } catch (e: RuntimeException) {
                reportError(session, e)
            }
        }
    }

    @Synchronized
    override fun onAudio(pcm: ByteArray) {
        if (!running || _error.value != null) return
        var offset = 0
        while (offset < pcm.size) {
            val count = minOf(WINDOW_BYTES - filled, pcm.size - offset)
            System.arraycopy(pcm, offset, samples, filled, count)
            filled += count
            offset += count
            if (filled == WINDOW_BYTES) {
                if (!queued) {
                    queued = true
                    val window = samples.copyOf()
                    val session = generation
                    executor.execute { classify(window, session) }
                }
                System.arraycopy(samples, HOP_BYTES, samples, 0, WINDOW_BYTES - HOP_BYTES)
                filled = WINDOW_BYTES - HOP_BYTES
            }
        }
    }

    private fun classify(pcm: ByteArray, session: Long) {
        try {
            val task = synchronized(this) {
                if (running && session == generation) classifier else null
            } ?: return
            val data = AudioData.create(
                AudioData.AudioDataFormat.builder()
                    .setNumOfChannels(1)
                    .setSampleRate(16_000f)
                    .build(),
                pcm.size / 2
            )
            val samples = ShortArray(pcm.size / 2) { i ->
                ((pcm[2 * i].toInt() and 0xff) or (pcm[2 * i + 1].toInt() shl 8)).toShort()
            }
            data.load(samples)
            val label = task.classify(data).classificationResults()
                .flatMap { it.classifications() }
                .flatMap { it.categories() }
                .maxByOrNull { it.score() }
                ?.categoryName()
                ?.let(LABELS::get)
            classificationsCompleted.incrementAndGet()
            synchronized(this) {
                if (!running || session != generation) return
                val now = SystemClock.elapsedRealtime()
                if (label != null) {
                    consecutive = if (label == previousLabel) consecutive + 1 else 1
                    previousLabel = label
                    if (consecutive >= 2) {
                        lastDetectedAt = now
                        _events.value = SoundEvent(label)
                    }
                } else {
                    previousLabel = null
                    consecutive = 0
                }
                if (now - lastDetectedAt >= 2_500) _events.value = null
            }
        } catch (e: RuntimeException) {
            reportError(session, e)
        } finally {
            synchronized(this) { if (session == generation) queued = false }
        }
    }

    private fun reportError(session: Long, error: RuntimeException) {
        Log.e("LiveCaptionSound", "Sound classification failed", error)
        synchronized(this) {
            if (session == generation) {
                _error.value = "Indicadores de sonido no disponibles"
                _events.value = null
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!running) return
        running = false
        generation++
        queued = false
        filled = 0
        _events.value = null
        _error.value = null
        executor.execute {
            synchronized(this) {
                classifier?.close()
                classifier = null
            }
        }
    }

    override fun close() {
        stop()
        executor.shutdown()
    }

    private companion object {
        const val WINDOW_BYTES = 31_200
        const val HOP_BYTES = 16_000
        val LABELS = mapOf(
            "Laughter" to "Risas",
            "Baby laughter" to "Risas",
            "Giggle" to "Risas",
            "Wind" to "Viento",
            "Wind noise (microphone)" to "Viento",
            "Music" to "Música",
            "Bark" to "Ladridos",
            "Doorbell" to "Timbre",
            "Siren" to "Sirena",
            "Rain" to "Lluvia",
            "Applause" to "Aplausos",
            "Knock" to "Llaman a la puerta"
        )
    }
}
