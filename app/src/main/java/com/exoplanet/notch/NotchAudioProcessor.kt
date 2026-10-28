package com.exoplanet.notch

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.exoplanet.notch.dsp.BiquadCoefficients
import com.exoplanet.notch.dsp.ChannelState
import com.exoplanet.notch.dsp.NotchSettings
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Four cascaded notches on 16-bit PCM. Each notch is a complete cut at its center.
 * Coefficients move toward the latest settings with a 20 ms time constant so a step
 * does not click. Place [androidx.media3.common.audio.ToInt16PcmAudioProcessor] ahead
 * of this one so float and wider integer input is converted first.
 */
class NotchAudioProcessor : BaseAudioProcessor() {
    private var filters = emptyArray<Array<ChannelState>>()
    private val targets = Array(NotchSettings.COUNT) { BiquadCoefficients.IDENTITY }
    private val live = Array(NotchSettings.COUNT) { DoubleArray(5) { if (it == 0) 1.0 else 0.0 } }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        ensureChannels(inputAudioFormat.channelCount)
        filters.forEach { channel -> channel.forEach { it.reset() } }
        for (coeffs in live) {
            coeffs[0] = 1.0
            coeffs[1] = 0.0
            coeffs[2] = 0.0
            coeffs[3] = 0.0
            coeffs[4] = 0.0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        if (channels <= 0 || bytesPerFrame <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        ensureChannels(channels)
        val sampleRate = inputAudioFormat.sampleRate
        val settings = NotchEngine.current()
        for (index in 0 until NotchSettings.COUNT) {
            val band = settings.bands.getOrNull(index)
            targets[index] = if (band == null) {
                BiquadCoefficients.IDENTITY
            } else {
                BiquadCoefficients.forBand(sampleRate, band, settings.bandwidthHz)
            }
        }
        val frames = inputBuffer.remaining() / bytesPerFrame
        val output = replaceOutputBuffer(frames * bytesPerFrame)
        val alpha = if (sampleRate > 0) 1.0 - exp(-1.0 / (sampleRate * 0.02)) else 1.0
        repeat(frames) {
            for (index in 0 until NotchSettings.COUNT) {
                val target = targets[index]
                val coeffs = live[index]
                coeffs[0] += (target.b0 - coeffs[0]) * alpha
                coeffs[1] += (target.b1 - coeffs[1]) * alpha
                coeffs[2] += (target.b2 - coeffs[2]) * alpha
                coeffs[3] += (target.a1 - coeffs[3]) * alpha
                coeffs[4] += (target.a2 - coeffs[4]) * alpha
            }
            for (channel in 0 until channels) {
                var sample = inputBuffer.short.toDouble() / 32768.0
                val states = filters[channel]
                for (index in 0 until NotchSettings.COUNT) {
                    val coeffs = live[index]
                    sample = states[index].process(
                        sample,
                        coeffs[0],
                        coeffs[1],
                        coeffs[2],
                        coeffs[3],
                        coeffs[4],
                    )
                }
                val clipped = sample.coerceIn(-1.0, 1.0)
                val pcm = (clipped * 32767.0).roundToInt().coerceIn(-32768, 32767)
                output.putShort(pcm.toShort())
            }
        }
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }

    private fun ensureChannels(channels: Int) {
        if (channels <= 0) return
        if (filters.size == channels && filters[0].size == NotchSettings.COUNT) return
        filters = Array(channels) { Array(NotchSettings.COUNT) { ChannelState() } }
    }
}
