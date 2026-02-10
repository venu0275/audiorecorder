package com.audio.audiorecorder

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(app: Application) : AndroidViewModel(app) {

    fun getOutputFile(mode: RecordingMode): File {
        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = if (mode == RecordingMode.MIC_ONLY) "m4a" else "wav"
        return File(dir, "Rec_$timestamp.$ext")
    }
}
