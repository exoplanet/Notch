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
    }

    @Test
    fun narrowBandPassesItsCenterAndRejectsAnOctaveAway() {
        val sampleRate = 48_000
        val center = rms(sampleRate, 4_000, listenHz = 4_000)
        val octave = rms(sampleRate, 4_000, listenHz = 8_000)
        assertTrue("center rms $center", center > 0.2)
        assertTrue("octave rms $octave vs center $center", octave < center * 0.1)
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
