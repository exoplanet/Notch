package com.exoplanet.notch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exoplanet.notch.MatchPitch
import com.exoplanet.notch.MatchPlayer
import com.exoplanet.notch.dsp.NotchBand
import com.exoplanet.notch.dsp.NotchSettings

@Composable
internal fun MatchScreen(
    bands: List<NotchBand>,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit,
    onUse: (index: Int, frequencyHz: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = remember { MatchPlayer() }
    var hz by remember { mutableIntStateOf(4_000) }
    var auditionHz by remember { mutableStateOf<Int?>(null) }
    var narrow by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val soundingHz = auditionHz ?: hz
    DisposableEffect(tone) {
        onDispose { tone.close() }
    }
    LaunchedEffect(soundingHz, narrow) {
        tone.frequencyHz = soundingHz
        tone.narrow = narrow
    }
    fun playSounding() {
        if (playing) return
        onPauseMusic()
        tone.frequencyHz = soundingHz
        tone.narrow = narrow
        playing = tone.start()
    }
    fun chooseCandidate(next: Int) {
        hz = next
        auditionHz = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Match", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(
            "A quiet tone to compare with what you hear. Use headphones and keep the phone volume low. This is not a hearing test.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1_000, 4_000, 8_000).forEach { preset ->
                TextButton(onClick = { chooseCandidate(preset) }) { Text(preset.toString()) }
            }
        }
        NumberRow(
            value = hz,
            min = NotchSettings.MIN_HZ,
            max = NotchSettings.MAX_HZ,
            maxDigits = 5,
            onValue = { chooseCandidate(it) },
            showUnit = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoundChoice("Sine", selected = !narrow, onClick = { narrow = false }, modifier = Modifier.weight(1f))
            SoundChoice("Narrow", selected = narrow, onClick = { narrow = true }, modifier = Modifier.weight(1f))
        }
        Button(
            onClick = {
                if (playing) {
                    tone.stop()
                    playing = false
                } else {
                    playSounding()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (playing) "Stop tone" else "Play tone") }
        val lower = MatchPitch.half(hz)
        val higher = MatchPitch.double(hz)
        Text(
            "An octave away can sound like the same pitch. Play the lower and higher tones. $hz Hz stays your match until you keep another one.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lower != null) {
                OctaveChoice(
                    hz = lower,
                    relation = "lower",
                    selected = auditionHz == lower,
                    onClick = {
                        val next = if (auditionHz == lower) null else lower
                        auditionHz = next
                        tone.frequencyHz = next ?: hz
                        playSounding()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (higher != null) {
                OctaveChoice(
                    hz = higher,
                    relation = "higher",
                    selected = auditionHz == higher,
                    onClick = {
                        val next = if (auditionHz == higher) null else higher
                        auditionHz = next
                        tone.frequencyHz = next ?: hz
                        playSounding()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (auditionHz != null) {
            Text(
                "Playing $auditionHz Hz. Your match is still $hz Hz.",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { auditionHz?.let { chooseCandidate(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Keep $auditionHz") }
        }
        Text("Write $hz Hz into a notch and turn that notch on.", color = Ink)
        bands.forEachIndexed { index, band ->
            Button(
                onClick = { onUse(index, hz) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Notch ${index + 1} · ${band.frequencyHz}", textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun OctaveChoice(
    hz: Int,
    relation: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (selected) "Playing $hz" else "$hz $relation"
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, textAlign = TextAlign.Center, maxLines = 2)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun SoundChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
