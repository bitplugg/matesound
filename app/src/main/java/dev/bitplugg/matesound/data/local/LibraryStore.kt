package dev.bitplugg.matesound.data.local

import android.content.Context
import dev.bitplugg.matesound.data.model.DownloadedTrack
import dev.bitplugg.matesound.data.model.HistoryEntry
import dev.bitplugg.matesound.data.model.LibraryData
import dev.bitplugg.matesound.data.model.ScTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Small JSON-backed store for everything that lives on the device:
 * local likes, playback history and offline downloads.
 */
class LibraryStore(context: Context, private val json: Json) {

    private val file = File(context.filesDir, "library.json")
    private val mutex = Mutex()
    private val _data = MutableStateFlow(readFromDisk())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    fun downloaded(trackId: Long): DownloadedTrack? =
        _data.value.downloads.firstOrNull { it.track.id == trackId }

    fun isLiked(trackId: Long): Boolean = _data.value.likes.any { it.id == trackId }

    suspend fun toggleLike(track: ScTrack): Boolean = mutate { current ->
        val liked = current.likes.any { it.id == track.id }
        val likes = if (liked) {
            current.likes.filterNot { it.id == track.id }
        } else {
            listOf(track) + current.likes.filterNot { it.id == track.id }
        }
        current.copy(likes = likes) to !liked
    }

    suspend fun addHistory(track: ScTrack) = mutate { current ->
        val history = (listOf(HistoryEntry(track, System.currentTimeMillis())) +
            current.history.filterNot { it.track.id == track.id }).take(MAX_HISTORY)
        current.copy(history = history) to Unit
    }

    suspend fun clearHistory() = mutate { it.copy(history = emptyList()) to Unit }

    suspend fun addDownload(download: DownloadedTrack) = mutate { current ->
        val downloads = listOf(download) + current.downloads.filterNot { it.track.id == download.track.id }
        current.copy(downloads = downloads) to Unit
    }

    suspend fun removeDownload(trackId: Long) = mutate { current ->
        current.copy(downloads = current.downloads.filterNot { it.track.id == trackId }) to Unit
    }

    suspend fun clearLikes() = mutate { it.copy(likes = emptyList()) to Unit }

    private suspend fun <R> mutate(block: (LibraryData) -> Pair<LibraryData, R>): R {
        val result = mutex.withLock {
            val (updated, value) = block(_data.value)
            _data.value = updated
            value
        }
        persist()
        return result
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val snapshot = _data.value
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(LibraryData.serializer(), snapshot))
            if (!tmp.renameTo(file)) {
                file.writeText(json.encodeToString(LibraryData.serializer(), snapshot))
                tmp.delete()
            }
        }
    }

    private fun readFromDisk(): LibraryData {
        if (!file.exists()) return LibraryData()
        return runCatching {
            json.decodeFromString(LibraryData.serializer(), file.readText())
        }.getOrElse { LibraryData() }
    }

    private companion object {
        const val MAX_HISTORY = 100
    }
}
