package com.danngalann.livecaption

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danngalann.livecaption.asr.HybridAsrManager
import com.danngalann.livecaption.asr.MoonshineProvider
import com.danngalann.livecaption.audio.AudioRecorder
import com.danngalann.livecaption.audio.MediaPipeSoundClassifier
import com.danngalann.livecaption.data.TranscriptRepository
import com.danngalann.livecaption.network.ElevenLabsClient
import com.danngalann.livecaption.network.HomeServerProvider
import com.danngalann.livecaption.ui.home.HomeScreen
import com.danngalann.livecaption.ui.home.HomeViewModel
import com.danngalann.livecaption.ui.theme.LiveCaptionTheme

class MainActivity : ComponentActivity() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var repository: TranscriptRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screen from turning off while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize dependencies once
        audioRecorder = AudioRecorder(this)
        val manager = HybridAsrManager(
            audioRecorder = audioRecorder,
            providers = listOf(
                HomeServerProvider(
                    endpointProvider = { BuildVariantAsr.homeServerUrl(this) },
                    tokenProvider = { BuildConfig.HOME_SERVER_TOKEN }
                ),
                ElevenLabsClient(),
                MoonshineProvider(this)
            ),
            developmentController = BuildVariantAsr.controller,
            soundClassifier = MediaPipeSoundClassifier(applicationContext)
        )
        repository = TranscriptRepository(manager)

        enableEdgeToEdge()

        setContent {
            LiveCaptionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: HomeViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return HomeViewModel(
                                    repository = repository,
                                    savedStateHandle = SavedStateHandle()
                                ) as T
                            }
                        }
                    )

                    HomeScreen(
                        viewModel = viewModel,
                        repository = repository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
