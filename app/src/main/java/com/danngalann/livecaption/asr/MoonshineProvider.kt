package com.danngalann.livecaption.asr

import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelSpec
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.Executors

class MoonshineProvider(context: Context) : SpeechRecognitionProvider {
    override val id = ProviderId.MOONSHINE

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val pending = ArrayDeque<ByteArray>()
    private var transcriber: Transcriber? = null
    private var listener: ProviderEventListener? = null
    private var started = false
    private var loading = false
    private var loadError: Throwable? = null

    override fun prepare() {
        synchronized(this) {
            if (transcriber != null || loading) return
            loading = true
        }
        executor.execute {
            runCatching {
                val arch = JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
                val spec = ModelSpec.stt("es", arch, false)
                val directory = File(appContext.filesDir, "moonshine/es-small-streaming")
                AssetDownloader().ensureModelPresent(directory, spec, null)
                Transcriber().apply {
                    setUpdateInterval(0.5)
                    addListener(::onMoonshineEvent)
                    loadFromFiles(directory.absolutePath, arch)
                }
            }.onSuccess { model ->
                synchronized(this) {
                    transcriber = model
                    loading = false
                    if (started) {
                        model.start()
                        while (pending.isNotEmpty()) {
                            model.addAudio(toFloatPcm(pending.removeFirst()), 16_000)
                        }
                        listener?.onEvent(
                            TranscriptionEvent.State(
                                state = ProviderConnectionState.READY,
                                model = "Moonshine Spanish Small Streaming",
                                runtime = "Moonshine Voice on-device"
                            )
                        )
                    }
                }
            }.onFailure { error ->
                synchronized(this) {
                    loadError = error
                    loading = false
                    listener?.onEvent(
                        TranscriptionEvent.Error(
                            "Moonshine model unavailable: ${error.message}",
                            false
                        )
                    )
                }
            }
        }
    }

    override fun start(listener: ProviderEventListener) {
        synchronized(this) {
            this.listener = listener
            started = true
            val model = transcriber
            when {
                model != null -> {
                    model.start()
                    listener.onEvent(
                        TranscriptionEvent.State(
                            state = ProviderConnectionState.READY,
                            model = "Moonshine Spanish Small Streaming",
                            runtime = "Moonshine Voice on-device"
                        )
                    )
                }
                loadError != null -> listener.onEvent(
                    TranscriptionEvent.Error(
                        "Moonshine model unavailable: ${loadError?.message}",
                        false
                    )
                )
                else -> {
                    listener.onEvent(
                        TranscriptionEvent.State(ProviderConnectionState.PREPARING)
                    )
                    prepare()
                }
            }
        }
    }

    override fun sendAudio(pcm: ByteArray) {
        synchronized(this) {
            val model = transcriber
            if (!started || model == null) {
                pending.addLast(pcm.copyOf())
                return
            }
            executor.execute {
                runCatching { model.addAudio(toFloatPcm(pcm), 16_000) }
                    .onFailure {
                        listener?.onEvent(
                            TranscriptionEvent.Error(
                                it.message ?: "Moonshine inference failed",
                                false
                            )
                        )
                    }
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            started = false
            pending.clear()
            val model = transcriber
            if (model != null) {
                executor.execute { runCatching(model::stop) }
            }
            listener = null
        }
    }

    override fun checkAvailability(callback: (Boolean, Long?) -> Unit) {
        callback(transcriber != null, null)
    }

    private fun onMoonshineEvent(event: TranscriptEvent) {
        when (event) {
            is TranscriptEvent.LineTextChanged -> listener?.onEvent(
                TranscriptionEvent.Partial(event.line.text.orEmpty())
            )
            is TranscriptEvent.LineCompleted -> listener?.onEvent(
                TranscriptionEvent.Final(event.line.text.orEmpty())
            )
            is TranscriptEvent.Error -> listener?.onEvent(
                TranscriptionEvent.Error(event.cause.message ?: "Moonshine error", false)
            )
            else -> Unit
        }
    }

    private fun toFloatPcm(pcm: ByteArray): FloatArray {
        val samples = FloatArray(pcm.size / 2)
        val bytes = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        for (index in samples.indices) {
            samples[index] = bytes.short / 32768f
        }
        return samples
    }
}
