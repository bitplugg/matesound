package dev.bitplugg.matesound.ui.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bitplugg.matesound.data.auth.AuthRepository
import dev.bitplugg.matesound.data.download.OfflineDownloader
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.data.local.LibraryStore
import dev.bitplugg.matesound.data.local.Prefs
import dev.bitplugg.matesound.data.model.AppLanguage
import dev.bitplugg.matesound.data.model.EqualizerPreset
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.ScPlaylist
import dev.bitplugg.matesound.data.model.ScTrack
import dev.bitplugg.matesound.data.model.ScUser
import dev.bitplugg.matesound.data.model.ThemeMode
import dev.bitplugg.matesound.data.remote.ClientIdProvider
import dev.bitplugg.matesound.data.repo.MusicRepository
import dev.bitplugg.matesound.playback.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Genre(val urn: String?, val label: String)

val GENRES = listOf(
    Genre(null, "All"),
    Genre("electronic", "Electronic"),
    Genre("hiphop-rap", "Hip-hop"),
    Genre("rock", "Rock"),
    Genre("pop", "Pop"),
    Genre("house", "House"),
    Genre("techno", "Techno"),
    Genre("trap", "Trap"),
    Genre("ambient", "Ambient"),
    Genre("jazz-blues", "Jazz"),
    Genre("classical", "Classical"),
    Genre("lo-fi", "Lo-fi"),
)

// ------------------------------------------------------------------- home

data class HomeUiState(
    val loading: Boolean = true,
    val tracks: List<ScTrack> = emptyList(),
    val genre: Genre = GENRES.first(),
    val error: String? = null,
)

class HomeViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load(GENRES.first())
    }

    fun selectGenre(genre: Genre) {
        if (genre == _state.value.genre && !_state.value.loading) return
        load(genre)
    }

    fun refresh() = load(_state.value.genre)

    private fun load(genre: Genre) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, genre = genre) }
            runCatching { repository.charts(genre.urn) }
                .onSuccess { tracks ->
                    _state.update { it.copy(loading = false, tracks = tracks) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "failed")
                    }
                }
        }
    }

    fun play(track: ScTrack) {
        val tracks = _state.value.tracks
        player.play(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }
}

// ----------------------------------------------------------------- search

data class SearchUiState(
    val query: String = "",
    val tab: Int = 0,
    val loading: Boolean = false,
    val searched: Boolean = false,
    val tracks: List<ScTrack> = emptyList(),
    val artists: List<ScUser> = emptyList(),
    val playlists: List<ScPlaylist> = emptyList(),
    val error: String? = null,
)

class SearchViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update {
                it.copy(
                    loading = false,
                    searched = false,
                    tracks = emptyList(),
                    artists = emptyList(),
                    playlists = emptyList(),
                    error = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(query)
        }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(tab = index) }
    }

    fun retry() {
        val query = _state.value.query
        if (query.trim().length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(loading = true, error = null) }
        val result = coroutineScope {
            val tracks = async { runCatching { repository.searchTracks(query) } }
            val artists = async { runCatching { repository.searchArtists(query) } }
            val playlists = async { runCatching { repository.searchPlaylists(query) } }
            Triple(tracks.await(), artists.await(), playlists.await())
        }
        val (tracks, artists, playlists) = result
        val allFailed = tracks.isFailure && artists.isFailure && playlists.isFailure
        _state.update {
            it.copy(
                loading = false,
                searched = true,
                tracks = tracks.getOrDefault(emptyList()),
                artists = artists.getOrDefault(emptyList()),
                playlists = playlists.getOrDefault(emptyList()),
                error = if (allFailed) {
                    tracks.exceptionOrNull()?.message ?: "failed"
                } else {
                    null
                },
            )
        }
    }

    fun play(track: ScTrack) {
        val tracks = _state.value.tracks
        player.play(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }
}

// ---------------------------------------------------------------- library

