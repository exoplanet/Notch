package com.exoplanet.notch

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlaylist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val entries: List<PlaylistEntry>,
) {
    fun trackCount(): Int {
        return entries.sumOf { entry ->
            when (entry) {
                is PlaylistEntry.SingleFile -> 1
                is PlaylistEntry.Album -> entry.tracks.size
            }
        }
    }
}

data class ResumePoint(
    val playlistId: String,
    val mediaIndex: Int,
    val positionMs: Long,
)

data class SavedLibrary(
    val playlists: List<SavedPlaylist> = emptyList(),
    val resume: ResumePoint? = null,
) {
    fun saveNew(id: String, name: String, entries: List<PlaylistEntry>, now: Long): SavedLibrary? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || id.isBlank() || entries.isEmpty()) return null
        val created = SavedPlaylist(
            id = id,
            name = trimmed,
            createdAt = now,
            updatedAt = now,
            entries = entries.map { it.forStorage() },
        )
        return upsert(created)
    }

    fun updateEntries(id: String, entries: List<PlaylistEntry>, now: Long): SavedLibrary? {
        val existing = playlists.find { it.id == id } ?: return null
        val stored = entries.map { it.forStorage() }
        if (existing.entries == stored) return this
        return upsert(existing.copy(entries = stored, updatedAt = now))
    }

    fun rename(id: String, name: String, now: Long): SavedLibrary? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val existing = playlists.find { it.id == id } ?: return null
        if (existing.name == trimmed) return this
        return upsert(existing.copy(name = trimmed, updatedAt = now))
    }

    fun delete(id: String): LibraryDeletion? {
        val existing = playlists.find { it.id == id } ?: return null
        val remaining = playlists.filterNot { it.id == id }
        val released = PlaylistUris.releaseAfterDelete(existing, remaining)
        val nextResume = if (resume?.playlistId == id) null else resume
        return LibraryDeletion(SavedLibrary(remaining, nextResume), released)
    }

    fun remember(point: ResumePoint): SavedLibrary {
        if (playlists.none { it.id == point.playlistId }) return this
        if (resume == point) return this
        return copy(resume = point)
    }

    fun clearResume(): SavedLibrary = if (resume == null) this else copy(resume = null)

    private fun upsert(playlist: SavedPlaylist): SavedLibrary {
        val index = playlists.indexOfFirst { it.id == playlist.id }
        val next = if (index < 0) {
            playlists + playlist
        } else {
            playlists.toMutableList().apply { this[index] = playlist }
        }
        return copy(playlists = next)
    }
}

data class LibraryDeletion(
    val library: SavedLibrary,
    val releasedUris: Set<String>,
)

internal object PlaylistUris {
    fun collect(playlist: SavedPlaylist): Set<String> = collect(playlist.entries)

    fun collect(entries: List<PlaylistEntry>): Set<String> {
        val uris = linkedSetOf<String>()
        for (entry in entries) {
            when (entry) {
                is PlaylistEntry.SingleFile -> if (entry.uri.isNotBlank()) uris += entry.uri
                is PlaylistEntry.Album -> {
                    if (entry.treeUri.isNotBlank()) uris += entry.treeUri
                    entry.tracks.forEach { track ->
                        if (track.uri.isNotBlank()) uris += track.uri
                    }
                }
            }
        }
        return uris
    }

    fun releaseAfterDelete(deleted: SavedPlaylist, remaining: List<SavedPlaylist>): Set<String> {
        val stillUsed = remaining.flatMap { collect(it) }.toSet()
        return collect(deleted) - stillUsed
    }
}

internal object PlaylistRefresh {
    fun refresh(
        entries: List<PlaylistEntry>,
        scan: (treeUri: String) -> AlbumScanResult,
        canRead: (uri: String) -> Boolean,
    ): List<PlaylistEntry> {
        return entries.map { entry ->
            when (entry) {
                is PlaylistEntry.SingleFile -> entry.copy(unavailable = !readable(entry.uri, canRead))
                is PlaylistEntry.Album -> refreshAlbum(entry, scanTree(entry.treeUri, scan), canRead)
            }
        }
    }

