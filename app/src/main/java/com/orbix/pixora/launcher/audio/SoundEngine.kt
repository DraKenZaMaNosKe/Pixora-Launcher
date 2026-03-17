package com.orbix.pixora.launcher.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbix.pixora.launcher.ui.pixoraDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * Synthesizes and plays UI sounds programmatically.
 * No audio files needed — all sounds generated via PCM synthesis.
 */
object SoundEngine {

    private const val TAG = "SoundEngine"
    private const val SAMPLE_RATE = 22050
    val KEY_SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
    val KEY_SOUND_THEME = stringPreferencesKey("sound_theme")

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    private val _currentTheme = MutableStateFlow("cyberpunk")
    val currentTheme: StateFlow<String> = _currentTheme

    val availableThemes = listOf("cyberpunk", "zen", "arcade", "minimal")

    /** Load preferences */
    suspend fun loadState(context: Context) {
        val prefs = context.pixoraDataStore.data.first()
        _enabled.value = prefs[KEY_SOUNDS_ENABLED] ?: true
        _currentTheme.value = prefs[KEY_SOUND_THEME] ?: "cyberpunk"
    }

    fun setEnabled(context: Context, on: Boolean) {
        _enabled.value = on
        scope.launch { context.pixoraDataStore.edit { it[KEY_SOUNDS_ENABLED] = on } }
    }

    fun setTheme(context: Context, theme: String) {
        _currentTheme.value = theme
        scope.launch { context.pixoraDataStore.edit { it[KEY_SOUND_THEME] = theme } }
    }

    // ── Public API ──────────────────────────────────────

    fun playTap() = play { getTheme().tap() }
    fun playLongPress() = play { getTheme().longPress() }
    fun playPageSwipe() = play { getTheme().pageSwipe() }
    fun playDrawerOpen() = play { getTheme().drawerOpen() }
    fun playDrawerClose() = play { getTheme().drawerClose() }
    fun playEditModeEnter() = play { getTheme().editModeEnter() }
    fun playEditModeExit() = play { getTheme().editModeExit() }
    fun playStoryFrame() = play { getTheme().storyFrame() }
    fun playNotification() = play { getTheme().notification() }

    // ── Internals ──────────────────────────────────────

    private fun play(generator: () -> ShortArray) {
        if (!_enabled.value) return
        scope.launch {
            try {
                val samples = generator()
                playPcm(samples)
            } catch (e: Exception) {
                Log.w(TAG, "Sound playback failed: ${e.message}")
            }
        }
    }

