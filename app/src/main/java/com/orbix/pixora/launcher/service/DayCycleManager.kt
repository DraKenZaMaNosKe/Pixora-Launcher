package com.orbix.pixora.launcher.service

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import com.orbix.pixora.launcher.ui.pixoraDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manages the Day/Night Cycle wallpaper mode.
 * Automatically switches panoramic wallpapers based on the time of day:
 * - Morning (6am-12pm), Afternoon (12pm-6pm), Evening (6pm-9pm), Night (9pm-6am)
 */
object DayCycleManager {

    private const val TAG = "DayCycle"
    val KEY_ACTIVE_THEME = stringPreferencesKey("day_cycle_active_theme")
    val KEY_THEME_PATHS = stringPreferencesKey("day_cycle_paths") // cached local paths

    private val gson = Gson()

    private val _activeTheme = MutableStateFlow<DayCycleTheme?>(null)
    val activeTheme: StateFlow<DayCycleTheme?> = _activeTheme

    private val _currentPeriod = MutableStateFlow(DayPeriod.MORNING)
    val currentPeriod: StateFlow<DayPeriod> = _currentPeriod

    private val _currentImagePath = MutableStateFlow<String?>(null)
    val currentImagePath: StateFlow<String?> = _currentImagePath

    /** Cached local paths for each period */
    private var cachedPaths: Map<String, String> = emptyMap()

    suspend fun loadState(context: Context) {
        val prefs = context.pixoraDataStore.data.first()
        val themeJson = prefs[KEY_ACTIVE_THEME]
        if (themeJson != null) {
            try {
                _activeTheme.value = gson.fromJson(themeJson, DayCycleTheme::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse day cycle theme", e)
            }
        }
        val pathsJson = prefs[KEY_THEME_PATHS]
        if (pathsJson != null) {
            try {
                cachedPaths = gson.fromJson(pathsJson, object : TypeToken<Map<String, String>>() {}.type)
            } catch (_: Exception) {}
        }
        updatePeriod()
    }

    /** Check the current time and update the period + wallpaper */
    fun updatePeriod(): DayPeriod {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 6..11 -> DayPeriod.MORNING
            in 12..17 -> DayPeriod.AFTERNOON
            in 18..20 -> DayPeriod.EVENING
            else -> DayPeriod.NIGHT
        }
        _currentPeriod.value = period

        // Update current image path from cache
        val theme = _activeTheme.value
        if (theme != null) {
            val imageFile = theme.imageForPeriod(period)
            _currentImagePath.value = cachedPaths[imageFile]
        }

        return period
    }

    /**
     * Activate a day cycle theme. Downloads all 4 images.
     */
    suspend fun activate(
        context: Context,
        theme: DayCycleTheme,
        downloadService: DownloadService,
        onProgress: (Int, Int, String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Activating day cycle: ${theme.name}")

        val periods = listOf(
            DayPeriod.MORNING to theme.morningImage,
            DayPeriod.AFTERNOON to theme.afternoonImage,
            DayPeriod.EVENING to theme.eveningImage,
            DayPeriod.NIGHT to theme.nightImage,
        )

        val paths = mutableMapOf<String, String>()
        for ((index, pair) in periods.withIndex()) {
            val (period, imageFile) = pair
            onProgress(index + 1, 4, period.label)
            val path = downloadService.downloadImage(imageFile)
            if (path == null) {
                Log.e(TAG, "Failed to download ${period.label}: $imageFile")
                return@withContext false
            }
            paths[imageFile] = path
        }

        // Persist
        context.pixoraDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_THEME] = gson.toJson(theme)
            prefs[KEY_THEME_PATHS] = gson.toJson(paths)
        }

        _activeTheme.value = theme
        cachedPaths = paths

        // Apply current period wallpaper
        val period = updatePeriod()
        val currentFile = theme.imageForPeriod(period)
        val currentPath = paths[currentFile]
        if (currentPath != null) {
            val installService = WallpaperInstallService(context)
            installService.setWallpaper(currentPath, WallpaperInstallService.Target.BOTH)
        }

        Log.d(TAG, "Day cycle activated: ${theme.name}, current period: ${period.label}")
        true
    }

    suspend fun deactivate(context: Context) {
        Log.d(TAG, "Deactivating day cycle")
        context.pixoraDataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_THEME)
            prefs.remove(KEY_THEME_PATHS)
        }
        _activeTheme.value = null
        _currentImagePath.value = null
        cachedPaths = emptyMap()
    }

    /** Check if wallpaper needs to change (called periodically) */
    suspend fun checkAndUpdate(context: Context) {
        val theme = _activeTheme.value ?: return
        val oldPeriod = _currentPeriod.value
        val newPeriod = updatePeriod()

        if (oldPeriod != newPeriod) {
            Log.d(TAG, "Period changed: ${oldPeriod.label} → ${newPeriod.label}")
            val imageFile = theme.imageForPeriod(newPeriod)
            val path = cachedPaths[imageFile] ?: return

            val installService = WallpaperInstallService(context)
            installService.setWallpaper(path, WallpaperInstallService.Target.BOTH)
            _currentImagePath.value = path
        }
    }

    /** Get the pano URI for the current period */
    fun getCurrentPanoUri(): String? {
        val path = _currentImagePath.value ?: return null
        return "pano:$path"
    }
}

enum class DayPeriod(val label: String, val emoji: String) {
    MORNING("Morning", "\u2600\uFE0F"),     // ☀️
    AFTERNOON("Afternoon", "\uD83C\uDF24"), // 🌤
    EVENING("Evening", "\uD83C\uDF05"),     // 🌅
    NIGHT("Night", "\uD83C\uDF19"),         // 🌙
}

/**
 * A set of 4 panoramic wallpapers for each time of day.
 */
data class DayCycleTheme(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val previewImage: String = "",
    val morningImage: String = "",
    val afternoonImage: String = "",
    val eveningImage: String = "",
    val nightImage: String = "",
    val glowColor: String = "#7C4DFF",
) {
    fun imageForPeriod(period: DayPeriod): String = when (period) {
        DayPeriod.MORNING -> morningImage
        DayPeriod.AFTERNOON -> afternoonImage
        DayPeriod.EVENING -> eveningImage
        DayPeriod.NIGHT -> nightImage
    }
}

/**
 * Catalog of available day cycle themes.
 */
data class DayCycleCatalog(
    val themes: List<DayCycleTheme> = emptyList(),
)
