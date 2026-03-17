package com.orbix.pixora.launcher.service

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.ui.pixoraDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Manages the active story — persists state, schedules frame cycling via WorkManager.
 * Story frames are panoramic images that scroll with the home pages.
 */
object StoryManager {

    private const val TAG = "StoryManager"
    private const val WORK_TAG = "pixora_story_cycle"

    val KEY_ACTIVE_STORY = stringPreferencesKey("active_story_json")
    val KEY_CURRENT_FRAME = intPreferencesKey("story_current_frame")
    val KEY_FRAME_PATHS = stringPreferencesKey("story_frame_paths")
    val KEY_ACTIVATED_AT = longPreferencesKey("story_activated_at")
    val KEY_LANG_INDEX = intPreferencesKey("story_lang_index")

    private val _activeStory = MutableStateFlow<Story?>(null)
    val activeStory: StateFlow<Story?> = _activeStory

    private val _currentFrameIndex = MutableStateFlow(0)
    val currentFrameIndex: StateFlow<Int> = _currentFrameIndex

    /** Current caption to show on home screen */
    private val _currentCaption = MutableStateFlow("")
    val currentCaption: StateFlow<String> = _currentCaption

    /** Language index for captions (0=ES, 1=EN, 2=JA) */
    private val _langIndex = MutableStateFlow(1)
    val langIndex: StateFlow<Int> = _langIndex

    /** Emits the pano: path whenever a frame changes so HomeViewModel can update */
    private val _currentFramePath = MutableStateFlow<String?>(null)
    val currentFramePath: StateFlow<String?> = _currentFramePath

    private val gson = Gson()

    /** Load active story from DataStore on app start */
    suspend fun loadState(context: Context) {
        val prefs = context.pixoraDataStore.data.first()
        val storyJson = prefs[KEY_ACTIVE_STORY]
        if (storyJson != null) {
            try {
                val story = gson.fromJson(storyJson, Story::class.java)
                _activeStory.value = story
                _currentFrameIndex.value = prefs[KEY_CURRENT_FRAME] ?: 0
                _langIndex.value = prefs[KEY_LANG_INDEX] ?: 1

                // Restore caption
                val frameIdx = _currentFrameIndex.value
                if (frameIdx in story.frames.indices) {
                    _currentCaption.value = story.frames[frameIdx].captionForLang(_langIndex.value)
                }

                // Restore frame path
                val pathsJson = prefs[KEY_FRAME_PATHS]
                if (pathsJson != null) {
                    val paths: List<String> = gson.fromJson(pathsJson, object : TypeToken<List<String>>() {}.type)
                    if (frameIdx in paths.indices) {
                        _currentFramePath.value = paths[frameIdx]
                    }
                }

                Log.d(TAG, "Loaded active story: ${story.title}, frame $frameIdx")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse stored story JSON", e)
            }
        }
    }

    suspend fun setLang(context: Context, index: Int) {
        _langIndex.value = index
        // Update caption
        val story = _activeStory.value ?: return
        val frameIdx = _currentFrameIndex.value
        if (frameIdx in story.frames.indices) {
            _currentCaption.value = story.frames[frameIdx].captionForLang(index)
        }
        // Persist
        withContext(Dispatchers.IO) {
            context.pixoraDataStore.edit { it[KEY_LANG_INDEX] = index }
        }
    }

    /**
     * Activate a story: download all frames, persist, schedule cycling.
     * Frames are applied as panoramic wallpapers (pano: prefix).
     */
    suspend fun activate(
        context: Context,
        story: Story,
        downloadService: DownloadService,
        onProgress: (Int, Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Activating story: ${story.title} (${story.frames.size} frames, ${story.intervalMinutes}min)")

        // Download all frames
        val paths = mutableListOf<String>()
        for ((index, frame) in story.frames.withIndex()) {
            onProgress(index + 1, story.frames.size)
            val path = downloadService.downloadImage(frame.imageFile)
            if (path == null) {
                Log.e(TAG, "Failed to download frame ${index + 1}: ${frame.imageFile}")
                return@withContext false
            }
            paths.add(path)
        }

        // Persist state
        context.pixoraDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_STORY] = gson.toJson(story)
            prefs[KEY_CURRENT_FRAME] = 0
            prefs[KEY_FRAME_PATHS] = gson.toJson(paths)
            prefs[KEY_ACTIVATED_AT] = System.currentTimeMillis()
        }

