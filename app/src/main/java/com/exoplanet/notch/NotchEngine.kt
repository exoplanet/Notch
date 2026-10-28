package com.exoplanet.notch

import android.content.Context
import com.exoplanet.notch.dsp.NotchSettings
import java.util.concurrent.atomic.AtomicReference

/**
 * Settings written on the main thread and read on the audio thread.
 * [attach] loads the last saved settings before the activity or the player starts.
 * The player in [PlaybackService] owns [processor].
 */
object NotchEngine {
    private val settingsRef = AtomicReference(NotchSettings())
    private var store: NotchSettingsStore? = null
    val processor = NotchAudioProcessor()

    fun attach(context: Context) {
        if (store != null) return
        val opened = NotchSettingsStore.open(context)
        store = opened
        settingsRef.set(opened.read())
    }

    fun current(): NotchSettings = settingsRef.get()

    fun replace(settings: NotchSettings) {
        val next = settings.normalized()
        settingsRef.set(next)
        store?.write(next)
    }
}
