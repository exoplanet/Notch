package com.exoplanet.notch.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.exoplanet.notch.NotchEngine
import com.exoplanet.notch.PlaybackService
import com.exoplanet.notch.dsp.NotchBand
import com.exoplanet.notch.dsp.NotchSettings
import com.exoplanet.notch.testNoiseUri
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Ink = Color(0xFFE6E1E5)
private val Muted = Color(0xFFC4C6CF)
private val Teal = Color(0xFF7EE0C6)
private val Amber = Color(0xFFE7C07A)
private val Background = Color(0xFF101418)
private val Panel = Color(0xFF1A2128)

@Composable
fun NotchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Background,
            primary = Teal,
            onPrimary = Color(0xFF00382E),
            secondary = Amber,
            onSurface = Ink,
            onSurfaceVariant = Muted,
        ),
        content = content,
    )
}

@Composable
fun NotchApp() {
    val context = LocalContext.current
    var player by remember { mutableStateOf<Player?>(null) }
    var settings by remember { mutableStateOf(NotchEngine.current()) }
    var clipName by remember { mutableStateOf("Nothing loaded") }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { player = runCatching { future.get() }.getOrNull() },
            context.mainExecutor,
        )
        onDispose { MediaController.releaseFuture(future) }
    }

    DisposableEffect(player) {
        val current = player ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                durationMs = player.duration.coerceAtLeast(0L)
                errorText = player.playerError?.message
                if (!scrubbing) positionMs = player.currentPosition.coerceAtLeast(0L)
            }
        }
        current.addListener(listener)
        onDispose { current.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying) {
        val current = player ?: return@LaunchedEffect
        while (isPlaying) {
            if (!scrubbing) positionMs = current.currentPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val openAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val current = player
        if (uri == null || current == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = displayName(context, uri)
        clipName = name
        ensureNotificationPermission()
        current.repeatMode = Player.REPEAT_MODE_OFF
        current.setMediaItem(mediaItem(uri, name))
        current.prepare()
        current.play()
    }

    fun push(next: NotchSettings) {
        settings = next
        NotchEngine.replace(next)
    }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Notch", style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text(
                "Each one removes its frequency completely. Octaves set each center's hertz width.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            NumberRow(
                label = "Width",
                value = settings.bandwidthTenths,
                min = NotchSettings.MIN_BANDWIDTH_TENTHS,
                max = NotchSettings.MAX_BANDWIDTH_TENTHS,
                maxDigits = 4,
                decimalPlaces = 1,
                showUnit = true,
                unit = "oct",
                accelerate = false,
                onValue = { next ->
                    val latest = NotchEngine.current()
                    if (latest.bandwidthTenths != next) push(latest.copy(bandwidthTenths = next))
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.width(48.dp))
                Spacer(Modifier.width(44.dp))
                Text(
                    "Center Frequency Hz",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Spacer(Modifier.width(44.dp))
            }
            settings.bands.forEachIndexed { index, band ->
                NumberRow(
                    value = band.frequencyHz,
                    min = NotchSettings.MIN_HZ,
                    max = NotchSettings.MAX_HZ,
                    maxDigits = 5,
                    enabled = band.enabled,
                    onEnabled = { checked ->
                        val latest = NotchEngine.current()
                        val current = latest.bands[index]
                        push(latest.copy(bands = latest.bands.replace(index, current.copy(enabled = checked))))
                    },
                    onValue = { next ->
                        val latest = NotchEngine.current()
                        val current = latest.bands[index]
                        if (current.frequencyHz != next) {
                            push(latest.copy(bands = latest.bands.replace(index, current.copy(frequencyHz = next))))
                        }
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val buttonPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                Button(
                    onClick = {
                        val current = player ?: return@Button
                        ensureNotificationPermission()
                        clipName = "Test noise"
                        current.repeatMode = Player.REPEAT_MODE_ONE
                        current.setMediaItem(mediaItem(testNoiseUri(context), "Test noise"))
                        current.prepare()
                        current.play()
                    },
                    enabled = player != null,
                    modifier = Modifier.weight(1.35f),
                    contentPadding = buttonPadding,
                ) { Text("Play test noise", textAlign = TextAlign.Center, maxLines = 2) }
                Button(
                    onClick = { openAudio.launch(arrayOf("audio/*")) },
                    enabled = player != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = buttonPadding,
                ) { Text("Open audio", textAlign = TextAlign.Center, maxLines = 2) }
                Button(
                    onClick = {
                        val current = player ?: return@Button
                        if (current.isPlaying) current.pause() else current.play()
                    },
                    enabled = player != null && durationMs > 0L,
                    modifier = Modifier.weight(0.75f),
                    contentPadding = buttonPadding,
                ) { Text(if (isPlaying) "Pause" else "Play", maxLines = 1) }
            }
            Text(clipName, color = Muted)
            if (durationMs > 0L) {
                Text("${formatTime(if (scrubbing) scrubMs else positionMs)} / ${formatTime(durationMs)}", color = Ink)
                Slider(
                    value = (if (scrubbing) scrubMs else positionMs).toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = {
                        scrubbing = true
                        scrubMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        player?.seekTo(scrubMs)
                        positionMs = scrubMs
                        scrubbing = false
                    },
                    valueRange = 0f..durationMs.toFloat(),
                )
            }
            errorText?.let { Text(it, color = Amber) }
        }
    }
}

@Composable
private fun NumberRow(
    value: Int,
    min: Int,
    max: Int,
    maxDigits: Int,
    onValue: (Int) -> Unit,
    label: String? = null,
    showUnit: Boolean = false,
    unit: String = "Hz",
    decimalPlaces: Int = 0,
    accelerate: Boolean = true,
    enabled: Boolean? = null,
    onEnabled: ((Boolean) -> Unit)? = null,
) {
    fun show(stored: Int) = formatAmount(stored, decimalPlaces)
    val draft = remember { mutableStateOf(show(value)) }
    val focused = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val applyLatest by rememberUpdatedState(onValue)
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(value) {
        if (!focused.value) draft.value = show(value)
    }

    fun commit() {
        val parsed = parseAmount(draft.value, decimalPlaces)
        val clamped = (parsed ?: currentValue).coerceIn(min, max)
        draft.value = show(clamped)
        if (clamped != currentValue) applyLatest(clamped)
    }

    fun step(delta: Int) {
        val base = parseAmount(draft.value, decimalPlaces) ?: currentValue
        val clamped = (base + delta).coerceIn(min, max)
        draft.value = show(clamped)
        if (clamped != currentValue) applyLatest(clamped)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label != null) {
            Text(label, color = Muted, modifier = Modifier.width(48.dp))
        }
        if (enabled != null && onEnabled != null) {
            RadioButton(
                selected = enabled,
                onClick = { onEnabled(!enabled) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = Teal,
                    unselectedColor = Muted,
                ),
            )
        }
        StepButton("−", onStep = { step(-it) }, accelerate = accelerate)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft.value,
                onValueChange = { raw ->
                    draft.value = sanitizeAmount(raw, maxDigits, decimalPlaces)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = if (enabled == false) Muted else Teal,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commit()
                        focusManager.clearFocus()
                    },
                ),
                cursorBrush = SolidColor(Teal),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        val now = it.isFocused
                        if (focused.value && !now) commit()
                        focused.value = now
                    },
            )
            if (showUnit) {
                Text(unit, color = Muted)
            }
        }
        StepButton("+", onStep = { step(it) }, accelerate = accelerate)
    }
}

