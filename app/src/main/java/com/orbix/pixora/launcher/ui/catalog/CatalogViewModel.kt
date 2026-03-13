package com.orbix.pixora.launcher.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.models.Wallpaper
import com.orbix.pixora.launcher.service.CatalogService
import com.orbix.pixora.launcher.service.DownloadService
import com.orbix.pixora.launcher.service.WallpaperInstallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(app: Application) : AndroidViewModel(app) {

    private val catalogService = CatalogService(app)
    private val downloadService = DownloadService(app)
    val installService = WallpaperInstallService(app)

    // Catalog data
    private val _wallpapers = MutableStateFlow<List<Wallpaper>>(emptyList())
    val wallpapers: StateFlow<List<Wallpaper>> = _wallpapers

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories

    val iconRooms = IconRoom.ALL

    // UI state
    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val categories = listOf(
        "ALL", "ANIME", "GAMING", "NATURE", "ANIMALS", "UNIVERSE",
        "SCENES", "HORROR", "ABSTRACT", "MINIMAL", "PANORAMIC", "SPECIAL"
    )

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _wallpapers.value = catalogService.getWallpapers()
                _stories.value = catalogService.getStories()
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun filteredWallpapers(): List<Wallpaper> {
        val cat = _selectedCategory.value
        val all = _wallpapers.value
        return if (cat == "ALL") all else all.filter { it.category.equals(cat, ignoreCase = true) }
    }

    suspend fun downloadWallpaper(filename: String, onProgress: ((Float) -> Unit)? = null): String? {
        _downloadProgress.value = 0f
        val path = downloadService.downloadImage(filename) { progress ->
            _downloadProgress.value = progress
            onProgress?.invoke(progress)
        }
        _downloadProgress.value = null
        return path
    }

    suspend fun downloadIconRoom(assetName: String): String? {
        return downloadService.copyAssetToFile(assetName)
    }

    fun clearError() { _error.value = null }
}
