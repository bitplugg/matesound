package dev.bitplugg.matesound.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.ui.Artwork
import dev.bitplugg.matesound.ui.DownloadedRow
import dev.bitplugg.matesound.ui.EmptyState
import dev.bitplugg.matesound.ui.LoadingBox
import dev.bitplugg.matesound.ui.PlaylistRow
import dev.bitplugg.matesound.ui.TrackItem
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.vm.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
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

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_library)) },
            actions = {
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
                tab == 1 -> when {
                    state.loading && state.playlists.isEmpty() -> LoadingBox()
                    state.playlists.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = stringResource(R.string.empty_playlists),
                        subtitle = state.error,
                        action = if (state.error != null) {
                            {
                                Button(onClick = vm::refresh) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        } else {
                            null
                        },
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.playlists, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id) },
                            )
                        }
                    }
                }
                tab == 2 -> if (state.downloads.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.empty_downloads),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.downloads, key = { it.track.id }) { download ->
                            DownloadedRow(
                                download = download,
                                onClick = {
                                    vm.play(listOf(download.track), download.track)
                                },
                                onDelete = { vm.deleteDownload(download.track) },
                            )
                        }
                    }
                }
                else -> if (state.history.isEmpty()) {
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
            }
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
