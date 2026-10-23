package dev.bitplugg.matesound.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.bitplugg.matesound.data.local.LibraryStore
import dev.bitplugg.matesound.data.local.Prefs
import dev.bitplugg.matesound.data.model.DownloadedTrack
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.data.repo.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED }

data class DownloadJob(
    val track: ScTrack,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
) {
    val isRunning: Boolean
        get() = status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING

    val isPaused: Boolean get() = status == DownloadStatus.PAUSED
}

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

    private val running = mutableMapOf<Long, Job>()
    private val limiter = Semaphore(2)

    private val root: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir).resolve("downloads")

    init {
        createNotificationChannel()
    }

    fun download(track: ScTrack) = enqueue(track)

    fun downloadAll(tracks: List<ScTrack>) {
        tracks.distinctBy { it.id }.forEach { enqueue(it) }
    }

    fun pause(track: ScTrack) {
        val job = running[track.id]
        job?.cancel()
        running.remove(track.id)
        val current = _jobs.value[track.id] ?: return
        updateJob(current.copy(status = DownloadStatus.PAUSED, error = null))
    }

    fun resume(track: ScTrack) {
        val current = _jobs.value[track.id]
        if (current?.status == DownloadStatus.RUNNING || current?.status == DownloadStatus.QUEUED) return
        enqueue(track)
    }

    fun retry(track: ScTrack) = resume(track)

    fun cancel(track: ScTrack) {
        running.remove(track.id)?.cancel()
        _jobs.value = _jobs.value - track.id
    }

    fun delete(track: ScTrack) {
        running.remove(track.id)?.cancel()
        scope.launch {
            root.resolve(track.id.toString()).deleteRecursively()
            library.removeDownload(track.id)
            _jobs.value = _jobs.value - track.id
        }
    }

    fun deleteAll() {
        running.values.forEach { it.cancel() }
        running.clear()
        scope.launch {
            root.deleteRecursively()
            library.clearDownloads()
            _jobs.value = emptyMap()
        }
    }

    private fun enqueue(track: ScTrack) {
        if (track.id == 0L || library.downloaded(track.id) != null) return
        val existing = _jobs.value[track.id]
        if (existing?.isRunning == true) return
        updateJob(DownloadJob(track, status = DownloadStatus.QUEUED))
        val job = scope.launch {
            limiter.withPermit { runDownload(track) }
        }
        running[track.id] = job
    }

    private suspend fun runDownload(track: ScTrack) {
        try {
            val settings = prefs.current()
            if (settings.downloadWifiOnly && !isOnUnmeteredNetwork()) {
                updateJob(DownloadJob(track, status = DownloadStatus.PAUSED, error = "Waiting for Wi-Fi"))
                return
            }
            updateJob(DownloadJob(track, status = DownloadStatus.RUNNING))
            val (url, isHls) = repository.streamUrl(track, settings.quality)
            val dir = root.resolve(track.id.toString())
            dir.deleteRecursively()
            dir.mkdirs()
            notifyProgress(track, 0f)
            val file = if (isHls) downloadHls(track, url, dir) else downloadProgressive(track, url, dir)
            val download = DownloadedTrack(
                track = track,
                filePath = file.absolutePath,
                isHls = false,
                downloadedAt = System.currentTimeMillis(),
                sizeBytes = file.length(),
            )
            library.addDownload(download)
            updateJob(DownloadJob(track, progress = 1f, status = DownloadStatus.DONE))
            notifyDone(track)
            delay(2500L)
            _jobs.value = _jobs.value - track.id
        } catch (cancelled: CancellationException) {
            val current = _jobs.value[track.id]
            if (current != null && current.status != DownloadStatus.FAILED && current.status != DownloadStatus.DONE) {
                updateJob(current.copy(status = DownloadStatus.PAUSED))
            }
            throw cancelled
        } catch (error: Exception) {
            updateJob(DownloadJob(track, status = DownloadStatus.FAILED, error = error.message ?: "download failed"))
        } finally {
            running.remove(track.id)
        }
    }

    private fun updateJob(job: DownloadJob) {
        _jobs.value = _jobs.value + (job.track.id to job)
    }

    private suspend fun downloadProgressive(track: ScTrack, url: String, dir: File): File {
        val extension = when {
            url.contains(".mp3", true) -> "mp3"
            url.contains(".m4a", true) -> "m4a"
            else -> "mp3"
        }
        val output = dir.resolve("audio.$extension")
        fetchToFile(url, output) { fraction -> onProgress(track, fraction * 0.98f) }
        return output
    }

    private suspend fun downloadHls(track: ScTrack, masterUrl: String, dir: File): File {
        var playlistUrl = masterUrl
        var playlist = fetchText(playlistUrl)
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val variants = parseVariants(playlist, playlistUrl)
            val best = variants.maxByOrNull { it.bandwidth } ?: throw IllegalStateException("No HLS variants")
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
                onProgress(track, ((index + 1).toFloat() / segments.size) * 0.98f)
            }
        }
        return output
    }

    private fun onProgress(track: ScTrack, fraction: Float) {
        val progress = fraction.coerceIn(0f, 1f)
        val current = _jobs.value[track.id] ?: return
        updateJob(current.copy(progress = progress))
        val pct = (progress * 100f).toInt()
        if (pct % 10 == 0) notifyProgress(track, progress)
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

    private suspend fun fetchToFile(url: String, output: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
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
        val request = Request.Builder().url(httpUrl).header("User-Agent", USER_AGENT).build()
        return http.newCall(request).execute()
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun notifyProgress(track: ScTrack, progress: Float) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(track.title.orEmpty())
            .setContentText("Downloading ${(progress * 100).toInt()}%")
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(notification)
    }

    private fun notifyDone(track: ScTrack) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(track.title.orEmpty())
            .setContentText("Downloaded")
            .setAutoCancel(true)
            .build()
        post(notification)
    }

    private fun post(notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val USER_AGENT = "matesound/1.0 (Android)"
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4242
        val MAP_PATTERN = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"")
        val BANDWIDTH_PATTERN = Regex("BANDWIDTH=(\\d+)")
    }
}
