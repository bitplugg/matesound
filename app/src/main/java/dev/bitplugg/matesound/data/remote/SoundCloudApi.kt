package dev.bitplugg.matesound.data.remote

import dev.bitplugg.matesound.data.local.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    suspend fun requestAbsolute(
        url: String,
        params: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull() ?: throw ScApiException(400, "Bad url: $url")
        val builder = parsed.newBuilder()
        var hasClientId = parsed.queryParameter("client_id") != null
        for ((key, value) in params) {
            if (key == "client_id") hasClientId = true
            builder.addQueryParameter(key, value)
        }
        val token = prefs.token()
        if (!hasClientId) builder.addQueryParameter("client_id", clientIds.get())
        if (token != null && builder.build().queryParameter("oauth_token") == null) {
            builder.addQueryParameter("oauth_token", token)
        }
        val response = perform("GET", builder.build())
        response.use {
            if (it.code == 401 || it.code == 403) throw AuthRequiredException()
            if (!it.isSuccessful) throw ScApiException(it.code, "HTTP ${it.code}", it.body?.string())
            it.body?.string().orEmpty()
        }
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

    private suspend fun perform(method: String, url: HttpUrl): okhttp3.Response {
        val token = prefs.token()
        val finalUrl = url.newBuilder().apply {
            if (token != null && url.queryParameter("oauth_token") == null) {
                addQueryParameter("oauth_token", token)
            }
        }.build()

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "en")
            .method(method, if (method == "GET" || method == "DELETE") null else EMPTY_BODY)
            .build()

        val call = http.newCall(request)
        val response = call.execute()
        if (response.code >= 500) {
            response.close()
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
    }
}
