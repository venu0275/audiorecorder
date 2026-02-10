package com.audio.audiorecorder

/**
 * Recording modes exposed to users.
 *
 * Internal and mixed capture require Android 10+ because they rely on
 * AudioPlaybackCapture + MediaProjection.
 */
enum class RecordingMode {
    INTERNAL_ONLY,
    MIC_AND_INTERNAL,
    MIC_ONLY;

    companion object {
        fun fromSpinnerPosition(position: Int): RecordingMode = when (position) {
            0 -> INTERNAL_ONLY
            1 -> MIC_AND_INTERNAL
            else -> MIC_ONLY
        }
    }
}
