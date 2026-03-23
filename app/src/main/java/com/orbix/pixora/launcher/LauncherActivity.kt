package com.orbix.pixora.launcher

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.edit
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.orbix.pixora.launcher.audio.AudioCaptureService
import com.orbix.pixora.launcher.audio.AudioSessionTracker
import com.orbix.pixora.launcher.audio.SoundEngine
import com.orbix.pixora.launcher.ui.theme.ThemeManager
import com.orbix.pixora.launcher.service.DayCycleManager
import com.orbix.pixora.launcher.service.StoryManager
import com.orbix.pixora.launcher.ui.EffectKeys
import com.orbix.pixora.launcher.ui.PixoraLauncherApp
import com.orbix.pixora.launcher.ui.pixoraDataStore
import com.orbix.pixora.launcher.ui.theme.PixoraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LauncherActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Pixora"

        /** Emits a timestamp whenever the system home button is pressed */
        private val _homeButtonPressed = MutableStateFlow(0L)
        val homeButtonPressed: StateFlow<Long> = _homeButtonPressed

        /** Emits a timestamp whenever the Activity resumes (launcher becomes visible) */
        private val _resumeEvent = MutableStateFlow(0L)
        val resumeEvent: StateFlow<Long> = _resumeEvent

        /** Show the equalizer explanation dialog before MediaProjection */
        val showEqExplanation = mutableStateOf(false)
        var onEqExplanationAccepted: (() -> Unit)? = null
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
            requestMediaProjection()
        }
    }

    private val defaultLauncherLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Pixora is now your default launcher!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing — we are the home screen
            }
        })

        AudioSessionTracker.register(applicationContext)

        // Load active story state + sound engine
        lifecycleScope.launch {
            StoryManager.loadState(applicationContext)
            SoundEngine.loadState(applicationContext)
            DayCycleManager.loadState(applicationContext)
            ThemeManager.loadState(applicationContext)
        }

        setContent {
            PixoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PixoraLauncherApp(
                        onReconnectEqualizer = { reconnectEqualizer() },
                        onSetDefaultLauncher = { setAsDefaultLauncher() },
                    )
                }

                // Equalizer explanation dialog
                if (showEqExplanation.value) {
                    Dialog(onDismissRequest = { showEqExplanation.value = false }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1A1A2E), Color(0xFF141420))
                                    )
                                )
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "\uD83C\uDFB5",
                                fontSize = 48.sp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Equalizer Setup",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Pixora needs access to the system audio so the equalizer can react to your music in real-time.\n\nOn the next screen, tap \"Siguiente\" (Next) to enable it.",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This does NOT record or share your screen.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { onEqExplanationAccepted?.invoke() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7C4DFF)
                                ),
                            ) {
                                Text(
                                    "Connect Equalizer",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showEqExplanation.value = false },
                            ) {
                                Text(
                                    "Skip for now",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                )
                            }
                            TextButton(
                                onClick = {
                                    showEqExplanation.value = false
                                    lifecycleScope.launch {
                                        pixoraDataStore.edit { it[EffectKeys.EQ_PROMPT_DISMISSED] = true }
                                    }
                                },
                            ) {
                                Text(
                                    "Don't ask again",
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Only request on fresh launch, not on recreation
        // Use lifecycleScope to avoid Handler leak
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                delay(3000)
                checkAndRequestPermissions()
            }
        }
    }

    private suspend fun checkAndRequestPermissions() {
        // Skip if the user dismissed the prompt permanently
        val dismissed = pixoraDataStore.data.map { it[EffectKeys.EQ_PROMPT_DISMISSED] ?: false }.first()
        if (dismissed) {
            Log.d(TAG, "EQ prompt dismissed by user, skipping")
            return
        }

        Log.d(TAG, "checkAndRequestPermissions")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting RECORD_AUDIO")
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture requires API 29+")
            return
        }
        if (AudioCaptureService.isCapturing.value) {
            Log.d(TAG, "Already capturing, skip MediaProjection request")
            return
        }

        // Show explanation dialog before the system dialog
        showEqExplanation.value = true
        onEqExplanationAccepted = {
            showEqExplanation.value = false
            Log.d(TAG, "Requesting MediaProjection permission...")
            try {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request MediaProjection: ${e.message}")
            }
        }
    }

    /**
     * Stop the current audio capture and re-request MediaProjection.
     * This fixes the case where the equalizer stops detecting music.
     */
    private fun reconnectEqualizer() {
        Log.d(TAG, "Reconnecting equalizer...")
        AudioCaptureService.stop(applicationContext)
        // Small delay to let the service fully stop before re-requesting
        lifecycleScope.launch {
            delay(500)
            if (ContextCompat.checkSelfPermission(this@LauncherActivity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                requestMediaProjection()
            }
        }
    }

    /**
     * Prompt user to set Pixora as the default launcher.
     */
    private fun setAsDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — use RoleManager
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                defaultLauncherLauncher.launch(intent)
            } else if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                Toast.makeText(this, "Pixora is already your default launcher!", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 and below — open home app settings
            try {
                val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open home settings: ${e.message}")
                Toast.makeText(this, "Open Settings > Apps > Default apps > Home app", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Tracks whether the activity was actually stopped (went to background).
     *  Starts as true so the first onResume after creation (including process death) triggers reload. */
    private var wasStopped = true

    override fun onStop() {
        super.onStop()
        wasStopped = true
        Log.d(TAG, "onStop — marking as stopped")
    }

    /**
     * Called every time the launcher becomes visible again.
     * Only emits a reload event if the activity was actually stopped
     * (returning from another app, screen unlock, process death).
     * Skips reload when the home button is pressed while already on the launcher.
     */
    override fun onResume() {
        super.onResume()
        if (wasStopped) {
            wasStopped = false
            Log.d(TAG, "onResume — was stopped, emitting resume event for reload")
            _resumeEvent.value = System.currentTimeMillis()
        } else {
            Log.d(TAG, "onResume — was NOT stopped, skipping reload")
        }
    }

    /**
     * Called when user presses system home button while launcher is already open.
     * This triggers scrolling to the designated home page.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent — home button pressed, emitting go-home event")
        _homeButtonPressed.value = System.currentTimeMillis()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        AudioSessionTracker.unregister(applicationContext)
        AudioCaptureService.stop(applicationContext)
    }

}
