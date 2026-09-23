package com.danngalann.livecaption.asr

import com.danngalann.livecaption.audio.AudioSource
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HybridAsrManager(
    private val audioRecorder: AudioSource,
    providers: List<SpeechRecognitionProvider>,
    private val developmentController: DevelopmentController,
    private val config: AsrConfig = AsrConfig()
) {
    private val providers = providers.associateBy { it.id }
    private val buffer = RollingAudioBuffer(config.rollingBufferMs)
    private val reconciler = TranscriptReconciler()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val _diagnostics = MutableStateFlow(AsrDiagnostics())
    val diagnostics: StateFlow<AsrDiagnostics> = _diagnostics.asStateFlow()

    private var listener: ProviderEventListener? = null
    private var active: SpeechRecognitionProvider? = null
    private var monitor: ScheduledFuture<*>? = null
    private var recording = false
    private var lastCommittedAudioMs = 0L
    private var committedText = ""
    private var lastProviderResponseAtMs = 0L
    private var slowResultCount = 0
    private var homeHealthySinceMs: Long? = null
    private var lastHomeProbeAtMs = 0L
    private var hasUncommittedSpeech = false
    private fun nowMs(): Long = System.nanoTime() / 1_000_000

    init {
        providers.forEach(SpeechRecognitionProvider::prepare)
    }

    @Synchronized
    fun start(listener: ProviderEventListener) {
        if (recording) return
        this.listener = listener
        recording = true
        buffer.clear()
        lastCommittedAudioMs = 0
        committedText = ""
        hasUncommittedSpeech = false
        developmentController.onSessionStarted()
        switchTo(preferredProvider(), replay = false, countFailover = false)
        audioRecorder.start(::onAudio)
        monitor = scheduler.scheduleAtFixedRate(::monitorHealth, 1, 1, TimeUnit.SECONDS)
    }

    @Synchronized
    fun stop() {
        if (!recording) return
        recording = false
        monitor?.cancel(false)
        monitor = null
        audioRecorder.stop()
        active?.stop()
        active = null
        listener = null
        _diagnostics.update {
            it.copy(activeProvider = null, connectionState = ProviderConnectionState.STOPPED)
        }
    }

    @Synchronized
    fun setMode(mode: ProviderMode) {
        _diagnostics.update { it.copy(configuredMode = mode) }
        if (recording) {
            switchTo(preferredProvider(), replay = true, countFailover = false)
        }
    }

    private fun onAudio(pcm: ByteArray) {
        synchronized(this) {
            if (!recording) return
            buffer.append(pcm)
            val provider = active ?: return
            if (developmentController.shouldFail(provider.id, pcm.size)) {
                handleProviderFailure(provider.id, "Injected provider failure")
                return
            }
            provider.sendAudio(pcm)
        }
    }

    private fun preferredProvider(): ProviderId =
        when (_diagnostics.value.configuredMode) {
            ProviderMode.AUTO, ProviderMode.HOME_SERVER -> ProviderId.HOME_SERVER
            ProviderMode.ELEVENLABS -> ProviderId.ELEVENLABS
            ProviderMode.MOONSHINE -> ProviderId.MOONSHINE
        }

    @Synchronized
    private fun switchTo(id: ProviderId, replay: Boolean, countFailover: Boolean) {
        if (!recording || active?.id == id) return
        active?.stop()
        val provider = providers.getValue(id)
        active = provider
        lastProviderResponseAtMs = nowMs()
        slowResultCount = 0
        _diagnostics.update {
            it.copy(
                activeProvider = id,
                connectionState = ProviderConnectionState.CONNECTING,
                failoverCount = it.failoverCount + if (countFailover) 1 else 0,
                lastProviderTransitionAtMs = nowMs()
            )
        }
        provider.start(ProviderEventListener { event -> onProviderEvent(id, event) })
        if (replay) {
            val replayFrom = (lastCommittedAudioMs - config.replayPreRollMs).coerceAtLeast(0)
            buffer.from(replayFrom).forEach(provider::sendAudio)
        }
    }

    private fun onProviderEvent(providerId: ProviderId, event: TranscriptionEvent) {
        synchronized(this) {
            if (!recording || active?.id != providerId) return
            lastProviderResponseAtMs = nowMs()
            when (event) {
                is TranscriptionEvent.Partial -> {
                    updatePerformance(event.latencyMs, event.backlogMs)
                    val text = reconciler.removeCommittedOverlap(committedText, event.text)
                    hasUncommittedSpeech = text.isNotBlank()
                    listener?.onEvent(event.copy(text = text))
                }
                is TranscriptionEvent.Final -> {
                    updatePerformance(event.latencyMs, event.backlogMs)
                    val text = reconciler.removeCommittedOverlap(committedText, event.text)
                    lastCommittedAudioMs = event.audioPositionMs ?: buffer.audioPositionMs
                    hasUncommittedSpeech = false
                    if (text.isNotBlank()) {
                        committedText = listOf(committedText, text)
                            .filter(String::isNotBlank)
                            .joinToString(" ")
                        listener?.onEvent(event.copy(text = text))
                    } else {
                        listener?.onEvent(TranscriptionEvent.Partial(""))
                    }
                }
                is TranscriptionEvent.State -> {
                    _diagnostics.update { it.copy(connectionState = event.state) }
                    listener?.onEvent(event)
                    if (event.state == ProviderConnectionState.FAILED) {
                        handleProviderFailure(providerId, "Provider entered failed state")
                    }
                }
                is TranscriptionEvent.Error -> {
                    _diagnostics.update { it.copy(lastError = event.message) }
                    listener?.onEvent(event)
                    if (event.retryable || providerId != ProviderId.MOONSHINE) {
                        handleProviderFailure(providerId, event.message)
                    }
                }
            }
        }
    }

    private fun updatePerformance(latencyMs: Long?, backlogMs: Long?) {
        _diagnostics.update {
            it.copy(transcriptionLatencyMs = latencyMs, audioBacklogMs = backlogMs)
        }
        slowResultCount = if (backlogMs != null && backlogMs > config.maxBacklogMs) {
            slowResultCount + 1
        } else {
            0
        }
        if (slowResultCount >= config.slowResultLimit) {
            active?.let { handleProviderFailure(it.id, "Persistent ASR backlog") }
        }
    }

    @Synchronized
    private fun handleProviderFailure(providerId: ProviderId, message: String) {
        if (active?.id != providerId) return
        _diagnostics.update { it.copy(lastError = message) }
        if (_diagnostics.value.configuredMode != ProviderMode.AUTO) {
            _diagnostics.update { it.copy(connectionState = ProviderConnectionState.FAILED) }
            return
        }
        val replacement = when (providerId) {
            ProviderId.HOME_SERVER -> ProviderId.ELEVENLABS
            ProviderId.ELEVENLABS -> ProviderId.MOONSHINE
            ProviderId.MOONSHINE -> null
        }
        if (replacement != null) {
            switchTo(replacement, replay = true, countFailover = true)
        }
    }

    private fun monitorHealth() {
        synchronized(this) {
            if (!recording) return
            val now = nowMs()
            val provider = active ?: return
            if (
                provider.id != ProviderId.MOONSHINE &&
                (
                    _diagnostics.value.connectionState != ProviderConnectionState.READY ||
                        hasUncommittedSpeech
                    ) &&
                now - lastProviderResponseAtMs > config.providerSilenceTimeoutMs
            ) {
                handleProviderFailure(provider.id, "Provider response timeout")
                return
            }
            if (
                _diagnostics.value.configuredMode == ProviderMode.AUTO &&
                provider.id != ProviderId.HOME_SERVER &&
                now - lastHomeProbeAtMs >= config.homeProbeIntervalMs
            ) {
                lastHomeProbeAtMs = now
                providers.getValue(ProviderId.HOME_SERVER).checkAvailability { healthy, latency ->
                    synchronized(this) {
                        _diagnostics.update { it.copy(serverLatencyMs = latency) }
                        if (!healthy) {
                            homeHealthySinceMs = null
                        } else {
                            val since = homeHealthySinceMs ?: nowMs().also {
                                homeHealthySinceMs = it
                            }
                            if (
                                nowMs() - since >=
                                config.homeRecoveryHealthyMs
                            ) {
                                homeHealthySinceMs = null
                                switchTo(
                                    ProviderId.HOME_SERVER,
                                    replay = true,
                                    countFailover = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
