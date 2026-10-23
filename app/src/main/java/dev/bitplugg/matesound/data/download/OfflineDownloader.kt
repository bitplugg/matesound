package dev.bitplugg.matesound.data.download

import android.content.Context
import dev.bitplugg.matesound.data.local.LibraryStore
import dev.bitplugg.matesound.data.local.Prefs
import dev.bitplugg.matesound.data.model.DownloadedTrack
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.data.repo.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class DownloadJob(
    val track: ScTrack,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val isRunning: Boolean get() = error == null && progress < 1f
}

/**
 * Downloads SoundCloud's HLS stream and stitches it into one ordinary file so
 * ExoPlayer can play it back offline without any extra storage layer.
 */
class OfflineDownloader(
    private val context: Context,
    private val http: OkHttpClient,
    private val repository: MusicRepository,
    private val library: LibraryStore,
    private val prefs: Prefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<Map<Long, DownloadJob>>(emptyMap())
    val jobs: StateFlow<Map<Long, DownloadJob>> = _jobs.asStateFlow()

    private val root: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("downloads")

    fun download(track: ScTrack) {
        if (_jobs.value[track.id]?.isRunning == true) return
        if (library.downloaded(track.id) != null) return
        _jobs.value = _jobs.value + (track.id to DownloadJob(track))
        scope.launch {
            runCatching {
                val settings = prefs.current()
                val (url, isHls) = repository.streamUrl(track, settings.quality)
                val dir = root.resolve(track.id.toString())
                dir.deleteRecursively()
                dir.mkdirs()
                val file = if (isHls) downloadHls(track, url, dir) else downloadProgressive(track, url, dir)
                val download = DownloadedTrack(
                    track = track,
                    filePath = file.absolutePath,
                    isHls = false,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = file.length(),
                )
                library.addDownload(download)
                _jobs.value = _jobs.value + (track.id to DownloadJob(track, progress = 1f))
                scope.launch {
                    kotlinx.coroutines.delay(2500)
                    _jobs.value = _jobs.value - track.id
                }
            }.onFailure { error ->
                _jobs.value = _jobs.value + (
                    track.id to DownloadJob(track, error = error.message ?: "download failed")
                    )
            }
        }
    }

    fun delete(track: ScTrack) {
        scope.launch {
            root.resolve(track.id.toString()).deleteRecursively()
            library.removeDownload(track.id)
            _jobs.value = _jobs.value - track.id
        }
    }

    private suspend fun downloadProgressive(track: ScTrack, url: String, dir: File): File {
        val extension = when {
            url.contains(".mp3", true) -> "mp3"
            url.contains(".m4a", true) -> "m4a"
            else -> "mp3"
        }
        val output = dir.resolve("audio.$extension")
        fetchToFile(url, output) { fraction ->
            _jobs.value = _jobs.value + (track.id to DownloadJob(track, progress = fraction * 0.98f))
        }
        return output
    }

    private suspend fun downloadHls(track: ScTrack, masterUrl: String, dir: File): File {
        var playlistUrl = masterUrl
        var playlist = fetchText(playlistUrl)

        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val variants = parseVariants(playlist, playlistUrl)
            val best = variants.maxByOrNull { it.bandwidth }
                ?: throw IllegalStateException("No HLS variants")
            playlistUrl = best.url
            playlist = fetchText(playlistUrl)
        }
        if (playlist.contains("METHOD=AES-128")) {
            throw IllegalStateException("Encrypted stream is not supported")
        }

        val base = playlistUrl.toHttpUrlOrNull() ?: throw IllegalStateException("Bad playlist url")
        val mapUri = MAP_PATTERN.find(playlist)?.groupValues?.getOrNull(1)?.let { base.resolve(it) }
        val segments = playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { base.resolve(it) }
            .toList()
        if (segments.isEmpty()) throw IllegalStateException("Empty HLS playlist")

        val isFragmented = mapUri != null || segments.any { it.toString().contains(".m4s") }
        val output = dir.resolve(if (isFragmented) "audio.mp4" else "audio.ts")

        FileOutputStream(output).use { sink ->
            if (mapUri != null) {
                writeUrlToStream(mapUri.toString(), sink)
            }
            segments.forEachIndexed { index, segment ->
                writeUrlToStream(segment.toString(), sink)
                val fraction = ((index + 1).toFloat() / segments.size) * 0.98f
                _jobs.value = _jobs.value + (track.id to DownloadJob(track, progress = fraction))
            }
        }
        return output
    }

    private data class Variant(val url: String, val bandwidth: Long)

    private fun parseVariants(playlist: String, baseUrl: String): List<Variant> {
        val base = baseUrl.toHttpUrlOrNull() ?: return emptyList()
        val lines = playlist.lines()
        val variants = mutableListOf<Variant>()
        for (index in lines.indices) {
            if (!lines[index].startsWith("#EXT-X-STREAM-INF")) continue
            val bandwidth = BANDWIDTH_PATTERN.find(lines[index])?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val uriLine = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
            val url = base.resolve(uriLine.trim()) ?: continue
            variants += Variant(url.toString(), bandwidth)
        }
        return variants
    }

    private fun fetchText(url: String): String = fetch(url).use { response ->
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for playlist")
        response.body?.string().orEmpty()
    }

    private fun writeUrlToStream(url: String, sink: FileOutputStream) {
        fetch(url).use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for segment")
            val body = response.body ?: throw IllegalStateException("Empty segment")
            body.byteStream().use { it.copyTo(sink) }
        }
    }

    private suspend fun fetchToFile(
        url: String,
        output: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        fetch(url).use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty body")
            val total = body.contentLength()
            output.outputStream().use { sink ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        onProgress(1f)
    }

    private fun fetch(url: String): okhttp3.Response {
        val httpUrl: HttpUrl = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Bad url")
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        return http.newCall(request).execute()
    }

    private companion object {
        const val USER_AGENT = "matesound/1.0 (Android)"
        val MAP_PATTERN = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"")
        val BANDWIDTH_PATTERN = Regex("BANDWIDTH=(\\d+)")
    }
}
