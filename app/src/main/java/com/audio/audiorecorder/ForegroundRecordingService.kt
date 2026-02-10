package com.audio.audiorecorder

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class ForegroundRecordingService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return
        val mode = RecordingMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: RecordingMode.MIC_ONLY.name)

        startAsForeground(mode)

        try {
            when (mode) {
                RecordingMode.MIC_ONLY -> startMicRecording(File(outputPath))
                RecordingMode.INTERNAL_ONLY,
                RecordingMode.MIC_AND_INTERNAL,
                -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        throw IllegalStateException("Internal audio capture requires Android 10+")
                    }

                    val projectionResultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED)
                    if (projectionResultCode != Activity.RESULT_OK) {
                        throw IllegalStateException("MediaProjection approval is required")
                    }
                    val projectionData = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
                        ?: throw IllegalStateException("MediaProjection approval is required")

                    audioRecorder.startInternalOrMixed(mode, File(outputPath), projectionResultCode, projectionData)
                }
            }

            RecordingSessionStore.update(
                RecordingState.Recording(
                    mode = mode,
                    fileName = File(outputPath).name,
                    startedAtMs = System.currentTimeMillis(),
                ),
            )
        } catch (error: Exception) {
            RecordingSessionStore.update(RecordingState.Error(error.message ?: "Unable to record audio"))
            stopRecording()
        }
    }

    private fun startMicRecording(outputFile: File) {
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }

        mediaRecorder?.release()
        mediaRecorder = null

        audioRecorder.stop()
        RecordingSessionStore.update(RecordingState.Idle)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(mode: RecordingMode) {
        val openMainIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, ForegroundRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_message, mode.name))
            .setContentIntent(openMainIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfoCompat.mediaProjectionOrMicrophone(mode),
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    object ServiceInfoCompat {
        fun mediaProjectionOrMicrophone(mode: RecordingMode): Int {
            val microphone = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            return when (mode) {
                RecordingMode.MIC_ONLY -> microphone
                else -> microphone or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        }
    }

    companion object {
        const val ACTION_START = "com.audio.audiorecorder.action.START"
        const val ACTION_STOP = "com.audio.audiorecorder.action.STOP"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_PROJECTION_RESULT_CODE = "extra_projection_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "recording_channel"
    }
}
