package com.audio.audiorecorder

import kotlin.math.max
import kotlin.math.min

object AudioMixer {
    fun mixPcm16(micData: ByteArray, internalData: ByteArray, bytesReadMic: Int, bytesReadInternal: Int): ByteArray {
        val maxBytes = max(bytesReadMic, bytesReadInternal)
        val output = ByteArray(maxBytes)

        var i = 0
        while (i < maxBytes - 1) {
            // Convert bytes to 16-bit integer (Little Endian)
            val micSample = if (i < bytesReadMic - 1) {
                ((micData[i + 1].toInt() shl 8) or (micData[i].toInt() and 0xFF)).toShort().toInt()
            } else 0

            val internalSample = if (i < bytesReadInternal - 1) {
                ((internalData[i + 1].toInt() shl 8) or (internalData[i].toInt() and 0xFF)).toShort().toInt()
            } else 0

            // Mix and clamp to Short range
            val mixed = min(Short.MAX_VALUE.toInt(), max(Short.MIN_VALUE.toInt(), micSample + internalSample))

            // Convert back to bytes
            output[i] = (mixed and 0xFF).toByte()
            output[i + 1] = ((mixed shr 8) and 0xFF).toByte()
            i += 2
        }
        return output
    }
}