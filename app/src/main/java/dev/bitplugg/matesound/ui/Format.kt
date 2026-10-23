package dev.bitplugg.matesound.ui

import dev.bitplugg.matesound.data.model.ScTrack

fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fK".format(value / 1_000f)
    else -> value.toString()
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000f)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
    else -> "$bytes B"
}

fun bestArtwork(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return url
        .replace("-large.", "-t500x500.")
        .replace("-t67x67.", "-t500x500.")
        .replace("-small.", "-t500x500.")
        .replace("-badge.", "-t500x500.")
}

fun ScTrack.artworkOrUserAvatar(): String? = bestArtwork(artworkUrl ?: user?.avatarUrl)
