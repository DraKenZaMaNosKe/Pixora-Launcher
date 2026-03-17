package com.orbix.pixora.launcher.service

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orbix.pixora.launcher.ui.pixoraDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class PixoraBackup(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val gridLayouts: Map<String, List<String?>> = emptyMap(),
    val homePages: Map<String, Int> = emptyMap(),
    val dockApps: List<String> = emptyList(),
    val background: String = "",
    val effects: Map<String, Boolean> = emptyMap(),
)

object BackupService {
    private const val TAG = "PixoraBackup"
    private val gson = Gson()

    suspend fun exportBackup(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.pixoraDataStore.data.first()

            // Collect all preferences
            val gridJson = prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_GRID_LAYOUTS]
            val gridLayouts: Map<String, List<String?>> = if (gridJson != null) {
                gson.fromJson(gridJson, object : TypeToken<Map<String, List<String?>>>() {}.type)
            } else emptyMap()

            val homePagesJson = prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_HOME_PAGES]
            val homePages: Map<String, Int> = if (homePagesJson != null) {
                gson.fromJson(homePagesJson, object : TypeToken<Map<String, Int>>() {}.type)
            } else emptyMap()

            val dockJson = prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_DOCK_APPS]
            val dockApps: List<String> = if (dockJson != null) {
                gson.fromJson(dockJson, object : TypeToken<List<String>>() {}.type)
            } else emptyList()

            val background = prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_BACKGROUND] ?: ""

            val effects = mapOf(
                "touch_glow" to (prefs[com.orbix.pixora.launcher.ui.EffectKeys.TOUCH_GLOW] ?: true),
                "equalizer" to (prefs[com.orbix.pixora.launcher.ui.EffectKeys.EQUALIZER] ?: true),
                "battery_ring" to (prefs[com.orbix.pixora.launcher.ui.EffectKeys.BATTERY_RING] ?: true),
                "system_rings" to (prefs[com.orbix.pixora.launcher.ui.EffectKeys.SYSTEM_RINGS] ?: true),
                "ambient_particles" to (prefs[com.orbix.pixora.launcher.ui.EffectKeys.AMBIENT_PARTICLES] ?: false),
            )

            val backup = PixoraBackup(
                gridLayouts = gridLayouts,
                homePages = homePages,
                dockApps = dockApps,
                background = background,
                effects = effects,
            )

            val json = gson.toJson(backup)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "pixora_backup_$timestamp.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pixora")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext null

                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Log.d(TAG, "Backup exported: $filename")
                filename
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pixora")
                dir.mkdirs()
                val file = File(dir, filename)
                file.writeText(json)
                Log.d(TAG, "Backup exported: ${file.absolutePath}")
                filename
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backup export failed", e)
            null
        }
    }

    suspend fun importBackup(context: Context, json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(json, PixoraBackup::class.java)
            if (backup.version != 1) {
                Log.e(TAG, "Unsupported backup version: ${backup.version}")
                return@withContext false
            }

            context.pixoraDataStore.edit { prefs ->
                prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_GRID_LAYOUTS] = gson.toJson(backup.gridLayouts)
                prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_HOME_PAGES] = gson.toJson(backup.homePages)
                prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_DOCK_APPS] = gson.toJson(backup.dockApps)
                if (backup.background.isNotBlank()) {
                    prefs[com.orbix.pixora.launcher.ui.home.HomeViewModel.KEY_BACKGROUND] = backup.background
                }
                backup.effects.forEach { (key, value) ->
                    when (key) {
                        "touch_glow" -> prefs[com.orbix.pixora.launcher.ui.EffectKeys.TOUCH_GLOW] = value
                        "equalizer" -> prefs[com.orbix.pixora.launcher.ui.EffectKeys.EQUALIZER] = value
                        "battery_ring" -> prefs[com.orbix.pixora.launcher.ui.EffectKeys.BATTERY_RING] = value
                        "system_rings" -> prefs[com.orbix.pixora.launcher.ui.EffectKeys.SYSTEM_RINGS] = value
                        "ambient_particles" -> prefs[com.orbix.pixora.launcher.ui.EffectKeys.AMBIENT_PARTICLES] = value
                    }
                }
            }

            Log.d(TAG, "Backup imported successfully (${backup.gridLayouts.size} layouts)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup import failed", e)
            false
        }
    }

    /** List available backup files */
    suspend fun listBackups(context: Context): List<String> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val backups = mutableListOf<String>()
                val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("pixora_backup_%.json")
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        backups.add(cursor.getString(nameCol))
                    }
                }
                backups
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pixora")
                if (dir.exists()) {
                    dir.listFiles { f -> f.name.startsWith("pixora_backup_") && f.name.endsWith(".json") }
                        ?.sortedByDescending { it.lastModified() }
                        ?.map { it.name }
                        ?: emptyList()
                } else emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list backups", e)
            emptyList()
        }
    }

    /** Read backup file content */
    suspend fun readBackup(context: Context, filename: String): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(filename)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    } else null
                }
            } else {
                @Suppress("DEPRECATION")
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pixora/$filename")
                if (file.exists()) file.readText() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup: $filename", e)
            null
        }
    }
}
