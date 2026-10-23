package dev.bitplugg.matesound.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.data.model.ScPlaylist
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackItem(
    track: ScTrack,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val library by container.library.data.collectAsStateWithLifecycle()
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val actions = rememberTrackActions()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var cloudPlaylists by remember { mutableStateOf<List<ScPlaylist>>(emptyList()) }
    var loadingPlaylists by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val downloaded = library.downloads.any { it.track.id == track.id }

    TrackRow(
        track = track,
        isCurrent = playerState.mediaId == track.id,
        isPlaying = playerState.isPlaying,
        isLiked = library.likes.any { it.id == track.id },
        isDownloaded = downloaded,
        onClick = onClick,
        onLike = { actions.like(track) },
        onDownload = { actions.download(track) },
        onMore = { showMenu = true },
    )

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = track.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                MenuRow(Icons.Rounded.PlaylistPlay, stringResource(R.string.play_next)) {
                    container.player.playNext(track)
                    showMenu = false
                }
                MenuRow(Icons.Rounded.QueueMusic, stringResource(R.string.add_to_queue)) {
                    container.player.addToQueue(track)
                    showMenu = false
                }
                MenuRow(Icons.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) {
                    showMenu = false
                    showAddToPlaylist = true
                    if (settings.token != null) {
                        loadingPlaylists = true
                        scope.launch {
                            cloudPlaylists = runCatching {
                                container.repository.myPlaylists()
                            }.getOrDefault(emptyList())
                            loadingPlaylists = false
                        }
                    }
                }
                MenuRow(
                    if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    if (downloaded) {
                        stringResource(R.string.delete_download)
                    } else {
                        stringResource(R.string.download)
                    },
                ) {
                    if (downloaded) container.downloads.delete(track) else actions.download(track)
                    showMenu = false
                }
                MenuRow(Icons.Rounded.Share, stringResource(R.string.share)) {
                    val url = track.permalinkUrl ?: "https://soundcloud.com"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(
                        Intent.createChooser(send, context.getString(R.string.share)),
                    )
                    showMenu = false
                }
                if (onRemove != null) {
                    MenuRow(Icons.Rounded.Add, stringResource(R.string.remove)) {
                        onRemove()
                        showMenu = false
                    }
                }
            }
        }
    }

    if (showAddToPlaylist) {
        ModalBottomSheet(onDismissRequest = { showAddToPlaylist = false }) {
            Column(
                Modifier
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.add_to_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                MenuRow(Icons.Rounded.Add, stringResource(R.string.create_playlist)) {
                    showAddToPlaylist = false
                    newTitle = ""
                    showCreatePlaylist = true
                }
                if (loadingPlaylists) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.loading))
                    }
                }
                if (library.localPlaylists.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.local_playlists),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    library.localPlaylists.forEach { playlist ->
                        MenuRow(Icons.Rounded.LibraryMusic, playlist.title) {
                            scope.launch {
                                container.library.addTracksToLocalPlaylist(playlist.id, listOf(track))
                                toast(context.getString(R.string.saved_playlist))
                            }
                            showAddToPlaylist = false
                        }
                    }
                }
                if (cloudPlaylists.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.my_playlists),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    cloudPlaylists.forEach { playlist ->
                        MenuRow(Icons.Rounded.LibraryMusic, playlist.title.orEmpty()) {
                            scope.launch {
                                saving = true
                                runCatching {
                                    container.repository.addTracksToPlaylist(
                                        playlist.id,
                                        listOf(track),
                                    )
                                }.onSuccess {
                                    toast(context.getString(R.string.saved_playlist))
                                }.onFailure {
                                    toast(it.message.orEmpty())
                                }
                                saving = false
                            }
                            showAddToPlaylist = false
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text(stringResource(R.string.create_playlist)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank() && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            if (settings.token != null) {
                                runCatching {
                                    container.repository.createPlaylist(
                                        newTitle.trim(),
                                        listOf(track),
                                    )
                                }.onSuccess {
                                    toast(context.getString(R.string.saved_playlist))
                                    showCreatePlaylist = false
                                }.onFailure {
                                    toast(it.message.orEmpty())
                                }
                            } else {
                                container.library.addLocalPlaylist(newTitle.trim(), listOf(track))
                                toast(context.getString(R.string.saved_playlist))
                                showCreatePlaylist = false
                            }
                            saving = false
                        }
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
