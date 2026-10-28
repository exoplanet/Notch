package com.exoplanet.notch.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
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
            bandwidthTenths = 1,
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

    @Test
    fun tenthOfAnOctaveScalesWithCenterFrequency() {
        val at1k = NotchSettings.bandwidthHz(1_000.0, 1)
        val at10k = NotchSettings.bandwidthHz(10_000.0, 1)
        val factor = 2.0.pow(0.05) - 2.0.pow(-0.05)
        assertEquals(1_000.0 * factor, at1k, 0.01)
        assertEquals(at1k * 10.0, at10k, 0.01)
    }

    @Test
    fun octaveEdgesSitNearMinusThreeDb() {
        val sampleRate = 48_000
        val center = 2_000.0
        val tenths = 5
        val octaves = tenths / 10.0
        val low = center / 2.0.pow(octaves / 2.0)
        val high = center * 2.0.pow(octaves / 2.0)
        val coefficients = BiquadCoefficients.notch(
            sampleRate,
            center,
            NotchSettings.bandwidthHz(center, tenths),
        )
        val lowDb = BiquadCoefficients.magnitudeDb(sampleRate, low, coefficients)
        val highDb = BiquadCoefficients.magnitudeDb(sampleRate, high, coefficients)
        assertTrue("low edge was $lowDb dB", abs(lowDb + 3.0) < 1.0)
        assertTrue("high edge was $highDb dB", abs(highDb + 3.0) < 1.0)
        assertTrue(BiquadCoefficients.magnitudeDb(sampleRate, center, coefficients) < -80.0)
    }

    @Test
    fun savedSettingsClampBackIntoRange() {
        val restored = NotchSettings(
            bandwidthTenths = 40,
            bands = listOf(
                NotchBand(enabled = true, frequencyHz = 12_000),
                NotchBand(enabled = false, frequencyHz = 100),
            ),
        ).normalized()
        assertEquals(NotchSettings.MAX_BANDWIDTH_TENTHS, restored.bandwidthTenths)
        assertEquals(4, restored.bands.size)
        assertEquals(true, restored.bands[0].enabled)
        assertEquals(NotchSettings.MAX_HZ, restored.bands[0].frequencyHz)
        assertEquals(false, restored.bands[1].enabled)
        assertEquals(NotchSettings.MIN_HZ, restored.bands[1].frequencyHz)
        assertEquals(NotchSettings.DEFAULT_BANDS[2], restored.bands[2])
        assertEquals(NotchSettings.DEFAULT_BANDS[3], restored.bands[3])
    }
}
