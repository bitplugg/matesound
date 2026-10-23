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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.ui.EmptyState
import dev.bitplugg.matesound.ui.LoadingBox
import dev.bitplugg.matesound.ui.SectionTitle
import dev.bitplugg.matesound.ui.TrackItem
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.vm.GENRES
import dev.bitplugg.matesound.ui.vm.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
) {
    val vm: HomeViewModel = appViewModel { HomeViewModel(it.repository, it.player) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(GENRES) { genre ->
                FilterChip(
                    selected = state.genre == genre,
                    onClick = { vm.selectGenre(genre) },
                    label = { Text(genre.label) },
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading && state.tracks.isEmpty() -> LoadingBox()

                state.error != null && state.tracks.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.error_network),
                    subtitle = state.error,
                    action = {
                        androidx.compose.material3.Button(onClick = vm::refresh) {
                            Text(stringResource(R.string.retry))
                        }
                    },
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        SectionTitle(
                            text = if (state.genre.urn == null) {
                                stringResource(R.string.trends_now)
                            } else {
                                state.genre.label
                            },
                        )
                    }
                    items(state.tracks, key = { it.id }) { track ->
                        TrackItem(track = track, onClick = { vm.play(track) })
                    }
                    item { Box(Modifier.padding(bottom = 12.dp)) }
                }
            }
        }
    }
}
