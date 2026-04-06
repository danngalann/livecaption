package com.danngalann.livecaption.ui.home

import java.io.Serializable

private const val PARAGRAPH_GAP_THRESHOLD_MS = 5_000L

data class TranscriptEntry(
    val text: String,
    val startsNewParagraph: Boolean = false
) : Serializable

fun shouldStartNewParagraph(
    previousTranscriptAtMillis: Long?,
    currentTranscriptAtMillis: Long
): Boolean {
    return previousTranscriptAtMillis != null &&
        currentTranscriptAtMillis - previousTranscriptAtMillis >= PARAGRAPH_GAP_THRESHOLD_MS
}

