package com.orbix.pixora.launcher.data.models

data class Wallpaper(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageFile: String = "",
    val previewFile: String = "",
    val imageSize: Long = 0,
    val previewSize: Long = 0,
    val glowColor: String = "#7C4DFF",
    val category: String = "",
    val badge: String? = null,
    val sortOrder: Int = 0,
    val featured: Boolean = false,
) {
    val isPanoramic: Boolean get() = category.equals("PANORAMIC", ignoreCase = true)
    val previewUrl: String get() = if (previewFile.isNotBlank()) previewFile else imageFile
}

data class WallpaperCatalog(
    val wallpapers: List<Wallpaper> = emptyList()
)
