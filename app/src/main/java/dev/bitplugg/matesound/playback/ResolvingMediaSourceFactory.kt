package dev.bitplugg.matesound.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * The queue contains lightweight `matesound://track/<id>` placeholders. The real
 * SoundCloud media URL (with a short-lived authorization token) is only resolved
 * when a track is about to be played.
 */
@OptIn(UnstableApi::class)
class ResolvingMediaSourceFactory(
    context: Context,
    private val resolver: (Long) -> Pair<String, Boolean>,
) : MediaSource.Factory {

    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
    private val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)

    override fun getSupportedTypes(): IntArray =
        intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = this

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri: Uri? = mediaItem.localConfiguration?.uri
        if (uri?.scheme == SCHEME) {
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null) {
                val resolved = runCatching { resolver(id) }.getOrNull()
                if (resolved != null) {
                    val played = mediaItem.buildUpon().setUri(Uri.parse(resolved.first)).build()
                    return if (resolved.second) {
                        hlsFactory.createMediaSource(played)
                    } else {
                        progressiveFactory.createMediaSource(played)
                    }
                }
            }
        }
        val url = uri?.toString().orEmpty()
        return if (url.contains(".m3u8", ignoreCase = true)) {
            hlsFactory.createMediaSource(mediaItem)
        } else {
            progressiveFactory.createMediaSource(mediaItem)
        }
    }

    companion object {
        const val SCHEME = "matesound"

        fun trackUri(id: Long): Uri = Uri.parse("$SCHEME://track/$id")

        fun newPlayer(context: Context, resolver: (Long) -> Pair<String, Boolean>): ExoPlayer =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(ResolvingMediaSourceFactory(context, resolver))
                .build()
    }
}
