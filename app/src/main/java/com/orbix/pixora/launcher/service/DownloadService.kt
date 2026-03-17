package com.orbix.pixora.launcher.service

import android.content.Context
import android.util.Log
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val wallpapersDir: File
        get() = File(context.filesDir, "wallpapers").also { it.mkdirs() }

    suspend fun downloadImage(filename: String, onProgress: ((Float) -> Unit)? = null): String? =
        withContext(Dispatchers.IO) {
            if (filename.isBlank()) {
                Log.e("PixoraDownload", "Empty filename!")
                return@withContext null
            }

            val localFile = File(wallpapersDir, filename)
            // Create parent dirs if filename has subdirectories (e.g. "panoramic/image.webp")
            localFile.parentFile?.mkdirs()

            // Return cached
            if (localFile.exists() && localFile.length() > 0) {
                Log.d("PixoraDownload", "Cached: ${localFile.absolutePath} (${localFile.length()} bytes)")
                return@withContext localFile.absolutePath
            }

            try {
                val url = SupabaseConfig.imageUrl(filename)
                Log.d("PixoraDownload", "Downloading: $url")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("PixoraDownload", "HTTP ${response.code}: ${response.message}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    localFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress?.invoke(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                }
                Log.d("PixoraDownload", "Downloaded: ${localFile.absolutePath} (${localFile.length()} bytes)")
                localFile.absolutePath
            } catch (e: Exception) {
                Log.e("PixoraDownload", "Download failed: $filename", e)
                localFile.delete()
                null
            }
        }

    suspend fun copyAssetToFile(assetName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Try WebP first (new format), fallback to PNG (legacy)
            val dir = File(context.filesDir, "icon_rooms").also { it.mkdirs() }
            val webpFile = File(dir, "$assetName.webp")
            if (webpFile.exists()) return@withContext webpFile.absolutePath
            val pngFile = File(dir, "$assetName.png")
            if (pngFile.exists()) return@withContext pngFile.absolutePath

            // Try to copy WebP from assets
            try {
                context.assets.open("icon_rooms/$assetName.webp").use { input ->
                    webpFile.outputStream().use { output -> input.copyTo(output) }
                }
                return@withContext webpFile.absolutePath
            } catch (_: Exception) { }

            // Fallback: download from Supabase via IconRoomRepository
            IconRoomRepository.downloadRoom(context, assetName)
        } catch (e: Exception) {
            Log.e("PixoraDownload", "copyAsset failed: $assetName", e)
            null
        }
    }
}