    private fun playPcm(samples: ShortArray) {
        val bufSize = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        // Auto-release after playback
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) { t?.release() }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
    }

    private fun getTheme(): SoundTheme = when (_currentTheme.value) {
        "cyberpunk" -> CyberpunkTheme
        "zen" -> ZenTheme
        "arcade" -> ArcadeTheme
        "minimal" -> MinimalTheme
        else -> CyberpunkTheme
    }

    // ── Synthesis Primitives ──────────────────────────────

    /** Generate sine wave */
    fun sine(freq: Double, duration: Double, volume: Double = 0.5, fadeOut: Boolean = true): ShortArray {
        val count = (SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = if (fadeOut) (1.0 - t / duration).pow(2.0) else 1.0
            val value = sin(2.0 * PI * freq * t) * volume * envelope
            samples[i] = (value * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /** Generate frequency sweep (whoosh) */
    fun sweep(startFreq: Double, endFreq: Double, duration: Double, volume: Double = 0.3): ShortArray {
        val count = (SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = startFreq + (endFreq - startFreq) * progress
            val envelope = (1.0 - progress).pow(1.5)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val value = sin(phase) * volume * envelope
            samples[i] = (value * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /** Generate filtered noise burst */
    fun noiseBurst(duration: Double, volume: Double = 0.15): ShortArray {
        val count = (SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(count)
        val rng = Random(System.nanoTime())
        var prev = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = (1.0 - t / duration).pow(3.0)
            val noise = rng.nextDouble() * 2.0 - 1.0
            // Simple low-pass filter
            prev = prev * 0.7 + noise * 0.3
            val value = prev * volume * envelope
            samples[i] = (value * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /** Chime: layered harmonics with decay */
    fun chime(baseFreq: Double, duration: Double, volume: Double = 0.4): ShortArray {
        val count = (SAMPLE_RATE * duration).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 5.0 / duration)
            val value = (
                sin(2.0 * PI * baseFreq * t) * 0.5 +
                sin(2.0 * PI * baseFreq * 2.0 * t) * 0.25 +
                sin(2.0 * PI * baseFreq * 3.0 * t) * 0.15 +
                sin(2.0 * PI * baseFreq * 5.0 * t) * 0.1
            ) * volume * env
            samples[i] = (value * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /** Mix two sample arrays */
    fun mix(a: ShortArray, b: ShortArray): ShortArray {
        val len = maxOf(a.size, b.size)
        val result = ShortArray(len)
        for (i in 0 until len) {
            val va = if (i < a.size) a[i].toInt() else 0
            val vb = if (i < b.size) b[i].toInt() else 0
            result[i] = ((va + vb) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    /** Concat sample arrays */
    fun concat(vararg arrays: ShortArray): ShortArray {
        val total = arrays.sumOf { it.size }
        val result = ShortArray(total)
        var offset = 0
        for (arr in arrays) {
            arr.copyInto(result, offset)
            offset += arr.size
        }
        return result
    }
}

// ── Sound Theme Interface ──────────────────────────────

interface SoundTheme {
    fun tap(): ShortArray
    fun longPress(): ShortArray
    fun pageSwipe(): ShortArray
    fun drawerOpen(): ShortArray
    fun drawerClose(): ShortArray
    fun editModeEnter(): ShortArray
    fun editModeExit(): ShortArray
    fun storyFrame(): ShortArray
    fun notification(): ShortArray
}

// ── Cyberpunk Theme: electronic, neon, futuristic ──────

object CyberpunkTheme : SoundTheme {
    override fun tap() = SoundEngine.sine(880.0, 0.06, 0.3)
    override fun longPress() = SoundEngine.sweep(200.0, 600.0, 0.15, 0.25)
    override fun pageSwipe() = SoundEngine.sweep(400.0, 800.0, 0.12, 0.2)
    override fun drawerOpen() = SoundEngine.sweep(300.0, 1200.0, 0.2, 0.25)
    override fun drawerClose() = SoundEngine.sweep(1200.0, 300.0, 0.15, 0.2)
    override fun editModeEnter() = SoundEngine.mix(
        SoundEngine.sine(440.0, 0.1, 0.3),
        SoundEngine.sine(880.0, 0.15, 0.2),
    )
    override fun editModeExit() = SoundEngine.mix(
        SoundEngine.sine(880.0, 0.1, 0.3),
        SoundEngine.sine(440.0, 0.15, 0.2),
    )
    override fun storyFrame() = SoundEngine.mix(
        SoundEngine.sweep(600.0, 1200.0, 0.3, 0.2),
        SoundEngine.noiseBurst(0.15, 0.1),
    )
    override fun notification() = SoundEngine.concat(
        SoundEngine.sine(660.0, 0.08, 0.35),
        SoundEngine.sine(880.0, 0.12, 0.3),
    )
}

// ── Zen Theme: soft, natural, calming ──────────────────

object ZenTheme : SoundTheme {
    override fun tap() = SoundEngine.chime(1200.0, 0.15, 0.2)
    override fun longPress() = SoundEngine.chime(600.0, 0.3, 0.2)
    override fun pageSwipe() = SoundEngine.noiseBurst(0.2, 0.08)
    override fun drawerOpen() = SoundEngine.chime(800.0, 0.4, 0.25)
    override fun drawerClose() = SoundEngine.chime(400.0, 0.3, 0.15)
    override fun editModeEnter() = SoundEngine.chime(528.0, 0.5, 0.25)
    override fun editModeExit() = SoundEngine.chime(396.0, 0.4, 0.2)
    override fun storyFrame() = SoundEngine.mix(
        SoundEngine.chime(528.0, 0.6, 0.2),
        SoundEngine.noiseBurst(0.3, 0.05),
    )
    override fun notification() = SoundEngine.chime(740.0, 0.35, 0.3)
}

// ── Arcade Theme: retro, 8-bit, fun ────────────────────

object ArcadeTheme : SoundTheme {
    private fun square(freq: Double, duration: Double, vol: Double = 0.25): ShortArray {
        val count = (22050 * duration).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / 22050
            val env = (1.0 - t / duration).pow(1.5)
            val value = if (sin(2.0 * PI * freq * t) > 0) vol else -vol
            samples[i] = (value * env * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    override fun tap() = square(1046.0, 0.05)
    override fun longPress() = SoundEngine.concat(square(523.0, 0.05), square(659.0, 0.05))
    override fun pageSwipe() = SoundEngine.sweep(600.0, 1400.0, 0.08, 0.2)
    override fun drawerOpen() = SoundEngine.concat(
        square(523.0, 0.04), square(659.0, 0.04), square(784.0, 0.06)
    )
    override fun drawerClose() = SoundEngine.concat(
        square(784.0, 0.04), square(659.0, 0.04), square(523.0, 0.06)
    )
    override fun editModeEnter() = SoundEngine.concat(
        square(440.0, 0.06), square(554.0, 0.06), square(659.0, 0.08)
    )
    override fun editModeExit() = SoundEngine.concat(
        square(659.0, 0.06), square(554.0, 0.06), square(440.0, 0.08)
    )
    override fun storyFrame() = SoundEngine.concat(
        square(784.0, 0.04), square(988.0, 0.04), square(1175.0, 0.08)
    )
    override fun notification() = SoundEngine.concat(
        square(880.0, 0.06), ShortArray(1100), square(880.0, 0.06)
    )
}

// ── Minimal Theme: subtle clicks, almost silent ────────

object MinimalTheme : SoundTheme {
    override fun tap() = SoundEngine.sine(1200.0, 0.03, 0.15)
    override fun longPress() = SoundEngine.sine(600.0, 0.06, 0.15)
    override fun pageSwipe() = SoundEngine.noiseBurst(0.06, 0.06)
    override fun drawerOpen() = SoundEngine.sine(800.0, 0.08, 0.12)
    override fun drawerClose() = SoundEngine.sine(600.0, 0.06, 0.1)
    override fun editModeEnter() = SoundEngine.sine(700.0, 0.08, 0.15)
    override fun editModeExit() = SoundEngine.sine(500.0, 0.08, 0.12)
    override fun storyFrame() = SoundEngine.chime(900.0, 0.2, 0.12)
    override fun notification() = SoundEngine.sine(900.0, 0.06, 0.18)
}
