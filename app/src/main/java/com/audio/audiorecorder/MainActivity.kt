package com.audio.audiorecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.audio.audiorecorder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(application) }

    private var pendingMode: RecordingMode? = null

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (!granted) {
                Toast.makeText(this, R.string.permission_required_message, Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            startCaptureFlow()
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val mode = pendingMode ?: return@registerForActivityResult
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                Toast.makeText(this, R.string.media_projection_denied, Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            startService(mode, result.resultCode, result.data)
        }

    // In MainActivity.kt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupModeSpinner()
        setupButtons()
        renderBatteryOptimizationWarning()
        observeUi()
    }

    private fun setupModeSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.recording_modes,
            android.R.layout.simple_spinner_item,
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spnMode.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            if (!hasStorageSpace()) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            pendingMode = RecordingMode.fromSpinnerPosition(binding.spnMode.selectedItemPosition)
            requestRequiredPermissions()
        }

        binding.btnStop.setOnClickListener {
            startService(Intent(this, ForegroundRecordingService::class.java).apply {
                action = ForegroundRecordingService.ACTION_STOP
            })
        }

        binding.btnBatterySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startCaptureFlow()
        } else {
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startCaptureFlow() {
        val mode = pendingMode ?: return
        if (mode == RecordingMode.MIC_ONLY) {
            startService(mode, Activity.RESULT_OK, null)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, R.string.internal_capture_not_supported, Toast.LENGTH_LONG).show()
            return
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startService(mode: RecordingMode, projectionResultCode: Int, projectionData: Intent?) {
        val output = viewModel.buildOutputFile(mode)

        val intent = Intent(this, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_START
            putExtra(ForegroundRecordingService.EXTRA_MODE, mode.name)
            putExtra(ForegroundRecordingService.EXTRA_OUTPUT_PATH, output.absolutePath)
            if (projectionData != null) {
                putExtra(ForegroundRecordingService.EXTRA_PROJECTION_RESULT_CODE, projectionResultCode)
                putExtra(ForegroundRecordingService.EXTRA_PROJECTION_DATA, projectionData)
            }
        }

        ContextCompat.startForegroundService(this, intent)
    }

    private fun hasStorageSpace(): Boolean {
        val externalDir = getExternalFilesDir(null) ?: return false
        return externalDir.usableSpace > MIN_FREE_SPACE_BYTES
    }

    private fun renderBatteryOptimizationWarning() {
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val optimized = !manager.isIgnoringBatteryOptimizations(packageName)
        binding.groupBatteryWarning.visibility = if (optimized) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun observeUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recordingState.collect { state ->
                        when (state) {
                            is RecordingState.Idle -> {
                                binding.tvStatus.text = getString(R.string.status_idle)
                                binding.btnStart.isEnabled = true
                                binding.btnStop.isEnabled = false
                            }

                            is RecordingState.Recording -> {
                                binding.tvStatus.text = getString(R.string.status_recording, state.fileName)
                                binding.btnStart.isEnabled = false
                                binding.btnStop.isEnabled = true
                            }

                            is RecordingState.Error -> {
                                binding.tvStatus.text = getString(R.string.status_error, state.message)
                                binding.btnStart.isEnabled = true
                                binding.btnStop.isEnabled = false
                                Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.timerText.collect { timer ->
                        binding.tvTimer.text = timer
                    }
                }
            }
        }
    }

    companion object {
        private const val MIN_FREE_SPACE_BYTES = 100L * 1024L * 1024L
    }
}
