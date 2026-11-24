package io.github.nevesz.onehz.core

import android.content.Context
import androidx.core.content.edit
import io.github.nevesz.onehz.core.prefs.PreferencesStore
import io.github.nevesz.onehz.core.update.ReleaseInfo
import io.github.nevesz.onehz.core.update.ReleaseSelector
import io.github.nevesz.onehz.core.update.UpdateChannel
import io.github.nevesz.onehz.core.update.UpdateInterval
import io.github.nevesz.onehz.core.update.UpdateStatus

/** Local configuration for OneHZ. All values are stored on-device only. */
object OneHzPreferences {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MASTER_PAUSED_UNTIL = "master_paused_until"
    internal const val KEY_PROFILE_NORMAL = "profile_normal"
    internal const val KEY_PROFILE_NORMAL_ENABLED = "profile_normal_enabled"
    internal const val KEY_PROFILE_ECO = "profile_eco"
    internal const val KEY_PROFILE_ECO_ENABLED = "profile_eco_enabled"
    internal const val KEY_AOD_MASTER_ENABLED = "aod_master_enabled"
    internal const val KEY_AOD_ENABLED = "aod_enabled"
    internal const val KEY_AOD_HZ = "aod_hz"
    private const val KEY_AOD_BRIGHTNESS_ENABLED = "aod_brightness_enabled"
    private const val KEY_AOD_BRIGHTNESS_PCT = "aod_brightness_pct"
    private const val KEY_AOD_BRIGHTNESS_MIN_PCT = "aod_brightness_min_pct"
    private const val KEY_AOD_BRIGHTNESS_MAX_PCT = "aod_brightness_max_pct"
    private const val KEY_AOD_BRIGHTNESS_WRITTEN = "aod_brightness_written"
    private const val KEY_AOD_SCHEDULE_ENABLED = "aod_schedule_enabled"
    private const val KEY_AOD_SCHEDULE_START = "aod_schedule_start"
    private const val KEY_AOD_SCHEDULE_END = "aod_schedule_end"
    private const val KEY_AOD_SCHEDULE_PCT = "aod_schedule_pct"
    internal const val KEY_VIDEO_ENABLED = "video_enabled"
    internal const val KEY_VIDEO_MIN_HZ = "video_min_hz"
    internal const val KEY_VIDEO_CONFIG = "video_config"
    private const val KEY_PROFILES_MIGRATION_NOTICE = "profiles_migration_notice"
    private const val KEY_SESSION_PENDING = "session_pending"
    private const val KEY_ECO_GLOBAL_BACKUP = "eco_global_backup"
    private const val KEY_RATE_BACKUP = "rate_backup"
    private const val KEY_LOGS_PAUSED = "logs_paused"
    private const val KEY_NOTIFICATION_PINNED = "notification_pinned"
    private const val KEY_SETUP_COMPLETED = "setup_completed"
    private const val KEY_TILE_ADDED = "tile_added"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_UPDATES_ENABLED = "updates_enabled"
    private const val KEY_UPDATE_INTERVAL_MINUTES = "update_interval_minutes"
    private const val KEY_UPDATE_CHANNEL = "update_channel"
    private const val KEY_UPDATE_LAST_ATTEMPT = "update_last_attempt"
    private const val KEY_UPDATE_LAST_STATUS = "update_last_status"
    private const val KEY_UPDATE_ETAG = "update_etag"
    private const val KEY_UPDATE_CACHED_TAG = "update_cached_tag"
    private const val KEY_UPDATE_CACHED_URL = "update_cached_url"
    private const val KEY_UPDATE_CACHED_NAME = "update_cached_name"
    private const val KEY_UPDATE_CACHED_PRERELEASE = "update_cached_prerelease"
    private const val KEY_UPDATE_CACHED_CHANNEL = "update_cached_channel"
    private const val KEY_UPDATE_DISMISSED_VERSION = "update_dismissed_version"
    internal const val APP_PREFIX = "app_"
    internal const val APP_PROFILE = "_profile"
    internal const val APP_IGNORE_ECO = "_ignore_eco"

