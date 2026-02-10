package com.audio.audiorecorder

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object MediaProjectionHelper {

    fun getMediaProjection(context: Context, resultCode: Int, resultData: Intent): MediaProjection {
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        return projectionManager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("MediaProjection not granted")
    }

    fun createPlaybackCaptureConfig(mediaProjection: MediaProjection): AudioPlaybackCaptureConfiguration {
        return AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
    }
}
