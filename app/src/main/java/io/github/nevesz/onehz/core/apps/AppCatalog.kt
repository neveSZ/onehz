package io.github.nevesz.onehz.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean
)

/** Read-only catalog of installed apps for the per-app profile screen. */
object AppCatalog {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap?>()

    fun load(context: Context, includeSystem: Boolean): List<InstalledApp> {
        val pm = context.packageManager
        return runCatching {
            pm.getInstalledApplications(0)
                .asSequence()
                .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map {
                    InstalledApp(
                        packageName = it.packageName,
                        label = it.loadLabel(pm).toString(),
                        system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }.getOrDefault(emptyList())
    }

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault(packageName)

    fun icon(context: Context, packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        val bitmap = runCatching {
            val pm = context.packageManager
            drawableToBitmap(pm.getApplicationInfo(packageName, 0).loadIcon(pm))
        }.getOrNull()
        iconCache[packageName] = bitmap
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable): ImageBitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
