package com.orbix.pixora.launcher.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.orbix.pixora.launcher.data.models.AppInfo
import java.io.ByteArrayOutputStream

class AppsRepository(private val context: Context) {

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        return resolveInfos
            .filter { it.activityInfo.packageName != context.packageName } // Exclude ourselves
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                    iconBytes = drawableToBytes(ri.loadIcon(pm)),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    private fun drawableToBytes(drawable: Drawable): ByteArray {
        val bitmap = drawableToBitmap(drawable, 96)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
