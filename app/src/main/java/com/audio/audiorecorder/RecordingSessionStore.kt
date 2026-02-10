package com.audio.audiorecorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RecordingSessionStore {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state

    fun update(state: RecordingState) {
        _state.value = state
    }
}
