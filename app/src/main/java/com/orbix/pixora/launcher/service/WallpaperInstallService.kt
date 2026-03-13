package com.orbix.pixora.launcher.service

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WallpaperInstallService(private val context: Context) {

    enum class Target { HOME, LOCK, BOTH }

    suspend fun setWallpaper(filePath: String, target: Target): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("PixoraInstall", "setWallpaper: path=$filePath target=$target")
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("PixoraInstall", "File does not exist: $filePath")
                return@withContext false
            }
            Log.d("PixoraInstall", "File size: ${file.length()} bytes")

            val manager = WallpaperManager.getInstance(context)
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (bitmap == null) {
                Log.e("PixoraInstall", "Failed to decode bitmap from: $filePath")
                return@withContext false
            }
            Log.d("PixoraInstall", "Bitmap: ${bitmap.width}x${bitmap.height}")

            val flag = when (target) {
                Target.HOME -> WallpaperManager.FLAG_SYSTEM
                Target.LOCK -> WallpaperManager.FLAG_LOCK
                Target.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            manager.setBitmap(bitmap, null, true, flag)
            bitmap.recycle()
            Log.d("PixoraInstall", "Wallpaper set successfully!")
            true
        } catch (e: Exception) {
            Log.e("PixoraInstall", "setWallpaper failed", e)
            false
        }
    }

    suspend fun saveToGallery(filePath: String, displayName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pixora")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext false

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "Pixora")
                dir.mkdirs()
                val dest = File(dir, displayName)
                file.copyTo(dest, overwrite = true)
                true
            }
        } catch (e: Exception) {
            Log.e("PixoraInstall", "saveToGallery failed", e)
            false
        }
    }
}
