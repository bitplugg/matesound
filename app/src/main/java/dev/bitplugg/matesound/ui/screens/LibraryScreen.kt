package dev.bitplugg.matesound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.download.DownloadJob
import dev.bitplugg.matesound.data.download.DownloadStatus
import dev.bitplugg.matesound.data.model.LocalPlaylist
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.ui.Artwork
import dev.bitplugg.matesound.ui.DownloadedRow
import dev.bitplugg.matesound.ui.EmptyState
import dev.bitplugg.matesound.ui.LoadingBox
import dev.bitplugg.matesound.ui.PlaylistRow
import dev.bitplugg.matesound.ui.SectionTitle
import dev.bitplugg.matesound.ui.TrackItem
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.vm.LibraryViewModel
import dev.bitplugg.matesound.ui.vm.LibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenLocalPlaylist: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    val vm: LibraryViewModel = appViewModel { container ->
        LibraryViewModel(
            container.repository,
            container.library,
            container.prefs,
            container.auth,
            container.downloads,
            container.player,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_library)) },
            actions = {
                IconButton(
                    onClick = {
                        newTitle = ""
                        creating = true
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.create_playlist))
                }
                if (state.loggedIn) {
                    IconButton(onClick = vm::refresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.retry),
                        )
                    }
                    IconButton(onClick = { vm.logout() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.sign_out),
                        )
                    }
                }
            },
        )

        if (!state.loggedIn) {
            SignInCard(onSignIn)
        } else {
            AccountHeader(
                name = state.user?.displayName.orEmpty(),
                avatar = state.user?.avatarUrl,
            )
        }

        val tabs = listOf(
            R.string.liked_tracks,
            R.string.my_playlists,
            R.string.downloads,
            R.string.history,
            R.string.following,
        )
        ScrollableTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(stringResource(label)) },
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading && tab == 0 -> LoadingBox()
                state.error != null && tab == 0 && state.likes.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.error_network),
                    subtitle = state.error,
                    action = {
                        Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                    },
                )
                tab == 0 -> if (state.likes.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = stringResource(R.string.empty_library),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Button(
                                onClick = { vm.playAll(state.likes) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) { Text(stringResource(R.string.play_all)) }
                        }
                        items(state.likes, key = { it.id }) { track ->
                            TrackItem(track = track, onClick = { vm.play(state.likes, track) })
                        }
                    }
                }
                tab == 1 -> PlaylistsTab(
                    state = state,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenLocalPlaylist = onOpenLocalPlaylist,
                )
                tab == 2 -> DownloadsTab(
                    state = state,
                    onPlay = { track -> vm.play(listOf(track), track) },
                    onDelete = vm::deleteDownload,
                    onDeleteAll = { confirmDeleteAll = true },
                    onPause = vm::pauseDownload,
                    onResume = vm::resumeDownload,
                    onRetry = vm::retryDownload,
                    onCancel = vm::cancelDownload,
                )
                tab == 3 -> if (state.history.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.empty_history),
                    )
                } else {
                    val historyTracks = state.history.map { it.track }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.history, key = { it.track.id }) { entry ->
                            TrackItem(
                                track = entry.track,
                                onClick = { vm.play(historyTracks, entry.track) },
                            )
                        }
                    }
                }
                else -> if (!state.loggedIn) {
                    EmptyState(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.sign_in),
                        action = { Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) } },
                    )
                } else if (state.feed.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.following_feed),
                        subtitle = state.error,
                        action = {
                            Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                        },
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.feed, key = { it.id }) { track ->
                            TrackItem(track = track, onClick = { vm.play(state.feed, track) })
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
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
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        vm.createPlaylist(newTitle.trim())
                        creating = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.clear_downloads)) },
            text = { Text(stringResource(R.string.clear_downloads_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllDownloads()
                        confirmDeleteAll = false
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistsTab(
    state: LibraryUiState,
    onOpenPlaylist: (Long) -> Unit,
    onOpenLocalPlaylist: (String) -> Unit,
) {
    if (state.playlists.isEmpty() && state.localPlaylists.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.LibraryMusic,
            title = stringResource(R.string.empty_playlists),
            subtitle = if (!state.loggedIn) {
                stringResource(R.string.empty_local_playlists)
            } else {
                state.error
            },
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.localPlaylists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.local_playlists)) }
            items(state.localPlaylists, key = { it.id }) { playlist ->
                LocalPlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenLocalPlaylist(playlist.id) },
                )
            }
        }
        if (state.playlists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.my_playlists)) }
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistRow(playlist: LocalPlaylist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = playlist.tracks.firstOrNull()?.artworkUrl,
            size = 54.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.track_count, playlist.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.MoreVert, contentDescription = null)
    }
}

@Composable
private fun DownloadsTab(
    state: LibraryUiState,
    onPlay: (ScTrack) -> Unit,
    onDelete: (ScTrack) -> Unit,
    onDeleteAll: () -> Unit,
    onPause: (ScTrack) -> Unit,
    onResume: (ScTrack) -> Unit,
    onRetry: (ScTrack) -> Unit,
    onCancel: (ScTrack) -> Unit,
) {
    val active = state.jobs.values.filter { it.status != DownloadStatus.DONE }
    if (state.downloads.isEmpty() && active.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.empty_downloads),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (active.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.downloading)) }
            items(active, key = { it.track.id }) { job ->
                DownloadJobRow(
                    job = job,
                    onPause = { onPause(job.track) },
                    onResume = {
                        if (job.status == DownloadStatus.FAILED) {
                            onRetry(job.track)
                        } else {
                            onResume(job.track)
                        }
                    },
                    onCancel = { onCancel(job.track) },
                )
            }
        }
        if (state.downloads.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(stringResource(R.string.downloads))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDeleteAll) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_downloads),
                        )
                    }
                }
            }
            items(state.downloads, key = { it.track.id }) { download ->
                DownloadedRow(
                    download = download,
                    onClick = { onPlay(download.track) },
                    onDelete = { onDelete(download.track) },
                )
            }
        }
    }
}

@Composable
private fun DownloadJobRow(
    job: DownloadJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = job.track.artworkUrl,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = job.track.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (job.status) {
                        DownloadStatus.QUEUED -> stringResource(R.string.download_queued)
                        DownloadStatus.RUNNING -> stringResource(R.string.download_running)
                        DownloadStatus.PAUSED ->
                            job.error ?: stringResource(R.string.download_paused)
                        DownloadStatus.FAILED ->
                            job.error ?: stringResource(R.string.download_failed_short)
                        DownloadStatus.DONE -> stringResource(R.string.download_done)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel_download))
            }
            TextButton(
                onClick = if (job.status == DownloadStatus.RUNNING) onPause else onResume,
            ) {
                Text(
                    stringResource(
                        when (job.status) {
                            DownloadStatus.RUNNING -> R.string.pause
                            DownloadStatus.FAILED -> R.string.retry_download
                            else -> R.string.resume
                        },
                    ),
                )
            }
        }
        if (job.status == DownloadStatus.RUNNING || job.status == DownloadStatus.QUEUED) {
            LinearProgressIndicator(
                progress = { (job.progress).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sign_in_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.sign_in_card_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
        }
    }
}

@Composable
private fun AccountHeader(name: String, avatar: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = avatar, size = 40.dp, corner = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
