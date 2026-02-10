package com.audio.audiorecorder

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File

class ForegroundRecordingService : Service() {

    private lateinit var audioRecorder: AudioRecorder

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: RecordingMode.MIC_ONLY.name
                val mode = RecordingMode.valueOf(modeName)
                val path = intent.getStringExtra(EXTRA_OUTPUT_PATH)!!

                // Get MediaProjection tokens
                val resultCode = intent.getIntExtra(EXTRA_PROJ_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_PROJ_DATA)

                startAsForeground(mode)

                if (mode == RecordingMode.MIC_ONLY) {
                    // Fallback to standard MediaRecorder for Mic Only (Simpler/Better Battery)
                    // Implementation omitted for brevity (standard API)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && data != null) {
                        audioRecorder.startInternalOrMixed(mode, File(path), resultCode, data)
                    }
                }
            }
            ACTION_STOP -> {
                audioRecorder.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(mode: RecordingMode) {
        val stopIntent = Intent(this, ForegroundRecordingService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Audio")
            .setContentText("Mode: $mode")
            .setSmallIcon(R.drawable.ic_mic)
            .addAction(R.drawable.ic_pause, "STOP", pendingStop)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mode != RecordingMode.MIC_ONLY) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }

        ServiceCompat.startForeground(this, 101, notification, type)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_OUTPUT_PATH = "EXTRA_PATH"
        const val EXTRA_PROJ_CODE = "EXTRA_CODE"
        const val EXTRA_PROJ_DATA = "EXTRA_DATA"
        const val CHANNEL_ID = "rec_channel"
    }
}