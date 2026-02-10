package com.audio.audiorecorder

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {
    private val sampleRate = 44_100
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var internalRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var recordingJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    fun startInternalOrMixed(
        mode: RecordingMode,
        outputFile: File,
        resultCode: Int,
        data: Intent
    ) {
        stop() // Ensure clean state

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = minBuffer.coerceAtLeast(sampleRate * 4)

        // 1. Setup MediaProjection
        val mpManager = context.getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        // 2. Config for capturing other apps' audio
        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        // 3. Init Internal Recorder
        internalRecord = AudioRecord.Builder()
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .build()

        // 4. Init Mic Recorder (if mixed mode)
        if (mode == RecordingMode.MIC_AND_INTERNAL) {
            micRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build()
        }

        // 5. Start Recording Loop
        recordingJob = scope.launch {
            BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                // Write placeholder WAV header
                WavWriter.writeHeader(output, sampleRate, 2, 16, 0)

                internalRecord?.startRecording()
                micRecord?.startRecording()

                val internalBuffer = ByteArray(bufferSize)
                val micBuffer = ByteArray(bufferSize)
                var totalBytes = 0

                while (isActive) {
                    val readInternal = internalRecord?.read(internalBuffer, 0, bufferSize) ?: 0
                    if (readInternal <= 0) continue

                    val finalData = if (mode == RecordingMode.MIC_AND_INTERNAL) {
                        val readMic = micRecord?.read(micBuffer, 0, bufferSize) ?: 0
                        AudioMixer.mixPcm16(micBuffer, internalBuffer, readMic, readInternal)
                    } else {
                        internalBuffer.copyOf(readInternal)
                    }

                    output.write(finalData)
                    totalBytes += finalData.size
                }

                output.flush()
                // Go back and write correct data size in header
                WavWriter.rewriteHeader(outputFile, sampleRate, 2, 16, totalBytes)
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            internalRecord?.stop(); internalRecord?.release()
            micRecord?.stop(); micRecord?.release()
            mediaProjection?.stop()
        } catch (e: Exception) { e.printStackTrace() }
        internalRecord = null; micRecord = null; mediaProjection = null
    }
}