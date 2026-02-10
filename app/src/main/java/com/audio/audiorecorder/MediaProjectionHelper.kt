package com.audio.audiorecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

object MediaProjectionHelper {

    /**
     * Creates the intent required to launch the system dialog "Start recording or casting with...?"
     */
    fun createScreenCaptureIntent(context: Context): Intent {
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    /**
     * Checks if the result from the activity is valid for MediaProjection.
     */
    fun isValidResult(resultCode: Int, data: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK && data != null
    }
}