    fun prefs(context: Context) = PreferencesStore.get(context)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit {
        putBoolean(KEY_ENABLED, value)
        if (!value) remove(KEY_MASTER_PAUSED_UNTIL)
    }

    /** Wall-clock deadline for the time-boxed master pause; 0 means not paused. */
    fun masterPausedUntil(context: Context) = prefs(context).getLong(KEY_MASTER_PAUSED_UNTIL, 0L)

    fun setMasterPausedUntil(context: Context, value: Long) =
        prefs(context).edit {
            if (value <= 0L) remove(KEY_MASTER_PAUSED_UNTIL)
            else putLong(KEY_MASTER_PAUSED_UNTIL, value)
        }

    fun isPaused(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        MasterPause.isPaused(masterPausedUntil(context), now)

    // ---------------------------------------------------------------- profiles

    /**
     * Default profile for a fresh install: device ceiling -> ~60 Hz -> device minimum,
     * with 2 s and 5 s holds. The ECO default starts at 60 Hz and holds it for 2 s.
     */
    fun defaultNormalProfile(context: Context): FrequencyProfile =
        FrequencyProfile.deviceDefault(DisplayRates.main(context), cap = null)

    fun defaultEcoProfile(context: Context): FrequencyProfile =
        FrequencyProfile.deviceDefault(DisplayRates.main(context), cap = 60)

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
    fun ecoGlobalBackup(context: Context): Map<String, String> =
        readSnapshot(context, KEY_ECO_GLOBAL_BACKUP)

    fun setEcoGlobalBackup(context: Context, values: Map<String, String>) =
        writeSnapshot(context, KEY_ECO_GLOBAL_BACKUP, values)

    /**
     * Original values of the non-Samsung refresh-rate keys touched by a session
     * (HyperOS uses `secure/miui_refresh_rate` or `secure/user_refresh_rate`).
     * An empty value means the key did not exist and must be deleted on restore.
     */
    fun rateBackup(context: Context): Map<String, String> =
        readSnapshot(context, KEY_RATE_BACKUP)

    fun setRateBackup(context: Context, values: Map<String, String>) =
        writeSnapshot(context, KEY_RATE_BACKUP, values)

    fun hasRateBackup(context: Context): Boolean = rateBackup(context).isNotEmpty()

    private fun readSnapshot(context: Context, key: String): Map<String, String> {
        val raw = prefs(context).getString(key, null) ?: return emptyMap()
        return raw.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null
                else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
    }

    private fun writeSnapshot(context: Context, key: String, values: Map<String, String>) {
        prefs(context).edit {
            if (values.isEmpty()) remove(key)
            else putString(
                key,
                values.entries.joinToString("\n") { "${it.key}=${it.value}" }
            )
        }
    }

    // --------------------------------------------------------------------- AOD

    /**
     * Master switch for the AOD section. It pauses both the rate and the brightness
     * controls without resetting their individual choices.
     *
     * The first read migrates installs created before the master existed:
     * AOD was considered active when either the rate or the brightness was on.
     */
    fun aodMasterEnabled(context: Context): Boolean {
        val p = prefs(context)
        if (!p.contains(KEY_AOD_MASTER_ENABLED)) {
            val initial = p.getBoolean(KEY_AOD_ENABLED, false) ||
                p.getBoolean(KEY_AOD_BRIGHTNESS_ENABLED, false)
            p.edit { putBoolean(KEY_AOD_MASTER_ENABLED, initial) }
            return initial
        }
        return p.getBoolean(KEY_AOD_MASTER_ENABLED, false)
    }

    /**
     * Turns the AOD master on or off. Turning it on with both sub-options off enables the
     * rate, so the master never becomes a no-op. Turning it off preserves the choices.
     */
    fun setAodMasterEnabled(context: Context, value: Boolean) {
        val p = prefs(context)
        if (value) {
            val rate = p.getBoolean(KEY_AOD_ENABLED, false)
            val brightness = p.getBoolean(KEY_AOD_BRIGHTNESS_ENABLED, false)
            p.edit {
                if (!rate && !brightness) putBoolean(KEY_AOD_ENABLED, true)
                putBoolean(KEY_AOD_MASTER_ENABLED, true)
            }
        } else {
            p.edit { putBoolean(KEY_AOD_MASTER_ENABLED, false) }
        }
    }

    fun aodRateEnabled(context: Context) = prefs(context).getBoolean(KEY_AOD_ENABLED, false)

    /** AOD always uses the lowest rate exposed by the panel. */
    fun aodHz(context: Context): Int =
        DisplayRates.main(context).filter { it > 0 }.minOrNull() ?: 30

    fun setAodRateEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_AOD_ENABLED, value) }
        normalizeAodMaster(context)
    }

    fun setAodHz(context: Context, value: Int) = prefs(context).edit { putInt(KEY_AOD_HZ, value) }

    // ---------------------------------------------------------- AOD brightness

    fun aodBrightnessEnabled(context: Context) =
        prefs(context).getBoolean(KEY_AOD_BRIGHTNESS_ENABLED, false)

    fun setAodBrightnessEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_AOD_BRIGHTNESS_ENABLED, value) }
        normalizeAodMaster(context)
    }

    /** Writes the three AOD switches together, used by backup import. */
    fun setAodSettings(
        context: Context,
        master: Boolean,
        rate: Boolean,
        brightness: Boolean
    ) {
        prefs(context).edit {
            putBoolean(KEY_AOD_MASTER_ENABLED, master && (rate || brightness))
            putBoolean(KEY_AOD_ENABLED, rate)
            putBoolean(KEY_AOD_BRIGHTNESS_ENABLED, brightness)
        }
    }

    /** Keeps the invariant "master on means at least one sub-option is on". */
    private fun normalizeAodMaster(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_AOD_MASTER_ENABLED, false) &&
            !p.getBoolean(KEY_AOD_ENABLED, false) &&
            !p.getBoolean(KEY_AOD_BRIGHTNESS_ENABLED, false)
        ) {
            p.edit { putBoolean(KEY_AOD_MASTER_ENABLED, false) }
        }
    }

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

    /** Samsung-only night schedule for the AOD brightness override. */
    fun aodScheduleEnabled(context: Context) =
        prefs(context).getBoolean(KEY_AOD_SCHEDULE_ENABLED, false)

    fun setAodScheduleEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_AOD_SCHEDULE_ENABLED, value) }

    fun aodScheduleStart(context: Context) =
        prefs(context).getInt(KEY_AOD_SCHEDULE_START, 22 * 60).coerceIn(0, 24 * 60 - 1)

    fun aodScheduleEnd(context: Context) =
        prefs(context).getInt(KEY_AOD_SCHEDULE_END, 7 * 60).coerceIn(0, 24 * 60 - 1)

    fun setAodScheduleWindow(context: Context, startMinute: Int, endMinute: Int) =
        prefs(context).edit {
            putInt(KEY_AOD_SCHEDULE_START, startMinute.coerceIn(0, 24 * 60 - 1))
            putInt(KEY_AOD_SCHEDULE_END, endMinute.coerceIn(0, 24 * 60 - 1))
        }

    fun aodSchedulePct(context: Context) = prefs(context).getInt(KEY_AOD_SCHEDULE_PCT, 15)
        .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)

    fun setAodSchedulePct(context: Context, value: Int) =
        prefs(context).edit {
            putInt(
                KEY_AOD_SCHEDULE_PCT,
                value.coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)
            )
        }

    // --------------------------------------------------------------------- video

    fun videoEnabled(context: Context) = prefs(context).getBoolean(KEY_VIDEO_ENABLED, false)

    fun setVideoEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_VIDEO_ENABLED, value) }

    fun videoMinHz(context: Context) = prefs(context).getInt(KEY_VIDEO_MIN_HZ, 60)

    fun setVideoMinHz(context: Context, value: Int) =
        prefs(context).edit { putInt(KEY_VIDEO_MIN_HZ, value) }

    /** Detection rules and the forced app list. Missing or invalid data falls back to defaults. */
    fun videoConfig(context: Context): VideoConfig =
        VideoConfig.decode(prefs(context).getString(KEY_VIDEO_CONFIG, null))

    fun setVideoConfig(context: Context, config: VideoConfig) =
        prefs(context).edit { putString(KEY_VIDEO_CONFIG, config.encode()) }

    /** Forced apps are global; the four detection rules belong to each device profile. */
    fun profilesMigrationNoticePending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROFILES_MIGRATION_NOTICE, false)

    fun setProfilesMigrationNoticePending(context: Context, pending: Boolean) =
        prefs(context).edit { putBoolean(KEY_PROFILES_MIGRATION_NOTICE, pending) }

    fun forcedVideoPackages(context: Context): Set<String> =
        videoConfig(context).forcedPackages

    fun setForcedVideoPackages(context: Context, packages: Set<String>) {
        setVideoConfig(context, videoConfig(context).copy(forcedPackages = packages))
    }

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

    /**
     * Keeps the foreground notification in the shade after a swipe. Off by default: the
     * notification is dismissible and OneHZ only restores it when the app is opened.
     */
    fun notificationPinned(context: Context) =
        prefs(context).getBoolean(KEY_NOTIFICATION_PINNED, false)

    fun setNotificationPinned(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_NOTIFICATION_PINNED, value) }

    fun setupCompleted(context: Context) = prefs(context).getBoolean(KEY_SETUP_COMPLETED, false)
    fun setSetupCompleted(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_SETUP_COMPLETED, value) }

    /**
     * Best-effort mirror of whether the Quick Settings tile is on the user's grid. Android
     * has no public query for this, so the tile reports its own lifecycle. A tile added
     * before this version is only detected once the Quick Settings panel opens the first
     * time. Local only; it is not part of the backup.
     */
    fun tileAdded(context: Context) = prefs(context).getBoolean(KEY_TILE_ADDED, false)
    fun setTileAdded(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_TILE_ADDED, value) }

    // -------------------------------------------------------------- appearance

    fun themeMode(context: Context): ThemeMode =
        ThemeMode.fromStored(prefs(context).getString(KEY_THEME_MODE, null))

    fun setThemeMode(context: Context, mode: ThemeMode) =
        prefs(context).edit { putString(KEY_THEME_MODE, mode.stored) }

    // ---------------------------------------------------------------- updates

    /** Off by default: no request is made before the user accepts the update check. */
    fun updatesEnabled(context: Context) =
        prefs(context).getBoolean(KEY_UPDATES_ENABLED, false)

    fun setUpdatesEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_UPDATES_ENABLED, value) }

    fun updateInterval(context: Context): UpdateInterval =
        UpdateInterval.fromMinutes(
            prefs(context).getInt(KEY_UPDATE_INTERVAL_MINUTES, UpdateInterval.DEFAULT.minutes)
        )

    fun setUpdateInterval(context: Context, interval: UpdateInterval) =
        prefs(context).edit { putInt(KEY_UPDATE_INTERVAL_MINUTES, interval.minutes) }

    fun updateChannel(context: Context): UpdateChannel =
        UpdateChannel.fromStored(prefs(context).getString(KEY_UPDATE_CHANNEL, null))

    /**
     * Changing the channel invalidates both the conditional request and the cached
     * release, otherwise a stable-only response could be offered while pre-releases are
     * enabled (or the other way around).
     */
    fun setUpdateChannel(context: Context, channel: UpdateChannel) {
        prefs(context).edit {
            putString(KEY_UPDATE_CHANNEL, channel.stored)
            remove(KEY_UPDATE_ETAG)
            remove(KEY_UPDATE_CACHED_TAG)
            remove(KEY_UPDATE_CACHED_URL)
            remove(KEY_UPDATE_CACHED_NAME)
            remove(KEY_UPDATE_CACHED_PRERELEASE)
            remove(KEY_UPDATE_CACHED_CHANNEL)
        }
    }

    fun updateLastAttempt(context: Context): Long =
        prefs(context).getLong(KEY_UPDATE_LAST_ATTEMPT, 0L)

    fun setUpdateLastAttempt(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_UPDATE_LAST_ATTEMPT, value) }

    fun updateLastStatus(context: Context): UpdateStatus =
        UpdateStatus.entries.firstOrNull {
            it.name == prefs(context).getString(KEY_UPDATE_LAST_STATUS, null)
        } ?: UpdateStatus.IDLE

    fun setUpdateLastStatus(context: Context, status: UpdateStatus) =
        prefs(context).edit { putString(KEY_UPDATE_LAST_STATUS, status.name) }

    fun updateEtag(context: Context): String? =
        prefs(context).getString(KEY_UPDATE_ETAG, null)

    fun setUpdateEtag(context: Context, value: String?) {
        prefs(context).edit {
            if (value.isNullOrBlank()) remove(KEY_UPDATE_ETAG) else putString(KEY_UPDATE_ETAG, value)
        }
    }

    fun updateCachedRelease(context: Context): ReleaseInfo? {
        val p = prefs(context)
        if (p.getString(KEY_UPDATE_CACHED_CHANNEL, null) != updateChannel(context).stored) {
            return null
        }
        val tag = p.getString(KEY_UPDATE_CACHED_TAG, null) ?: return null
        val url = p.getString(KEY_UPDATE_CACHED_URL, null) ?: return null
        if (!ReleaseSelector.isAllowedReleaseUrl(url)) return null
        return ReleaseInfo(
            tag = tag,
            url = url,
            name = p.getString(KEY_UPDATE_CACHED_NAME, null),
            prereleaseFlag = p.getBoolean(KEY_UPDATE_CACHED_PRERELEASE, false)
        )
    }

    fun setUpdateCachedRelease(context: Context, release: ReleaseInfo) {
        val channel = updateChannel(context).stored
        prefs(context).edit {
            putString(KEY_UPDATE_CACHED_TAG, release.tag)
            putString(KEY_UPDATE_CACHED_URL, release.url)
            if (release.name != null) putString(KEY_UPDATE_CACHED_NAME, release.name)
            else remove(KEY_UPDATE_CACHED_NAME)
            putBoolean(KEY_UPDATE_CACHED_PRERELEASE, release.isPrerelease)
            putString(KEY_UPDATE_CACHED_CHANNEL, channel)
        }
    }

    fun clearUpdateCache(context: Context) {
        prefs(context).edit {
            remove(KEY_UPDATE_CACHED_TAG)
            remove(KEY_UPDATE_CACHED_URL)
            remove(KEY_UPDATE_CACHED_NAME)
            remove(KEY_UPDATE_CACHED_PRERELEASE)
            remove(KEY_UPDATE_CACHED_CHANNEL)
        }
    }

    fun updateDismissedVersion(context: Context): String? =
        prefs(context).getString(KEY_UPDATE_DISMISSED_VERSION, null)

    fun setUpdateDismissedVersion(context: Context, tag: String?) {
        prefs(context).edit {
            if (tag.isNullOrBlank()) remove(KEY_UPDATE_DISMISSED_VERSION)
            else putString(KEY_UPDATE_DISMISSED_VERSION, tag)
        }
    }

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
