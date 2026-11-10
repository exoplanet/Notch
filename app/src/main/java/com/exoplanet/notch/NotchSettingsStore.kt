package com.exoplanet.notch

import android.content.Context
import android.content.SharedPreferences
import com.exoplanet.notch.dsp.NotchBand
import com.exoplanet.notch.dsp.NotchSettings

/**
 * Remembers the width and the four notches across process restarts.
 * Writes are applied immediately so a later force-stop still sees them.
 */
internal class NotchSettingsStore(private val preferences: SharedPreferences) {
    fun read(): NotchSettings {
        val hundredths = when {
            preferences.contains(KEY_BANDWIDTH_HUNDREDTHS) -> preferences.getInt(
                KEY_BANDWIDTH_HUNDREDTHS,
                NotchSettings.DEFAULT_BANDWIDTH_HUNDREDTHS,
            )
            preferences.contains(KEY_BANDWIDTH_TENTHS) -> NotchSettings.hundredthsFromLegacyTenths(
                preferences.getInt(KEY_BANDWIDTH_TENTHS, 1),
            )
            else -> return NotchSettings()
        }
        return NotchSettings(
            bandwidthHundredths = hundredths,
            bands = List(NotchSettings.COUNT) { index ->
                val fallback = NotchSettings.DEFAULT_BANDS[index]
                NotchBand(
                    enabled = preferences.getBoolean(enabledKey(index), fallback.enabled),
                    frequencyHz = preferences.getInt(frequencyKey(index), fallback.frequencyHz),
                )
            },
        ).normalized()
    }

    fun write(settings: NotchSettings) {
        val stored = settings.normalized()
        val editor = preferences.edit()
        editor.putInt(KEY_BANDWIDTH_HUNDREDTHS, stored.bandwidthHundredths)
        editor.putInt(KEY_BANDWIDTH_TENTHS, NotchSettings.legacyTenths(stored.bandwidthHundredths))
        stored.bands.forEachIndexed { index, band ->
            editor.putBoolean(enabledKey(index), band.enabled)
            editor.putInt(frequencyKey(index), band.frequencyHz)
        }
        editor.commit()
    }

    companion object {
        private const val PREFS_NAME = "notch-settings"
        private const val KEY_BANDWIDTH_HUNDREDTHS = "bandwidth_hundredths"
        private const val KEY_BANDWIDTH_TENTHS = "bandwidth_tenths"

        fun open(context: Context): NotchSettingsStore {
            return NotchSettingsStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
        }

        private fun enabledKey(index: Int) = "band_${index}_enabled"

        private fun frequencyKey(index: Int) = "band_${index}_hz"
    }
}
