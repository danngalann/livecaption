package com.danngalann.livecaption.asr

enum class ProviderId {
    HOME_SERVER,
    ELEVENLABS,
    MOONSHINE
}

enum class ProviderMode {
    AUTO,
    HOME_SERVER,
    ELEVENLABS,
    MOONSHINE
}

enum class ProviderConnectionState {
    IDLE,
    PREPARING,
    CONNECTING,
    READY,
    FAILED,
    STOPPED
}

sealed interface TranscriptionEvent {
    data class Partial(
        val text: String,
        val audioPositionMs: Long? = null,
        val latencyMs: Long? = null,
        val backlogMs: Long? = null
    ) : TranscriptionEvent

    data class Final(
        val text: String,
        val audioPositionMs: Long? = null,
        val latencyMs: Long? = null,
        val backlogMs: Long? = null
    ) : TranscriptionEvent

    data class State(
        val state: ProviderConnectionState
    ) : TranscriptionEvent

    data class Error(
        val message: String,
        val retryable: Boolean
    ) : TranscriptionEvent
}

data class AsrDiagnostics(
    val configuredMode: ProviderMode = ProviderMode.AUTO,
    val activeProvider: ProviderId? = null,
    val connectionState: ProviderConnectionState = ProviderConnectionState.IDLE,
    val serverLatencyMs: Long? = null,
    val transcriptionLatencyMs: Long? = null,
    val audioBacklogMs: Long? = null,
    val lastError: String? = null,
    val failoverCount: Int = 0,
    val lastProviderTransitionAtMs: Long? = null
)

fun interface ProviderEventListener {
    fun onEvent(event: TranscriptionEvent)
}

