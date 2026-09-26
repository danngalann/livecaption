package com.danngalann.livecaption.data

import androidx.annotation.RequiresPermission
import com.danngalann.livecaption.asr.AsrDiagnostics
import com.danngalann.livecaption.asr.HybridAsrManager
import com.danngalann.livecaption.asr.ProviderMode
import com.danngalann.livecaption.asr.ProviderEventListener
import com.danngalann.livecaption.asr.TranscriptionEvent
import com.danngalann.livecaption.audio.SoundEvent
import kotlinx.coroutines.flow.StateFlow

class TranscriptRepository(
    private val asrManager: HybridAsrManager
) {
    val diagnostics: StateFlow<AsrDiagnostics> = asrManager.diagnostics
    val sounds: StateFlow<SoundEvent?>? = asrManager.sounds
    val soundErrors: StateFlow<String?>? = asrManager.soundErrors

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startTranscription(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        asrManager.start(ProviderEventListener { event ->
            when (event) {
                is TranscriptionEvent.Partial -> onPartial(event.text)
                is TranscriptionEvent.Final -> onFinal(event.text)
                is TranscriptionEvent.Error -> onError(event.message)
                is TranscriptionEvent.State -> Unit
            }
        })
    }

    fun stopTranscription() {
        asrManager.stop()
    }

    fun release() {
        asrManager.release()
    }

    fun clearTranscription() {
        asrManager.clearTranscript()
    }

    fun setProviderMode(mode: ProviderMode) {
        asrManager.setMode(mode)
    }
}
