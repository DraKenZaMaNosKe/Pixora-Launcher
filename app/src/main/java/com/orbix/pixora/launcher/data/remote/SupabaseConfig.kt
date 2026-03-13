package com.orbix.pixora.launcher.data.remote

object SupabaseConfig {
    private const val PROJECT_URL = "https://vzuwvsmlyigjtsearxym.supabase.co"
    private const val STORAGE_BASE = "$PROJECT_URL/storage/v1/object/public"
    private const val BUCKET = "wallpaper-images"

    fun imageUrl(filename: String): String = "$STORAGE_BASE/$BUCKET/$filename"
    fun catalogUrl(): String = "$STORAGE_BASE/$BUCKET/dynamic_catalog.json"
    fun storiesCatalogUrl(): String = "$STORAGE_BASE/$BUCKET/stories_catalog.json"
}
