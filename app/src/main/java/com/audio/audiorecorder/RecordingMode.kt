package com.audio.audiorecorder

enum class RecordingMode {
    /** Only captures internal system audio (Android 10+) */
    INTERNAL_ONLY,

    /** Captures microphone and mixes it with internal audio */
    MIC_AND_INTERNAL,

    /** Standard microphone recording (fallback for older Android) */
    MIC_ONLY
}