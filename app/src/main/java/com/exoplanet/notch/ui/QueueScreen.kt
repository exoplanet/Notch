package com.exoplanet.notch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.exoplanet.notch.Playlist
import com.exoplanet.notch.PlaylistEntry

@Composable
internal fun QueueScreen(
    playlist: Playlist,
    currentMediaIndex: Int,
    loadedName: String?,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onDragEntry: (entryId: String, proposedOffset: Float, heights: Map<String, Int>) -> Float,
    onRemoveEntry: (String) -> Unit,
    onRemoveTrack: (String, Int) -> Unit,
    onSave: (String) -> Unit,
    onUpdate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var saveOpen by remember { mutableStateOf(false) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    val dragEntry = rememberUpdatedState(onDragEntry)
    val entries = playlist.entries
    val flats = playlist.flatten()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Queue", style = MaterialTheme.typography.headlineMedium, color = Ink)
        if (!loadedName.isNullOrBlank()) {
            Text(loadedName, color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { saveOpen = true },
                enabled = entries.isNotEmpty(),
            ) { Text("Save as playlist") }
            if (onUpdate != null) {
                TextButton(onClick = onUpdate) { Text("Update playlist") }
            }
        }
        val unavailable = entries.sumOf { entry ->
            when (entry) {
                is PlaylistEntry.SingleFile -> if (entry.unavailable) 1 else 0
                is PlaylistEntry.Album -> entry.tracks.count { it.unavailable }
            }
        }
        if (unavailable > 0) {
            Text(
                if (unavailable == 1) "1 track unavailable" else "$unavailable tracks unavailable",
                color = Amber,
            )
        }
        if (entries.isEmpty()) {
            Text(
                "Nothing queued",
                color = Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                val dragging = dragId == entry.id
                Column(
                    modifier = Modifier
                        .onSizeChanged { heights[entry.id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (entry) {
                        is PlaylistEntry.SingleFile -> {
                            val flat = flats.firstOrNull { it.entryId == entry.id }
                            QueueRow(
                                title = flat?.title ?: entry.metadata.title ?: "Audio",
                                subtitle = if (entry.unavailable) "Unavailable" else flat?.artist,
                                header = false,
                                highlighted = flat != null && flat.mediaIndex == currentMediaIndex,
                                flagged = entry.unavailable,
                                onClick = flat?.let { track -> { onSeek(track.mediaIndex) } },
                                onRemove = { onRemoveEntry(entry.id) },
                                onDrag = { delta ->
                                    dragId = entry.id
                                    dragOffset = dragEntry.value(entry.id, dragOffset + delta, heights)
                                },
                                onDragEnd = {
                                    dragId = null
                                    dragOffset = 0f
                                },
                            )
                        }
                        is PlaylistEntry.Album -> {
                            val missing = entry.tracks.count { it.unavailable }
                            val available = entry.tracks.size - missing
                            QueueRow(
                                title = entry.title,
                                subtitle = albumSubtitle(available, missing),
                                header = true,
                                highlighted = false,
                                onClick = null,
                                onRemove = { onRemoveEntry(entry.id) },
                                onDrag = { delta ->
                                    dragId = entry.id
                                    dragOffset = dragEntry.value(entry.id, dragOffset + delta, heights)
                                },
                                onDragEnd = {
                                    dragId = null
                                    dragOffset = 0f
                                },
                            )
                            entry.tracks.forEachIndexed { trackIndex, track ->
                                val flat = flats.firstOrNull { it.entryId == entry.id && it.trackIndex == trackIndex }
                                key("${entry.id}#$trackIndex") {
                                    QueueRow(
                                        title = track.title?.takeIf { it.isNotBlank() } ?: track.fileName,
                                        subtitle = if (track.unavailable) "Unavailable" else track.artist,
                                        header = false,
                                        indented = true,
                                        highlighted = flat != null && flat.mediaIndex == currentMediaIndex,
                                        flagged = track.unavailable,
                                        onClick = flat?.let { item -> { onSeek(item.mediaIndex) } },
                                        onRemove = { onRemoveTrack(entry.id, trackIndex) },
                                        onDrag = null,
                                        onDragEnd = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (saveOpen) {
        NameDialog(
            title = "Save as playlist",
            initial = loadedName.orEmpty(),
            confirmLabel = "Save",
            onDismiss = { saveOpen = false },
            onConfirm = { name ->
                saveOpen = false
                onSave(name)
            },
        )
    }
}

private fun albumSubtitle(available: Int, missing: Int): String {
    if (missing == 0) return if (available == 1) "1 track" else "$available tracks"
    if (available == 0) return if (missing == 1) "1 unavailable" else "$missing unavailable"
    return "$available tracks, $missing unavailable"
}

@Composable
private fun QueueRow(
    title: String,
    subtitle: String?,
    header: Boolean,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    flagged: Boolean = false,
    onRemove: () -> Unit,
    onDrag: ((Float) -> Unit)?,
    onDragEnd: () -> Unit,
    indented: Boolean = false,
) {
    val remove = rememberUpdatedState(onRemove)
    val drag = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onDragEnd)
    val dismissed = remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDrag != null) {
            Text(
                "⋮⋮",
                color = Muted,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { end.value() },
                            onDragCancel = { end.value() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                drag.value?.invoke(amount)
                            },
                        )
                    },
            )
        }
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.weight(1f),
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            onDismiss = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart && !dismissed.value) {
                    dismissed.value = true
                    remove.value()
                }
            },
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFB3261E), shape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text("Remove", color = Color.White)
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, shape)
                    .background(if (highlighted) Teal.copy(alpha = 0.35f) else Color.Transparent, shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Ink,
                        style = if (header) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = if (flagged) Amber else Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
