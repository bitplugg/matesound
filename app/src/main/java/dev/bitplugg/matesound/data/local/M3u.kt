package dev.bitplugg.matesound.data.local

import dev.bitplugg.matesound.data.model.ScTrack

/** Simple M3U/M3U8 helpers used by local playlists. */
object M3u {
    fun build(tracks: List<ScTrack>): String = buildString {
        appendLine("#EXTM3U")
        tracks.forEach { track ->
            val seconds = (track.duration / 1000L).coerceAtLeast(0L)
            appendLine("#EXTINF:$seconds,${track.artistName} - ${track.title.orEmpty()}")
            appendLine(track.permalinkUrl ?: "https://api.soundcloud.com/tracks/${track.id}")
        }
    }

    /** Returns every http(s) link in the file, in order. */
    fun parseLinks(content: String): List<String> = content
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
        .distinct()
        .toList()
}
