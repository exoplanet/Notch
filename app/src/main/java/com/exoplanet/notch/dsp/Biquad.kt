package com.exoplanet.notch.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

data class NotchBand(
    val enabled: Boolean = false,
    val frequencyHz: Int = 1_000,
)

/**
 * Four independent notches. [bandwidthHz] is the approximate width of each cut.
 * An enabled notch removes its center frequency completely.
 */
data class NotchSettings(
    val bandwidthHz: Int = DEFAULT_BANDWIDTH_HZ,
    val bands: List<NotchBand> = DEFAULT_BANDS,
) {
    companion object {
        const val COUNT = 4
        const val MIN_HZ = 500
        const val MAX_HZ = 10_000
        const val MIN_BANDWIDTH_HZ = 5
        const val MAX_BANDWIDTH_HZ = 100
        const val DEFAULT_BANDWIDTH_HZ = 50
        val DEFAULT_BANDS: List<NotchBand> = listOf(
            NotchBand(frequencyHz = 1_000),
            NotchBand(frequencyHz = 2_000),
            NotchBand(frequencyHz = 4_000),
            NotchBand(frequencyHz = 10_000),
        )
    }
}

/**
 * Robert Bristow-Johnson notch. The center frequency is a zero, so the cut is complete there.
 * [NotchSettings.bandwidthHz] is the approximate width between the −3 dB points.
 */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    companion object {
        val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)

        fun forBand(sampleRate: Int, band: NotchBand, bandwidthHz: Int): BiquadCoefficients {
            if (!band.enabled || sampleRate <= 0) return IDENTITY
            return notch(sampleRate, band.frequencyHz.toDouble(), bandwidthHz.toDouble())
        }

        fun notch(sampleRate: Int, frequencyHz: Double, bandwidthHz: Double): BiquadCoefficients {
            if (sampleRate <= 0) return IDENTITY
            val nyquist = sampleRate * 0.5
            val frequency = frequencyHz.coerceIn(10.0, nyquist * 0.45)
            val bandwidth = bandwidthHz.coerceIn(1.0, frequency)
            val q = (frequency / bandwidth).coerceIn(0.5, 2_000.0)
            val omega = 2.0 * PI * frequency / sampleRate
            val alpha = sin(omega) / (2.0 * q)
            val cosine = cos(omega)
            val a0 = 1.0 + alpha
            return BiquadCoefficients(
                b0 = 1.0 / a0,
                b1 = (-2.0 * cosine) / a0,
                b2 = 1.0 / a0,
                a1 = (-2.0 * cosine) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        fun magnitudeDb(sampleRate: Int, atHz: Double, coefficients: BiquadCoefficients): Double {
            if (sampleRate <= 0) return 0.0
            val omega = 2.0 * PI * atHz / sampleRate
            val cos1 = cos(omega)
            val cos2 = cos(2.0 * omega)
            val b0 = coefficients.b0
            val b1 = coefficients.b1
            val b2 = coefficients.b2
            val a1 = coefficients.a1
            val a2 = coefficients.a2
            val numerator =
                b0 * b0 + b1 * b1 + b2 * b2 +
                    2.0 * cos1 * (b0 * b1 + b1 * b2) +
                    2.0 * cos2 * b0 * b2
            val denominator =
                1.0 + a1 * a1 + a2 * a2 +
                    2.0 * cos1 * (a1 + a1 * a2) +
                    2.0 * cos2 * a2
            return 10.0 * log10((numerator / denominator).coerceAtLeast(1e-20))
        }
    }
}

class ChannelState {
    private var z1 = 0.0
    private var z2 = 0.0

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    fun process(x: Double, b0: Double, b1: Double, b2: Double, a1: Double, a2: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        if (z1 > -1e-20 && z1 < 1e-20) z1 = 0.0
        if (z2 > -1e-20 && z2 < 1e-20) z2 = 0.0
        return y
    }
}
