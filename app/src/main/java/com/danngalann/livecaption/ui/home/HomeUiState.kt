package com.danngalann.livecaption.ui.home

import com.danngalann.livecaption.asr.AsrDiagnostics
import com.danngalann.livecaption.audio.SoundEvent

data class HomeUiState(
    val isRecording: Boolean = false,
    val partialText: String = "",
    val transcripts: List<TranscriptEntry> = emptyList(),
    val error: String? = null,
    val diagnostics: AsrDiagnostics = AsrDiagnostics(),
    val soundEvent: SoundEvent? = null,
    val soundError: String? = null
)
