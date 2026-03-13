package com.orbix.pixora.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PixoraDarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF141420),
    surfaceVariant = Color(0xFF1A1A2E),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun PixoraTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Use dynamic color from wallpaper on Android 12+
        dynamicDarkColorScheme(LocalContext.current).copy(
            background = Color(0xFF0A0A0F),
            surface = Color(0xFF141420),
        )
    } else {
        PixoraDarkColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
