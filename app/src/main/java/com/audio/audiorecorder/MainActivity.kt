package com.audio.audiorecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.audio.audiorecorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var pendingMode: RecordingMode = RecordingMode.MIC_ONLY

    // Permission Request
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) startRecordingFlow()
    }

    // Media Projection Request
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startService(pendingMode, result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen Capture Denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val modes = RecordingMode.values().map { it.name }
        binding.spnMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)

        binding.btnStart.setOnClickListener {
            pendingMode = RecordingMode.values()[binding.spnMode.selectedItemPosition]
            checkPermissions()
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, ForegroundRecordingService::class.java).apply {
                action = ForegroundRecordingService.ACTION_STOP
            }
            startService(intent)
        }

        // NEW: Wire up the recordings list button
        binding.btnViewRecordings.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)

        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startRecordingFlow()
        } else {
            permLauncher.launch(perms.toTypedArray())
        }
    }

    private fun startRecordingFlow() {
        if (pendingMode == RecordingMode.MIC_ONLY) {
            startService(pendingMode, 0, null)
        } else {
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }
    }

    private fun startService(mode: RecordingMode, resultCode: Int, data: Intent?) {
        val file = viewModel.getOutputFile(mode)
        val serviceIntent = Intent(this, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_START
            putExtra(ForegroundRecordingService.EXTRA_MODE, mode.name)
            putExtra(ForegroundRecordingService.EXTRA_OUTPUT_PATH, file.absolutePath)
            if (data != null) {
                putExtra(ForegroundRecordingService.EXTRA_PROJ_CODE, resultCode)
                putExtra(ForegroundRecordingService.EXTRA_PROJ_DATA, data)
            }
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}