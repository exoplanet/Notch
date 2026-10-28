package com.exoplanet.notch

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

fun testNoiseUri(context: Context): Uri {
    val file = File(context.cacheDir, "notch-test-noise.wav")
    if (!file.exists() || file.length() < 44) {
        writeNoiseWav(file)
    }
    return Uri.fromFile(file)
}

private fun writeNoiseWav(file: File) {
    val sampleRate = 48_000
    val channels = 2
    val frames = sampleRate * 3
    val dataBytes = frames * channels * 2
    FileOutputStream(file).use { output ->
        output.write(wavHeader(dataBytes, sampleRate, channels))
        val random = Random(42)
        val frame = ByteBuffer.allocate(channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            val sample = ((random.nextDouble() * 2.0 - 1.0) * 0.18 * 32767.0)
                .toInt()
                .coerceIn(-32768, 32767)
                .toShort()
            frame.clear()
            frame.putShort(sample)
            frame.putShort(sample)
            output.write(frame.array())
        }
    }
}

private fun wavHeader(dataBytes: Int, sampleRate: Int, channels: Int): ByteArray {
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".encodeToByteArray())
    buffer.putInt(36 + dataBytes)
    buffer.put("WAVE".encodeToByteArray())
    buffer.put("fmt ".encodeToByteArray())
    buffer.putInt(16)
    buffer.putShort(1)
    buffer.putShort(channels.toShort())
    buffer.putInt(sampleRate)
    buffer.putInt(byteRate)
    buffer.putShort(blockAlign.toShort())
    buffer.putShort(bitsPerSample.toShort())
    buffer.put("data".encodeToByteArray())
    buffer.putInt(dataBytes)
    return buffer.array()
}
