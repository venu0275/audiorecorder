package com.audio.audiorecorder

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AudioRecorderApp : Application() {

    companion object {
        lateinit var instance: AudioRecorderApp
        lateinit var preferences: SharedPreferences
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }
}