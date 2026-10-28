package com.exoplanet.notch.dsp

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadTest {
    @Test
    fun centerIsFullyRemovedAndDistantFrequenciesStay() {
        val sampleRate = 48_000
        val coefficients = BiquadCoefficients.notch(sampleRate, 2_000.0, 40.0)
        val center = BiquadCoefficients.magnitudeDb(sampleRate, 2_000.0, coefficients)
        assertTrue("center was $center dB", center < -80.0)
        assertEquals(0.0, BiquadCoefficients.magnitudeDb(sampleRate, 200.0, coefficients), 1.0)
        assertEquals(0.0, BiquadCoefficients.magnitudeDb(sampleRate, 12_000.0, coefficients), 1.0)
        val skirt = BiquadCoefficients.magnitudeDb(sampleRate, 2_020.0, coefficients)
        assertTrue("edge was $skirt dB", skirt < -1.0 && skirt > -8.0)
    }

    @Test
    fun disabledBandIsFlat() {
        val sampleRate = 48_000
        val coefficients = BiquadCoefficients.forBand(
            sampleRate,
            NotchBand(enabled = false, frequencyHz = 2_000),
            bandwidthHz = 40,
        )
        assertEquals(0.0, BiquadCoefficients.magnitudeDb(sampleRate, 2_000.0, coefficients), 0.01)
    }

    @Test
    fun sineAtCenterIsRemoved() {
        val sampleRate = 48_000
        val frequency = 2_000.0
        val coefficients = BiquadCoefficients.notch(sampleRate, frequency, 40.0)
        val state = ChannelState()
        val total = sampleRate
        val skip = sampleRate / 5
        var sumIn = 0.0
        var sumOut = 0.0
        for (n in 0 until total) {
            val input = 0.5 * sin(2.0 * PI * frequency * n / sampleRate)
            val output = state.process(
                input,
                coefficients.b0,
                coefficients.b1,
                coefficients.b2,
                coefficients.a1,
                coefficients.a2,
            )
            if (n >= skip) {
                sumIn += input * input
                sumOut += output * output
            }
        }
        val count = (total - skip).toDouble()
        val dropDb = 20.0 * log10((sqrt(sumOut / count) / sqrt(sumIn / count)).coerceAtLeast(1e-12))
        assertTrue("drop was $dropDb dB", dropDb < -60.0)
    }
}
