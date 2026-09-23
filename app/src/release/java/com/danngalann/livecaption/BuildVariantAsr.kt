package com.danngalann.livecaption

import android.content.Context
import androidx.compose.runtime.Composable
import com.danngalann.livecaption.asr.AsrDiagnostics
import com.danngalann.livecaption.asr.DevelopmentController
import com.danngalann.livecaption.asr.ProviderId
import com.danngalann.livecaption.asr.ProviderMode

object BuildVariantAsr {
    val controller: DevelopmentController = object : DevelopmentController {
        override fun onSessionStarted() = Unit

        override fun shouldFail(provider: ProviderId, audioBytes: Int): Boolean = false
    }

    fun homeServerUrl(context: Context): String = BuildConfig.HOME_SERVER_URL

    @Composable
    fun Controls(
        diagnostics: AsrDiagnostics,
        onModeChange: (ProviderMode) -> Unit
    ) = Unit
}

