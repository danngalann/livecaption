package com.danngalann.livecaption.ui.home

import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import com.danngalann.livecaption.data.TranscriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel(
    private val repository: TranscriptRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start() {
        _state.update { it.copy(isRecording = true) }
        repository.startTranscription(
            onPartial = { text ->
                _state.update { it.copy(partialText = text) }
            },
            onFinal = { text ->
                _state.update {
                    it.copy(
                        partialText = "",
                        transcripts = it.transcripts + text
                    )
                }
            }
        )
    }

    fun stop() {
        repository.stopTranscription()
        _state.update { it.copy(isRecording = false) }
    }

    fun clear() {
        _state.update { HomeUiState() }
    }
}
