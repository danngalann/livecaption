package com.danngalann.livecaption

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.danngalann.livecaption.asr.AsrDiagnostics
import com.danngalann.livecaption.asr.DevelopmentController
import com.danngalann.livecaption.asr.ProviderId
import com.danngalann.livecaption.asr.ProviderMode

object BuildVariantAsr {
    private const val PREFERENCES = "asr_debug"
    private const val HOME_SERVER_URL = "home_server_url"

    private val debugController = DebugDevelopmentController()
    val controller: DevelopmentController = debugController

    fun homeServerUrl(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(HOME_SERVER_URL, BuildConfig.HOME_SERVER_URL)
            .orEmpty()

    @Composable
    fun Controls(
        diagnostics: AsrDiagnostics,
        onModeChange: (ProviderMode) -> Unit
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        if (!expanded) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp)
            ) {
                Text("ASR debug")
            }
            return
        }
        var endpoint by remember { mutableStateOf(homeServerUrl(context)) }
        var failEnabled by remember { mutableStateOf(debugController.enabled) }
        var failSeconds by remember { mutableStateOf(debugController.failAfterSeconds.toString()) }
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text("ASR development settings")
                    Text("Home endpoint: ${homeServerUrl(context).ifBlank { "not configured" }}")
                    Text("Configured: ${diagnostics.configuredMode}")
                    Text(
                        "Active: ${diagnostics.activeProvider ?: "none"} / " +
                            diagnostics.connectionState
                    )
                    Text("Model: ${diagnostics.activeModel ?: "-"}")
                    Text("Runtime: ${diagnostics.activeRuntime ?: "-"}")
                    Text(
                        "Server RTT: ${diagnostics.serverLatencyMs ?: "-"} ms; " +
                            "ASR: ${diagnostics.transcriptionLatencyMs ?: "-"} ms; " +
                            "backlog: ${diagnostics.audioBacklogMs ?: "-"} ms"
                    )
                    Text(
                        "Failovers: ${diagnostics.failoverCount}; " +
                            "last error: ${diagnostics.lastError ?: "-"}"
                    )
                    Text("Provider mode")
                    ProviderMode.entries.forEach { mode ->
                        Button(
                            onClick = { onModeChange(mode) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Text(mode.name)
                        }
                    }
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Home server URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                            .edit()
                            .putString(HOME_SERVER_URL, endpoint.trim())
                            .apply()
                    }) {
                        Text("Save endpoint")
                    }
                    Row {
                        Checkbox(
                            checked = failEnabled,
                            onCheckedChange = {
                                failEnabled = it
                                debugController.enabled = it
                            }
                        )
                        Text("Fail active provider after")
                    }
                    OutlinedTextField(
                        value = failSeconds,
                        onValueChange = {
                            failSeconds = it
                            debugController.failAfterSeconds =
                                it.toLongOrNull()?.coerceAtLeast(1) ?: 10
                        },
                        label = { Text("Seconds of audio") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { expanded = false }) {
                        Text("Close debug settings")
                    }
                }
            }
        }
    }

    private class DebugDevelopmentController : DevelopmentController {
        var enabled by mutableStateOf(false)
        var failAfterSeconds by mutableLongStateOf(10)
        private var audioBytes = 0L
        private var fired = false

        override fun onSessionStarted() {
            audioBytes = 0
            fired = false
        }

        override fun shouldFail(provider: ProviderId, audioBytes: Int): Boolean {
            if (!enabled || fired) return false
            this.audioBytes += audioBytes
            val audioSeconds = this.audioBytes / (16_000.0 * 2.0)
            return (audioSeconds >= failAfterSeconds).also { if (it) fired = true }
        }
    }
}
