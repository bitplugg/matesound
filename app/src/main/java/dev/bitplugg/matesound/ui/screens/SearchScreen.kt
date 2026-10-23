package dev.bitplugg.matesound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.model.Resolved
import dev.bitplugg.matesound.ui.EmptyState
import dev.bitplugg.matesound.ui.LoadingBox
import dev.bitplugg.matesound.ui.PlaylistRow
import dev.bitplugg.matesound.ui.TrackItem
import dev.bitplugg.matesound.ui.UserRow
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.vm.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val vm: SearchViewModel = appViewModel {
        SearchViewModel(it.repository, it.player, it.library)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.resolved) {
        when (val resolved = state.resolved) {
            is Resolved.Playlist -> {
                onOpenPlaylist(resolved.playlist.id)
                vm.clearResolved()
            }
            is Resolved.User -> {
                onOpenArtist(resolved.user.id)
                vm.clearResolved()
            }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.nav_search)) })

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    vm.submit()
                },
            ),
        )

        if (!state.searched && state.history.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.search_history),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::clearHistory) {
                    Text(stringResource(R.string.clear_search_history))
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.history, key = { it }) { query ->
                    AssistChip(
                        onClick = {
                            keyboard?.hide()
                            vm.applyQuery(query)
                        },
                        label = { Text(query) },
                    )
                }
            }
        }

        TabRow(selectedTabIndex = state.tab) {
            listOf(
                R.string.tab_tracks,
                R.string.tab_artists,
                R.string.tab_playlists,
            ).forEachIndexed { index, label ->
                Tab(
                    selected = state.tab == index,
                    onClick = { vm.selectTab(index) },
                    text = { Text(stringResource(label)) },
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            val hasResults = state.tracks.isNotEmpty() ||
                state.artists.isNotEmpty() ||
                state.playlists.isNotEmpty()
            when {
                state.loading -> LoadingBox()
                state.error != null && !hasResults -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.error_network),
                    subtitle = state.error,
                    action = {
                        Button(onClick = vm::retry) { Text(stringResource(R.string.retry)) }
                    },
                )
                !state.searched -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.empty_search),
                )
                else -> when (state.tab) {
                    0 -> if (state.tracks.isEmpty()) {
                        NoResults()
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.tracks, key = { it.id }) { track ->
                                TrackItem(track = track, onClick = { vm.play(track) })
                            }
                        }
                    }
                    1 -> if (state.artists.isEmpty()) {
                        NoResults()
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.artists, key = { it.id }) { user ->
                                UserRow(user = user, onClick = { onOpenArtist(user.id) })
                            }
                        }
                    }
                    else -> if (state.playlists.isEmpty()) {
                        NoResults()
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.playlists, key = { it.id }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoResults() {
    EmptyState(
        icon = Icons.Rounded.SearchOff,
        title = stringResource(R.string.no_results),
    )
}
