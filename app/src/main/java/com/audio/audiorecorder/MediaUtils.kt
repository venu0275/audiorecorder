package com.audio.audiorecorder

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object MediaUtils {

    fun saveToGallery(context: Context, audioFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, audioFile)
        } else {
            scanFile(context, audioFile)
        }
    }

    private fun saveToMediaStore(context: Context, audioFile: File) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, audioFile.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AudioRecordings")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, contentValues)

        uri?.let {
            resolver.openOutputStream(it).use { outputStream ->
                FileInputStream(audioFile).use { inputStream ->
                    inputStream.copyTo(outputStream as OutputStream)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    private fun scanFile(context: Context, audioFile: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(audioFile.absolutePath),
            arrayOf("audio/*")
        ) { path, uri ->
            // File scanned
        }
    }

    fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%02d:%02d", minutes % 60, seconds % 60)
        }
    }
}