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
import com.danngalann.livecaption.audio.AudioRecorder
import com.danngalann.livecaption.data.TranscriptRepository
import com.danngalann.livecaption.network.ElevenLabsClient
import com.danngalann.livecaption.ui.home.HomeScreen
import com.danngalann.livecaption.ui.home.HomeViewModel
import com.danngalann.livecaption.ui.theme.LiveCaptionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screen from turning off while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        val audioRecorder = AudioRecorder(this)
        val elevenLabsClient = ElevenLabsClient(audioRecorder)
        val repository = TranscriptRepository(elevenLabsClient)
        val viewModel = HomeViewModel(repository)

        setContent {
            LiveCaptionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
