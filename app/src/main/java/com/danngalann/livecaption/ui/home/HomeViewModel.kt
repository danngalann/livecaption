package com.danngalann.livecaption.ui.home

import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.danngalann.livecaption.data.TranscriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val KEY_IS_RECORDING = "isRecording"
private const val KEY_PARTIAL_TEXT = "partialText"
private const val KEY_TRANSCRIPTS = "transcripts"

class HomeViewModel(
    private val repository: TranscriptRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            isRecording = savedStateHandle.get<Boolean>(KEY_IS_RECORDING) ?: false,
            partialText = savedStateHandle.get<String>(KEY_PARTIAL_TEXT) ?: "",
            transcripts = savedStateHandle.get<List<String>>(KEY_TRANSCRIPTS) ?: emptyList()
        )
    )
    val state: StateFlow<HomeUiState> = _state

    init {
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
                _state.update {
                    it.copy(
                        partialText = "",
                        transcripts = it.transcripts + text
                    )
                }
                savedStateHandle[KEY_PARTIAL_TEXT] = ""
                savedStateHandle[KEY_TRANSCRIPTS] = _state.value.transcripts
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
        savedStateHandle[KEY_TRANSCRIPTS] = emptyList<String>()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopTranscription()
    }
}
