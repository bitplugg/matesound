package dev.bitplugg.matesound.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.data.model.EqualizerPreset
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.playback.PlayerUiState
import dev.bitplugg.matesound.ui.Artwork
import dev.bitplugg.matesound.ui.LocalAppContainer
import dev.bitplugg.matesound.ui.formatDuration
import dev.bitplugg.matesound.ui.rememberTrackActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val player = container.player
    val state by player.state.collectAsStateWithLifecycle()
    val library by container.library.data.collectAsStateWithLifecycle()
    val jobs by container.downloads.jobs.collectAsStateWithLifecycle()
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val actions = rememberTrackActions()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var track by remember { mutableStateOf<ScTrack?>(null) }
    LaunchedEffect(state.mediaId) {
        track = state.mediaId?.let { id ->
            player.currentTrack() ?: runCatching { player.resolveTrack(id) }.getOrNull()
        }
    }

    val isLiked = state.mediaId?.let { id -> library.likes.any { it.id == id } } == true
    val downloaded = state.mediaId?.let { id -> library.downloads.any { it.track.id == id } } == true
    val downloadJob = state.mediaId?.let { jobs[it] }

    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue = (if (dragging) dragValue else state.positionMs.toFloat()).coerceIn(0f, duration)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.close))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.player_now_playing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (track?.isPreview == true) {
                        Text(
                            text = stringResource(R.string.preview),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = { sheet = PlayerSheet.Queue }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = stringResource(R.string.player_queue))
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val artworkSide = minOf(maxWidth, maxHeight - 8.dp).coerceAtLeast(96.dp)
                Artwork(
                    url = state.artwork,
                    size = artworkSide,
                    corner = 20.dp,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { stringResource(R.string.now_playing_unknown) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { track?.let(actions.like) }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    player.seekTo(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..duration,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = formatDuration(if (dragging) dragValue.toLong() else state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { player.toggle() },
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        imageVector = if (state.repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = if (state.repeatMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { sheet = PlayerSheet.Speed }) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("%.2fx".format(state.speed))
                }
                TextButton(onClick = { sheet = PlayerSheet.Sleep }) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (state.sleepRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = state.sleepRemainingMs?.let { formatDuration(it) }
                            ?: stringResource(R.string.player_sleep),
                    )
                }
                TextButton(onClick = { sheet = PlayerSheet.Equalizer }) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val current = track ?: return@IconButton
                        if (downloaded) {
                            container.downloads.delete(current)
                        } else {
                            actions.download(current)
                        }
                    },
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    val url = track?.permalinkUrl ?: return@IconButton
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            },
                        )
                    }
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                }
            }

            if (downloadJob != null && downloadJob.error == null && downloadJob.progress < 1f) {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = "${stringResource(R.string.downloading)} ${(downloadJob.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (downloadJob?.error != null) {
                Text(
                    text = "${stringResource(R.string.download_failed)}: ${downloadJob.error}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                TextButton(onClick = { player.clearError() }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    when (sheet) {
        PlayerSheet.Queue -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            QueueSheet(state = state, onSelect = { index ->
                player.playIndex(index)
                sheet = null
            })
        }

        PlayerSheet.Speed -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            OptionSheet(
                title = stringResource(R.string.player_speed),
                options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f),
                selected = state.speed,
                label = { "%.2fx".format(it) },
                onSelect = {
                    player.setSpeed(it)
                    sheet = null
                },
            )
        }

        PlayerSheet.Sleep -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            OptionSheet(
                title = stringResource(R.string.player_sleep),
                options = listOf(0, 15, 30, 45, 60, 90),
                selected = 0,
                label = { if (it == 0) stringResource(R.string.sleep_off) else "${it} min" },
                onSelect = {
                    player.setSleepTimer(it)
                    sheet = null
                },
            )
        }

        PlayerSheet.Equalizer -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            EqualizerSheet(
                settings = settings,
                onToggle = { enabled ->
                    scope.launch { container.prefs.setEqualizerEnabled(enabled) }
                },
                onPreset = { preset ->
                    scope.launch { container.prefs.setEqualizerPreset(preset) }
                },
            )
        }

        null -> Unit
    }
}

private enum class PlayerSheet { Queue, Speed, Sleep, Equalizer }

@Composable
private fun QueueSheet(state: PlayerUiState, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.player_queue),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue) { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index == state.currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (index == state.currentIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp),
        )
        options.forEach { option ->
            Text(
                text = label(option),
                style = MaterialTheme.typography.bodyLarge,
                color = if (option == selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EqualizerSheet(
    settings: AppSettings,
    onToggle: (Boolean) -> Unit,
    onPreset: (EqualizerPreset) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_equalizer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.equalizerEnabled,
                onCheckedChange = onToggle,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqualizerPreset.entries.take(3).forEach { preset ->
                FilterChip(
                    selected = settings.equalizerPreset == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(eqName(preset)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqualizerPreset.entries.drop(3).forEach { preset ->
                FilterChip(
                    selected = settings.equalizerPreset == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(eqName(preset)) },
                )
            }
        }
    }
}

@Composable
private fun eqName(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.FLAT -> stringResource(R.string.eq_flat)
    EqualizerPreset.BASS -> stringResource(R.string.eq_bass)
    EqualizerPreset.VOCAL -> stringResource(R.string.eq_vocal)
    EqualizerPreset.ELECTRONIC -> stringResource(R.string.eq_electronic)
    EqualizerPreset.ROCK -> stringResource(R.string.eq_rock)
}
