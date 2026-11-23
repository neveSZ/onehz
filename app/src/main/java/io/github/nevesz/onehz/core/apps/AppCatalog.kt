package io.github.nevesz.onehz.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val category: Int = 0
)

/** Read-only catalog of installed apps for the per-app profile screen. */
object AppCatalog {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap?>()

    fun load(context: Context, includeSystem: Boolean): List<InstalledApp> {
        val pm = context.packageManager
        return runCatching {
            installedApplications(pm)
                .asSequence()
                .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map {
                    InstalledApp(
                        packageName = it.packageName,
                        label = it.loadLabel(pm).toString(),
                        system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        category = it.category
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }.getOrDefault(emptyList())
    }

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        applicationInfo(pm, packageName).loadLabel(pm).toString()
    }.getOrDefault(packageName)

    fun icon(context: Context, packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        val bitmap = runCatching {
            val pm = context.packageManager
            drawableToBitmap(applicationInfo(pm, packageName).loadIcon(pm))
        }.getOrNull()
        iconCache[packageName] = bitmap
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun installedApplications(pm: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

    @Suppress("DEPRECATION")
    private fun applicationInfo(pm: PackageManager, packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getApplicationInfo(packageName, 0)
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
