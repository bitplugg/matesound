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
import dev.bitplugg.matesound.data.model.LocalPlaylist
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.data.model.Resolved
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
    val user: ScUser? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val prefs: Prefs,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load(GENRES.first())
        viewModelScope.launch {
            prefs.settings
                .map { it.token }
                .distinctUntilChanged()
                .collect {
                    runCatching { if (prefs.current().token != null) repository.me() else null }
                        .getOrNull()
                        ?.let { user -> _state.update { it.copy(user = user) } }
                        ?: _state.update { it.copy(user = null) }
                }
        }
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
    val history: List<String> = emptyList(),
    val resolved: Resolved? = null,
    val error: String? = null,
)

class SearchViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val library: LibraryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            library.data.collect { data ->
                _state.update { it.copy(history = data.searchHistory) }
            }
        }
    }

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

    fun applyQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120)
            if (isSoundCloudLink(query)) openLink() else runSearch(query)
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        if (isSoundCloudLink(query)) {
            viewModelScope.launch { openLink() }
        } else {
            viewModelScope.launch {
                library.addSearch(query)
                searchJob?.cancel()
                runSearch(query)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearSearchHistory() }
    }

    fun clearResolved() {
        _state.update { it.copy(resolved = null) }
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

    private suspend fun openLink() {
        val url = _state.value.query.trim()
        _state.update { it.copy(loading = true, error = null) }
        val resolved = repository.resolveAny(url)
        _state.update {
            it.copy(
                loading = false,
                resolved = resolved,
                error = if (resolved == null) "Could not resolve this link" else null,
            )
        }
        if (resolved is Resolved.Track) {
            player.play(listOf(resolved.track), 0)
        }
    }

    private suspend fun runSearch(query: String) {
        val value = query.trim()
        if (value.length < 2) return
        library.addSearch(value)
        _state.update { it.copy(loading = true, error = null) }
        val result = coroutineScope {
            val tracks = async { runCatching { repository.searchTracks(value) } }
            val artists = async { runCatching { repository.searchArtists(value) } }
            val playlists = async { runCatching { repository.searchPlaylists(value) } }
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

    private fun isSoundCloudLink(value: String): Boolean =
        value.startsWith("http://", true) || value.startsWith("https://", true)
}

// ---------------------------------------------------------------- library

data class RemoteState(
    val likes: List<ScTrack> = emptyList(),
    val playlists: List<ScPlaylist> = emptyList(),
    val feed: List<ScTrack> = emptyList(),
    val following: List<ScUser> = emptyList(),
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
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val feed: List<ScTrack> = emptyList(),
    val following: List<ScUser> = emptyList(),
    val jobs: Map<Long, dev.bitplugg.matesound.data.download.DownloadJob> = emptyMap(),
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
        combine(prefs.settings, library.data, remote, downloads.jobs) { settings, local, cloud, jobs ->
            LibraryUiState(
                settings = settings,
                likes = if (cloud.loaded) cloud.likes else local.likes,
                playlists = cloud.playlists,
                downloads = local.downloads,
                history = local.history,
                localPlaylists = local.localPlaylists,
                feed = cloud.feed,
                following = cloud.following,
                jobs = jobs,
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
            val likes = runCatching { repository.syncLikes() }
            val playlists = runCatching { repository.myPlaylists() }
            val feed = runCatching { repository.followingFeed() }
            val following = runCatching { repository.myFollowings() }
            runCatching { repository.me() }
            val failure = likes.exceptionOrNull() ?: playlists.exceptionOrNull()
            remote.update { current ->
                current.copy(
                    loading = false,
                    loaded = current.loaded || likes.isSuccess || playlists.isSuccess,
                    likes = likes.getOrElse { current.likes },
                    playlists = playlists.getOrElse { current.playlists },
                    feed = feed.getOrElse { current.feed },
                    following = following.getOrElse { current.following },
                    error = failure?.message,
                )
            }
            if (likes.isSuccess && prefs.current().autoDownloadLikes) {
                val missing = likes.getOrDefault(emptyList())
                    .filter { library.downloaded(it.id) == null }
                    .take(30)
                downloads.downloadAll(missing)
            }
        }
    }

    fun downloadAll(tracks: List<ScTrack>) = downloads.downloadAll(tracks)
    fun download(track: ScTrack) = downloads.download(track)
    fun pauseDownload(track: ScTrack) = downloads.pause(track)
    fun resumeDownload(track: ScTrack) = downloads.resume(track)
    fun retryDownload(track: ScTrack) = downloads.retry(track)
    fun cancelDownload(track: ScTrack) = downloads.cancel(track)
    fun deleteAllDownloads() = downloads.deleteAll()

    fun createPlaylist(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (prefs.current().token != null) {
                runCatching { repository.createPlaylist(title.trim()) }
                    .onSuccess { refresh() }
                    .onFailure { error ->
                        remote.update { it.copy(error = error.message) }
                    }
            } else {
                library.addLocalPlaylist(title.trim(), emptyList())
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
    private val downloads: OfflineDownloader,
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

    fun setStreamWifiOnly(enabled: Boolean) =
        viewModelScope.launch { prefs.setStreamWifiOnly(enabled) }

    fun setOfflineMode(enabled: Boolean) = viewModelScope.launch { prefs.setOfflineMode(enabled) }

    fun setAutoDownloadLikes(enabled: Boolean) =
        viewModelScope.launch { prefs.setAutoDownloadLikes(enabled) }

    fun setAutoplayRelated(enabled: Boolean) =
        viewModelScope.launch { prefs.setAutoplayRelated(enabled) }

    fun setAmoledBlack(enabled: Boolean) = viewModelScope.launch { prefs.setAmoledBlack(enabled) }
    fun setCardStyle(value: dev.bitplugg.matesound.data.local.CardStyle) = viewModelScope.launch { prefs.setCardStyle(value) }
    fun setLibraryGridMode(value: dev.bitplugg.matesound.data.local.ListGridMode) = viewModelScope.launch { prefs.setLibraryGridMode(value) }
    fun setPlaylistsGridMode(value: dev.bitplugg.matesound.data.local.ListGridMode) = viewModelScope.launch { prefs.setPlaylistsGridMode(value) }
    fun setNavStyle(value: dev.bitplugg.matesound.data.local.NavStyle) = viewModelScope.launch { prefs.setNavStyle(value) }
    fun setTrackRowStyle(value: dev.bitplugg.matesound.data.local.TrackRowStyle) = viewModelScope.launch { prefs.setTrackRowStyle(value) }
    fun setShowTrackDuration(enabled: Boolean) = viewModelScope.launch { prefs.setShowTrackDuration(enabled) }
    fun setTransparentMiniPlayer(enabled: Boolean) = viewModelScope.launch { prefs.setTransparentMiniPlayer(enabled) }
    fun setProgressBarStyle(value: dev.bitplugg.matesound.data.local.ProgressBarStyle) = viewModelScope.launch { prefs.setProgressBarStyle(value) }
    fun setPlayerBgStyle(value: dev.bitplugg.matesound.data.local.PlayerBgStyle) = viewModelScope.launch { prefs.setPlayerBgStyle(value) }
    fun setGlassEffect(enabled: Boolean) = viewModelScope.launch { prefs.setGlassEffect(enabled) }
    fun setCoverGlow(enabled: Boolean) = viewModelScope.launch { prefs.setCoverGlow(enabled) }
    fun toggleHideBlock(block: dev.bitplugg.matesound.data.local.HomeBlock, hide: Boolean) = viewModelScope.launch { prefs.toggleHideBlock(block, hide) }


    fun clearDownloads() = viewModelScope.launch { downloads.deleteAll() }

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
    val likes: List<ScTrack> = emptyList(),
    val playlists: List<ScPlaylist> = emptyList(),
    val albums: List<ScPlaylist> = emptyList(),
    val following: Boolean = false,
    val loggedIn: Boolean = false,
    val tab: Int = 0,
    val error: String? = null,
)

class ArtistViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val prefs: Prefs,
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
                    val tracks = async { runCatching { repository.artistTracks(artistId) }.getOrDefault(emptyList()) }
                    val likes = async { runCatching { repository.artistLikes(artistId) }.getOrDefault(emptyList()) }
                    val playlists = async { runCatching { repository.artistPlaylists(artistId) }.getOrDefault(emptyList()) }
                    val albums = async { runCatching { repository.artistAlbums(artistId) }.getOrDefault(emptyList()) }
                    val following = async {
                        if (prefs.current().token != null) {
                            runCatching { repository.isFollowing(artistId) }.getOrDefault(false)
                        } else {
                            false
                        }
                    }
                    ArtistUiState(
                        loading = false,
                        user = user.await(),
                        tracks = tracks.await(),
                        likes = likes.await(),
                        playlists = playlists.await(),
                        albums = albums.await(),
                        following = following.await(),
                    )
                }
            }.onSuccess { next ->
                _state.update { it.copy(
                    loading = false,
                    user = next.user,
                    tracks = next.tracks,
                    likes = next.likes,
                    playlists = next.playlists,
                    albums = next.albums,
                    following = next.following,
                    loggedIn = prefs.current().token != null,
                ) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "failed") }
            }
        }
    }

    fun selectTab(index: Int) = _state.update { it.copy(tab = index) }

    fun toggleFollow() {
        val next = !_state.value.following
        _state.update { it.copy(following = next) }
        viewModelScope.launch {
            runCatching { repository.follow(artistId, next) }
                .onFailure { _state.update { it.copy(following = !next) } }
        }
    }

    fun play(track: ScTrack) {
        val tracks = _state.value.visibleTracks()
        player.play(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun playAll(tracks: List<ScTrack>? = null) {
        val list = tracks ?: _state.value.visibleTracks()
        if (list.isNotEmpty()) player.play(list, 0)
    }

    private fun ArtistUiState.visibleTracks(): List<ScTrack> = when (tab) {
        1 -> likes
        0 -> tracks
        else -> tracks
    }
}

// --------------------------------------------------------------- playlist

data class PlaylistUiState(
    val loading: Boolean = true,
    val playlist: ScPlaylist? = null,
    val tracks: List<ScTrack> = emptyList(),
    val isOwner: Boolean = false,
    val error: String? = null,
)

class PlaylistViewModel(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val prefs: Prefs,
    private val downloads: OfflineDownloader,
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
            runCatching { repository.playlistFull(playlistId) }
                .onSuccess { playlist ->
                    val userId = prefs.current().userId
                    _state.update {
                        it.copy(
                            loading = false,
                            playlist = playlist,
                            tracks = playlist.tracks.filter { track -> track.id != 0L },
                            isOwner = playlist.user?.id != null && playlist.user.id == userId,
                        )
                    }
                }.recoverCatching { repository.playlist(playlistId) }
                .onSuccess { playlist ->
                    val userId = prefs.current().userId
                    _state.update { current ->
                        if (current.playlist != null) current else current.copy(
                            loading = false,
                            playlist = playlist,
                            tracks = playlist.tracks.filter { track -> track.id != 0L },
                            isOwner = playlist.user?.id != null && playlist.user.id == userId,
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

    fun enqueueAll() {
        val tracks = _state.value.tracks
        if (tracks.isNotEmpty()) player.addAllToQueue(tracks)
    }

    fun downloadAll() = downloads.downloadAll(_state.value.tracks)

    fun removeTrack(track: ScTrack) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            runCatching { repository.removeTrackFromPlaylist(playlist.id, track.id) }
                .onSuccess { load() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "failed") }
                }
        }
    }

    fun rename(title: String) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            runCatching {
                repository.updatePlaylist(
                    playlist.id,
                    title.trim().ifBlank { playlist.title.orEmpty() },
                    _state.value.tracks,
                    isPrivate = true,
                )
            }.onSuccess { updated ->
                _state.update { it.copy(playlist = updated, error = null) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "failed") }
            }
        }
    }
}
