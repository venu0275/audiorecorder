package com.audio.audiorecorder

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _timerText = MutableStateFlow("00:00")
    val timerText: StateFlow<String> = _timerText.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            RecordingSessionStore.state.collectLatest { state ->
                _recordingState.value = state
                if (state is RecordingState.Recording) {
                    startTimer(state.startedAtMs)
                } else {
                    timerJob?.cancel()
                    _timerText.value = "00:00"
                }
            }
        }
    }

    fun buildOutputFile(mode: RecordingMode): File {
        val extension = if (mode == RecordingMode.MIC_ONLY) "m4a" else "wav"
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".${extension}"
        val dir = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName)
    }

    private fun startTimer(startTimeMs: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                _timerText.value = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                delay(1000)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
