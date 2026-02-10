package com.audio.audiorecorder

import android.os.Environment
import android.os.StatFs
import java.util.Locale

object MediaUtils {

    /**
     * Formats milliseconds into MM:SS or HH:MM:SS
     */
    fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Checks if there is enough storage space (e.g., > 50MB).
     */
    fun hasEnoughStorage(): Boolean {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        val megabytesAvailable = bytesAvailable / (1024 * 1024)
        return megabytesAvailable > 50
    }
}