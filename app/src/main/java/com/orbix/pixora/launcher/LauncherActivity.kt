package com.orbix.pixora.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.orbix.pixora.launcher.ui.PixoraLauncherApp
import com.orbix.pixora.launcher.ui.theme.PixoraTheme

class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PixoraLauncherApp()
                }
            }
        }
    }

    // Launcher should not finish on back press
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing — we are the home screen
    }
}
