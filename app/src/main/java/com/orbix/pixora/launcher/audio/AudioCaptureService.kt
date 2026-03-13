package com.orbix.pixora.launcher.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "AudioCapture"
        private const val CHANNEL_ID = "pixora_audio_capture"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 1024
        const val BAR_COUNT = 12

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        private val _bandLevels = MutableStateFlow(FloatArray(BAR_COUNT))
        val bandLevels: StateFlow<FloatArray> = _bandLevels

        private val _peakLevels = MutableStateFlow(FloatArray(BAR_COUNT))
        val peakLevels: StateFlow<FloatArray> = _peakLevels

        private val _isCapturing = MutableStateFlow(false)
        val isCapturing: StateFlow<Boolean> = _isCapturing

        // Store projection data temporarily so it survives the intent transfer
        @Volatile
        private var pendingResultCode: Int = Int.MIN_VALUE
        @Volatile
        private var pendingData: Intent? = null

        fun start(context: Context, resultCode: Int, data: Intent) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            // Store in companion so the service can pick it up
            pendingResultCode = resultCode
            pendingData = data
            val intent = Intent(context, AudioCaptureService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioCaptureService::class.java))
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground IMMEDIATELY - before anything else
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture requires API 29+")
            stopSelf()
            return START_NOT_STICKY
        }

        // Get the projection data from companion storage
        val resultCode = pendingResultCode
        val data = pendingData
        pendingResultCode = Int.MIN_VALUE
        pendingData = null

        if (resultCode == Int.MIN_VALUE || data == null) {
            Log.e(TAG, "Missing MediaProjection result (code=$resultCode, data=${data != null})")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Got MediaProjection data, resultCode=$resultCode")

        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get MediaProjection")
                stopSelf()
                return START_NOT_STICKY
            }

            startAudioCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaProjection: ${e.message}", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val bufferSize = maxOf(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                FFT_SIZE * 2
            )

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                stopSelf()
                return
            }

            audioRecord?.startRecording()
            _isCapturing.value = true
            Log.d(TAG, "Audio capture started! bufferSize=$bufferSize")

            serviceScope.launch { processAudioLoop() }
            serviceScope.launch { peakDecayLoop() }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture: ${e.message}", e)
            stopSelf()
        }
    }

    private suspend fun processAudioLoop() = coroutineScope {
        val buffer = ShortArray(FFT_SIZE)
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val window = DoubleArray(FFT_SIZE) { 0.5 * (1 - cos(2 * PI * it / (FFT_SIZE - 1))) }
        val currentBands = FloatArray(BAR_COUNT)
        val currentPeaks = FloatArray(BAR_COUNT)

        while (isActive) {
            val read = audioRecord?.read(buffer, 0, FFT_SIZE) ?: -1
            if (read <= 0) {
                delay(10)
                continue
            }

            for (i in 0 until read) {
                re[i] = buffer[i].toDouble() / 32768.0 * window[i]
                im[i] = 0.0
            }
            for (i in read until FFT_SIZE) {
                re[i] = 0.0
                im[i] = 0.0
            }

            fft(re, im)

            // 12 bands with logarithmic spacing (43 Hz per bin)
            val binRanges = arrayOf(
                1..1,       // ~43 Hz      Sub-bass
                2..2,       // ~86 Hz      Deep bass
                3..4,       // ~129-172 Hz Bass
                5..7,       // ~215-301 Hz Upper bass
                8..11,      // ~344-473 Hz Low-mid
                12..17,     // ~516-731 Hz Mid
                18..25,     // ~774-1075 Hz Upper-mid
                26..38,     // ~1118-1634 Hz Presence
                39..58,     // ~1677-2494 Hz High-mid
                59..90,     // ~2537-3870 Hz Brilliance
                91..140,    // ~3913-6020 Hz Low treble
                141..250,   // ~6063-10750 Hz Treble
            )

            for (i in 0 until BAR_COUNT) {
                val range = binRanges[i]
                var sum = 0.0
                var count = 0
                for (bin in range) {
                    if (bin >= FFT_SIZE / 2) break
                    val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                    sum += magnitude
                    count++
                }
                val avg = if (count > 0) sum / count else 0.0

                // FFT magnitudes for normalized PCM range ~1-200
                // Map 0dB..42dB → 0..1 (avoids bars pegged at max)
                val db = if (avg > 1e-6) 20.0 * log10(avg) else -40.0
                val normalized = (db / 42.0).coerceIn(0.0, 1.0).toFloat()

                currentBands[i] = if (normalized > currentBands[i]) {
                    currentBands[i] + (normalized - currentBands[i]) * 0.5f
                } else {
                    currentBands[i] + (normalized - currentBands[i]) * 0.15f
                }

                if (currentBands[i] > currentPeaks[i]) {
                    currentPeaks[i] = currentBands[i]
                }
            }

            _bandLevels.value = currentBands.copyOf()
            _peakLevels.value = currentPeaks.copyOf()
        }
    }

    private suspend fun peakDecayLoop() = coroutineScope {
        val peaks = FloatArray(BAR_COUNT)
        while (isActive) {
            delay(33)
            val current = _bandLevels.value
            val currentPeaks = _peakLevels.value
            for (i in peaks.indices) {
                // Fast gravity-like decay so peaks fall quickly
                peaks[i] = max(current[i], currentPeaks[i] - 0.06f)
            }
            _peakLevels.value = peaks.copyOf()
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpRe = re[i]; re[i] = re[j]; re[j] = tmpRe
                val tmpIm = im[i]; im[i] = im[j]; im[j] = tmpIm
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val newCurRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = newCurRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pixora Equalizer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time audio visualization"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pixora")
            .setContentText("Equalizer active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        audioRecord = null
        mediaProjection = null
        _isCapturing.value = false
        _bandLevels.value = FloatArray(BAR_COUNT)
        _peakLevels.value = FloatArray(BAR_COUNT)
        Log.d(TAG, "Audio capture stopped")
    }
}
