package dev.bitplugg.matesound.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bitplugg.matesound.BuildConfig
import dev.bitplugg.matesound.MateSoundApp
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.model.AppLanguage
import dev.bitplugg.matesound.data.model.EqualizerPreset
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.ThemeMode
import dev.bitplugg.matesound.ui.AuthWebViewActivity
import dev.bitplugg.matesound.ui.LocalAppContainer
import dev.bitplugg.matesound.ui.UpdateCard
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.vm.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignIn: () -> Unit,
) {
    val vm: SettingsViewModel = appViewModel { container ->
        SettingsViewModel(
            container.prefs,
            container.clientIds,
            container.library,
            container.auth,
            container.downloads,
        )
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val update by container.updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ -> }
    var showTokenDialog by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf<String?>(null) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    val crashFile = remember { File(context.filesDir, MateSoundApp.CRASH_FILE) }
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }

    var accountExpanded by remember { mutableStateOf(true) }
    var playbackExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var storageExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var updatesExpanded by remember { mutableStateOf(true) }
    var aboutExpanded by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
            )
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_account),
                expanded = accountExpanded,
                onToggle = { accountExpanded = !accountExpanded },
            ) {
                if (settings.token == null) {
                    SettingsClickableRow(
                        title = stringResource(R.string.sign_in),
                        subtitle = stringResource(R.string.sign_in_card_text),
                        onClick = {
                            signInLauncher.launch(Intent(context, AuthWebViewActivity::class.java))
                        },
                    )
                } else {
                    SettingsClickableRow(
                        title = settings.userName ?: stringResource(R.string.signed_in),
                        subtitle = stringResource(R.string.sign_out),
                        trailingIcon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = vm::logout,
                    )
                }
                SettingsClickableRow(
                    title = stringResource(R.string.manual_token),
                    subtitle = stringResource(R.string.manual_token_hint),
                    trailingIcon = Icons.Rounded.Key,
                    onClick = { showTokenDialog = true },
                )
                if (settings.token != null) {
                    SettingsClickableRow(
                        title = stringResource(R.string.copy_token),
                        subtitle = null,
                        trailingIcon = Icons.Rounded.ContentCopy,
                        onClick = {
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("oauth_token", settings.token),
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.token_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_playback),
                expanded = playbackExpanded,
                onToggle = { playbackExpanded = !playbackExpanded },
            ) {
                SettingsOptionsGroup(
                    title = stringResource(R.string.settings_quality),
                    options = listOf(
                        Quality.HIGH to stringResource(R.string.quality_high),
                        Quality.LOW to stringResource(R.string.quality_low),
                    ),
                    selected = settings.quality,
                    onSelect = vm::setQuality,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.player_equalizer),
                    subtitle = stringResource(R.string.eq_hint),
                    checked = settings.equalizerEnabled,
                    onCheckedChange = vm::setEqualizerEnabled,
                    icon = Icons.Rounded.GraphicEq,
                )
                if (settings.equalizerEnabled) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(EqualizerPreset.entries) { preset ->
                            FilterChip(
                                selected = settings.equalizerPreset == preset,
                                onClick = { vm.setEqualizerPreset(preset) },
                                label = { Text(eqLabel(preset)) },
                            )
                        }
                    }
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.autoplay_related),
                    subtitle = stringResource(R.string.autoplay_related_hint),
                    checked = settings.autoplayRelated,
                    onCheckedChange = vm::setAutoplayRelated,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.stream_wifi_only),
                    subtitle = null,
                    checked = settings.streamWifiOnly,
                    onCheckedChange = vm::setStreamWifiOnly,
                    icon = Icons.Rounded.WifiOff,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.offline_mode),
                    subtitle = stringResource(R.string.offline_mode_hint),
                    checked = settings.offlineMode,
                    onCheckedChange = vm::setOfflineMode,
                    icon = Icons.Rounded.CloudOff,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.auto_download_likes),
                    subtitle = stringResource(R.string.auto_download_likes_hint),
                    checked = settings.autoDownloadLikes,
                    onCheckedChange = vm::setAutoDownloadLikes,
                    icon = Icons.Rounded.Download,
                )
            }
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_appearance),
                expanded = appearanceExpanded,
                onToggle = { appearanceExpanded = !appearanceExpanded },
            ) {
                SettingsOptionsGroup(
                    title = stringResource(R.string.theme),
                    options = listOf(
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ),
                    selected = settings.themeMode,
                    onSelect = vm::setTheme,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.material_you),
                    subtitle = stringResource(R.string.material_you_hint),
                    checked = settings.dynamicColor,
                    onCheckedChange = vm::setDynamicColor,
                    icon = Icons.Rounded.Palette,
                )
                SettingsOptionsGroup(
                    title = stringResource(R.string.settings_language),
                    options = listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                        AppLanguage.RU to stringResource(R.string.lang_russian),
                        AppLanguage.EN to stringResource(R.string.lang_english),
                    ),
                    selected = settings.language,
                    onSelect = vm::setLanguage,
                )
            }
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_storage),
                expanded = storageExpanded,
                onToggle = { storageExpanded = !storageExpanded },
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.wifi_only),
                    subtitle = null,
                    checked = settings.downloadWifiOnly,
                    onCheckedChange = vm::setWifiOnly,
                )
                SettingsClickableRow(
                    title = stringResource(R.string.clear_history),
                    onClick = vm::clearHistory,
                )
                SettingsClickableRow(
                    title = stringResource(R.string.clear_likes),
                    onClick = vm::clearLikes,
                )
                SettingsClickableRow(
                    title = stringResource(R.string.clear_downloads),
                    trailingIcon = Icons.Rounded.DeleteSweep,
                    onClick = { confirmClearDownloads = true },
                )
            }
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_advanced),
                expanded = advancedExpanded,
                onToggle = { advancedExpanded = !advancedExpanded },
            ) {
                SettingsClickableRow(
                    title = stringResource(R.string.refresh_client_id),
                    subtitle = settings.clientId,
                    onClick = vm::refreshClientId,
                )
                if (crashFile.exists()) {
                    SettingsClickableRow(
                        title = stringResource(R.string.crash_log),
                        subtitle = crashFile.name,
                        onClick = {
                            crashText = runCatching { crashFile.readText() }.getOrNull()
                        },
                    )
                    SettingsClickableRow(
                        title = stringResource(R.string.report_crash),
                        subtitle = stringResource(R.string.report_crash_hint),
                        trailingIcon = Icons.Rounded.BugReport,
                        onClick = {
                            val log = runCatching { crashFile.readText() }.getOrDefault("")
                            val url = Uri.Builder()
                                .scheme("https")
                                .authority("github.com")
                                .appendPath("bitplugg")
                                .appendPath("matesound")
                                .appendPath("issues")
                                .appendPath("new")
                                .appendQueryParameter(
                                    "title",
                                    "[crash] matesound ${BuildConfig.VERSION_NAME}",
                                )
                                .appendQueryParameter(
                                    "body",
                                    "**Version:** ${BuildConfig.VERSION_NAME}\n\n" +
                                        "**What happened:**\n\n\n" +
                                        "**Crash log:**\n```\n${log.take(6_000)}\n```\n",
                                )
                                .build()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                            }
                        },
                    )
                }
            }
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_updates),
                expanded = updatesExpanded,
                onToggle = { updatesExpanded = !updatesExpanded },
            ) {
                val subtitle = when {
                    update.checking -> stringResource(R.string.update_checking)
                    update.info != null -> stringResource(
                        R.string.update_available,
                        update.info?.version.orEmpty(),
                    )
                    update.upToDate -> stringResource(R.string.update_up_to_date)
                    update.error != null -> stringResource(
                        R.string.update_error,
                        update.error.orEmpty(),
                    )
                    else -> stringResource(R.string.update_current, BuildConfig.VERSION_NAME)
                }
                SettingsClickableRow(
                    title = stringResource(R.string.check_updates),
                    subtitle = subtitle,
                    trailingIcon = Icons.Rounded.SystemUpdate,
                    onClick = { container.updates.check(userTriggered = true) },
                )
                if (update.info != null) {
                    UpdateCard(
                        state = update,
                        onDownload = { container.updates.download() },
                        onInstall = { container.updates.install() },
                        onOpenRelease = { container.updates.openReleasePage() },
                    )
                }
            }
        }

        item {
            SettingsCard(
                title = stringResource(R.string.settings_about),
                expanded = aboutExpanded,
                onToggle = { aboutExpanded = !aboutExpanded },
            ) {
                SettingsClickableRow(
                    title = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    subtitle = "matesound",
                    onClick = {},
                )
                SettingsClickableRow(
                    title = stringResource(R.string.source_code),
                    subtitle = "github.com/bitplugg/matesound",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/bitplugg/matesound"),
                                ),
                            )
                        }
                    },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text(stringResource(R.string.clear_downloads)) },
            text = { Text(stringResource(R.string.clear_downloads_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearDownloads()
                        confirmClearDownloads = false
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showTokenDialog) {
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text(stringResource(R.string.manual_token)) },
            text = {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = { Text(stringResource(R.string.token_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (token.isNotBlank()) vm.saveToken(token)
                        showTokenDialog = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    crashText?.let { text ->
        AlertDialog(
            onDismissRequest = { crashText = null },
            title = { Text(stringResource(R.string.crash_log)) },
            text = {
                Text(
                    text = text.take(8_000),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { crashText = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun eqLabel(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.FLAT -> stringResource(R.string.eq_flat)
    EqualizerPreset.BASS -> stringResource(R.string.eq_bass)
    EqualizerPreset.VOCAL -> stringResource(R.string.eq_vocal)
    EqualizerPreset.ELECTRONIC -> stringResource(R.string.eq_electronic)
    EqualizerPreset.ROCK -> stringResource(R.string.eq_rock)
}

@Composable
private fun SettingsCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> SettingsOptionsGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        options.forEach { (value, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == value, onClick = { onSelect(value) })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