    private fun refreshAlbum(
        entry: PlaylistEntry.Album,
        scan: AlbumScanResult,
        canRead: (String) -> Boolean,
    ): PlaylistEntry.Album {
        val base = when (scan) {
            is AlbumScanResult.Ok -> mergeScan(entry.tracks, scan.tracks)
            AlbumScanResult.Failed -> entry.tracks
        }
        val present = (scan as? AlbumScanResult.Ok)?.tracks?.map { it.uri }?.toSet()
        val marked = base.map { track ->
            val missingFromScan = present != null && track.uri !in present
            track.copy(unavailable = missingFromScan || !readable(track.uri, canRead))
        }
        return entry.copy(tracks = marked)
    }

    /**
     * A file that was saved and is no longer in the folder stays in the list so the
     * queue can flag it. Fresh scan metadata wins when the same uri is still present.
     */
    private fun mergeScan(cached: List<AlbumTrack>, scanned: List<AlbumTrack>): List<AlbumTrack> {
        val seen = scanned.map { it.uri }.toSet()
        val missing = cached.filter { it.uri.isNotBlank() && it.uri !in seen }.distinctBy { it.uri }
        return AlbumOrder.sort(scanned + missing)
    }

    private fun scanTree(treeUri: String, scan: (String) -> AlbumScanResult): AlbumScanResult {
        if (treeUri.isBlank()) return AlbumScanResult.Failed
        return try {
            scan(treeUri)
        } catch (_: Exception) {
            AlbumScanResult.Failed
        }
    }

    private fun readable(uri: String, canRead: (String) -> Boolean): Boolean {
        if (uri.isBlank()) return false
        return try {
            canRead(uri)
        } catch (_: Exception) {
            false
        }
    }
}

internal object PlaylistCodec {
    fun encode(library: SavedLibrary): String {
        val root = JSONObject()
        val playlists = JSONArray()
        library.playlists.forEach { playlist ->
            val item = JSONObject()
                .put("id", playlist.id)
                .put("name", playlist.name)
                .put("createdAt", playlist.createdAt)
                .put("updatedAt", playlist.updatedAt)
                .put("entries", JSONArray().apply {
                    playlist.entries.forEach { put(entryJson(it.forStorage())) }
                })
            playlists.put(item)
        }
        root.put("playlists", playlists)
        library.resume?.let { point ->
            root.put(
                "resume",
                JSONObject()
                    .put("playlistId", point.playlistId)
                    .put("mediaIndex", point.mediaIndex)
                    .put("positionMs", point.positionMs),
            )
        }
        return root.toString()
    }

