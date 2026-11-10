package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MatchPitchTest {
    @Test
    fun halfAndDoubleStayInsideTheNotchRange() {
        assertEquals(2_000, MatchPitch.half(4_000))
        assertEquals(8_000, MatchPitch.double(4_000))
        assertNull(MatchPitch.half(500))
        assertNull(MatchPitch.double(10_000))
        assertNull(MatchPitch.half(900))
        assertEquals(1_000, MatchPitch.double(500))
        assertEquals(500, MatchPitch.half(999))
        assertNull(MatchPitch.half(998))
        assertEquals(10_000, MatchPitch.double(5_000))
        assertNull(MatchPitch.double(5_001))
        assertNull(MatchPitch.half(0))
        assertNull(MatchPitch.double(-4_000))
    }

    @Test
    fun narrowBandPassesItsCenterAndRejectsAnOctaveAway() {
        val sampleRate = 48_000
        val center = rms(sampleRate, 4_000, listenHz = 4_000)
        val octave = rms(sampleRate, 4_000, listenHz = 8_000)
        assertTrue("center rms $center", center > 0.2)
        assertTrue("octave rms $octave vs center $center", octave < center * 0.1)
    }

    @Test
    fun retuningToTheSameFrequencyKeepsTheFilterState() {
        val band = NarrowBandpass(48_000)
        band.retune(4_000)
        band.process(1.0)
        band.retune(4_000)
        val continued = band.process(0.0)
        assertTrue("state was cleared ($continued)", kotlin.math.abs(continued) > 1e-6)
    }

    @Test
    fun narrowBandStaysFiniteAtTheNotchEdges() {
        for (hz in listOf(500, 10_000)) {
            val band = NarrowBandpass(48_000)
            band.retune(hz)
            var peak = 0.0
            repeat(4_000) {
                val sample = if (it % 2 == 0) 1.0 else -1.0
                val output = band.process(sample)
                assertTrue("non-finite output $output at $hz", output.isFinite())
                peak = maxOf(peak, kotlin.math.abs(output))
            }
            assertTrue("silent at $hz", peak > 0.0)
        }
    }

    private fun rms(sampleRate: Int, tunedHz: Int, listenHz: Int): Double {
        val band = NarrowBandpass(sampleRate)
        band.retune(tunedHz)
        var energy = 0.0
        val skip = sampleRate / 10
        val count = sampleRate / 2
        for (index in 0 until skip + count) {
            val sample = kotlin.math.sin(2.0 * Math.PI * listenHz * index / sampleRate)
            val output = band.process(sample)
            if (index >= skip) energy += output * output
        }
        return sqrt(energy / count)
    }
}
