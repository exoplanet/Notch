package com.exoplanet.notch.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.documentfile.provider.DocumentFile
import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.exoplanet.notch.AlbumScanner
import com.exoplanet.notch.FlatTrack
import com.exoplanet.notch.NotchEngine
import com.exoplanet.notch.PlaybackService
import com.exoplanet.notch.Playlist
import com.exoplanet.notch.PlaylistEntry
import com.exoplanet.notch.TrackMetadataReader
import com.exoplanet.notch.TrackTags
import com.exoplanet.notch.toMediaItem
import com.exoplanet.notch.dsp.NotchBand
import com.exoplanet.notch.dsp.NotchSettings
import com.exoplanet.notch.testNoiseUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

internal val Ink: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE6E1E5) else Color(0xFF1A1C1E)

internal val Muted: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFC4C6CF) else Color(0xFF5C656E)

internal val Teal: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7EE0C6) else Color(0xFF0E6B5C)

private val Amber: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE7C07A) else Color(0xFF8A4E00)

private val Background: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF101418) else Color(0xFFE7EEEC)

internal val Panel: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1A2128) else Color(0xFFFFFFFF)

private val OnPrimary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF00382E) else Color(0xFFFFFFFF)

@Composable
fun NotchTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            background = Background,
            surface = Background,
            primary = Teal,
            onPrimary = OnPrimary,
            secondary = Amber,
            onSurface = Ink,
            onSurfaceVariant = Muted,
        )
    } else {
        lightColorScheme(
            background = Background,
            surface = Background,
            primary = Teal,
            onPrimary = OnPrimary,
            secondary = Amber,
            onSurface = Ink,
            onSurfaceVariant = Muted,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun NotchApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var albumJob by remember { mutableStateOf<Job?>(null) }
    var player by remember { mutableStateOf<Player?>(null) }
    var settings by remember { mutableStateOf(NotchEngine.current()) }
    var clipName by remember { mutableStateOf("Nothing loaded") }
    var queueIndex by remember { mutableStateOf(0) }
    var queueCount by remember { mutableStateOf(0) }
    var hasPrevious by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(AppPage.Player) }
    var menuOpen by remember { mutableStateOf(false) }
    var playQueue by remember { mutableStateOf(Playlist()) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    BackHandler(enabled = page != AppPage.Player) { page = AppPage.Player }

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
        current.shuffleModeEnabled = false
        repeatMode = current.repeatMode
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                durationMs = player.duration.coerceAtLeast(0L)
                player.playerError?.message?.let { errorText = it }
                if (!scrubbing) positionMs = player.currentPosition.coerceAtLeast(0L)
                queueIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                queueCount = player.mediaItemCount
                hasPrevious = player.hasPreviousMediaItem()
                hasNext = player.hasNextMediaItem()
                repeatMode = player.repeatMode
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    clipName = Playlist.trackTitle(
                        mediaItem.mediaMetadata.title,
                        mediaItem.localConfiguration?.uri?.lastPathSegment,
                    )
                }
                queueIndex = current.currentMediaItemIndex.coerceAtLeast(0)
                queueCount = current.mediaItemCount
                hasPrevious = current.hasPreviousMediaItem()
                hasNext = current.hasNextMediaItem()
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

    fun replaceQueue(entries: List<PlaylistEntry>, repeat: Int, label: String) {
        val current = player ?: return
        val next = Playlist(entries)
        val items = next.flatten().map { it.toMediaItem() }
        if (items.isEmpty()) return
        playQueue = next
        clipName = label
        errorText = null
        ensureNotificationPermission()
        current.shuffleModeEnabled = false
        current.repeatMode = repeat
        repeatMode = repeat
        current.setMediaItems(items, 0, 0L)
        current.prepare()
        current.play()
    }

    fun appendEntries(more: List<PlaylistEntry>) {
        val current = player ?: return
        val wasEmpty = playQueue.entries.isEmpty()
        var next = playQueue
        val added = mutableListOf<FlatTrack>()
        for (entry in more) {
            val before = next.flatten().size
            next = when (entry) {
                is PlaylistEntry.Album -> next.appendAlbum(entry)
                is PlaylistEntry.SingleFile -> next.appendAll(listOf(entry))
            }
            val flat = next.flatten()
            if (flat.size > before) added += flat.drop(before)
        }
        if (added.isEmpty()) return
        playQueue = next
        errorText = null
        ensureNotificationPermission()
        current.shuffleModeEnabled = false
        val items = added.map { it.toMediaItem() }
        if (wasEmpty) {
            clipName = Playlist.trackTitle(items.first().mediaMetadata.title, items.first().localConfiguration?.uri?.lastPathSegment)
            current.setMediaItems(items, 0, 0L)
            current.prepare()
            current.play()
        } else {
            current.addMediaItems(items)
        }
    }

    fun moveQueueEntry(from: Int, to: Int) {
        val current = player ?: return
        val move = playQueue.mediaMove(from, to) ?: return
        current.moveMediaItems(move.fromIndex, move.toIndexExclusive, move.newIndex)
        playQueue = playQueue.moveEntry(from, to)
    }

    fun removeQueueEntry(id: String) {
        val current = player ?: return
        val index = playQueue.entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val range = playQueue.mediaRange(index)
        current.removeMediaItems(range.first, range.last + 1)
        playQueue = playQueue.removeEntry(id)
        if (playQueue.entries.isEmpty()) clipName = "Nothing loaded"
    }

    fun removeQueueTrack(entryId: String, trackIndex: Int) {
        val current = player ?: return
        val flat = playQueue.flatten().firstOrNull { it.entryId == entryId && it.trackIndex == trackIndex } ?: return
        current.removeMediaItems(flat.mediaIndex, flat.mediaIndex + 1)
        playQueue = playQueue.removeTrack(entryId, trackIndex)
        if (playQueue.entries.isEmpty()) clipName = "Nothing loaded"
    }

    fun applyDrag(entryId: String, proposedOffset: Float, heights: Map<String, Int>): Float {
        var offset = proposedOffset
        var guard = 0
        while (guard++ < 24) {
            val entries = playQueue.entries
            val index = entries.indexOfFirst { it.id == entryId }
            if (index < 0) return 0f
            val nextHeight = entries.getOrNull(index + 1)?.let { (heights[it.id] ?: 120).toFloat() }
            val prevHeight = entries.getOrNull(index - 1)?.let { (heights[it.id] ?: 120).toFloat() }
            when {
                nextHeight != null && offset > nextHeight / 2f -> {
                    moveQueueEntry(index, index + 1)
                    offset -= nextHeight
                }
                prevHeight != null && offset < -(prevHeight / 2f) -> {
                    moveQueueEntry(index, index - 1)
                    offset += prevHeight
                }
                else -> return offset
            }
        }
        return offset
    }

    fun cycleRepeat() {
        val current = player ?: return
        val next = when (current.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        current.repeatMode = next
        repeatMode = next
    }

    val openAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || player == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        albumJob?.cancel()
        albumJob = scope.launch {
            val name = displayName(context, uri)
            val tags = withContext(Dispatchers.IO) {
                runCatching { TrackMetadataReader.read(context, uri) }.getOrDefault(TrackTags())
            }
            if (!isActive) return@launch
            val title = tags.title?.takeIf { it.isNotBlank() } ?: name
            replaceQueue(
                listOf(
                    PlaylistEntry.SingleFile(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        metadata = tags.copy(title = title),
                    ),
                ),
                repeat = Player.REPEAT_MODE_OFF,
                label = title,
            )
        }
    }

    val addFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty() || player == null) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
        }
        albumJob?.cancel()
        albumJob = scope.launch {
            val entries = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val tags = runCatching { TrackMetadataReader.read(context, uri) }.getOrDefault(TrackTags())
                    val name = displayName(context, uri)
                    PlaylistEntry.SingleFile(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        metadata = tags.copy(title = tags.title?.takeIf { it.isNotBlank() } ?: name),
                    )
                }
            }
            if (!isActive) return@launch
            appendEntries(entries)
        }
    }

    val addAlbum = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null || player == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        albumJob?.cancel()
        albumJob = scope.launch {
            val previousName = clipName
            val queueWasEmpty = playQueue.entries.isEmpty()
            if (queueWasEmpty) clipName = "Reading album…"
            val tracks = AlbumScanner.collect(context, treeUri)
            if (!isActive) return@launch
            if (tracks.isEmpty()) {
                if (queueWasEmpty) clipName = previousName
                errorText = "No audio files in that folder."
                return@launch
            }
            val albumTitle = tracks.firstNotNullOfOrNull { it.album?.trim()?.takeIf { name -> name.isNotEmpty() } }
                ?: treeDisplayName(context, treeUri)
                ?: "Album"
            appendEntries(
                listOf(
                    PlaylistEntry.Album(
                        id = UUID.randomUUID().toString(),
                        treeUri = treeUri.toString(),
                        title = albumTitle,
                        tracks = tracks,
                    ),
                ),
            )
        }
    }

    fun push(next: NotchSettings) {
        settings = next
        NotchEngine.replace(next)
    }

    Scaffold(containerColor = Background) { padding ->
        if (page == AppPage.Queue) {
            QueueScreen(
                playlist = playQueue,
                currentMediaIndex = queueIndex,
                onBack = { page = AppPage.Player },
                onSeek = { index ->
                    val current = player ?: return@QueueScreen
                    if (index !in 0 until current.mediaItemCount) return@QueueScreen
                    current.seekTo(index, 0L)
                    current.play()
                },
                onDragEntry = { entryId, offset, heights -> applyDrag(entryId, offset, heights) },
                onRemoveEntry = { id -> removeQueueEntry(id) },
                onRemoveTrack = { entryId, trackIndex -> removeQueueTrack(entryId, trackIndex) },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        if (page != AppPage.Player) {
            AppPageScreen(
                page = page,
                onBack = { page = AppPage.Player },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Notch",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                )
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("Menu") }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Panel,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Guide") },
                            onClick = {
                                menuOpen = false
                                page = AppPage.Guide
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = {
                                menuOpen = false
                                page = AppPage.About
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Update") },
                            onClick = {
                                menuOpen = false
                                page = AppPage.Update
                            },
                        )
                    }
                }
            }
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
                        albumJob?.cancel()
                        albumJob = null
                        replaceQueue(
                            listOf(
                                PlaylistEntry.SingleFile(
                                    id = UUID.randomUUID().toString(),
                                    uri = testNoiseUri(context).toString(),
                                    metadata = TrackTags(title = "Test noise"),
                                ),
                            ),
                            repeat = Player.REPEAT_MODE_ONE,
                            label = "Test noise",
                        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val buttonPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                Button(
                    onClick = { addFiles.launch(arrayOf("audio/*")) },
                    enabled = player != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = buttonPadding,
                ) { Text("Add files", textAlign = TextAlign.Center, maxLines = 2) }
                Button(
                    onClick = { player?.seekToPreviousMediaItem() },
                    enabled = player != null && hasPrevious,
                    modifier = Modifier.weight(1f),
                    contentPadding = buttonPadding,
                ) { Text("Previous", textAlign = TextAlign.Center, maxLines = 1) }
                Button(
                    onClick = { player?.seekToNextMediaItem() },
                    enabled = player != null && hasNext,
                    modifier = Modifier.weight(1f),
                    contentPadding = buttonPadding,
                ) { Text("Next", textAlign = TextAlign.Center, maxLines = 1) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val buttonPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                Button(
                    onClick = { addAlbum.launch(null) },
                    enabled = player != null,
                    modifier = Modifier.weight(1.4f),
                    contentPadding = buttonPadding,
                ) { Text("Add album", textAlign = TextAlign.Center, maxLines = 1) }
                Button(
                    onClick = { page = AppPage.Queue },
                    modifier = Modifier.weight(0.8f),
                    contentPadding = buttonPadding,
                ) { Text("Queue", textAlign = TextAlign.Center, maxLines = 1) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(clipName, color = Muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { cycleRepeat() }, enabled = player != null) {
                    Text(repeatLabel(repeatMode))
                }
            }
            Playlist.positionLabel(queueIndex, queueCount)?.let { label ->
                Text(label, color = Muted)
            }
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

private fun treeDisplayName(context: Context, treeUri: Uri): String? {
    return DocumentFile.fromTreeUri(context, treeUri)?.name?.trim()?.takeIf { it.isNotEmpty() }
}

private fun repeatLabel(mode: Int): String {
    return when (mode) {
        Player.REPEAT_MODE_ALL -> "Repeat all"
        Player.REPEAT_MODE_ONE -> "Repeat one"
        else -> "Repeat off"
    }
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
