package com.orbix.pixora.launcher.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.orbix.pixora.launcher.data.models.StoryCatalog
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.models.Wallpaper
import com.orbix.pixora.launcher.data.models.WallpaperCatalog
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class CatalogService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // In-memory cache
    private var wallpaperCache: List<Wallpaper>? = null
    private var storyCache: List<Story>? = null
    private var lastWallpaperFetch = 0L
    private var lastStoryFetch = 0L

    private val cacheMutex = Mutex()
    private val cacheDir get() = context.filesDir

    companion object {
        private const val CACHE_DURATION = 6 * 60 * 60 * 1000L // 6 hours
    }

    suspend fun getWallpapers(forceRefresh: Boolean = false): List<Wallpaper> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            // Check memory cache
            if (!forceRefresh && wallpaperCache != null &&
                System.currentTimeMillis() - lastWallpaperFetch < CACHE_DURATION) {
                return@withContext wallpaperCache ?: emptyList()
            }

            // Try network
            if (!NetworkHelper.isOnline(context)) {
                Log.d("CatalogService", "No network, skipping fetch")
            } else {
                try {
                    val json = fetchUrl(SupabaseConfig.catalogUrl())
                    val catalog = gson.fromJson(json, WallpaperCatalog::class.java)
                    wallpaperCache = catalog.wallpapers.sortedBy { it.sortOrder }
                    lastWallpaperFetch = System.currentTimeMillis()
                    // Save to disk cache
                    File(cacheDir, "catalog_cache.json").writeText(json)
                    return@withContext wallpaperCache ?: emptyList()
                } catch (e: Exception) {
                    Log.w("CatalogService", "Network fetch failed: ${e.message}")
                }
            }

            // Fallback to disk cache
            try {
                val file = File(cacheDir, "catalog_cache.json")
                if (file.exists()) {
                    val catalog = gson.fromJson(file.readText(), WallpaperCatalog::class.java)
                    wallpaperCache = catalog.wallpapers.sortedBy { it.sortOrder }
                    lastWallpaperFetch = System.currentTimeMillis()
                    return@withContext wallpaperCache ?: emptyList()
                }
            } catch (e: Exception) {
                Log.w("CatalogService", "Disk cache read failed: ${e.message}")
            }

            emptyList()
        }
    }

    suspend fun getStories(forceRefresh: Boolean = false): List<Story> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            if (!forceRefresh && storyCache != null &&
                System.currentTimeMillis() - lastStoryFetch < CACHE_DURATION) {
                return@withContext storyCache ?: emptyList()
            }

            if (!NetworkHelper.isOnline(context)) {
                Log.d("CatalogService", "No network, skipping fetch")
            } else {
                try {
                    val json = fetchUrl(SupabaseConfig.storiesCatalogUrl())
                    val catalog = gson.fromJson(json, StoryCatalog::class.java)
                    storyCache = catalog.stories
                    lastStoryFetch = System.currentTimeMillis()
                    File(cacheDir, "stories_cache.json").writeText(json)
                    return@withContext storyCache ?: emptyList()
                } catch (e: Exception) {
                    Log.w("CatalogService", "Network fetch failed: ${e.message}")
                }
            }

            try {
                val file = File(cacheDir, "stories_cache.json")
                if (file.exists()) {
                    val catalog = gson.fromJson(file.readText(), StoryCatalog::class.java)
                    storyCache = catalog.stories
                    lastStoryFetch = System.currentTimeMillis()
                    return@withContext storyCache ?: emptyList()
                }
            } catch (e: Exception) {
                Log.w("CatalogService", "Disk cache read failed: ${e.message}")
            }

            emptyList()
        }
    }

    suspend fun getDayCycleThemes(): List<DayCycleTheme> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            // Try network
            if (NetworkHelper.isOnline(context)) {
                try {
                    val json = fetchUrl(SupabaseConfig.dayCycleCatalogUrl())
                    val catalog = gson.fromJson(json, DayCycleCatalog::class.java)
                    File(cacheDir, "day_cycle_cache.json").writeText(json)
                    return@withLock catalog.themes
                } catch (e: Exception) {
                    Log.w("CatalogService", "Day cycle fetch failed: ${e.message}")
                }
            }
            // Fallback to disk cache
            try {
                val file = File(cacheDir, "day_cycle_cache.json")
                if (file.exists()) {
                    val catalog = gson.fromJson(file.readText(), DayCycleCatalog::class.java)
                    return@withLock catalog.themes
                }
            } catch (e: Exception) {
                Log.w("CatalogService", "Day cycle cache read failed: ${e.message}")
            }
            emptyList()
        }
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        return response.use { it.body?.string() ?: throw Exception("Empty response") }
    }
}
