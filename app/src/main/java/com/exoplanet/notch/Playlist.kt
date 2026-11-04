package com.exoplanet.notch

object Playlist {
    fun positionLabel(index: Int, count: Int): String? {
        if (count <= 0) return null
        val n = index.coerceIn(0, count - 1) + 1
        return "$n / $count"
    }

    fun trackTitle(metadataTitle: CharSequence?, uriPath: String?): String {
        val fromMeta = metadataTitle?.toString()?.trim().orEmpty()
        if (fromMeta.isNotEmpty()) return fromMeta
        val fromPath = uriPath?.trim().orEmpty()
        if (fromPath.isNotEmpty()) return fromPath
        return "Audio"
    }
}
