package dev.bitplugg.matesound.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.bitplugg.matesound.data.model.AppLanguage
import dev.bitplugg.matesound.data.model.EqualizerPreset
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "matesound_settings")

data class AppSettings(
    val token: String? = null,
    val clientId: String? = null,
    val quality: Quality = Quality.HIGH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val playbackSpeed: Float = 1f,
    val downloadWifiOnly: Boolean = true,
    val userId: Long? = null,
    val userName: String? = null,
    val userAvatar: String? = null,
)

class Prefs(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun token(): String? = current().token

    suspend fun clientId(): String? = store.data.first()[Keys.CLIENT_ID]

    suspend fun setToken(token: String?) {
        store.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(Keys.TOKEN)
                prefs.remove(Keys.USER_ID)
                prefs.remove(Keys.USER_NAME)
                prefs.remove(Keys.USER_AVATAR)
            } else {
                prefs[Keys.TOKEN] = token
            }
        }
    }

    suspend fun setClientId(clientId: String) {
        store.edit { it[Keys.CLIENT_ID] = clientId }
    }

    suspend fun setAccount(id: Long?, name: String?, avatar: String?) {
        store.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.USER_ID)
                prefs.remove(Keys.USER_NAME)
                prefs.remove(Keys.USER_AVATAR)
            } else {
                prefs[Keys.USER_ID] = id
                prefs[Keys.USER_NAME] = name.orEmpty()
                prefs[Keys.USER_AVATAR] = avatar.orEmpty()
            }
        }
    }

    suspend fun setQuality(quality: Quality) {
        store.edit { it[Keys.QUALITY] = quality.key }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME] = mode.key }
    }

    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[Keys.LANGUAGE] = language.key }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        store.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        store.edit { it[Keys.EQ_PRESET] = preset.key }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        store.edit { it[Keys.SPEED] = speed }
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        store.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    private fun Preferences.toSettings() = AppSettings(
        token = this[Keys.TOKEN]?.takeIf { it.isNotBlank() },
        clientId = this[Keys.CLIENT_ID]?.takeIf { it.isNotBlank() },
        quality = Quality.from(this[Keys.QUALITY]),
        themeMode = ThemeMode.from(this[Keys.THEME]),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        language = AppLanguage.from(this[Keys.LANGUAGE]),
        equalizerEnabled = this[Keys.EQ_ENABLED] ?: false,
        equalizerPreset = EqualizerPreset.from(this[Keys.EQ_PRESET]),
        playbackSpeed = this[Keys.SPEED] ?: 1f,
        downloadWifiOnly = this[Keys.WIFI_ONLY] ?: true,
        userId = this[Keys.USER_ID],
        userName = this[Keys.USER_NAME]?.takeIf { it.isNotBlank() },
        userAvatar = this[Keys.USER_AVATAR]?.takeIf { it.isNotBlank() },
    )

    private object Keys {
        val TOKEN = stringPreferencesKey("oauth_token")
        val CLIENT_ID = stringPreferencesKey("client_id")
        val QUALITY = stringPreferencesKey("quality")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val SPEED = floatPreferencesKey("playback_speed")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val USER_ID = longPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
    }
}
