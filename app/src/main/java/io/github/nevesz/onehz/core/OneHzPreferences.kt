package io.github.nevesz.onehz.core

import android.content.Context
import androidx.core.content.edit

/** Local configuration for OneHZ. All values are stored on-device only. */
object OneHzPreferences {
    private const val NAME = "onehz_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_PROFILE_NORMAL = "profile_normal"
    private const val KEY_PROFILE_NORMAL_ENABLED = "profile_normal_enabled"
    private const val KEY_PROFILE_ECO = "profile_eco"
    private const val KEY_PROFILE_ECO_ENABLED = "profile_eco_enabled"
    private const val KEY_AOD_ENABLED = "aod_enabled"
    private const val KEY_AOD_HZ = "aod_hz"
    private const val KEY_AOD_BRIGHTNESS_ENABLED = "aod_brightness_enabled"
    private const val KEY_AOD_BRIGHTNESS_PCT = "aod_brightness_pct"
    private const val KEY_AOD_BRIGHTNESS_MIN_PCT = "aod_brightness_min_pct"
    private const val KEY_AOD_BRIGHTNESS_MAX_PCT = "aod_brightness_max_pct"
    private const val KEY_AOD_BRIGHTNESS_WRITTEN = "aod_brightness_written"
    private const val KEY_VIDEO_ENABLED = "video_enabled"
    private const val KEY_VIDEO_MIN_HZ = "video_min_hz"
    private const val KEY_SESSION_PENDING = "session_pending"
    private const val KEY_ECO_GLOBAL_BACKUP = "eco_global_backup"
    private const val KEY_LOGS_PAUSED = "logs_paused"
    private const val KEY_SETUP_COMPLETED = "setup_completed"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
    private const val APP_PREFIX = "app_"
    private const val APP_PROFILE = "_profile"
    private const val APP_IGNORE_ECO = "_ignore_eco"

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_ENABLED, value) }

    // ---------------------------------------------------------------- profiles

    /**
     * Default profile for a fresh install: device ceiling -> ~60 Hz -> device minimum.
     * The ECO default caps the first step at 90 Hz on panels that support it.
     */
    fun defaultNormalProfile(context: Context): FrequencyProfile =
        FrequencyProfile.deviceDefault(SamsungDisplayRates.main(context), cap = null)

    fun defaultEcoProfile(context: Context): FrequencyProfile =
        FrequencyProfile.deviceDefault(SamsungDisplayRates.main(context), cap = 90)

    fun normalProfile(context: Context): FrequencyProfile =
        readProfile(context, KEY_PROFILE_NORMAL, defaultNormalProfile(context))

    fun ecoProfile(context: Context): FrequencyProfile =
        readProfile(context, KEY_PROFILE_ECO, defaultEcoProfile(context))

    private fun readProfile(context: Context, key: String, fallback: FrequencyProfile): FrequencyProfile {
        val prefs = prefs(context)
        if (!prefs.contains(key)) return fallback
        return FrequencyProfile.parse(prefs.getString(key, null)) ?: fallback
    }

    fun saveNormalProfile(context: Context, profile: FrequencyProfile) =
        prefs(context).edit { putString(KEY_PROFILE_NORMAL, profile.serialize()) }

    fun saveEcoProfile(context: Context, profile: FrequencyProfile) =
        prefs(context).edit { putString(KEY_PROFILE_ECO, profile.serialize()) }

    fun normalEnabled(context: Context) = prefs(context).getBoolean(KEY_PROFILE_NORMAL_ENABLED, true)
    fun setNormalEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_PROFILE_NORMAL_ENABLED, value) }

    fun ecoEnabled(context: Context) = prefs(context).getBoolean(KEY_PROFILE_ECO_ENABLED, true)
    fun setEcoEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_PROFILE_ECO_ENABLED, value) }

    /**
     * Original values of the Samsung battery-saver refresh-rate keys changed by OneHZ.
     * Kept so a crash or process death cannot leave the system settings modified forever.
     */
    fun ecoGlobalBackup(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_ECO_GLOBAL_BACKUP, null) ?: return emptyMap()
        return raw.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null
                else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
    }

    fun setEcoGlobalBackup(context: Context, values: Map<String, String>) {
        prefs(context).edit {
            if (values.isEmpty()) remove(KEY_ECO_GLOBAL_BACKUP)
            else putString(
                KEY_ECO_GLOBAL_BACKUP,
                values.entries.joinToString("\n") { "${it.key}=${it.value}" }
            )
        }
    }

    // --------------------------------------------------------------------- AOD

    fun aodEnabled(context: Context) = prefs(context).getBoolean(KEY_AOD_ENABLED, false)
    fun aodHz(context: Context) = prefs(context).getInt(KEY_AOD_HZ, 30)
    fun setAodEnabled(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_AOD_ENABLED, value) }
    fun setAodHz(context: Context, value: Int) = prefs(context).edit { putInt(KEY_AOD_HZ, value) }

    // ---------------------------------------------------------- AOD brightness

    fun aodBrightnessEnabled(context: Context) =
        prefs(context).getBoolean(KEY_AOD_BRIGHTNESS_ENABLED, false)

    fun setAodBrightnessEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_AOD_BRIGHTNESS_ENABLED, value) }

    /** Percentage of the screen brightness used for the AOD, independent from the safety band. */
    fun aodBrightnessPct(context: Context): Int =
        prefs(context).getInt(KEY_AOD_BRIGHTNESS_PCT, 10)
            .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)

    fun aodBrightnessMinPct(context: Context) = aodBrightnessBand(context).first

    fun aodBrightnessMaxPct(context: Context) = aodBrightnessBand(context).second

    fun setAodBrightnessPct(context: Context, value: Int) {
        prefs(context).edit {
            putInt(
                KEY_AOD_BRIGHTNESS_PCT,
                value.coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)
            )
        }
    }

    /** Saves the safety band only. The chosen percentage is never moved by the band. */
    fun setAodBrightnessBand(context: Context, minPct: Int, maxPct: Int) {
        val min = minPct.coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT - 1)
        val max = maxPct.coerceIn(min + 1, AodBrightnessMath.MAX_PCT)
        prefs(context).edit {
            putInt(KEY_AOD_BRIGHTNESS_MIN_PCT, min)
            putInt(KEY_AOD_BRIGHTNESS_MAX_PCT, max)
        }
    }

    private fun aodBrightnessBand(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        val min = p.getInt(KEY_AOD_BRIGHTNESS_MIN_PCT, AodBrightnessMath.DEFAULT_MIN_PCT)
            .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT - 1)
        val max = p.getInt(KEY_AOD_BRIGHTNESS_MAX_PCT, AodBrightnessMath.DEFAULT_MAX_PCT)
            .coerceIn(min + 1, AodBrightnessMath.MAX_PCT)
        return min to max
    }

    /**
     * True while the app may have left the debug AOD brightness key behind. Used to clean up
     * only when needed, instead of writing a Settings row on every service start.
     */
    fun aodBrightnessWritten(context: Context) =
        prefs(context).getBoolean(KEY_AOD_BRIGHTNESS_WRITTEN, false)

    fun setAodBrightnessWritten(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_AOD_BRIGHTNESS_WRITTEN, value) }

    // --------------------------------------------------------------------- video

    fun videoEnabled(context: Context) = prefs(context).getBoolean(KEY_VIDEO_ENABLED, false)

    fun setVideoEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_VIDEO_ENABLED, value) }

    fun videoMinHz(context: Context) = prefs(context).getInt(KEY_VIDEO_MIN_HZ, 60)

    fun setVideoMinHz(context: Context, value: Int) =
        prefs(context).edit { putInt(KEY_VIDEO_MIN_HZ, value) }

    /**
     * Set while a session is active. If the process dies without a clean release, the next
     * connection resets peak/min back to the system defaults.
     */
    fun markSessionPending(context: Context) = prefs(context).edit { putBoolean(KEY_SESSION_PENDING, true) }

    fun hasPendingSession(context: Context): Boolean = prefs(context).getBoolean(KEY_SESSION_PENDING, false)

    fun clearSessionPending(context: Context) = prefs(context).edit {
        putBoolean(KEY_SESSION_PENDING, false)
    }

    fun logsPaused(context: Context) = prefs(context).getBoolean(KEY_LOGS_PAUSED, false)
    fun setLogsPaused(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_LOGS_PAUSED, value) }

    fun setupCompleted(context: Context) = prefs(context).getBoolean(KEY_SETUP_COMPLETED, false)
    fun setSetupCompleted(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_SETUP_COMPLETED, value) }

    // -------------------------------------------------------------- appearance

    fun themeMode(context: Context): ThemeMode =
        ThemeMode.fromStored(prefs(context).getString(KEY_THEME_MODE, null))

    fun setThemeMode(context: Context, mode: ThemeMode) =
        prefs(context).edit { putString(KEY_THEME_MODE, mode.stored) }

    // ------------------------------------------------------------- diagnostics

    /** Diagnostics are hidden until the user unlocks them from the About screen. */
    fun diagnosticsEnabled(context: Context) =
        prefs(context).getBoolean(KEY_DIAGNOSTICS_ENABLED, false)

    fun setDiagnosticsEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_DIAGNOSTICS_ENABLED, value) }

    // ------------------------------------------------------------ per-app profile

    data class AppProfile(
        val packageName: String,
        val profile: FrequencyProfile? = null,
        val ignoreEco: Boolean = false
    ) {
        val configured: Boolean get() = profile != null || ignoreEco
    }

    fun appProfile(context: Context, packageName: String): AppProfile {
        val p = prefs(context)
        val key = APP_PREFIX + packageName
        val profile = if (p.contains(key + APP_PROFILE)) {
            FrequencyProfile.parse(p.getString(key + APP_PROFILE, null))
        } else {
            null
        }
        return AppProfile(
            packageName = packageName,
            profile = profile?.takeIf { it.steps.isNotEmpty() },
            ignoreEco = p.getBoolean(key + APP_IGNORE_ECO, false)
        )
    }

    fun saveAppProfile(context: Context, profile: AppProfile) {
        val key = APP_PREFIX + profile.packageName
        prefs(context).edit {
            if (profile.profile != null && profile.profile.steps.isNotEmpty()) {
                putString(key + APP_PROFILE, profile.profile.serialize())
            } else {
                remove(key + APP_PROFILE)
            }
            putBoolean(key + APP_IGNORE_ECO, profile.ignoreEco)
        }
    }

    fun clearAppProfile(context: Context, packageName: String) {
        val key = APP_PREFIX + packageName
        prefs(context).edit {
            remove(key + APP_PROFILE)
            remove(key + APP_IGNORE_ECO)
        }
    }

    fun configuredPackages(context: Context): List<String> {
        val keys = prefs(context).all.keys
        val withProfile = keys.filter { it.startsWith(APP_PREFIX) && it.endsWith(APP_PROFILE) }
        val withIgnore = keys.filter { it.startsWith(APP_PREFIX) && it.endsWith(APP_IGNORE_ECO) }
        return (withProfile.map { it.removePrefix(APP_PREFIX).removeSuffix(APP_PROFILE) } +
            withIgnore.map { it.removePrefix(APP_PREFIX).removeSuffix(APP_IGNORE_ECO) })
            .distinct()
    }

    /** True when at least one app has a profile or an ignore-ECO exception. */
    fun hasAppProfiles(context: Context): Boolean = configuredPackages(context).isNotEmpty()
}
