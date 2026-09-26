package com.danngalann.livecaption.ui.home

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.danngalann.livecaption.ui.theme.LiveCaptionTheme
import com.danngalann.livecaption.BuildVariantAsr
import com.danngalann.livecaption.data.TranscriptRepository

@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    debugControls: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hasText = state.transcripts.any { it.text.isNotBlank() } || state.partialText.isNotBlank()

    // Adjust sizes based on orientation
    val captionTextSize = if (isLandscape) 32.sp else 40.sp
    val captionLineHeight = if (isLandscape) 40.sp else 48.sp

    LaunchedEffect(state.transcripts.size) {
        if (listState.firstVisibleItemIndex == 0 && !listState.isScrollInProgress) {
            listState.scrollToItem(0)
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
        if (state.isRecording) {
            val sound = state.soundEvent
            val error = state.soundError.takeIf { sound == null }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 64.dp else 88.dp)
            ) {
                if (sound != null || error != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize().testTag("soundIndicator"),
                        shape = MaterialTheme.shapes.large,
                        color = if (sound != null) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = if (sound != null) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (sound != null) "Sonido detectado" else "Sonidos",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = sound?.label ?: error.orEmpty(),
                                fontSize = if (sound != null) 28.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ===== Transcription Area =====
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true
            ) {
                if (state.partialText.isNotBlank()) {
                    item(key = "partial") {
                        Text(
                            text = state.partialText,
                            fontSize = captionTextSize,
                            lineHeight = captionLineHeight,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                items(
                    count = state.transcripts.size,
                    key = { index -> state.transcripts.size - index }
                ) { index ->
                    val entry = state.transcripts[state.transcripts.lastIndex - index]
                    Column {
                        Text(
                            text = entry.text,
                            fontSize = captionTextSize,
                            lineHeight = captionLineHeight,
                        )

                        if (entry.startsNewParagraph) {
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
            if (isLandscape && hasText) {
                Button(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                        contentDescription = "Limpiar"
                    )
                }
            }
        }

        // Only show controls in portrait mode
        if (!isLandscape) {
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
                    visible = hasText,
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

            debugControls()
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    repository: TranscriptRepository,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val feedback = remember(context) { ActionFeedback(context) }

    DisposableEffect(feedback) {
        onDispose {
            feedback.release()
        }
    }

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
            feedback.playStart()
            viewModel.start()
        }
    }

    // Handle start button click with permission check
    val handleStart = {
        if (hasPermission) {
            feedback.playStart()
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val handleStop = {
        feedback.playStop()
        viewModel.stop()
    }

    val handleClear = {
        feedback.playClear()
        viewModel.clear()
    }

    HomeScreenContent(
        state = state,
        onStart = handleStart,
        onStop = handleStop,
        onClear = handleClear,
        debugControls = {
            BuildVariantAsr.Controls(
                diagnostics = state.diagnostics,
                onModeChange = repository::setProviderMode
            )
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    LiveCaptionTheme {
        HomeScreenContent(
            state = HomeUiState(
                transcripts = listOf(
                    TranscriptEntry(text = "Hello"),
                    TranscriptEntry(text = "World", startsNewParagraph = true)
                ),
                partialText = "Listening...",
                isRecording = false
            ),
            onStart = {},
            onStop = {},
            onClear = {}
        )
    }
}
