package dev.bitplugg.matesound.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.local.M3u
import dev.bitplugg.matesound.data.model.Resolved
import dev.bitplugg.matesound.ui.Artwork
import dev.bitplugg.matesound.ui.EmptyState
import dev.bitplugg.matesound.ui.LocalAppContainer
import dev.bitplugg.matesound.ui.TrackItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    onBack: () -> Unit,
    navController: NavController? = null,
) {
    val container = LocalAppContainer.current
    val library by container.library.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlistId = navController?.currentBackStackEntry?.arguments?.getString("id")
        ?: ""
    val playlist = library.localPlaylists.find { p -> p.id == playlistId }
    var renaming by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null || playlist == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.readBytes().decodeToString()
                    val links = M3u.parseLinks(text)
                    if (links.isEmpty()) throw IllegalStateException("empty")
                    val resolved = links.mapNotNull { link ->
                        runCatching { container.repository.resolveAny(link) }
                            .getOrNull()
                            ?.let { result ->
                                when (result) {
                                    is Resolved.Track -> result.track
                                    else -> null
                                }
                            }
                    }
                    if (resolved.isEmpty()) throw IllegalStateException("empty")
                    val current = playlist
                    if (current != null) {
                        container.library.addTracksToLocalPlaylist(current.id, resolved)
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist?.title ?: stringResource(R.string.local_playlists)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        newTitle = playlist?.title.orEmpty()
                        renaming = true
                    },
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.rename))
                }
                IconButton(
                    onClick = {
                        launcher.launch(arrayOf("text/*", "application/*"))
                    },
                ) {
                    Icon(
                        Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.import_m3u),
                    )
                }
                IconButton(onClick = { exporting = true }) {
                    Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.export_m3u))
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            if (playlist != null) {
                                container.library.deleteLocalPlaylist(playlist.id)
                            }
                        }
                        onBack()
                    },
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            },
        )
        if (playlist == null) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.no_results),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Artwork(
                                url = playlist.tracks.firstOrNull()?.artworkUrl,
                                size = 100.dp,
                            )
                            Spacer(Modifier.padding(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.track_count, playlist.itemCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.padding(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (playlist.tracks.isNotEmpty()) {
                                        container.player.play(playlist.tracks, 0)
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text(stringResource(R.string.play_all))
                            }
                            OutlinedButton(
                                onClick = {
                                    container.player.addAllToQueue(playlist.tracks)
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                                Text(stringResource(R.string.enqueue_all))
                            }
                        }
                    }
                }
                if (playlist.tracks.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.empty_playlists),
                        )
                    }
                } else {
                    items(playlist.tracks, key = { it.id }) { track ->
                        TrackItem(
                            track = track,
                            onClick = {
                                container.player.play(
                                    playlist.tracks,
                                    playlist.tracks.indexOfFirst { t -> t.id == track.id }
                                        .coerceAtLeast(0),
                                )
                            },
                            onRemove = {
                                scope.launch {
                                    val pl = playlist
                                    if (pl != null) {
                                        container.library.removeTrackFromLocalPlaylist(pl.id, track.id)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming && playlist != null) {
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
                        scope.launch {
                            val pl = playlist
                            if (pl != null) {
                                container.library.renameLocalPlaylist(pl.id, newTitle)
                            }
                        }
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

    if (exporting && playlist != null) {
        val content = M3u.build(playlist.tracks)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, playlist.title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_m3u)))
        exporting = false
    } else if (exporting) {
        exporting = false
    }
}