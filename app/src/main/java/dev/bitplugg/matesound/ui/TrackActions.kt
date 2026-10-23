package dev.bitplugg.matesound.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.data.model.ScTrack
import kotlinx.coroutines.launch

class TrackActions(
    val like: (ScTrack) -> Unit,
    val download: (ScTrack) -> Unit,
)

@Composable
fun rememberTrackActions(): TrackActions {
    val container = LocalAppContainer.current
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    return remember(container, settings.token) {
        TrackActions(
            like = { track ->
                scope.launch {
                    runCatching {
                        if (settings.token != null) {
                            container.repository.like(
                                track,
                                !container.library.isLiked(track.id),
                            )
                        } else {
                            container.library.toggleLike(track)
                        }
                    }
                }
            },
            download = { track -> container.downloads.download(track) },
        )
    }
}

@Composable
fun TrackItem(
    track: ScTrack,
    onClick: () -> Unit,
) {
    val container = LocalAppContainer.current
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val library by container.library.data.collectAsStateWithLifecycle()
    val actions = rememberTrackActions()
    TrackRow(
        track = track,
        isCurrent = playerState.mediaId == track.id,
        isPlaying = playerState.isPlaying,
        isLiked = library.likes.any { it.id == track.id },
        isDownloaded = library.downloads.any { it.track.id == track.id },
        onClick = onClick,
        onLike = { actions.like(track) },
        onDownload = { actions.download(track) },
    )
}
