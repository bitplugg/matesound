package dev.bitplugg.matesound.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.bitplugg.matesound.data.local.LibraryStore
import dev.bitplugg.matesound.data.local.Prefs
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.data.repo.MusicRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class QueueItem(
    val id: Long,
    val title: String,
    val artist: String,
    val artwork: String?,
)

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val mediaId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val sleepRemainingMs: Long? = null,
    val error: String? = null,
)

class PlayerController(
    private val context: Context,
    private val repository: MusicRepository,
    private val library: LibraryStore,
    private val prefs: Prefs,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            _state.update { it.copy(error = error.message ?: "player error") }
        },
    )
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pendingPlay: PendingPlay? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var autoplayJob: Job? = null
    private val knownTracks = mutableMapOf<Long, ScTrack>()

    private data class PendingPlay(val tracks: List<ScTrack>, val index: Int)

    private var preloadJob: Job? = null

    init {
        connect()
        scope.launch {
            state.collect { s ->
                preloadNext(s)
            }
        }
    }

    private fun preloadNext(s: PlayerUiState) {
        if (!s.isPlaying) return
        if (preloadJob?.isActive == true) return
        if (s.durationMs <= 0 || s.positionMs < s.durationMs * 0.7) return
        val nextIndex = s.currentIndex + 1
        if (nextIndex >= s.queue.size) return
        val nextId = s.queue[nextIndex].id
        if (nextId == 0L) return
        preloadJob = scope.launch(Dispatchers.IO) {
            runCatching { repository.streamUrlById(nextId, prefs.current().quality) }
        }
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    _state.update { it.copy(connected = true) }
                    startTicker()
                    sync()
                    pendingPlay?.let { request ->
                        pendingPlay = null
                        play(request.tracks, request.index)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(connected = false, error = error.message ?: "player unavailable")
                    }
                }
        }, ContextCompat.getMainExecutor(context))
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            runCatching {
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    knownTracks[id]?.let { track ->
                        scope.launch { runCatching { library.addHistory(track) } }
                    }
                }
                sync()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.message ?: "playback error") }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) maybeAutoplayRelated()
        }
    }

    /**
     * When the queue finishes, continue with tracks related to the last one so
     * playback never stops at the end of an album/playlist.
     */
    private fun maybeAutoplayRelated() {
        val mediaController = controller ?: return
        if (autoplayJob?.isActive == true) return
        val lastId = mediaController.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        autoplayJob = scope.launch {
            val enabled = runCatching { prefs.current().autoplayRelated }.getOrDefault(false)
            if (!enabled) return@launch
            val related = runCatching { repository.relatedTracks(lastId, 20) }
                .getOrDefault(emptyList())
                .filter { it.streamable && it.id != 0L }
                .distinctBy { it.id }
                .filter { track -> track.id !in knownTracks }
                .take(10)
            if (related.isEmpty()) return@launch
            val start = mediaController.mediaItemCount
            related.forEach { track ->
                knownTracks[track.id] = track
                mediaController.addMediaItem(track.toMediaItem())
            }
            mediaController.seekToDefaultPosition(start)
            mediaController.prepare()
            mediaController.play()
        }
    }

    fun play(tracks: List<ScTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        tracks.forEach { knownTracks[it.id] = it }
        val mediaController = controller
        if (mediaController == null) {
            pendingPlay = PendingPlay(tracks, startIndex)
            connect()
            return
        }
        val items = tracks.map { it.toMediaItem() }
        mediaController.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        mediaController.prepare()
        mediaController.play()
        scope.launch {
            tracks.getOrNull(startIndex)?.let { library.addHistory(it) }
        }
    }

    fun playTrack(track: ScTrack) = play(listOf(track))

    fun toggle() {
        val mediaController = controller ?: return
        when {
            mediaController.isPlaying -> mediaController.pause()
            mediaController.playbackState == Player.STATE_IDLE -> {
                mediaController.prepare()
                mediaController.play()
            }
            else -> mediaController.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun playIndex(index: Int) {
        val mediaController = controller ?: return
        if (index in 0 until mediaController.mediaItemCount) {
            mediaController.seekToDefaultPosition(index)
            mediaController.play()
        }
    }

    fun playNext(track: ScTrack) {
        knownTracks[track.id] = track
        val mediaController = controller ?: return
        val index = (mediaController.currentMediaItemIndex + 1)
            .coerceIn(0, mediaController.mediaItemCount)
        mediaController.addMediaItem(index, track.toMediaItem())
    }

    fun addToQueue(track: ScTrack) {
        knownTracks[track.id] = track
        val mediaController = controller ?: return
        mediaController.addMediaItem(track.toMediaItem())
    }

    fun addAllToQueue(tracks: List<ScTrack>) {
        if (tracks.isEmpty()) return
        tracks.forEach { knownTracks[it.id] = it }
        val mediaController = controller ?: return
        mediaController.addMediaItems(tracks.map { it.toMediaItem() })
    }

    fun removeQueueItem(index: Int) {
        val mediaController = controller ?: return
        if (index in 0 until mediaController.mediaItemCount) {
            mediaController.removeMediaItem(index)
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val mediaController = controller ?: return
        if (from in 0 until mediaController.mediaItemCount &&
            to in 0 until mediaController.mediaItemCount &&
            from != to
        ) {
            mediaController.moveMediaItem(from, to)
        }
    }

    fun clearQueue() {
        val mediaController = controller ?: return
        mediaController.stop()
        mediaController.clearMediaItems()
    }

    fun queueTracks(): List<ScTrack> = _state.value.queue.mapNotNull { knownTracks[it.id] }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val mediaController = controller ?: return
        mediaController.seekTo((mediaController.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val mediaController = controller ?: return
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val mediaController = controller ?: return
        mediaController.repeatMode = when (mediaController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        scope.launch { prefs.setPlaybackSpeed(speed) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepRemainingMs = null) }
            return
        }
        sleepJob = scope.launch {
            var remaining = minutes * 60_000L
            while (remaining > 0 && isActive) {
                _state.update { it.copy(sleepRemainingMs = remaining) }
                delay(1_000L)
                remaining -= 1_000L
            }
            controller?.pause()
            _state.update { it.copy(sleepRemainingMs = null) }
        }
    }

    fun currentTrack(): ScTrack? = _state.value.mediaId?.let { knownTracks[it] }

    suspend fun resolveTrack(id: Long): ScTrack? = knownTracks[id] ?: runCatching { repository.track(id) }.getOrNull()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                syncPosition()
                delay(500L)
            }
        }
    }

    private fun syncPosition() {
        val mediaController = controller ?: return
        runCatching {
            _state.update {
                it.copy(
                    positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                    durationMs = mediaController.duration.coerceAtLeast(0L),
                    isPlaying = mediaController.isPlaying,
                )
            }
        }
    }

    private fun sync() {
        val mediaController = controller ?: return
        runCatching { syncFromController(mediaController) }
    }

    private fun syncFromController(mediaController: MediaController) {
        val mediaItem = mediaController.currentMediaItem
        val metadata = mediaItem?.mediaMetadata
        val queue = (0 until mediaController.mediaItemCount).map { index ->
            val item = mediaController.getMediaItemAt(index)
            QueueItem(
                id = item.mediaId.toLongOrNull() ?: 0L,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                artwork = item.mediaMetadata.artworkUri?.toString(),
            )
        }
        _state.update {
            it.copy(
                connected = true,
                isPlaying = mediaController.isPlaying,
                isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
                mediaId = mediaItem?.mediaId?.toLongOrNull(),
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                artwork = metadata?.artworkUri?.toString(),
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = mediaController.duration.coerceAtLeast(0L),
                shuffle = mediaController.shuffleModeEnabled,
                repeatMode = mediaController.repeatMode,
                speed = mediaController.playbackParameters.speed,
                queue = queue,
                currentIndex = mediaController.currentMediaItemIndex,
            )
        }
    }

    private fun ScTrack.toMediaItem(): MediaItem {
        val downloaded = library.downloaded(id)
        val uri = downloaded?.let { Uri.fromFile(File(it.filePath)) }
            ?: ResolvingMediaSourceFactory.trackUri(id)
        val artwork = artworkUrl?.let { Uri.parse(it) }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.orEmpty())
                    .setArtist(artistName)
                    .apply { artwork?.let { setArtworkUri(it) } }
                    .build(),
            )
            .build()
    }
}
