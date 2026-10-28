package com.exoplanet.notch

import com.exoplanet.notch.dsp.NotchSettings
import java.util.concurrent.atomic.AtomicReference

/**
 * Settings written on the main thread and read on the audio thread.
 * The player in [PlaybackService] owns [processor].
 */
object NotchEngine {
    private val settingsRef = AtomicReference(NotchSettings())
    val processor = NotchAudioProcessor()

    fun current(): NotchSettings = settingsRef.get()

    fun replace(settings: NotchSettings) {
        settingsRef.set(settings)
    }
}
