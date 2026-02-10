package com.audio.audiorecorder

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

object WavWriter {
    private const val WAV_HEADER_SIZE = 44

    fun writeHeader(
        output: OutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmDataSize: Int,
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmDataSize + WAV_HEADER_SIZE - 8

        val header = ByteArray(WAV_HEADER_SIZE)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)
        writeShortLE(header, 22, channels)
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, blockAlign)
        writeShortLE(header, 34, bitsPerSample)
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, pcmDataSize)

        output.write(header)
    }

    fun rewriteHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int, pcmDataSize: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmDataSize + WAV_HEADER_SIZE - 8

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.writeInt(Integer.reverseBytes(totalDataLen))
            raf.seek(22)
            raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            raf.seek(24)
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.seek(28)
            raf.writeInt(Integer.reverseBytes(byteRate))
            raf.seek(32)
            raf.writeShort(java.lang.Short.reverseBytes(blockAlign.toShort()).toInt())
            raf.seek(34)
            raf.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())
            raf.seek(40)
            raf.writeInt(Integer.reverseBytes(pcmDataSize))
        }
    }

    private fun writeIntLE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
