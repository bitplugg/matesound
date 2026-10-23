package dev.bitplugg.matesound.data.remote

import dev.bitplugg.matesound.data.local.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * SoundCloud stopped handing out API keys to new apps. Third-party clients work
 * around it by reusing the public client_id that SoundCloud's own web player
 * embeds in its pages. The id is not stable forever, so we discover it at
 * runtime, validate it and cache the working value.
 */
class ClientIdProvider(
    private val prefs: Prefs,
    private val http: OkHttpClient,
) {
    @Volatile
    private var cached: String? = null

    private val mutex = Mutex()

    suspend fun get(forceRefresh: Boolean = false): String {
        if (!forceRefresh) cached?.let { return it }
        return mutex.withLock {
            if (!forceRefresh) cached?.let { return@withLock it }

            val stored = prefs.clientId()
            if (!forceRefresh && !stored.isNullOrBlank() && validate(stored)) {
                cached = stored
                return@withLock stored
            }

            val discovered = discover() ?: stored?.takeIf { validate(it) } ?: DEFAULT_CLIENT_ID
            prefs.setClientId(discovered)
            cached = discovered
            discovered
        }
    }

    private suspend fun discover(): String? = withContext(Dispatchers.IO) {
        val candidates = LinkedHashSet<String>()
        for (page in SOURCES) {
            val html = runCatching { fetch(page) }.getOrNull() ?: continue
            CLIENT_ID_PATTERN.findAll(html).forEach { match ->
                if (candidates.size < MAX_CANDIDATES) candidates += match.groupValues[1]
            }
            if (candidates.isNotEmpty()) break
        }
        for (candidate in candidates) {
            if (validate(candidate)) return@withContext candidate
        }
        null
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private suspend fun validate(clientId: String): Boolean = withContext(Dispatchers.IO) {
        val url = ("https://api-v2.soundcloud.com/search/tracks" +
            "?q=test&limit=1&client_id=$clientId").toHttpUrlOrNull() ?: return@withContext false
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                response.isSuccessful && response.body?.string()?.contains("\"collection\"") == true
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val DEFAULT_CLIENT_ID = "KKzJxmw11tYpCs6T24P4uUYhqmjalG6M"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        const val MAX_CANDIDATES = 12
        val SOURCES = listOf(
            "https://soundcloud.com/",
            "https://m.soundcloud.com/",
        )
        val CLIENT_ID_PATTERN = Regex("\"([A-Za-z0-9]{32})\"")
    }
}
