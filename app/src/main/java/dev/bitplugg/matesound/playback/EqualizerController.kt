package dev.bitplugg.matesound.playback

import android.media.audiofx.Equalizer
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.data.model.EqualizerPreset
import kotlin.math.roundToInt

/**
 * Thin wrapper around Android's system equalizer attached to ExoPlayer's audio
 * session. Presets are mapped onto whatever band count the device exposes.
 */
class EqualizerController {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0

    fun attach(audioSessionId: Int, settings: AppSettings) {
        if (audioSessionId == 0) return
        if (audioSessionId == sessionId && equalizer != null) {
            apply(settings)
            return
        }
        release()
        sessionId = audioSessionId
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        apply(settings)
    }

    fun apply(settings: AppSettings) {
        val effect = equalizer ?: return
        runCatching {
            effect.enabled = settings.equalizerEnabled
            if (!settings.equalizerEnabled) return
            val range = effect.bandLevelRange
            if (range.size < 2) return
            val min = range[0].toInt()
            val max = range[1].toInt()
            val bands = effect.numberOfBands.toInt()
            val profile = profileFor(settings.equalizerPreset)
            for (index in 0 until bands) {
                val position = if (bands <= 1) 0.5f else index / (bands - 1f)
                val slot = (position * (profile.size - 1)).roundToInt().coerceIn(0, profile.size - 1)
                val level = (max * profile[slot]).roundToInt().coerceIn(min, max)
                effect.setBandLevel(index.toShort(), level.toShort())
            }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    private fun profileFor(preset: EqualizerPreset): FloatArray = when (preset) {
        EqualizerPreset.FLAT -> floatArrayOf(0f)
        EqualizerPreset.BASS -> floatArrayOf(0.9f, 0.7f, 0.3f, 0f, -0.15f, -0.25f)
        EqualizerPreset.VOCAL -> floatArrayOf(-0.15f, 0.1f, 0.45f, 0.8f, 0.5f, 0.1f)
        EqualizerPreset.ELECTRONIC -> floatArrayOf(0.8f, 0.45f, 0f, 0.2f, 0.6f, 0.8f)
        EqualizerPreset.ROCK -> floatArrayOf(0.7f, 0.45f, 0.1f, 0.4f, 0.6f, 0.7f)
    }
}
