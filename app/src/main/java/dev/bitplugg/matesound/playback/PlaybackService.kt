package dev.bitplugg.matesound.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.bitplugg.matesound.MateSoundApp
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.data.model.Quality
import dev.bitplugg.matesound.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val equalizer = EqualizerController()
    private var settings = AppSettings()
    private var quality = Quality.HIGH

    override fun onCreate() {
        super.onCreate()
        val container = (application as MateSoundApp).container

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                ResolvingMediaSourceFactory(this) { trackId ->
                    runBlocking(Dispatchers.IO) {
                        withTimeout(25_000L) {
                            container.repository.streamUrlById(trackId, quality)
                        }
                    }
                },
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                equalizer.attach(audioSessionId, settings)
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(contentIntent())
            .build()

        scope.launch {
            container.prefs.settings.collectLatest { updated ->
                settings = updated
                quality = updated.quality
                equalizer.apply(updated)
                if (player.playbackParameters.speed != updated.playbackSpeed) {
                    player.setPlaybackSpeed(updated.playbackSpeed)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.mediaItemCount == 0 || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        equalizer.release()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
