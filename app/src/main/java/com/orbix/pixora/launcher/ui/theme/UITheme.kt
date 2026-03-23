package com.orbix.pixora.launcher.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbix.pixora.launcher.ui.pixoraDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Complete UI theme definition for the entire launcher.
 */
data class UITheme(
    val id: String,
    val name: String,
    val emoji: String,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val glowColor: Color,
    val eqGradient: List<Color>,
    val isDark: Boolean = true,
)

object ThemeManager {
    val KEY_THEME = stringPreferencesKey("ui_theme")

    private val _current = MutableStateFlow(Themes.NEON_NIGHT)
    val current: StateFlow<UITheme> = _current

    /** Dedicated scope for fire-and-forget persistence (survives config changes) */
    private val persistScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    suspend fun loadState(context: Context) {
        val id = context.pixoraDataStore.data.first()[KEY_THEME] ?: "neon_night"
        _current.value = Themes.ALL.find { it.id == id } ?: Themes.NEON_NIGHT
    }

    fun setTheme(context: Context, theme: UITheme) {
        _current.value = theme
        persistScope.launch {
            context.pixoraDataStore.edit { it[KEY_THEME] = theme.id }
        }
    }
}

object Themes {

    val NEON_NIGHT = UITheme(
        id = "neon_night", name = "Neon Night", emoji = "\uD83D\uDFE3",
        primary = Color(0xFF7C4DFF),
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF0A0A0F),
        surface = Color(0xFF141420),
        surfaceVariant = Color(0xFF1A1A2E),
        onBackground = Color.White,
        onSurface = Color.White,
        glowColor = Color(0xFF7C4DFF),
        eqGradient = listOf(Color(0xFF00E5FF), Color(0xFF00E676), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF1744)),
    )

    val NATURE = UITheme(
        id = "nature", name = "Nature", emoji = "\uD83C\uDF3F",
        primary = Color(0xFF2E7D32),
        secondary = Color(0xFF81C784),
        background = Color(0xFF0D1A0D),
        surface = Color(0xFF1A2E1A),
        surfaceVariant = Color(0xFF243524),
        onBackground = Color(0xFFE8F5E9),
        onSurface = Color(0xFFE8F5E9),
        glowColor = Color(0xFF4CAF50),
        eqGradient = listOf(Color(0xFF4CAF50), Color(0xFF81C784), Color(0xFFA5D6A7), Color(0xFFFFEB3B), Color(0xFFFF9800)),
    )

    val LIGHT = UITheme(
        id = "light", name = "Light", emoji = "☁\uFE0F",
        primary = Color(0xFF2196F3),
        secondary = Color(0xFF64B5F6),
        background = Color(0xFFF5F5F5),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE8E8E8),
        onBackground = Color(0xFF212121),
        onSurface = Color(0xFF212121),
        glowColor = Color(0xFF2196F3),
        eqGradient = listOf(Color(0xFF2196F3), Color(0xFF42A5F5), Color(0xFF64B5F6), Color(0xFF90CAF9), Color(0xFFBBDEFB)),
        isDark = false,
    )

    val AMOLED = UITheme(
        id = "amoled", name = "AMOLED Dark", emoji = "\uD83D\uDD73",
        primary = Color.White,
        secondary = Color(0xFFBDBDBD),
        background = Color.Black,
        surface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFF121212),
        onBackground = Color.White,
        onSurface = Color.White,
        glowColor = Color.White,
        eqGradient = listOf(Color.White, Color(0xFFBDBDBD), Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFF616161)),
    )

    val OCEAN = UITheme(
        id = "ocean", name = "Ocean", emoji = "\uD83C\uDF0A",
        primary = Color(0xFF0277BD),
        secondary = Color(0xFF00BCD4),
        background = Color(0xFF051A2B),
        surface = Color(0xFF0A2540),
        surfaceVariant = Color(0xFF103050),
        onBackground = Color(0xFFE1F5FE),
        onSurface = Color(0xFFE1F5FE),
        glowColor = Color(0xFF00BCD4),
        eqGradient = listOf(Color(0xFF006064), Color(0xFF00838F), Color(0xFF0097A7), Color(0xFF00BCD4), Color(0xFF00E5FF)),
    )

    val SUNSET = UITheme(
        id = "sunset", name = "Sunset", emoji = "\uD83C\uDF05",
        primary = Color(0xFFFF6D00),
        secondary = Color(0xFFFFAB40),
        background = Color(0xFF1A0F05),
        surface = Color(0xFF2E1A0A),
        surfaceVariant = Color(0xFF3D2415),
        onBackground = Color(0xFFFFF3E0),
        onSurface = Color(0xFFFFF3E0),
        glowColor = Color(0xFFFF9800),
        eqGradient = listOf(Color(0xFFFF6D00), Color(0xFFFF9800), Color(0xFFFFAB40), Color(0xFFFFD54F), Color(0xFFFF5722)),
    )

    val ROSE_GOLD = UITheme(
        id = "rose_gold", name = "Rose Gold", emoji = "\uD83C\uDF39",
        primary = Color(0xFFE91E63),
        secondary = Color(0xFFF48FB1),
        background = Color(0xFF1A0D12),
        surface = Color(0xFF2E1520),
        surfaceVariant = Color(0xFF3D1E2D),
        onBackground = Color(0xFFFCE4EC),
        onSurface = Color(0xFFFCE4EC),
        glowColor = Color(0xFFE91E63),
        eqGradient = listOf(Color(0xFFE91E63), Color(0xFFF06292), Color(0xFFF48FB1), Color(0xFFF8BBD0), Color(0xFFFFD700)),
    )

    val CRIMSON = UITheme(
        id = "crimson", name = "Crimson", emoji = "\uD83D\uDD25",
        primary = Color(0xFFD32F2F),
        secondary = Color(0xFFFF5252),
        background = Color(0xFF0F0505),
        surface = Color(0xFF1A0A0A),
        surfaceVariant = Color(0xFF2E1212),
        onBackground = Color(0xFFFFEBEE),
        onSurface = Color(0xFFFFEBEE),
        glowColor = Color(0xFFFF1744),
        eqGradient = listOf(Color(0xFFFF1744), Color(0xFFD32F2F), Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFFFF6D00)),
    )

    val CORPORATE = UITheme(
        id = "corporate", name = "Corporate", emoji = "\uD83D\uDCBC",
        primary = Color(0xFF1565C0),
        secondary = Color(0xFF90A4AE),
        background = Color(0xFF0A1929),
        surface = Color(0xFF132F4C),
        surfaceVariant = Color(0xFF1A3A5C),
        onBackground = Color(0xFFECEFF1),
        onSurface = Color(0xFFECEFF1),
        glowColor = Color(0xFF42A5F5),
        eqGradient = listOf(Color(0xFF1565C0), Color(0xFF1976D2), Color(0xFF42A5F5), Color(0xFF90A4AE), Color(0xFFB0BEC5)),
    )

    val MONOCHROME = UITheme(
        id = "monochrome", name = "Monochrome", emoji = "\u26AB",
        primary = Color(0xFF9E9E9E),
        secondary = Color(0xFF757575),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        surfaceVariant = Color(0xFF2A2A2A),
        onBackground = Color(0xFFE0E0E0),
        onSurface = Color(0xFFE0E0E0),
        glowColor = Color(0xFFBDBDBD),
        eqGradient = listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD), Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFF616161)),
    )

    val WIN95 = UITheme(
        id = "win95", name = "Windows 95", emoji = "\uD83D\uDDA5",
        primary = Color(0xFF008080),       // Classic teal
        secondary = Color(0xFF000080),     // Navy blue
        background = Color(0xFF008080),    // Teal desktop
        surface = Color(0xFFC0C0C0),       // Silver/gray
        surfaceVariant = Color(0xFF808080), // Darker gray
        onBackground = Color.White,
        onSurface = Color.Black,
        glowColor = Color(0xFF008080),
        eqGradient = listOf(Color(0xFF008080), Color(0xFF000080), Color(0xFF808080), Color(0xFFC0C0C0), Color(0xFF008080)),
        isDark = false,
    )

    val WIN98 = UITheme(
        id = "win98", name = "Windows 98", emoji = "\uD83D\uDCBB",
        primary = Color(0xFF0A246A),       // Win98 blue titlebar
        secondary = Color(0xFFA6CAF0),     // Light blue
        background = Color(0xFF3A6EA5),    // Win98 blue desktop
        surface = Color(0xFFD4D0C8),       // Win98 gray
        surfaceVariant = Color(0xFF808080),
        onBackground = Color.White,
        onSurface = Color.Black,
        glowColor = Color(0xFF0A246A),
        eqGradient = listOf(Color(0xFF0A246A), Color(0xFF3A6EA5), Color(0xFFA6CAF0), Color(0xFFD4D0C8), Color(0xFF0A246A)),
        isDark = false,
    )

    val ALL = listOf(
        NEON_NIGHT, NATURE, LIGHT, AMOLED, OCEAN, SUNSET,
        ROSE_GOLD, CRIMSON, CORPORATE, MONOCHROME, WIN95, WIN98,
    )
}
