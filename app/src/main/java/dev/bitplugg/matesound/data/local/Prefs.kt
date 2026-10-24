
package dev.bitplugg.matesound.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    val streamWifiOnly: Boolean = false,
    val offlineMode: Boolean = false,
    val autoDownloadLikes: Boolean = false,
    val autoplayRelated: Boolean = true,
    val userId: Long? = null,
    val userName: String? = null,
    val userAvatar: String? = null,
    val cornerRadius: Int = 16,
    val trackDensity: TrackListDensity = TrackListDensity.COMFORTABLE,
    val blurMiniPlayer: Boolean = false,
    val reduceAnimations: Boolean = false,
    val playerStyle: PlayerStyle = PlayerStyle.DEFAULT,
    val accentStyle: AccentStyle = AccentStyle.DYNAMIC,
    val showTrackNumbers: Boolean = false,
    val hideMiniPlayerOnScroll: Boolean = false,
    val albumArtSize: Int = 100,
    val albumArtZoom: Boolean = true,
    val amoledBlack: Boolean = false,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val libraryGridMode: ListGridMode = ListGridMode.LIST,
    val playlistsGridMode: ListGridMode = ListGridMode.LIST,
    val navStyle: NavStyle = NavStyle.TEXT_ICON,
    val trackRowStyle: TrackRowStyle = TrackRowStyle.CLASSIC,
    val showTrackDuration: Boolean = true,
    val transparentMiniPlayer: Boolean = false,
    val progressBarStyle: ProgressBarStyle = ProgressBarStyle.CLASSIC,
    val playerBgStyle: PlayerBgStyle = PlayerBgStyle.DEFAULT,
    val glassEffect: Boolean = false,
    val coverGlow: Boolean = false,
    val hideBlocks: Set<HomeBlock> = emptySet(),
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
                val encrypted = try {
                    TokenCipher.encrypt(token)
                } catch (e: Exception) {
                    throw e
                }
                prefs[Keys.TOKEN] = encrypted
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

    suspend fun setStreamWifiOnly(enabled: Boolean) {
        store.edit { it[Keys.STREAM_WIFI_ONLY] = enabled }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        store.edit { it[Keys.OFFLINE_MODE] = enabled }
    }

    suspend fun setAutoDownloadLikes(enabled: Boolean) {
        store.edit { it[Keys.AUTO_DOWNLOAD] = enabled }
    }

    suspend fun setAutoplayRelated(enabled: Boolean) {
        store.edit { it[Keys.AUTOPLAY] = enabled }
    }

    private fun Preferences.toSettings() = AppSettings(
        token = this[Keys.TOKEN]?.takeIf { it.isNotBlank() }?.let { stored ->
            TokenCipher.decrypt(stored)?.takeIf { it.isNotBlank() }
        },
        clientId = this[Keys.CLIENT_ID]?.takeIf { it.isNotBlank() },
        quality = Quality.from(this[Keys.QUALITY]),
        themeMode = ThemeMode.from(this[Keys.THEME]),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
        language = AppLanguage.from(this[Keys.LANGUAGE]),
        equalizerEnabled = this[Keys.EQ_ENABLED] ?: false,
        equalizerPreset = EqualizerPreset.from(this[Keys.EQ_PRESET]),
        playbackSpeed = this[Keys.SPEED] ?: 1f,
        downloadWifiOnly = this[Keys.WIFI_ONLY] ?: true,
        streamWifiOnly = this[Keys.STREAM_WIFI_ONLY] ?: false,
        offlineMode = this[Keys.OFFLINE_MODE] ?: false,
        autoDownloadLikes = this[Keys.AUTO_DOWNLOAD] ?: false,
        autoplayRelated = this[Keys.AUTOPLAY] ?: true,
        userId = this[Keys.USER_ID],
        userName = this[Keys.USER_NAME]?.takeIf { it.isNotBlank() },
        userAvatar = this[Keys.USER_AVATAR]?.takeIf { it.isNotBlank() },
        cornerRadius = (this[Keys.CORNER_RADIUS] ?: 16).coerceIn(8, 32),
        trackDensity = runCatching { TrackListDensity.valueOf(this[Keys.TRACK_DENSITY] ?: TrackListDensity.COMFORTABLE.name) }.getOrDefault(TrackListDensity.COMFORTABLE),
        blurMiniPlayer = this[Keys.BLUR_MINI_PLAYER] ?: false,
        reduceAnimations = this[Keys.REDUCE_ANIMATIONS] ?: false,
        playerStyle = runCatching { PlayerStyle.valueOf(this[Keys.PLAYER_STYLE] ?: PlayerStyle.DEFAULT.name) }.getOrDefault(PlayerStyle.DEFAULT),
        accentStyle = runCatching { AccentStyle.valueOf(this[Keys.ACCENT_STYLE] ?: AccentStyle.DYNAMIC.name) }.getOrDefault(AccentStyle.DYNAMIC),
        showTrackNumbers = this[Keys.SHOW_TRACK_NUMBERS] ?: false,
        hideMiniPlayerOnScroll = this[Keys.HIDE_MINI_PLAYER_ON_SCROLL] ?: false,
        albumArtSize = (this[Keys.ALBUM_ART_SIZE] ?: 100).coerceIn(60, 140),
        albumArtZoom = this[Keys.ALBUM_ART_ZOOM] ?: true,
        amoledBlack = this[Keys.AMOLED_BLACK] ?: false,
        cardStyle = runCatching { CardStyle.valueOf(this[Keys.CARD_STYLE] ?: CardStyle.FILLED.name) }.getOrDefault(CardStyle.FILLED),
        libraryGridMode = runCatching { ListGridMode.valueOf(this[Keys.LIBRARY_GRID] ?: ListGridMode.LIST.name) }.getOrDefault(ListGridMode.LIST),
        playlistsGridMode = runCatching { ListGridMode.valueOf(this[Keys.PLAYLISTS_GRID] ?: ListGridMode.LIST.name) }.getOrDefault(ListGridMode.LIST),
        navStyle = runCatching { NavStyle.valueOf(this[Keys.NAV_STYLE] ?: NavStyle.TEXT_ICON.name) }.getOrDefault(NavStyle.TEXT_ICON),
        trackRowStyle = runCatching { TrackRowStyle.valueOf(this[Keys.TRACK_ROW_STYLE] ?: TrackRowStyle.CLASSIC.name) }.getOrDefault(TrackRowStyle.CLASSIC),
        showTrackDuration = this[Keys.SHOW_TRACK_DURATION] ?: true,
        transparentMiniPlayer = this[Keys.TRANSPARENT_MINI_PLAYER] ?: false,
        progressBarStyle = runCatching { ProgressBarStyle.valueOf(this[Keys.PROGRESS_BAR_STYLE] ?: ProgressBarStyle.CLASSIC.name) }.getOrDefault(ProgressBarStyle.CLASSIC),
        playerBgStyle = runCatching { PlayerBgStyle.valueOf(this[Keys.PLAYER_BG_STYLE] ?: PlayerBgStyle.DEFAULT.name) }.getOrDefault(PlayerBgStyle.DEFAULT),
        glassEffect = this[Keys.GLASS_EFFECT] ?: false,
        coverGlow = this[Keys.COVER_GLOW] ?: false,
        hideBlocks = (this[Keys.HIDE_BLOCKS] ?: "").split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { HomeBlock.valueOf(it) }.getOrNull() }.toSet(),
    )

    suspend fun setCornerRadius(value: Int) { store.edit { it[Keys.CORNER_RADIUS] = value.coerceIn(8, 32) } }
    suspend fun setTrackDensity(value: TrackListDensity) { store.edit { it[Keys.TRACK_DENSITY] = value.name } }
    suspend fun setBlurMiniPlayer(enabled: Boolean) { store.edit { it[Keys.BLUR_MINI_PLAYER] = enabled } }
    suspend fun setReduceAnimations(enabled: Boolean) { store.edit { it[Keys.REDUCE_ANIMATIONS] = enabled } }
    suspend fun setPlayerStyle(value: PlayerStyle) { store.edit { it[Keys.PLAYER_STYLE] = value.name } }
    suspend fun setAccentStyle(value: AccentStyle) { store.edit { it[Keys.ACCENT_STYLE] = value.name } }
    suspend fun setShowTrackNumbers(enabled: Boolean) { store.edit { it[Keys.SHOW_TRACK_NUMBERS] = enabled } }
    suspend fun setHideMiniPlayerOnScroll(enabled: Boolean) { store.edit { it[Keys.HIDE_MINI_PLAYER_ON_SCROLL] = enabled } }
    suspend fun setAlbumArtSize(value: Int) { store.edit { it[Keys.ALBUM_ART_SIZE] = value.coerceIn(60, 140) } }
    suspend fun setAlbumArtZoom(enabled: Boolean) { store.edit { it[Keys.ALBUM_ART_ZOOM] = enabled } }
    suspend fun setAmoledBlack(enabled: Boolean) { store.edit { it[Keys.AMOLED_BLACK] = enabled } }
    suspend fun setCardStyle(value: CardStyle) { store.edit { it[Keys.CARD_STYLE] = value.name } }
    suspend fun setLibraryGridMode(value: ListGridMode) { store.edit { it[Keys.LIBRARY_GRID] = value.name } }
    suspend fun setPlaylistsGridMode(value: ListGridMode) { store.edit { it[Keys.PLAYLISTS_GRID] = value.name } }
    suspend fun setNavStyle(value: NavStyle) { store.edit { it[Keys.NAV_STYLE] = value.name } }
    suspend fun setTrackRowStyle(value: TrackRowStyle) { store.edit { it[Keys.TRACK_ROW_STYLE] = value.name } }
    suspend fun setShowTrackDuration(enabled: Boolean) { store.edit { it[Keys.SHOW_TRACK_DURATION] = enabled } }
    suspend fun setTransparentMiniPlayer(enabled: Boolean) { store.edit { it[Keys.TRANSPARENT_MINI_PLAYER] = enabled } }
    suspend fun setProgressBarStyle(value: ProgressBarStyle) { store.edit { it[Keys.PROGRESS_BAR_STYLE] = value.name } }
    suspend fun setPlayerBgStyle(value: PlayerBgStyle) { store.edit { it[Keys.PLAYER_BG_STYLE] = value.name } }
    suspend fun setGlassEffect(enabled: Boolean) { store.edit { it[Keys.GLASS_EFFECT] = enabled } }
    suspend fun setCoverGlow(enabled: Boolean) { store.edit { it[Keys.COVER_GLOW] = enabled } }
    suspend fun setHideBlocks(blocks: Set<HomeBlock>) { store.edit { it[Keys.HIDE_BLOCKS] = blocks.joinToString(",") { b -> b.name } } }
    suspend fun toggleHideBlock(block: HomeBlock, hide: Boolean) {
        val current = (store.data.first()[Keys.HIDE_BLOCKS] ?: "").split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { HomeBlock.valueOf(it) }.getOrNull() }.toMutableSet()
        if (hide) current.add(block) else current.remove(block)
        setHideBlocks(current)
    }

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
        val STREAM_WIFI_ONLY = booleanPreferencesKey("stream_wifi_only")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_likes")
        val AUTOPLAY = booleanPreferencesKey("autoplay_related")
        val USER_ID = longPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val CORNER_RADIUS = intPreferencesKey("corner_radius")
        val TRACK_DENSITY = stringPreferencesKey("track_density")
        val BLUR_MINI_PLAYER = booleanPreferencesKey("blur_mini_player")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val PLAYER_STYLE = stringPreferencesKey("player_style")
        val ACCENT_STYLE = stringPreferencesKey("accent_style")
        val SHOW_TRACK_NUMBERS = booleanPreferencesKey("show_track_numbers")
        val HIDE_MINI_PLAYER_ON_SCROLL = booleanPreferencesKey("hide_mini_player_on_scroll")
        val ALBUM_ART_SIZE = intPreferencesKey("album_art_size")
        val ALBUM_ART_ZOOM = booleanPreferencesKey("album_art_zoom")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val CARD_STYLE = stringPreferencesKey("card_style")
        val LIBRARY_GRID = stringPreferencesKey("library_grid_mode")
        val PLAYLISTS_GRID = stringPreferencesKey("playlists_grid_mode")
        val NAV_STYLE = stringPreferencesKey("nav_style")
        val TRACK_ROW_STYLE = stringPreferencesKey("track_row_style")
        val SHOW_TRACK_DURATION = booleanPreferencesKey("show_track_duration")
        val TRANSPARENT_MINI_PLAYER = booleanPreferencesKey("transparent_mini_player")
        val PROGRESS_BAR_STYLE = stringPreferencesKey("progress_bar_style")
        val PLAYER_BG_STYLE = stringPreferencesKey("player_bg_style")
        val GLASS_EFFECT = booleanPreferencesKey("glass_effect")
        val COVER_GLOW = booleanPreferencesKey("cover_glow")
        val HIDE_BLOCKS = stringPreferencesKey("hide_blocks")
    }
}

enum class TrackListDensity { COMPACT, COMFORTABLE, SPACIOUS }
enum class PlayerStyle { DEFAULT, COMPACT, EXPANDED }
enum class AccentStyle { DYNAMIC, BLUE, PURPLE, PINK, ORANGE, GREEN, TEAL, RED }
enum class CardStyle { FILLED, OUTLINED, ELEVATED }
enum class ListGridMode { LIST, GRID2, GRID3 }
enum class NavStyle { TEXT_ICON, ICON_ONLY }
enum class TrackRowStyle { CLASSIC, SINGLE_LINE, WITH_DURATION }
enum class ProgressBarStyle { CLASSIC, THIN, ROUNDED, PULSING }
enum class PlayerBgStyle { DEFAULT, BLUR_COVER, GRADIENT_COVER, ANIMATED_COVER }
enum class HomeBlock { TRENDS, NEW_RELEASES, FOLLOWING_FEED, CHARTS, MIXES }
