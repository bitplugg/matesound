package dev.bitplugg.matesound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.model.ScPlaylist
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.data.model.ScUser
import dev.bitplugg.matesound.ui.Artwork
import dev.bitplugg.matesound.ui.EmptyState
import dev.bitplugg.matesound.ui.LoadingBox
import dev.bitplugg.matesound.ui.PlaylistRow
import dev.bitplugg.matesound.ui.SectionTitle
import dev.bitplugg.matesound.ui.TrackItem
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.formatCount
import dev.bitplugg.matesound.ui.vm.ArtistViewModel
import dev.bitplugg.matesound.ui.vm.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val vm: ArtistViewModel = appViewModel { container ->
        ArtistViewModel(
            container.repository,
            container.player,
            container.prefs,
            createSavedStateHandle(),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.user?.displayName ?: stringResource(R.string.tab_artists)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
        )
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingBox()
                state.error != null && state.user == null -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.error_network),
                    subtitle = state.error,
                    action = { Button(onClick = vm::load) { Text(stringResource(R.string.retry)) } },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ArtistHeader(
                            user = state.user,
                            trackCount = state.tracks.size,
                            loggedIn = state.loggedIn,
                            following = state.following,
                            onToggleFollow = vm::toggleFollow,
                            onPlayAll = { vm.playAll() },
                        )
                    }
                    item {
                        ScrollableTabRow(selectedTabIndex = state.tab, edgePadding = 8.dp) {
                            val tabs = listOf(
                                R.string.tab_tracks,
                                R.string.tab_likes,
                                R.string.tab_playlists,
                                R.string.tab_albums,
                            )
                            tabs.forEachIndexed { index, label ->
                                Tab(
                                    selected = state.tab == index,
                                    onClick = { vm.selectTab(index) },
                                    text = { Text(stringResource(label)) },
                                )
                            }
                        }
                    }
                    when (state.tab) {
                        0 -> {
                            if (state.tracks.isEmpty()) {
                                item {
                                    EmptyState(
                                        icon = Icons.Rounded.MusicNote,
                                        title = stringResource(R.string.no_results),
                                    )
                                }
                            } else {
                                items(state.tracks, key = { it.id }) { track ->
                                    TrackItem(track = track, onClick = { vm.play(track) })
                                }
                            }
                        }
                        1 -> {
                            if (state.likes.isEmpty()) {
                                item {
                                    EmptyState(
                                        icon = Icons.Rounded.MusicNote,
                                        title = stringResource(R.string.no_results),
                                    )
                                }
                            } else {
                                items(state.likes, key = { it.id }) { track ->
                                    TrackItem(track = track, onClick = { vm.play(track) })
                                }
                            }
                        }
                        2 -> {
                            if (state.playlists.isEmpty()) {
                                item {
                                    EmptyState(
                                        icon = Icons.Rounded.MusicNote,
                                        title = stringResource(R.string.empty_playlists),
                                    )
                                }
                            } else {
                                items(state.playlists, key = { it.id }) { playlist ->
                                    PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                                }
                            }
                        }
                        3 -> {
                            if (state.albums.isEmpty()) {
                                item {
                                    EmptyState(
                                        icon = Icons.Rounded.MusicNote,
                                        title = stringResource(R.string.no_results),
                                    )
                                }
                            } else {
                                items(state.albums, key = { it.id }) { playlist ->
                                    PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistHeader(
    user: ScUser?,
    trackCount: Int,
    loggedIn: Boolean,
    following: Boolean,
    onToggleFollow: () -> Unit,
    onPlayAll: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = user?.avatarUrl,
            size = 104.dp,
            modifier = Modifier.clip(CircleShape),
            corner = 52.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = user?.displayName.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        val followers = user?.followersCount
        Text(
            text = buildString {
                if (followers != null) append("${formatCount(followers)} followers • ")
                append("$trackCount tracks")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!user?.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = user?.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayAll) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.play_all))
            }
            if (loggedIn) {
                OutlinedButton(onClick = onToggleFollow) {
                    Icon(
                        if (following) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(
                            if (following) R.string.following else R.string.follow,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
) {
    val vm: PlaylistViewModel = appViewModel { container ->
        PlaylistViewModel(
            container.repository,
            container.player,
            container.prefs,
            container.downloads,
            createSavedStateHandle(),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.playlist?.title ?: stringResource(R.string.tab_playlists)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
        )
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingBox()
                state.error != null && state.playlist == null -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.error_network),
                    subtitle = state.error,
                    action = { Button(onClick = vm::load) { Text(stringResource(R.string.retry)) } },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        PlaylistHeader(
                            playlist = state.playlist,
                            trackCount = state.tracks.size,
                            isOwner = state.isOwner,
                            onPlayAll = vm::playAll,
                            onDownloadAll = vm::downloadAll,
                            onEnqueueAll = vm::enqueueAll,
                            onRename = {
                                newTitle = state.playlist?.title.orEmpty()
                                renaming = true
                            },
                        )
                    }
                    if (state.tracks.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.MusicNote,
                                title = stringResource(R.string.no_results),
                            )
                        }
                    } else {
                        items(state.tracks, key = { it.id }) { track ->
                            TrackItem(
                                track = track,
                                onClick = { vm.play(track) },
                                onRemove = if (state.isOwner) {
                                    { vm.removeTrack(track) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rename(newTitle)
                        renaming = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistHeader(
    playlist: ScPlaylist?,
    trackCount: Int,
    isOwner: Boolean,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onEnqueueAll: () -> Unit,
    onRename: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(url = playlist?.artworkUrl, size = 120.dp, corner = 12.dp)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${playlist?.user?.displayName.orEmpty()} • $trackCount tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isOwner) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.rename))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayAll) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.play_all))
            }
            OutlinedButton(onClick = onDownloadAll) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.download_all))
            }
            IconButton(onClick = onEnqueueAll) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.enqueue_all),
                )
            }
        }
    }
}
