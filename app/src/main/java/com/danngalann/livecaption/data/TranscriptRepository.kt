package com.danngalann.livecaption.data

import androidx.annotation.RequiresPermission
import com.danngalann.livecaption.network.ElevenLabsClient

class TranscriptRepository(
    private val elevenLabsClient: ElevenLabsClient
) {

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startTranscription(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) {
        elevenLabsClient.connect(
            onPartial = onPartial,
            onFinal = onFinal
        )
    }

    fun stopTranscription() {
        elevenLabsClient.stop()
    }
}
