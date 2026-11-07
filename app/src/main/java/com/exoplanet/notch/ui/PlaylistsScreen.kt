package com.exoplanet.notch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exoplanet.notch.SavedPlaylist
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistsScreen(
    playlists: List<SavedPlaylist>,
    onBack: () -> Unit,
    onNew: (String) -> Unit,
    onPlay: (SavedPlaylist) -> Unit,
    onAddToQueue: suspend (SavedPlaylist) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<SavedPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedPlaylist?>(null) }
    var newTarget by remember { mutableStateOf<String?>(null) }
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
                onClick = { newTarget = "New playlist" },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ordered, key = { it.id }) { playlist ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(playlist.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                        Text(trackLabel(playlist.trackCount()), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onPlay(playlist) }) { Text("Play") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        onAddToQueue(playlist)
                                    }
                                },
                            ) { Text("Add to queue") }
                            TextButton(onClick = { renameTarget = playlist }) { Text("Rename") }
                            TextButton(onClick = { deleteTarget = playlist }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    newTarget?.let { label ->
        NameDialog(
            title = label,
            initial = "Untitled",
            confirmLabel = "Create",
            onDismiss = { newTarget = null },
            onConfirm = { name ->
                newTarget = null
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) onNew(trimmed)
            },
        )
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
