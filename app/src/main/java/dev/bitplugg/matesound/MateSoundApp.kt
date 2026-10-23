package dev.bitplugg.matesound

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.bitplugg.matesound.data.auth.AuthRepository
import dev.bitplugg.matesound.data.download.OfflineDownloader
import dev.bitplugg.matesound.data.local.LibraryStore
import dev.bitplugg.matesound.data.local.Prefs
import dev.bitplugg.matesound.data.model.AppLanguage
import dev.bitplugg.matesound.data.remote.ClientIdProvider
import dev.bitplugg.matesound.data.remote.SoundCloudApi
import dev.bitplugg.matesound.data.repo.MusicRepository
import dev.bitplugg.matesound.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    val prefs = Prefs(application)
    val clientIds = ClientIdProvider(prefs, http)
    val api = SoundCloudApi(http, json, prefs, clientIds)
    val library = LibraryStore(application, json)
    val repository = MusicRepository(api, prefs, library, clientIds, json)
    val downloads = OfflineDownloader(application, http, repository, library, prefs)
    val auth = AuthRepository(prefs)

    val player: PlayerController by lazy {
        PlayerController(application, repository, library, prefs)
    }
}

class MateSoundApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch {
            container.prefs.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect { applyLanguage(it) }
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        val desired = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.key)
        }
        if (AppCompatDelegate.getApplicationLocales() != desired) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}
