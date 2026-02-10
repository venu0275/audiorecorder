package com.audio.audiorecorder

sealed class RecordingState {
    data object Idle : RecordingState()
    data class Recording(
        val mode: RecordingMode,
        val fileName: String,
        val startedAtMs: Long,
    ) : RecordingState()

    data class Error(val message: String) : RecordingState()
}
