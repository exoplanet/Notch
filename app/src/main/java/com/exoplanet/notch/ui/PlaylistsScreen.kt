package com.exoplanet.notch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exoplanet.notch.PlaylistEntry
import com.exoplanet.notch.SavedPlaylist
import kotlinx.coroutines.launch

internal data class PlaylistDraft(
    val id: String,
    val isNew: Boolean,
    val name: String,
    val entries: List<PlaylistEntry>,
)

@Composable
internal fun PlaylistsScreen(
    playlists: List<SavedPlaylist>,
    editor: PlaylistDraft?,
    editorMessage: String?,
    onBack: () -> Unit,
    onStartNew: () -> Unit,
    onStartEdit: (SavedPlaylist) -> Unit,
    onPlay: (SavedPlaylist) -> Unit,
    onAddToQueue: suspend (SavedPlaylist) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onEditorName: (String) -> Unit,
    onAddEditorTracks: () -> Unit,
    onAddEditorAlbum: () -> Unit,
    onRemoveEditorEntry: (String) -> Unit,
    onSaveEditor: () -> Unit,
    onCancelEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (editor != null) {
        PlaylistEditor(
            draft = editor,
            message = editorMessage,
            onName = onEditorName,
            onAddTracks = onAddEditorTracks,
            onAddAlbum = onAddEditorAlbum,
            onRemove = onRemoveEditorEntry,
            onSave = onSaveEditor,
            onCancel = onCancelEditor,
            modifier = modifier,
        )
        return
    }
    var openMenuId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<SavedPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedPlaylist?>(null) }
    val scope = rememberCoroutineScope()
    val ordered = playlists.sortedByDescending { it.updatedAt }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(
                onClick = onStartNew,
                modifier = Modifier.weight(1.35f),
            ) {
                Text("New playlist", textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Text("Playlists", style = MaterialTheme.typography.headlineMedium, color = Ink)
        if (ordered.isEmpty()) {
            Text(
                "No saved playlists",
                color = Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ordered, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                playlist.name,
                                color = Ink,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                trackLabel(playlist.trackCount()),
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Box {
                            TextButton(onClick = { openMenuId = playlist.id }) { Text("...") }
                            DropdownMenu(
                                expanded = openMenuId == playlist.id,
                                onDismissRequest = { openMenuId = null },
                                containerColor = Panel,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play") },
                                    onClick = {
                                        openMenuId = null
                                        onPlay(playlist)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to queue") },
                                    onClick = {
                                        openMenuId = null
                                        scope.launch { onAddToQueue(playlist) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        openMenuId = null
                                        onStartEdit(playlist)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        openMenuId = null
                                        renameTarget = playlist
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        openMenuId = null
                                        deleteTarget = playlist
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    renameTarget?.let { playlist ->
        NameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) onRename(playlist.id, trimmed)
            },
        )
    }
    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Panel,
            title = { Text("Delete ${playlist.name}?", color = Ink) },
            text = {
                Text(
                    "This removes the saved playlist from this phone.",
                    color = Ink,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDelete(playlist.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistEditor(
    draft: PlaylistDraft,
    message: String?,
    onName: (String) -> Unit,
    onAddTracks: () -> Unit,
    onAddAlbum: () -> Unit,
    onRemove: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Back") }
            Button(
                onClick = onSave,
                enabled = draft.name.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (draft.isNew) "Create" else "Save")
            }
        }
        Text(
            if (draft.isNew) "New playlist" else "Edit playlist",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = onName,
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onAddTracks,
                modifier = Modifier.weight(1f),
            ) {
                Text("Add tracks", textAlign = TextAlign.Center, maxLines = 1)
            }
            Button(
                onClick = onAddAlbum,
                modifier = Modifier.weight(1f),
            ) {
                Text("Add album", textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        if (!message.isNullOrBlank()) {
            Text(
                message,
                color = Amber,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            trackLabel(draft.entries.sumOf { it.mediaCount }),
            color = Muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (draft.entries.isEmpty()) {
            Text(
                "Add tracks or an album, then save.",
                color = Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(draft.entries, key = { it.id }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entryTitle(entry), color = Ink, style = MaterialTheme.typography.bodyLarge)
                        Text(entrySubtitle(entry), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { onRemove(entry.id) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title, color = Ink) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun trackLabel(count: Int): String {
    return if (count == 1) "1 track" else "$count tracks"
}

private fun entryTitle(entry: PlaylistEntry): String {
    return when (entry) {
        is PlaylistEntry.SingleFile -> entry.metadata.title?.takeIf { it.isNotBlank() } ?: "Audio"
        is PlaylistEntry.Album -> entry.title
    }
}

private fun entrySubtitle(entry: PlaylistEntry): String {
    return when (entry) {
        is PlaylistEntry.SingleFile -> {
            if (entry.unavailable) "Unavailable"
            else entry.metadata.artist?.takeIf { it.isNotBlank() } ?: "Track"
        }
        is PlaylistEntry.Album -> {
            val count = entry.tracks.size
            if (count == 1) "Album, 1 track" else "Album, $count tracks"
        }
    }
}
