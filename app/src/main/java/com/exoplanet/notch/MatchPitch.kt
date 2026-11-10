package com.exoplanet.notch

import com.exoplanet.notch.dsp.NotchSettings
import kotlin.math.roundToInt

/** Octave moves for the in-app matcher. A result outside the notch range is refused. */
object MatchPitch {
    fun half(hz: Int): Int? = ((hz / 2.0).roundToInt()).takeIf { it in NotchSettings.MIN_HZ..NotchSettings.MAX_HZ }

    fun double(hz: Int): Int? = ((hz * 2.0).roundToInt()).takeIf { it in NotchSettings.MIN_HZ..NotchSettings.MAX_HZ }
}
