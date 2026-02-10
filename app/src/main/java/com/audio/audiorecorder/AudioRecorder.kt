package com.audio.audiorecorder

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Recorder engine for internal and mixed audio capture.
 *
 * This intentionally uses PCM + WAV because internal capture is not directly supported by MediaRecorder.
 */
class AudioRecorder(
    private val context: Context,
) {
    private val sampleRate = 44_100
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var internalRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var recordingJob: Job? = null

    fun startInternalOrMixed(
        mode: RecordingMode,
        outputFile: File,
        projectionResultCode: Int,
        projectionData: Intent,
    ) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Internal capture requires Android 10+"
        }

        stop()

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = minBuffer.coerceAtLeast(sampleRate * 4)

        mediaProjection = MediaProjectionHelper.getMediaProjection(context, projectionResultCode, projectionData)
        val playbackConfig = MediaProjectionHelper.createPlaybackCaptureConfig(mediaProjection!!)

        internalRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .build()

        if (internalRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Unable to initialize internal audio recorder")
        }

        if (mode == RecordingMode.MIC_AND_INTERNAL) {
            micRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (micRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Unable to initialize microphone recorder")
            }
        }

        recordingJob = scope.launch {
            BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                WavWriter.writeHeader(
                    output,
                    sampleRate = sampleRate,
                    channels = 2,
                    bitsPerSample = 16,
                    pcmDataSize = 0,
                )

                internalRecord?.startRecording()
                micRecord?.startRecording()

                val internalBuffer = ByteArray(bufferSize)
                val micBuffer = ByteArray(bufferSize)
                var pcmBytesWritten = 0

                while (isActive) {
                    val internalRead = internalRecord?.read(internalBuffer, 0, internalBuffer.size) ?: 0
                    if (internalRead <= 0) continue

                    val mixedData = if (mode == RecordingMode.MIC_AND_INTERNAL) {
                        val micRead = micRecord?.read(micBuffer, 0, micBuffer.size) ?: 0
                        AudioMixer.mixPcm16(micBuffer, internalBuffer, micRead, internalRead)
                    } else {
                        internalBuffer.copyOf(internalRead)
                    }

                    output.write(mixedData)
                    pcmBytesWritten += mixedData.size
                }

                output.flush()
                WavWriter.rewriteHeader(outputFile, sampleRate, 2, 16, pcmBytesWritten)
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            internalRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            micRecord?.stop()
        } catch (_: Exception) {
        }

        internalRecord?.release()
        micRecord?.release()
        internalRecord = null
        micRecord = null

        mediaProjection?.stop()
        mediaProjection = null
    }
}
