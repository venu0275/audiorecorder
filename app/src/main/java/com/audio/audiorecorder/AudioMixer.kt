package com.audio.audiorecorder

import kotlin.math.max
import kotlin.math.min

object AudioMixer {

    /**
     * Mixes two PCM 16-bit little-endian buffers.
     * When one input is shorter, the remaining samples of the longer input are used as-is.
     */
    fun mixPcm16(micData: ByteArray, internalData: ByteArray, bytesReadMic: Int, bytesReadInternal: Int): ByteArray {
        val maxBytes = max(bytesReadMic, bytesReadInternal)
        val output = ByteArray(maxBytes)

        var i = 0
        while (i < maxBytes - 1) {
            val micSample = if (i < bytesReadMic - 1) {
                ((micData[i + 1].toInt() shl 8) or (micData[i].toInt() and 0xFF)).toShort().toInt()
            } else {
                0
            }

            val internalSample = if (i < bytesReadInternal - 1) {
                ((internalData[i + 1].toInt() shl 8) or (internalData[i].toInt() and 0xFF)).toShort().toInt()
            } else {
                0
            }

            val mixed = min(Short.MAX_VALUE.toInt(), max(Short.MIN_VALUE.toInt(), micSample + internalSample))
            output[i] = (mixed and 0xFF).toByte()
            output[i + 1] = ((mixed shr 8) and 0xFF).toByte()
            i += 2
        }

        return output
    }
}
