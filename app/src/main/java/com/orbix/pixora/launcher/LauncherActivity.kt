package com.orbix.pixora.launcher

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.orbix.pixora.launcher.audio.AudioCaptureService
import com.orbix.pixora.launcher.audio.AudioSessionTracker
import com.orbix.pixora.launcher.ui.PixoraLauncherApp
import com.orbix.pixora.launcher.ui.theme.PixoraTheme

class LauncherActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Pixora"
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "MediaProjection result: code=${result.resultCode} data=${result.data != null}")
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.d(TAG, "MediaProjection GRANTED - starting AudioCaptureService")
            AudioCaptureService.start(applicationContext, result.resultCode, result.data!!)
        } else {
            Log.w(TAG, "MediaProjection DENIED by user")
        }
    }

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "RECORD_AUDIO result: granted=$granted")
        if (granted) {
            requestMediaProjectionDelayed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()

        AudioSessionTracker.register(applicationContext)

        setContent {
            PixoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PixoraLauncherApp()
                }
            }
        }

        // Only request on fresh launch, not on recreation
        if (savedInstanceState == null) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestPermissions()
            }, 3000)
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "checkAndRequestPermissions")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting RECORD_AUDIO")
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestMediaProjectionDelayed()
        }
    }

    private fun requestMediaProjectionDelayed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture requires API 29+")
            return
        }
        if (AudioCaptureService.isCapturing.value) {
            Log.d(TAG, "Already capturing, skip MediaProjection request")
            return
        }

        Log.d(TAG, "Requesting MediaProjection permission...")
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request MediaProjection: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        AudioSessionTracker.unregister(applicationContext)
        AudioCaptureService.stop(applicationContext)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing — we are the home screen
    }
}
