package dev.bitplugg.matesound.data.remote

import dev.bitplugg.matesound.data.local.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ScApiException(
    val code: Int,
    message: String,
    val body: String? = null,
) : Exception(message)

class AuthRequiredException : Exception("SoundCloud authorization required")

class SoundCloudApi(
    private val http: OkHttpClient,
    val json: Json,
    private val prefs: Prefs,
    private val clientIds: ClientIdProvider,
) {
    suspend fun request(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): String = execute(method = "GET", path = path, params = params)

    suspend fun send(
        method: String,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): Int = withContext(Dispatchers.IO) {
        val response = perform(method, apiUrl(path, params))
        response.use {
            if (it.code == 401 || it.code == 403) throw AuthRequiredException()
            if (!it.isSuccessful) throw ScApiException(it.code, "HTTP ${it.code}", it.body?.string())
            it.code
        }
    }

    /** Sends a JSON body (used by the playlist/follow endpoints). */
    suspend fun sendJson(
        method: String,
        path: String,
        body: String,
        params: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val response = perform(
            method,
            apiUrl(path, params),
            body.toRequestBody(JSON_MEDIA_TYPE),
        )
        response.use {
            if (it.code == 401 || it.code == 403) throw AuthRequiredException()
            if (!it.isSuccessful) throw ScApiException(it.code, "HTTP ${it.code}", it.body?.string())
            it.body?.string().orEmpty()
        }
    }

    suspend fun requestAbsolute(
        url: String,
        params: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull() ?: throw ScApiException(400, "Bad url")
        if (parsed.scheme != "https") throw ScApiException(400, "Untrusted scheme")
        if (!isTrustedHost(parsed.host)) throw ScApiException(400, "Untrusted host")
        if (parsed.port != 443 && parsed.port != -1) throw ScApiException(400, "Untrusted port")
        val builder = parsed.newBuilder()
        var hasClientId = parsed.queryParameter("client_id") != null
        for ((key, value) in params) {
            if (key == "client_id") hasClientId = true
            builder.addQueryParameter(key, value)
        }
        val token = prefs.token()
        if (!hasClientId) builder.addQueryParameter("client_id", clientIds.get())
        val response = perform("GET", builder.build(), allowAuth = token != null)
        response.use {
            if (it.code == 401 || it.code == 403) throw AuthRequiredException()
            if (!it.isSuccessful) throw ScApiException(it.code, "HTTP ${it.code}", it.body?.string())
            it.body?.string().orEmpty()
        }
    }

    private fun isTrustedHost(host: String): Boolean {
        val h = host.lowercase()
        return h == API_HOST ||
            h == "api.soundcloud.com" ||
            h == "cf-hls-media.sndcdn.com" ||
            h == "ec-media.soundcloud.com" ||
            h.endsWith(".sndcdn.com") ||
            h.endsWith(".soundcloud.com")
    }

    suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): T = json.decodeFromString(request(path, params))

    private suspend fun execute(
        method: String,
        path: String,
        params: Map<String, String>,
    ): String = withContext(Dispatchers.IO) {
        val response = perform(method, apiUrl(path, params))
        response.use {
            when {
                it.code == 401 || it.code == 403 -> throw AuthRequiredException()
                !it.isSuccessful -> throw ScApiException(it.code, "HTTP ${it.code}", it.body?.string())
                else -> it.body?.string().orEmpty()
            }
        }
    }

    private fun apiUrl(path: String, params: Map<String, String>): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host(API_HOST)
            .addPathSegments(path.trimStart('/'))
        for ((key, value) in params) builder.addQueryParameter(key, value)
        return builder.build()
    }

    private suspend fun perform(
        method: String,
        url: HttpUrl,
        body: okhttp3.RequestBody? = null,
        allowAuth: Boolean = true,
    ): okhttp3.Response {
        val token = if (allowAuth) prefs.token() else null
        val finalUrl = url.newBuilder().apply {
            if (url.queryParameter("client_id") == null) {
                addQueryParameter("client_id", clientIds.get())
            }
        }.build()

        if (!isTrustedHost(finalUrl.host)) throw ScApiException(400, "Untrusted host")
        if (finalUrl.scheme != "https") throw ScApiException(400, "Untrusted scheme")

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
            .apply { if (token != null && isTrustedHost(finalUrl.host)) header("Authorization", "OAuth $token") }
            .method(method, body ?: if (method == "GET" || method == "DELETE") null else EMPTY_BODY)
            .build()

        val call = http.newCall(request)
        val response = call.execute()
        if (response.code >= 500 && response.code < 600) {
            response.close()
            kotlinx.coroutines.delay(250)
            return http.newCall(request).execute()
        }
        return response
    }

    companion object {
        private const val API_HOST = "api-v2.soundcloud.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null, 0, 0)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
