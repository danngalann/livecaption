package com.danngalann.livecaption.ui.home

data class HomeUiState(
    val isRecording: Boolean = false,
    val partialText: String = "",
    val transcripts: List<TranscriptEntry> = emptyList(),
    val error: String? = null
)
