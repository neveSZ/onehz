package io.github.nevesz.onehz.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

private const val DEFAULT_XIAOMI_KEY = "miui_refresh_rate"
private const val XIAOMI_USER_KEY = "user_refresh_rate"
private const val SAMSUNG_MODE_KEY = "refresh_rate_mode"

/**
 * Supported display routes. Anything else is explicitly unsupported and must fail closed.
 * There is intentionally no AOSP/generic route: the app only controls devices whose route
 * was validated.
 */
enum class DisplayVendor { SAMSUNG, XIAOMI, UNSUPPORTED }

data class DisplayProfile(
    val vendor: DisplayVendor,
    val hyperOsMajor: Int? = null,
    val hyperOsKey: String? = null,
    val unsupportedReason: String? = null
) {
    val supported: Boolean get() = vendor != DisplayVendor.UNSUPPORTED
    val supportsAodBrightness: Boolean get() = vendor == DisplayVendor.SAMSUNG
    val supportsSamsungEcoKeys: Boolean get() = vendor == DisplayVendor.SAMSUNG

    val label: String
        get() = when (vendor) {
            DisplayVendor.SAMSUNG -> "Samsung"
            DisplayVendor.XIAOMI -> "HyperOS" + (hyperOsMajor?.let { " $it" } ?: "")
            DisplayVendor.UNSUPPORTED -> "Unsupported"
        }
}

object DisplayProfileResolver {

    @Volatile
    private var cached: DisplayProfile? = null

    /**
     * Cached per process. The vendor and route keys do not change while the app runs, and the
     * cache is cleared on a configuration change (fold/unfold) and when the privileged
     * service reconnects. An unsupported result is not cached so a transient read failure
     * during boot can recover.
     */
    fun detect(context: Context): DisplayProfile {
        cached?.let { return it }
        val fresh = detectUncached(context)
        if (fresh.supported) cached = fresh
        return fresh
    }

    fun invalidate() {
        cached = null
    }

    private fun detectUncached(context: Context): DisplayProfile {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brand = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        val isSamsung = manufacturer.contains("samsung") || brand.contains("samsung")
        val isXiaomi = listOf("xiaomi", "redmi", "poco").any {
            manufacturer.contains(it) || brand.contains(it)
        }

        return when {
            isSamsung -> samsungProfile(context)
            isXiaomi -> xiaomiProfile(context)
            else -> DisplayProfile(
                vendor = DisplayVendor.UNSUPPORTED,
                unsupportedReason = "manufacturer=${Build.MANUFACTURER.ifBlank { "?" }} " +
                    "(supported: Samsung, Xiaomi/Redmi/POCO)"
            )
        }
    }

    private fun samsungProfile(context: Context): DisplayProfile =
        if (readSecureInt(context, SAMSUNG_MODE_KEY) != null) {
            DisplayProfile(DisplayVendor.SAMSUNG)
        } else {
            DisplayProfile(
                vendor = DisplayVendor.UNSUPPORTED,
                unsupportedReason = "Samsung route not found ($SAMSUNG_MODE_KEY)"
            )
        }

    private fun xiaomiProfile(context: Context): DisplayProfile {
        val major = detectHyperOsMajor()
        val preferred = if (major != null && major <= 1) XIAOMI_USER_KEY else DEFAULT_XIAOMI_KEY
        val fallback = if (preferred == DEFAULT_XIAOMI_KEY) XIAOMI_USER_KEY else DEFAULT_XIAOMI_KEY
        val key = when {
            readSecureInt(context, preferred) != null -> preferred
            readSecureInt(context, fallback) != null -> fallback
            else -> null
        }
        return if (key == null) {
            DisplayProfile(
                vendor = DisplayVendor.UNSUPPORTED,
                hyperOsMajor = major,
                unsupportedReason = "HyperOS refresh-rate key not found ($DEFAULT_XIAOMI_KEY/$XIAOMI_USER_KEY)"
            )
        } else {
            DisplayProfile(
                vendor = DisplayVendor.XIAOMI,
                hyperOsMajor = major,
                hyperOsKey = key
            )
        }
    }

    private fun readSecureInt(context: Context, key: String): Int? =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }
            .getOrNull()
            ?.toIntOrNull()

    private fun detectHyperOsMajor(): Int? {
        val values = listOf(
            Build.VERSION.INCREMENTAL,
            Build.DISPLAY,
            systemProperty("ro.mi.os.version.name"),
            systemProperty("ro.mi.os.version.incremental")
        )
        val pattern = Regex("""(?:HYPER\s*OS|HYPEROS|OS)[\s_-]?(\d+)""", RegexOption.IGNORE_CASE)
        return values.asSequence()
            .filterNotNull()
            .mapNotNull { pattern.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()
    }

    @SuppressLint("PrivateApi")
    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        get.invoke(null, key, "") as? String
    }.getOrNull()
}