    fun decode(raw: String?): SavedLibrary {
        if (raw.isNullOrBlank()) return SavedLibrary()
        return try {
            val root = JSONObject(raw)
            val playlists = mutableListOf<SavedPlaylist>()
            val array = root.optJSONArray("playlists") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                decodePlaylist(item)?.let { playlists += it }
            }
            val resume = decodeResume(root.optJSONObject("resume"), playlists)
            SavedLibrary(playlists, resume)
        } catch (_: Exception) {
            SavedLibrary()
        }
    }

    private fun decodePlaylist(json: JSONObject): SavedPlaylist? {
        val id = json.optString("id").trim()
        val name = json.optString("name").trim()
        if (id.isEmpty() || name.isEmpty()) return null
        val entries = mutableListOf<PlaylistEntry>()
        val array = json.optJSONArray("entries") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            decodeEntry(item)?.let { entries += it }
        }
        return SavedPlaylist(
            id = id,
            name = name,
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            entries = entries,
        )
    }

    private fun decodeEntry(json: JSONObject): PlaylistEntry? {
        return when (json.optString("type")) {
            "file" -> {
                val uri = json.optString("uri")
                val id = json.optString("id").trim()
                if (uri.isBlank() || id.isEmpty()) return null
                PlaylistEntry.SingleFile(
                    id = id,
                    uri = uri,
                    metadata = TrackTags(
                        title = text(json, "title"),
                        artist = text(json, "artist"),
                        album = text(json, "album"),
                        trackNumber = positive(json, "track"),
                        discNumber = positive(json, "disc"),
                    ),
                )
            }
            "album" -> {
                val id = json.optString("id").trim()
                val treeUri = json.optString("treeUri")
                if (id.isEmpty() || treeUri.isBlank()) return null
                val tracks = mutableListOf<AlbumTrack>()
                val array = json.optJSONArray("tracks") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    decodeTrack(item)?.let { tracks += it }
                }
                PlaylistEntry.Album(
                    id = id,
                    treeUri = treeUri,
                    title = json.optString("title").trim().ifEmpty { "Album" },
                    tracks = tracks,
                )
            }
            else -> null
        }
    }

    private fun decodeTrack(json: JSONObject): AlbumTrack? {
        val uri = json.optString("uri")
        if (uri.isBlank()) return null
        return AlbumTrack(
            uri = uri,
            fileName = json.optString("fileName").ifBlank { "audio" },
            relativeFolder = json.optString("relativeFolder"),
            discTag = positive(json, "disc"),
            trackNumber = positive(json, "track"),
            title = text(json, "title"),
            artist = text(json, "artist"),
            album = text(json, "album"),
        )
    }

    private fun decodeResume(json: JSONObject?, playlists: List<SavedPlaylist>): ResumePoint? {
        if (json == null) return null
        val id = json.optString("playlistId").trim()
        if (id.isEmpty() || playlists.none { it.id == id }) return null
        return ResumePoint(
            playlistId = id,
            mediaIndex = json.optInt("mediaIndex", 0).coerceAtLeast(0),
            positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
        )
    }

    private fun entryJson(entry: PlaylistEntry): JSONObject {
        return when (entry) {
            is PlaylistEntry.SingleFile -> JSONObject()
                .put("type", "file")
                .put("id", entry.id)
                .put("uri", entry.uri)
                .putText("title", entry.metadata.title)
                .putText("artist", entry.metadata.artist)
                .putText("album", entry.metadata.album)
                .putCount("track", entry.metadata.trackNumber)
                .putCount("disc", entry.metadata.discNumber)
            is PlaylistEntry.Album -> JSONObject()
                .put("type", "album")
                .put("id", entry.id)
                .put("treeUri", entry.treeUri)
                .put("title", entry.title)
                .put("tracks", JSONArray().apply {
                    entry.tracks.forEach { track ->
                        put(
                            JSONObject()
                                .put("uri", track.uri)
                                .put("fileName", track.fileName)
                                .put("relativeFolder", track.relativeFolder)
                                .putText("title", track.title)
                                .putText("artist", track.artist)
                                .putText("album", track.album)
                                .putCount("track", track.trackNumber)
                                .putCount("disc", track.discTag),
                        )
                    }
                })
        }
    }

    private fun text(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).trim().ifEmpty { null }
    }

    private fun positive(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optInt(key, -1).takeIf { it > 0 }
    }

    private fun JSONObject.putText(key: String, value: String?): JSONObject {
        if (!value.isNullOrBlank()) put(key, value)
        return this
    }

    private fun JSONObject.putCount(key: String, value: Int?): JSONObject {
        if (value != null && value > 0) put(key, value)
        return this
    }
}

internal fun PlaylistEntry.forStorage(): PlaylistEntry {
    return when (this) {
        is PlaylistEntry.SingleFile -> copy(unavailable = false)
        is PlaylistEntry.Album -> copy(tracks = tracks.map { it.copy(unavailable = false) })
    }
}

internal class PlaylistStore(private val preferences: SharedPreferences) {
    fun read(): SavedLibrary = PlaylistCodec.decode(preferences.getString(KEY_LIBRARY, null))

    fun write(library: SavedLibrary) {
        preferences.edit().putString(KEY_LIBRARY, PlaylistCodec.encode(library)).commit()
    }

    companion object {
        private const val PREFS_NAME = "notch-playlists"
        private const val KEY_LIBRARY = "library"

        fun open(context: Context): PlaylistStore {
            return PlaylistStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
        }

        fun release(context: Context, uris: Set<String>) {
            val resolver = context.contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            for (raw in uris) {
                val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
                try {
                    resolver.releasePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                }
            }
        }

        fun canRead(context: Context, uri: String): Boolean {
            if (uri.isBlank()) return false
            val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
            return try {
                context.contentResolver.openFileDescriptor(parsed, "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
    }
}