@Composable
private fun StepButton(label: String, onStep: (Int) -> Unit, accelerate: Boolean) {
    val currentStep by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .pointerInput(accelerate) {
                detectTapGestures(
                    onPress = {
                        currentStep(1)
                        coroutineScope {
                            val job = launch {
                                delay(400)
                                var ticks = 0
                                while (isActive) {
                                    ticks += 1
                                    val step = if (!accelerate) {
                                        1
                                    } else {
                                        when {
                                            ticks < 12 -> 1
                                            ticks < 36 -> 10
                                            else -> 100
                                        }
                                    }
                                    currentStep(step)
                                    delay(50)
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Teal, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatAmount(stored: Int, decimalPlaces: Int): String {
    if (decimalPlaces <= 0) return stored.toString()
    val scale = 10.0.pow(decimalPlaces)
    return String.format(Locale.US, "%.${decimalPlaces}f", stored / scale)
}

private fun parseAmount(text: String, decimalPlaces: Int): Int? {
    if (decimalPlaces <= 0) return text.toIntOrNull()
    val number = text.toDoubleOrNull() ?: return null
    return (number * 10.0.pow(decimalPlaces)).roundToInt()
}

private fun sanitizeAmount(raw: String, maxDigits: Int, decimalPlaces: Int): String {
    if (decimalPlaces <= 0) return raw.filter { it.isDigit() }.take(maxDigits)
    val cleaned = buildString {
        var dotted = false
        for (ch in raw) {
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !dotted -> {
                    dotted = true
                    append(ch)
                }
            }
        }
    }
    return cleaned.take(maxDigits)
}

private fun List<NotchBand>.replace(index: Int, band: NotchBand): List<NotchBand> {
    return mapIndexed { i, existing -> if (i == index) band else existing }
}

private fun mediaItem(uri: Uri, title: String): MediaItem {
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index) ?: uri.lastPathSegment ?: "Audio"
        }
    }
    return uri.lastPathSegment ?: "Audio"
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
