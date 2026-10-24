package dev.bitplugg.matesound.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.bitplugg.matesound.BuildConfig
import dev.bitplugg.matesound.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

data class UpdateInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val releaseUrl: String,
)

data class UpdateState(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val upToDate: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val apkFile: File? = null,
    val error: String? = null,
)

/**
 * OTA updates through GitHub Releases. Every release published by the CI
 * contains `app-debug.apk`, signed with the same key as the installed build,
 * so it can be handed to the system package installer directly.
 */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check(userTriggered: Boolean = false) {
        if (_state.value.checking) return
        scope.launch {
            _state.update { it.copy(checking = true, error = null, upToDate = false) }
            runCatching { fetchLatest() }
                .onSuccess { release ->
                    val info = release ?: run {
                        _state.update { it.copy(checking = false, upToDate = true) }
                        return@onSuccess
                    }
                    if (isNewer(info.version, BuildConfig.VERSION_NAME)) {
                        _state.update { it.copy(checking = false, info = info) }
                    } else {
                        _state.update { it.copy(checking = false, info = null, upToDate = true) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            checking = false,
                            error = if (userTriggered) {
                                error.message ?: "update check failed"
                            } else {
                                null
                            },
                        )
                    }
                }
        }
    }

    fun download() {
        val info = _state.value.info ?: return
        if (_state.value.downloading) return
        scope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, error = null, apkFile = null) }
            runCatching {
                val dir = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "updates",
                ).apply { mkdirs() }
                val target = File(dir, "matesound-${info.version}.apk")
                val partial = File(dir, "${target.name}.part")
                fetchToFile(info.apkUrl, partial) { fraction ->
                    _state.update { it.copy(progress = fraction) }
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                target
            }.onSuccess { file ->
                _state.update {
                    it.copy(downloading = false, progress = 1f, apkFile = file)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(downloading = false, error = error.message ?: "download failed")
                }
            }
        }
    }

    /**
     * Opens the system installer. On Android 8+ the user may first have to
     * allow installing unknown apps for matesound.
     */
    fun install() {
        val file = _state.value.apkFile ?: return
        if (!file.exists() || file.length() == 0L) {
            _state.update { it.copy(error = "invalid file") }
            return
        }
        // Basic package verification
        val pm = context.packageManager
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            }
        }.getOrNull()
        if (pkgInfo?.packageName != context.packageName) {
            _state.update { it.copy(error = "Package mismatch") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.update { it.copy(error = context.getString(R.string.update_unknown_sources)) }
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { error ->
            _state.update { it.copy(error = error.message ?: "install failed") }
        }
    }

    fun openReleasePage() {
        val url = _state.value.info?.releaseUrl ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private suspend fun fetchToFile(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) error("invalid url")
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host != "github.com" && host != "objects.githubusercontent.com") error("untrusted host")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/octet-stream")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty update file")
            val total = body.contentLength()
            target.outputStream().use { sink ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (target.length() == 0L) {
            target.delete()
            error("empty download")
        }
        onProgress(1f)
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("GitHub API HTTP ${response.code}")
            val raw = response.body?.string().orEmpty()
            val release = runCatching {
                json.decodeFromString(GithubRelease.serializer(), raw)
            }.getOrNull() ?: return@withContext null
            if (release.draft || release.prerelease) return@withContext null
            val asset = release.assets.firstOrNull { it.name == "app-debug.apk" }
                ?: release.assets.firstOrNull { it.name == "app-release.apk" }
                ?: release.assets.firstOrNull {
                    val n = it.name.lowercase()
                    n.endsWith(".apk") && !n.contains("unsigned")
                }
                ?: return@withContext null
            if (!isAllowedAssetName(asset.name)) return@withContext null
            if (!asset.downloadUrl.startsWith("https://")) return@withContext null
            val host = runCatching { Uri.parse(asset.downloadUrl).host?.lowercase() }.getOrNull().orEmpty()
            if (host != "github.com" && host != "objects.githubusercontent.com") return@withContext null
            if (asset.size > 0 && asset.size > 200L * 1024 * 1024) return@withContext null
            UpdateInfo(
                version = release.tagName.removePrefix("v"),
                tag = release.tagName,
                notes = release.body.orEmpty(),
                apkUrl = asset.downloadUrl,
                apkSize = asset.size,
                releaseUrl = release.htmlUrl,
            )
        }
    }

    private fun isAllowedAssetName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains("..") || name.contains("/") || name.contains("\\\\")) return false
        val n = name.lowercase()
        return n.endsWith(".apk")
    }

    companion object {
        private const val RELEASES_API =
            "https://api.github.com/repos/bitplugg/matesound/releases/latest"
        private const val USER_AGENT = "matesound-android-app"

        fun isNewer(remote: String, current: String): Boolean {
            val remoteParts = parseVersion(remote)
            val currentParts = parseVersion(current)
            val size = maxOf(remoteParts.size, currentParts.size)
            for (index in 0 until size) {
                val remotePart = remoteParts.getOrElse(index) { 0 }
                val currentPart = currentParts.getOrElse(index) { 0 }
                if (remotePart != currentPart) return remotePart > currentPart
            }
            return false
        }

        private fun parseVersion(version: String): List<Int> =
            version.trim()
                .removePrefix("v")
                .substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
    }
}
