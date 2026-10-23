package dev.bitplugg.matesound.data.repo

import dev.bitplugg.matesound.data.local.LibraryStore
import dev.bitplugg.matesound.data.local.Prefs
import dev.bitplugg.matesound.data.model.ChartsPage
import dev.bitplugg.matesound.data.model.PlaylistPage
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.Resolved
import dev.bitplugg.matesound.data.model.ScPlaylist
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.data.model.ScUser
import dev.bitplugg.matesound.data.model.StreamResponse
import dev.bitplugg.matesound.data.model.TrackPage
import dev.bitplugg.matesound.data.model.UserPage
import dev.bitplugg.matesound.data.remote.AuthRequiredException
import dev.bitplugg.matesound.data.remote.ClientIdProvider
import dev.bitplugg.matesound.data.remote.ScApiException
import dev.bitplugg.matesound.data.remote.SoundCloudApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

class MusicRepository(
    private val api: SoundCloudApi,
    private val prefs: Prefs,
    private val library: LibraryStore,
    private val clientIds: ClientIdProvider,
    private val json: Json,
) {
    private data class CachedStream(val url: String, val isHls: Boolean, val at: Long)

    private val streamCache = ConcurrentHashMap<Long, CachedStream>()

    // ---------------------------------------------------------------- search

    suspend fun searchTracks(query: String, limit: Int = 30, offset: Int = 0): List<ScTrack> =
        api.get<TrackPage>(
            "/search/tracks",
            mapOf("q" to query, "limit" to "$limit", "offset" to "$offset"),
        ).collection

    suspend fun searchArtists(query: String, limit: Int = 30): List<ScUser> =
        api.get<UserPage>(
            "/search/users",
            mapOf("q" to query, "limit" to "$limit"),
        ).collection

    suspend fun searchPlaylists(query: String, limit: Int = 30): List<ScPlaylist> =
        api.get<PlaylistPage>(
            "/search/playlists",
            mapOf("q" to query, "limit" to "$limit"),
        ).collection

    // ---------------------------------------------------------------- charts

    suspend fun charts(genre: String? = null, limit: Int = 30): List<ScTrack> = withContext(Dispatchers.IO) {
        val genreParam = genre?.let { "soundcloud:genres:$it" }
        val params = buildMap {
            put("kind", "top")
            put("limit", "$limit")
            put("offset", "0")
            put("linked_partitioning", "1")
            if (genreParam != null) put("genre", genreParam)
        }
        val fromCharts = runCatching { parseChartTracks(api.get<ChartsPage>("/charts", params)) }
            .getOrDefault(emptyList())
        if (fromCharts.isNotEmpty()) return@withContext fromCharts
        // Charts endpoint often requires an authorized session; fall back to search.
        val fallbackQuery = genre?.let { "$it music" } ?: "trending music"
        runCatching { searchTracks(fallbackQuery, limit) }.getOrDefault(emptyList())
    }

    private fun parseChartTracks(page: ChartsPage): List<ScTrack> = page.collection.mapNotNull { entry ->
        entry.track ?: entry.playlist?.tracks?.firstOrNull()
    }

    // ---------------------------------------------------------------- content

    suspend fun track(id: Long): ScTrack = api.get("/tracks/$id")

    suspend fun artist(id: Long): ScUser = api.get("/users/$id")

    suspend fun artistTracks(id: Long, limit: Int = 50): List<ScTrack> =
        api.get<TrackPage>(
            "/users/$id/tracks",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        ).collection

    suspend fun artistLikes(id: Long, limit: Int = 30): List<ScTrack> =
        api.get<TrackPage>(
            "/users/$id/likes",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        ).collection

    suspend fun playlist(id: Long): ScPlaylist = api.get("/playlists/$id")

    suspend fun relatedTracks(trackId: Long, limit: Int = 20): List<ScTrack> =
        api.get<TrackPage>(
            "/tracks/$trackId/related",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        ).collection

    suspend fun artistPlaylists(id: Long, limit: Int = 50): List<ScPlaylist> =
        api.get<PlaylistPage>(
            "/users/$id/playlists",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        ).collection

    suspend fun artistAlbums(id: Long, limit: Int = 50): List<ScPlaylist> =
        api.get<PlaylistPage>(
            "/users/$id/albums",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        ).collection

    suspend fun resolve(url: String): ScTrack? = runCatching {
        api.get<ScTrack>("/resolve", mapOf("url" to url))
    }.getOrNull()

    /**
     * Resolves any soundcloud.com URL (track, playlist or user) into the
     * matching model. Used by deep links and by pasting a link in search.
     */
    suspend fun resolveAny(url: String): Resolved? = runCatching {
        val raw = api.request("/resolve", mapOf("url" to url))
        val obj = json.parseToJsonElement(raw).jsonObject
        when (obj["kind"]?.jsonPrimitive?.contentOrNull) {
            "track" -> Resolved.Track(json.decodeFromJsonElement(ScTrack.serializer(), obj))
            "playlist", "album" ->
                Resolved.Playlist(json.decodeFromJsonElement(ScPlaylist.serializer(), obj))
            "user" -> Resolved.User(json.decodeFromJsonElement(ScUser.serializer(), obj))
            else -> null
        }
    }.getOrNull()

    // ---------------------------------------------------------------- account

    suspend fun me(): ScUser {
        val user: ScUser = api.get("/me")
        prefs.setAccount(user.id, user.displayName, user.avatarUrl)
        return user
    }

    suspend fun myLikes(limit: Int = 100): List<ScTrack> {
        val raw = api.request(
            "/me/likes/tracks",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        )
        return parseTrackCollection(raw)
    }

    suspend fun myPlaylists(limit: Int = 50): List<ScPlaylist> {
        val primary = runCatching {
            parsePlaylistCollection(
                api.request("/me/playlists", mapOf("limit" to "$limit")),
            )
        }
        if (primary.isSuccess) return primary.getOrThrow()
        // Some accounts/API revisions only expose playlists through the user resource.
        val userId = prefs.current().userId
            ?: runCatching { me().id }.getOrNull()
            ?: throw (primary.exceptionOrNull() ?: ScApiException(404, "No playlists"))
        return parsePlaylistCollection(
            api.request("/users/$userId/playlists", mapOf("limit" to "$limit")),
        )
    }

    suspend fun like(track: ScTrack, liked: Boolean): Boolean {
        val method = if (liked) "POST" else "DELETE"
        runCatching { api.send(method, "/me/likes/tracks/${track.id}") }
            .onFailure { if (it is AuthRequiredException) throw it }
        library.toggleLike(track)
        return liked
    }

    suspend fun isFollowing(userId: Long): Boolean = runCatching {
        val raw = api.request("/me/followings/$userId")
        val obj = json.parseToJsonElement(raw).jsonObject
        obj["following"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: (obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() == userId)
    }.getOrDefault(false)

    suspend fun follow(userId: Long, follow: Boolean): Boolean {
        val method = if (follow) "POST" else "DELETE"
        api.send(method, "/me/followings/$userId")
        return follow
    }

    suspend fun myFollowings(limit: Int = 100): List<ScUser> {
        val raw = api.request(
            "/me/followings",
            mapOf("limit" to "$limit", "linked_partitioning" to "1"),
        )
        return parseUserCollection(raw)
    }

    suspend fun followingFeed(limit: Int = 50): List<ScTrack> {
        val raw = runCatching {
            api.request(
                "/stream",
                mapOf("limit" to "$limit", "linked_partitioning" to "1"),
            )
        }.getOrElse {
            api.request(
                "/me/followings/tracks",
                mapOf("limit" to "$limit", "linked_partitioning" to "1"),
            )
        }
        return parseTrackCollection(raw)
    }

    suspend fun syncLikes(limit: Int = 100): List<ScTrack> {
        val likes = myLikes(limit)
        library.replaceLikes(likes)
        return likes
    }

    // ------------------------------------------------------------- playlists

    suspend fun createPlaylist(
        title: String,
        tracks: List<ScTrack> = emptyList(),
        isPrivate: Boolean = true,
    ): ScPlaylist {
        val body = playlistBody(title, tracks, isPrivate)
        val response = api.sendJson("POST", "/playlists", body)
        return json.decodeFromString(ScPlaylist.serializer(), response)
    }

    suspend fun updatePlaylist(
        playlistId: Long,
        title: String,
        tracks: List<ScTrack>,
        isPrivate: Boolean = true,
    ): ScPlaylist {
        val body = playlistBody(title, tracks, isPrivate)
        val response = api.sendJson("PUT", "/playlists/$playlistId", body)
        return json.decodeFromString(ScPlaylist.serializer(), response)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        api.send("DELETE", "/playlists/$playlistId")
    }

    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<ScTrack>): ScPlaylist {
        val current = playlist(playlistId)
        val merged = (current.tracks + tracks).filter { it.id != 0L }.distinctBy { it.id }
        return updatePlaylist(
            playlistId = playlistId,
            title = current.title.orEmpty(),
            tracks = merged,
            isPrivate = true,
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long): ScPlaylist {
        val current = playlist(playlistId)
        val remaining = current.tracks.filter { it.id != 0L && it.id != trackId }
        return updatePlaylist(
            playlistId = playlistId,
            title = current.title.orEmpty(),
            tracks = remaining,
            isPrivate = true,
        )
    }

    private fun playlistBody(title: String, tracks: List<ScTrack>, isPrivate: Boolean): String =
        buildJsonObject {
            put("playlist", buildJsonObject {
                put("title", title)
                put("sharing", if (isPrivate) "private" else "public")
                putJsonArray("tracks") {
                    tracks.forEach { track ->
                        add(buildJsonObject { put("id", track.id) })
                    }
                }
            })
        }.toString()

    // ---------------------------------------------------------------- streams

    /**
     * SoundCloud serves HLS with a per-request `track_authorization` token.
     * Returns the actual media URL that ExoPlayer can play.
     */
    suspend fun streamUrl(track: ScTrack, quality: Quality = Quality.HIGH): Pair<String, Boolean> {
        streamCache[track.id]?.let { cached ->
            if (System.currentTimeMillis() - cached.at < STREAM_CACHE_TTL_MS) {
                return cached.url to cached.isHls
            }
        }
        val detailed = if (track.media?.transcodings.isNullOrEmpty()) track(track.id) else track
        val transcodings = detailed.media?.transcodings.orEmpty()
        if (transcodings.isEmpty()) throw ScApiException(404, "Track is not streamable")

        val picked = pickTranscoding(transcodings, quality)
            ?: throw ScApiException(404, "No compatible stream")

        val builder = picked.url.toHttpUrlOrNull()?.newBuilder()
            ?: throw ScApiException(400, "Bad stream url")
        builder.addQueryParameter("client_id", clientIds.get())
        detailed.trackAuthorization?.let { builder.addQueryParameter("track_authorization", it) }
        val body = api.requestAbsolute(builder.build().toString())
        val mediaUrl = runCatching {
            json.decodeFromString<StreamResponse>(body).url
        }.getOrElse { throw ScApiException(500, "Unreadable stream response") }
        if (mediaUrl.isBlank()) throw ScApiException(404, "Empty stream url")

        val isHls = picked.format?.protocol == "hls" ||
            mediaUrl.contains(".m3u8", ignoreCase = true) ||
            body.contains("m3u8", ignoreCase = true)
        streamCache[track.id] = CachedStream(mediaUrl, isHls, System.currentTimeMillis())
        return mediaUrl to isHls
    }

    suspend fun streamUrlById(id: Long, quality: Quality): Pair<String, Boolean> {
        streamCache[id]?.let { cached ->
            if (System.currentTimeMillis() - cached.at < STREAM_CACHE_TTL_MS) {
                return cached.url to cached.isHls
            }
        }
        return streamUrl(track(id), quality)
    }

    private fun pickTranscoding(
        transcodings: List<dev.bitplugg.matesound.data.model.ScTranscoding>,
        quality: Quality,
    ): dev.bitplugg.matesound.data.model.ScTranscoding? {
        // `abr_sq` transcodings require a real OAuth session, prefer fixed bitrates.
        val playable = transcodings.filter { t ->
            t.url.isNotBlank() &&
                t.format?.protocol == "hls" &&
                t.preset != "abr_sq"
        }
        val preferred = when (quality) {
            Quality.HIGH -> listOf("aac_160k", "aac_96k", "mp3_128k", "mp3_64k")
            Quality.LOW -> listOf("aac_96k", "mp3_64k", "aac_160k", "mp3_128k")
        }
        return preferred.firstNotNullOfOrNull { preset ->
            playable.firstOrNull { it.preset == preset }
        } ?: playable.firstOrNull() ?: transcodings.firstOrNull()
    }

    // ---------------------------------------------------------------- helpers

    private fun parsePlaylistCollection(raw: String): List<ScPlaylist> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["collection"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val kind = obj["kind"]?.jsonPrimitive?.contentOrNull
            if (kind != null && kind != "playlist" && kind != "album") return@mapNotNull null
            runCatching {
                json.decodeFromJsonElement(ScPlaylist.serializer(), obj)
            }.getOrNull()
        }
    }

    private fun parseTrackCollection(raw: String): List<ScTrack> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val collection = root["collection"]?.jsonArray ?: return emptyList()
        return collection.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val nested = obj["track"] as? JsonObject
            val target = nested ?: obj
            val kind = target["kind"]?.jsonPrimitive?.contentOrNull
            if (kind != null && kind != "track") return@mapNotNull null
            runCatching {
                json.decodeFromJsonElement(ScTrack.serializer(), target)
            }.getOrNull()
        }
    }

    private fun parseUserCollection(raw: String): List<ScUser> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["collection"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            runCatching {
                json.decodeFromJsonElement(ScUser.serializer(), obj)
            }.getOrNull()
        }
    }

    private companion object {
        const val STREAM_CACHE_TTL_MS = 10 * 60 * 1000L
    }
}
