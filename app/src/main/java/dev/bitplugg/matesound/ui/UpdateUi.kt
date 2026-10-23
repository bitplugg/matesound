package dev.bitplugg.matesound.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.update.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info = state.info ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.update_available, info.version),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = info.notes.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.update_downloading,
                            (state.progress * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.update_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (state.apkFile != null) {
                Button(onClick = onInstall) {
                    Text(stringResource(R.string.update_install))
                }
            } else {
                Button(onClick = onDownload, enabled = !state.downloading) {
                    Text(stringResource(R.string.update_download))
                }
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onOpenRelease) {
                    Text(stringResource(R.string.update_open_release))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        },
    )
}

@Composable
fun UpdateCard(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val info = state.info ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.update_available, info.version),
            style = MaterialTheme.typography.titleSmall,
        )
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = info.notes.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.update_downloading,
                    (state.progress * 100).toInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { error ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.update_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.apkFile != null) {
            Button(onClick = onInstall) {
                Text(stringResource(R.string.update_install))
            }
        } else {
            Button(onClick = onDownload, enabled = !state.downloading) {
                Text(stringResource(R.string.update_download))
            }
        }
        TextButton(onClick = onOpenRelease) {
            Text(stringResource(R.string.update_open_release))
        }
    }
}