        _activeStory.value = story
        _currentFrameIndex.value = 0
        if (paths.isNotEmpty()) {
            _currentFramePath.value = paths[0]
        }

        // Set caption
        if (story.frames.isNotEmpty()) {
            _currentCaption.value = story.frames[0].captionForLang(_langIndex.value)
        }

        // Apply first frame as system wallpaper too
        if (paths.isNotEmpty()) {
            val installService = WallpaperInstallService(context)
            installService.setWallpaper(paths[0], WallpaperInstallService.Target.BOTH)
        }

        // Schedule periodic frame cycling
        scheduleWork(context, story.intervalMinutes)

        Log.d(TAG, "Story activated! ${paths.size} frames downloaded, cycling every ${story.intervalMinutes}min")
        true
    }

    /** Deactivate the current story and cancel scheduling */
    suspend fun deactivate(context: Context) {
        Log.d(TAG, "Deactivating story")
        // Clean up downloaded frame files
        val prefs = context.pixoraDataStore.data.first()
        val pathsJson = prefs[KEY_FRAME_PATHS]
        if (pathsJson != null) {
            try {
                val paths: List<String> = gson.fromJson(pathsJson, object : TypeToken<List<String>>() {}.type)
                for (path in paths) {
                    try { java.io.File(path).delete() } catch (_: Exception) {}
                }
                Log.d(TAG, "Cleaned up ${paths.size} frame files")
            } catch (_: Exception) {}
        }
        context.pixoraDataStore.edit { p ->
            p.remove(KEY_ACTIVE_STORY)
            p.remove(KEY_CURRENT_FRAME)
            p.remove(KEY_FRAME_PATHS)
            p.remove(KEY_ACTIVATED_AT)
        }
        _activeStory.value = null
        _currentFrameIndex.value = 0
        _currentCaption.value = ""
        _currentFramePath.value = null
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    /** Advance to next frame (called by StoryFrameWorker) */
    suspend fun advanceFrame(context: Context) {
        val prefs = context.pixoraDataStore.data.first()
        val storyJson = prefs[KEY_ACTIVE_STORY] ?: return
        val pathsJson = prefs[KEY_FRAME_PATHS] ?: return

        val story = gson.fromJson(storyJson, Story::class.java)
        val paths: List<String> = gson.fromJson(pathsJson, object : TypeToken<List<String>>() {}.type)
        if (paths.isEmpty()) return

        val currentFrame = (prefs[KEY_CURRENT_FRAME] ?: 0)
        val nextFrame = (currentFrame + 1) % paths.size

        Log.d(TAG, "Advancing frame: $currentFrame -> $nextFrame (${story.title})")

        // Set system wallpaper
        val installService = WallpaperInstallService(context)
        val success = installService.setWallpaper(paths[nextFrame], WallpaperInstallService.Target.BOTH)

        if (success) {
            context.pixoraDataStore.edit { it[KEY_CURRENT_FRAME] = nextFrame }
            _currentFrameIndex.value = nextFrame
            _currentFramePath.value = paths[nextFrame]

            // Update caption
            val lang = prefs[KEY_LANG_INDEX] ?: 1
            if (nextFrame in story.frames.indices) {
                _currentCaption.value = story.frames[nextFrame].captionForLang(lang)
            }
        }
    }

    /** Get the pano: URI for the current frame (for HomeViewModel to set as background) */
    fun getCurrentPanoUri(): String? {
        val path = _currentFramePath.value ?: return null
        return "pano:$path"
    }

    private fun scheduleWork(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)

        val request = PeriodicWorkRequestBuilder<StoryFrameWorker>(
            intervalMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES
        )
            .addTag(WORK_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Log.d(TAG, "Scheduled frame cycling every ${intervalMinutes.coerceAtLeast(15)}min")
    }

}

class StoryFrameWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("StoryFrameWorker", "Executing frame advance")
        StoryManager.advanceFrame(applicationContext)
        return Result.success()
    }
}
