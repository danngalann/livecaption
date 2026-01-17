package com.danngalann.livecaption.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.danngalann.livecaption.ui.theme.LiveCaptionTheme

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val captionTextSize = 40.sp
    val captionLineHeight = 48.sp

    // Auto-scroll to bottom (index 0 in reversed layout) when new transcript arrives
    LaunchedEffect(state.transcripts.size, state.partialText) {
        if (state.transcripts.isNotEmpty() || state.partialText.isNotBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color.Red),
        horizontalArrangement = Arrangement.Center
    ) {
        if (state.isRecording) {
            Text("Estoy escuchando", fontSize = 20.sp, modifier = Modifier.padding(8.dp))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        // ===== Transcription Area =====
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            reverseLayout = true  // Makes content flow from bottom to top
        ) {
            // Show partial text first (at the bottom)
            if (state.partialText.isNotBlank()) {
                item {
                    Text(
                        text = state.partialText,
                        fontSize = captionTextSize,
                        lineHeight = captionLineHeight,
                        color = Color.Gray
                    )
                }
            }

            // Show transcripts in reverse order (newest at bottom)
            items(state.transcripts.reversed()) { text ->
                Text(
                    text = text,
                    fontSize = captionTextSize,
                    lineHeight = captionLineHeight,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ===== Controls =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = if (state.isRecording) onStop else onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = if (state.isRecording) "Parar" else "Escuchar",
                    fontSize = 24.sp
                )
            }

            AnimatedVisibility(
                visible = (state.transcripts.any { it.isNotBlank() } || state.partialText.isNotBlank()) && !state.isRecording,
                enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { it / 2 }
                ),
                exit = fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { it / 2 }
                )
            ) {
                Button(
                    onClick = onClear,
                    modifier = Modifier
                        .height(56.dp)
                        .padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                        contentDescription = "Limpiar"
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Track permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.start()
        }
    }

    // Handle start button click with permission check
    val handleStart = {
        if (hasPermission) {
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    HomeScreenContent(
        state = state,
        onStart = handleStart,
        onStop = viewModel::stop,
        onClear = viewModel::clear,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    LiveCaptionTheme {
        HomeScreenContent(
            state = HomeUiState(
                transcripts = listOf("", "Hello"),
                partialText = "Listening...",
                isRecording = false
            ),
            onStart = {},
            onStop = {},
            onClear = {}
        )
    }
}
