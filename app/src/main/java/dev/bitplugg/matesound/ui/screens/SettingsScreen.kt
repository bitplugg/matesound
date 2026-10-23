package dev.bitplugg.matesound.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.model.AppLanguage
import dev.bitplugg.matesound.data.model.EqualizerPreset
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.ThemeMode
import dev.bitplugg.matesound.ui.AuthWebViewActivity
import dev.bitplugg.matesound.ui.appViewModel
import dev.bitplugg.matesound.ui.vm.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignIn: () -> Unit,
) {
    val vm: SettingsViewModel = appViewModel { container ->
        SettingsViewModel(container.prefs, container.clientIds, container.library, container.auth)
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ -> }
    var showTokenDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                actions = {
                    Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                },
            )
        }

        item { SettingsSection(stringResource(R.string.settings_account)) }
        if (settings.token == null) {
            item {
                SettingsClickableRow(
                    title = stringResource(R.string.sign_in),
                    subtitle = stringResource(R.string.sign_in_card_text),
                    onClick = {
                        signInLauncher.launch(Intent(context, AuthWebViewActivity::class.java))
                    },
                )
            }
        } else {
            item {
                SettingsClickableRow(
                    title = settings.userName ?: stringResource(R.string.signed_in),
                    subtitle = stringResource(R.string.sign_out),
                    trailingIcon = Icons.AutoMirrored.Rounded.Logout,
                    onClick = vm::logout,
                )
            }
        }
        item {
            SettingsClickableRow(
                title = stringResource(R.string.manual_token),
                subtitle = stringResource(R.string.manual_token_hint),
                trailingIcon = Icons.Rounded.Key,
                onClick = { showTokenDialog = true },
            )
        }

        item { SettingsSection(stringResource(R.string.settings_playback)) }
        item {
            SettingsOptionsGroup(
                title = stringResource(R.string.settings_quality),
                options = listOf(
                    Quality.HIGH to stringResource(R.string.quality_high),
                    Quality.LOW to stringResource(R.string.quality_low),
                ),
                selected = settings.quality,
                onSelect = vm::setQuality,
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.player_equalizer),
                subtitle = stringResource(R.string.eq_hint),
                checked = settings.equalizerEnabled,
                onCheckedChange = vm::setEqualizerEnabled,
                icon = Icons.Rounded.GraphicEq,
            )
        }
        if (settings.equalizerEnabled) {
            item {
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
        }

        item { SettingsSection(stringResource(R.string.settings_appearance)) }
        item {
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
        }
        item {
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

        item { SettingsSection(stringResource(R.string.settings_storage)) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.wifi_only),
                subtitle = null,
                checked = settings.downloadWifiOnly,
                onCheckedChange = vm::setWifiOnly,
            )
        }
        item {
            SettingsClickableRow(
                title = stringResource(R.string.clear_history),
                onClick = vm::clearHistory,
            )
        }
        item {
            SettingsClickableRow(
                title = stringResource(R.string.clear_likes),
                onClick = vm::clearLikes,
            )
        }

        item { SettingsSection(stringResource(R.string.settings_advanced)) }
        item {
            SettingsClickableRow(
                title = stringResource(R.string.refresh_client_id),
                subtitle = settings.clientId,
                onClick = vm::refreshClientId,
            )
        }

        item { SettingsSection(stringResource(R.string.settings_about)) }
        item {
            SettingsClickableRow(
                title = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                subtitle = "matesound",
                onClick = {},
            )
        }
        item {
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
        item { Spacer(Modifier.height(24.dp)) }
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
private fun SettingsSection(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
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
