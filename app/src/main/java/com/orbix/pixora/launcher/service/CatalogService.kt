package com.orbix.pixora.launcher.service

import android.content.Context
import com.google.gson.Gson
import com.orbix.pixora.launcher.data.models.StoryCatalog
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.models.Wallpaper
import com.orbix.pixora.launcher.data.models.WallpaperCatalog
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import kotlinx.coroutines.Dispatchers
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

    private val cacheDir get() = context.filesDir

    companion object {
        private const val CACHE_DURATION = 6 * 60 * 60 * 1000L // 6 hours
    }

    suspend fun getWallpapers(forceRefresh: Boolean = false): List<Wallpaper> = withContext(Dispatchers.IO) {
        // Check memory cache
        if (!forceRefresh && wallpaperCache != null &&
            System.currentTimeMillis() - lastWallpaperFetch < CACHE_DURATION) {
            return@withContext wallpaperCache!!
        }

        // Try network
        try {
            val json = fetchUrl(SupabaseConfig.catalogUrl())
            val catalog = gson.fromJson(json, WallpaperCatalog::class.java)
            wallpaperCache = catalog.wallpapers.sortedBy { it.sortOrder }
            lastWallpaperFetch = System.currentTimeMillis()
            // Save to disk cache
            File(cacheDir, "catalog_cache.json").writeText(json)
            return@withContext wallpaperCache!!
        } catch (_: Exception) { }

        // Fallback to disk cache
        try {
            val file = File(cacheDir, "catalog_cache.json")
            if (file.exists()) {
                val catalog = gson.fromJson(file.readText(), WallpaperCatalog::class.java)
                wallpaperCache = catalog.wallpapers.sortedBy { it.sortOrder }
                lastWallpaperFetch = System.currentTimeMillis()
                return@withContext wallpaperCache!!
            }
        } catch (_: Exception) { }

        emptyList()
    }

    suspend fun getStories(forceRefresh: Boolean = false): List<Story> = withContext(Dispatchers.IO) {
        if (!forceRefresh && storyCache != null &&
            System.currentTimeMillis() - lastStoryFetch < CACHE_DURATION) {
            return@withContext storyCache!!
        }

        try {
            val json = fetchUrl(SupabaseConfig.storiesCatalogUrl())
            val catalog = gson.fromJson(json, StoryCatalog::class.java)
            storyCache = catalog.stories
            lastStoryFetch = System.currentTimeMillis()
            File(cacheDir, "stories_cache.json").writeText(json)
            return@withContext storyCache!!
        } catch (_: Exception) { }

        try {
            val file = File(cacheDir, "stories_cache.json")
            if (file.exists()) {
                val catalog = gson.fromJson(file.readText(), StoryCatalog::class.java)
                storyCache = catalog.stories
                lastStoryFetch = System.currentTimeMillis()
                return@withContext storyCache!!
            }
        } catch (_: Exception) { }

        emptyList()
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        return response.body?.string() ?: throw Exception("Empty response")
    }
}
