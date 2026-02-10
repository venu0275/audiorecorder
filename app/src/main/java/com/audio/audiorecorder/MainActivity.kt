package com.audio.audiorecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.audio.audiorecorder.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var currentFile: File? = null

    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    // In MainActivity.kt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        binding.btnRecordings.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }

        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startRecording() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, 100)
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
        if (!dir.exists()) dir.mkdirs()

        currentFile = File(dir, "REC_$timeStamp.m4a")

        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START_RECORDING
            putExtra("file_path", currentFile?.absolutePath)
        }
        startService(intent)
        isRecording = true
        updateUI()
    }

    private fun stopRecording() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP_RECORDING
        }
        startService(intent)
        isRecording = false
        updateUI()
        Toast.makeText(this, "Saved to ${currentFile?.name}", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        binding.btnRecord.text = if (isRecording) "Stop" else "Record"
        binding.tvStatus.text = if (isRecording) "Recording..." else "Ready"
    }

    private fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}