package com.danngalann.livecaption.ui.home

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.danngalann.livecaption.data.TranscriptRepository
import java.util.ArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope

private const val KEY_IS_RECORDING = "isRecording"
private const val KEY_PARTIAL_TEXT = "partialText"
private const val KEY_TRANSCRIPTS = "transcripts"
private const val KEY_LAST_FINAL_TRANSCRIPT_AT = "lastFinalTranscriptAt"

class HomeViewModel(
    private val repository: TranscriptRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            isRecording = savedStateHandle.get<Boolean>(KEY_IS_RECORDING) ?: false,
            partialText = savedStateHandle.get<String>(KEY_PARTIAL_TEXT) ?: "",
            transcripts = savedStateHandle.get<ArrayList<TranscriptEntry>>(KEY_TRANSCRIPTS)?.toList()
                ?: emptyList()
        )
    )
    val state: StateFlow<HomeUiState> = _state

    private var lastFinalTranscriptAtMillis =
        savedStateHandle.get<Long>(KEY_LAST_FINAL_TRANSCRIPT_AT)?.takeIf { it >= 0L }

    init {
        repository.diagnostics
            .onEach { diagnostics -> _state.update { it.copy(diagnostics = diagnostics) } }
            .launchIn(viewModelScope)

        // If we were recording before configuration change, restart transcription
        // Permission was already granted before the configuration change
        if (_state.value.isRecording) {
            @SuppressLint("MissingPermission")
            restartTranscription()
        }
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start() {
        _state.update { it.copy(isRecording = true) }
        savedStateHandle[KEY_IS_RECORDING] = true
        startTranscriptionInternal()
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun startTranscriptionInternal() {
        repository.startTranscription(
            onPartial = { text ->
                _state.update { it.copy(partialText = text) }
                savedStateHandle[KEY_PARTIAL_TEXT] = text
            },
            onFinal = { text ->
                val now = SystemClock.elapsedRealtime()
                _state.update {
                    val startsNewParagraph = it.transcripts.isNotEmpty() &&
                        shouldStartNewParagraph(lastFinalTranscriptAtMillis, now)

                    it.copy(
                        partialText = "",
                        transcripts = it.transcripts + TranscriptEntry(
                            text = text,
                            startsNewParagraph = startsNewParagraph
                        )
                    )
                }
                lastFinalTranscriptAtMillis = now
                savedStateHandle[KEY_LAST_FINAL_TRANSCRIPT_AT] = now
                savedStateHandle[KEY_PARTIAL_TEXT] = ""
                savedStateHandle[KEY_TRANSCRIPTS] = ArrayList(_state.value.transcripts)
            },
            onError = { message ->
                _state.update { it.copy(error = message) }
            }
        )
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun restartTranscription() {
        startTranscriptionInternal()
    }

    fun stop() {
        repository.stopTranscription()
        _state.update { it.copy(isRecording = false) }
        savedStateHandle[KEY_IS_RECORDING] = false
    }

    fun clear() {
        _state.update { HomeUiState() }
        savedStateHandle[KEY_IS_RECORDING] = false
        savedStateHandle[KEY_PARTIAL_TEXT] = ""
        savedStateHandle[KEY_TRANSCRIPTS] = ArrayList<TranscriptEntry>()
        savedStateHandle[KEY_LAST_FINAL_TRANSCRIPT_AT] = -1L
        lastFinalTranscriptAtMillis = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopTranscription()
    }
}
