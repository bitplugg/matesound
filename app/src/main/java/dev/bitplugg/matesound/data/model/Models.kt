package dev.bitplugg.matesound.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScUser(
    val id: Long = 0L,
    val username: String = "",
    val permalink: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("followers_count") val followersCount: Long? = null,
    @SerialName("followings_count") val followingsCount: Long? = null,
    @SerialName("track_count") val trackCount: Long? = null,
    @SerialName("playlist_count") val playlistCount: Long? = null,
    @SerialName("likes_count") val likesCount: Long? = null,
    val verified: Boolean? = null,
    val following: Boolean? = null,
    val description: String? = null,
    val city: String? = null,
    val country_code: String? = null,
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
data class ScFormat(
    val protocol: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
)

@Serializable
data class ScTranscoding(
    val url: String = "",
    val preset: String? = null,
    val quality: String? = null,
    val format: ScFormat? = null,
)

@Serializable
data class ScMedia(
    val transcodings: List<ScTranscoding> = emptyList(),
)

@Serializable
data class ScTrack(
    val id: Long = 0L,
    val title: String? = null,
    val user: ScUser? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val duration: Long = 0L,
    @SerialName("full_duration") val fullDuration: Long? = null,
    val streamable: Boolean = false,
    val policy: String? = null,
    val genre: String? = null,
    val description: String? = null,
    @SerialName("playback_count") val playbackCount: Long? = null,
    @SerialName("likes_count") val likesCount: Long? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("track_authorization") val trackAuthorization: String? = null,
    @SerialName("waveform_url") val waveformUrl: String? = null,
    @SerialName("user_favorite") val userFavorite: Boolean? = null,
    val media: ScMedia? = null,
    val access: String? = null,
    @SerialName("monetization_model") val monetizationModel: String? = null,
) {
    val artistName: String get() = user?.displayName ?: ""
    val isPreview: Boolean
        get() = policy != null && policy != "ALLOW" ||
            (fullDuration != null && fullDuration > duration + 1500)
}

@Serializable
data class ScPlaylist(
    val id: Long = 0L,
    val title: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val user: ScUser? = null,
    val tracks: List<ScTrack> = emptyList(),
    @SerialName("track_count") val trackCount: Int? = null,
    val duration: Long? = null,
    val description: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("is_album") val isAlbum: Boolean = false,
    val genre: String? = null,
    @SerialName("likes_count") val likesCount: Long? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val label: ScUser? = null,
) {
    val itemCount: Int get() = trackCount ?: tracks.size
}

@Serializable
data class TrackPage(
    val collection: List<ScTrack> = emptyList(),
    @SerialName("next_href") val nextHref: String? = null,
)

@Serializable
data class UserPage(
    val collection: List<ScUser> = emptyList(),
    @SerialName("next_href") val nextHref: String? = null,
)

@Serializable
data class PlaylistPage(
    val collection: List<ScPlaylist> = emptyList(),
    @SerialName("next_href") val nextHref: String? = null,
)

@Serializable
data class ChartEntry(
    val track: ScTrack? = null,
    val playlist: ScPlaylist? = null,
)

@Serializable
data class ChartsPage(
    val collection: List<ChartEntry> = emptyList(),
)

@Serializable
data class StreamResponse(
    val url: String = "",
)

@Serializable
data class HistoryEntry(
    val track: ScTrack,
    val playedAt: Long = 0L,
)

@Serializable
data class DownloadedTrack(
    val track: ScTrack,
    val filePath: String,
    val isHls: Boolean = false,
    val downloadedAt: Long = 0L,
    val sizeBytes: Long = 0L,
)

@Serializable
data class LibraryData(
    val likes: List<ScTrack> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val downloads: List<DownloadedTrack> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val localPlaylists: List<LocalPlaylist> = emptyList(),
)

@Serializable
data class LocalPlaylist(
    val id: String,
    val title: String,
    val tracks: List<ScTrack> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val itemCount: Int get() = tracks.size
}

/** Result of resolving a soundcloud.com link. */
sealed class Resolved {
    data class Track(val track: ScTrack) : Resolved()
    data class Playlist(val playlist: ScPlaylist) : Resolved()
    data class User(val user: ScUser) : Resolved()
}

enum class Quality(val key: String) {
    HIGH("high"),
    LOW("low");

    companion object {
        fun from(key: String?): Quality = entries.firstOrNull { it.key == key } ?: HIGH
    }
}

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class AppLanguage(val key: String) {
    SYSTEM("system"),
    RU("ru"),
    EN("en");

    companion object {
        fun from(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class EqualizerPreset(val key: String) {
    FLAT("flat"),
    BASS("bass"),
    VOCAL("vocal"),
    ELECTRONIC("electronic"),
    ROCK("rock");

    companion object {
        fun from(key: String?): EqualizerPreset = entries.firstOrNull { it.key == key } ?: FLAT
    }
}