data class RemoteState(
    val likes: List<ScTrack> = emptyList(),
    val playlists: List<ScPlaylist> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

data class LibraryUiState(
    val settings: AppSettings = AppSettings(),
    val likes: List<ScTrack> = emptyList(),
    val playlists: List<ScPlaylist> = emptyList(),
    val downloads: List<dev.bitplugg.matesound.data.model.DownloadedTrack> = emptyList(),
    val history: List<dev.bitplugg.matesound.data.model.HistoryEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val loggedIn: Boolean get() = settings.token != null
    val user: ScUser?
        get() = settings.userId?.let {
            ScUser(id = it, username = settings.userName.orEmpty(), avatarUrl = settings.userAvatar)
        }
}

class LibraryViewModel(
    private val repository: MusicRepository,
    private val library: LibraryStore,
    private val prefs: Prefs,
    private val auth: AuthRepository,
    private val downloads: OfflineDownloader,
    private val player: PlayerController,
) : ViewModel() {

    private val remote = MutableStateFlow(RemoteState())

    val state: StateFlow<LibraryUiState> =
        combine(prefs.settings, library.data, remote) { settings, local, cloud ->
            LibraryUiState(
                settings = settings,
                likes = if (cloud.loaded) cloud.likes else local.likes,
                playlists = cloud.playlists,
                downloads = local.downloads,
                history = local.history,
                loading = cloud.loading,
                error = cloud.error,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryUiState(),
        )

    init {
        viewModelScope.launch {
            prefs.settings
                .map { it.token }
                .distinctUntilChanged()
                .collect { token ->
                    if (token == null) {
                        remote.value = RemoteState()
                    } else {
                        refresh()
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            remote.update { it.copy(loading = true, error = null) }
            val likes = runCatching { repository.myLikes() }
            val playlists = runCatching { repository.myPlaylists() }
            runCatching { repository.me() }
            val failure = likes.exceptionOrNull() ?: playlists.exceptionOrNull()
            remote.update { current ->
                current.copy(
                    loading = false,
                    loaded = current.loaded || likes.isSuccess || playlists.isSuccess,
                    likes = likes.getOrElse { current.likes },
                    playlists = playlists.getOrElse { current.playlists },
                    error = failure?.message,
                )
            }
        }
    }

    fun play(tracks: List<ScTrack>, track: ScTrack) {
        if (tracks.isEmpty()) return
        player.play(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun playAll(tracks: List<ScTrack>) {
        if (tracks.isNotEmpty()) player.play(tracks, 0)
    }

    fun deleteDownload(track: ScTrack) {
        downloads.delete(track)
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            remote.value = RemoteState()
        }
    }
}

// --------------------------------------------------------------- settings

class SettingsViewModel(
    private val prefs: Prefs,
    private val clientIds: ClientIdProvider,
    private val library: LibraryStore,
    private val auth: AuthRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = prefs.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch { prefs.setLanguage(language) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { prefs.setDynamicColor(enabled) }

    fun setQuality(quality: Quality) = viewModelScope.launch { prefs.setQuality(quality) }

    fun setEqualizerEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setEqualizerEnabled(enabled) }

    fun setEqualizerPreset(preset: EqualizerPreset) =
        viewModelScope.launch { prefs.setEqualizerPreset(preset) }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { prefs.setDownloadWifiOnly(enabled) }

    fun saveToken(token: String) = viewModelScope.launch { auth.saveToken(token) }

    fun logout() = viewModelScope.launch { auth.logout() }

    fun clearHistory() = viewModelScope.launch { library.clearHistory() }

    fun clearLikes() = viewModelScope.launch { library.clearLikes() }

    fun refreshClientId() {
        viewModelScope.launch { runCatching { clientIds.get(forceRefresh = true) } }
    }
}

// ----------------------------------------------------------------- artist

data class ArtistUiState(
    val loading: Boolean = true,
    val user: ScUser? = null,
    val tracks: List<ScTrack> = emptyList(),
    val error: String? = null,
)

class ArtistViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                coroutineScope {
                    val user = async { repository.artist(artistId) }
                    val tracks = async { repository.artistTracks(artistId) }
                    user.await() to tracks.await()
                }
            }.onSuccess { (user, tracks) ->
                _state.update { it.copy(loading = false, user = user, tracks = tracks) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "failed") }
            }
        }
    }

    fun play(track: ScTrack) {
        val tracks = _state.value.tracks
        player.play(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun playAll() = _state.value.tracks.let { if (it.isNotEmpty()) player.play(it, 0) }
}

// --------------------------------------------------------------- playlist

data class PlaylistUiState(
    val loading: Boolean = true,
    val playlist: ScPlaylist? = null,
    val tracks: List<ScTrack> = emptyList(),
    val error: String? = null,
)

class PlaylistViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val playlistId: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.playlist(playlistId) }
                .onSuccess { playlist ->
                    _state.update {
                        it.copy(
                            loading = false,
                            playlist = playlist,
                            tracks = playlist.tracks.filter { track -> track.id != 0L },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: "failed") }
                }
        }
    }

    fun play(track: ScTrack) {
        val tracks = _state.value.tracks
        player.play(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun playAll() = _state.value.tracks.let { if (it.isNotEmpty()) player.play(it, 0) }
}
