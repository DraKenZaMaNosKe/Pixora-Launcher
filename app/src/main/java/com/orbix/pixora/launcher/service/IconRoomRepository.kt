package com.orbix.pixora.launcher.service

import android.content.Context
import android.util.Log
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages icon room images — bundled room 01 from assets, rest downloaded from Supabase.
 * All rooms cached locally as WebP after first download.
 */
object IconRoomRepository {

    private const val TAG = "IconRoomRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "icon_rooms").also { it.mkdirs() }

    /**
     * Get the local file path for an icon room image.
     * Returns immediately if cached, downloads if not.
     * For the bundled room (01), copies from assets on first use.
     */
    suspend fun getRoomImagePath(context: Context, room: IconRoom): String? {
        val cached = File(cacheDir(context), "${room.assetName}.webp")
        if (cached.exists() && cached.length() > 0) {
            return cached.absolutePath
        }

        return if (room.isBundled) {
            copyBundledRoom(context, room.assetName)
        } else {
            downloadRoom(context, room.assetName)
        }
    }

    /**
     * Get the image data source for Coil/AsyncImage.
     * Returns File if cached, or asset URI for bundled room 01.
     */
    fun getRoomImageSource(context: Context, room: IconRoom): Any {
        val cached = File(cacheDir(context), "${room.assetName}.webp")
        if (cached.exists() && cached.length() > 0) {
            return cached
        }
        if (room.isBundled) {
            return "file:///android_asset/icon_rooms/${room.assetName}.webp"
        }
        // Not cached — return Supabase URL (Coil will download)
        return SupabaseConfig.iconRoomUrl(room.assetName)
    }

    /** Check if a room is already cached locally */
    fun isRoomCached(context: Context, room: IconRoom): Boolean {
        if (room.isBundled) return true
        val cached = File(cacheDir(context), "${room.assetName}.webp")
        return cached.exists() && cached.length() > 0
    }

    /** Download a room and cache it */
    suspend fun downloadRoom(
        context: Context,
        assetName: String,
        onProgress: ((Float) -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        val cached = File(cacheDir(context), "$assetName.webp")
        if (cached.exists() && cached.length() > 0) return@withContext cached.absolutePath

        try {
            val url = SupabaseConfig.iconRoomUrl(assetName)
            Log.d(TAG, "Downloading room: $url")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code} for $assetName")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                cached.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) onProgress?.invoke(downloaded.toFloat() / totalBytes)
                    }
                }
            }

            Log.d(TAG, "Downloaded $assetName: ${cached.length()} bytes")
            cached.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $assetName", e)
            cached.delete()
            null
        }
    }

    /** Copy bundled room from assets to cache */
    private suspend fun copyBundledRoom(context: Context, assetName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val cached = File(cacheDir(context), "$assetName.webp")
                context.assets.open("icon_rooms/$assetName.webp").use { input ->
                    cached.outputStream().use { output -> input.copyTo(output) }
                }
                cached.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled room: $assetName", e)
                null
            }
        }
}
