package com.audio.audiorecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingFile: File? = null
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar)

        // Setup buttons
        binding.btnRecord.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnRecordings.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }

        // Update UI based on recording state
        updateUI()
    }

    private fun startRecording() {
        if (!hasPermissions()) {
            Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaRecorder = MediaRecorder().apply {
                // Configure based on settings
                val audioSource = when (AudioRecorderApp.preferences.getString(
                    "audio_source",
                    "MIC"
                )) {
                    "MIC" -> MediaRecorder.AudioSource.MIC
                    "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                    "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                    "CAMCORDER" -> MediaRecorder.AudioSource.CAMCORDER
                    else -> MediaRecorder.AudioSource.MIC
                }

                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)

                // Create file
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val recordingsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "AudioRecordings"
                )
                if (!recordingsDir.exists()) {
                    recordingsDir.mkdirs()
                }

                recordingFile = File(recordingsDir, "recording_$timeStamp.m4a")
                setOutputFile(recordingFile?.absolutePath)

                prepare()
                start()

                isRecording = true
                updateUI()

                Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
            }

            // Start foreground service for recording
            startService(Intent(this, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.ACTION_START_RECORDING
                putExtra("file_path", recordingFile?.absolutePath)
            })

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            resetRecorder()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            isRecording = false
            updateUI()

            // Stop service
            stopService(Intent(this, AudioRecordingService::class.java))

            // Save to gallery/media store
            recordingFile?.let { file ->
                MediaUtils.saveToGallery(this, file)
                Toast.makeText(this, "Recording saved to gallery", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to stop recording", Toast.LENGTH_SHORT).show()
        } finally {
            resetRecorder()
        }
    }

    private fun resetRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        updateUI()
    }

    private fun updateUI() {
        binding.btnRecord.text = if (isRecording) "Stop Recording" else "Start Recording"
        binding.btnRecord.setBackgroundColor(
            ContextCompat.getColor(this,
                if (isRecording) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
        )
        binding.tvStatus.text = if (isRecording) "Recording..." else "Ready to record"
        binding.btnRecordings.isEnabled = !isRecording
    }

    private fun checkPermissions() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (!hasPermissions()) {
                Toast.makeText(this, "Permissions are required to use the app", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resetRecorder()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}