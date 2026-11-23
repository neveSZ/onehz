package io.github.nevesz.onehz.core

import android.content.Context
import androidx.core.content.edit
import io.github.nevesz.onehz.core.prefs.PreferencesStore

/**
 * Preferences owned by the Sleep feature. Kept apart from [OneHzPreferences] so the sleep
 * work does not share a file with unrelated changes.
 */
object SleepPreferences {

    private const val KEY_ENABLED = "sleep_enabled"
    private const val KEY_DEEP = "sleep_deep"
    private const val KEY_SKIP_CHARGING = "sleep_skip_charging"
    private const val KEY_DOZE_WITH_MUSIC = "sleep_doze_with_music"

    /** Written by 1.2.x before the music option became a positive "doze with music" switch. */
    private const val LEGACY_KEY_SKIP_MUSIC = "sleep_skip_music"
    private const val KEY_WINDOW_ENABLED = "sleep_window_enabled"
    private const val KEY_WINDOW_START = "sleep_window_start"
    private const val KEY_WINDOW_END = "sleep_window_end"
    private const val KEY_PAUSED_UNTIL = "sleep_paused_until"
    private const val KEY_FORCED = "sleep_forced"
    private const val KEY_FORCED_AT = "sleep_forced_at"
    private const val KEY_RECORDS = "sleep_records"
    private const val KEY_OPEN = "sleep_open"
    private const val KEY_WARNING_SEEN = "sleep_warning_seen"

    private fun prefs(context: Context) = PreferencesStore.get(context)

    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }

    fun deep(context: Context) = prefs(context).getBoolean(KEY_DEEP, true)

    fun setDeep(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_DEEP, value) }

    fun skipCharging(context: Context) = prefs(context).getBoolean(KEY_SKIP_CHARGING, true)

    fun setSkipCharging(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_SKIP_CHARGING, value) }

    /**
     * Keeps Doze active even while music is playing.
     *
     * The old negative preference is read once per lookup instead of being rewritten, so a
     * downgrade still sees the user's previous choice. An absent legacy value means the
     * 1.2.x default: pause with music.
     */
    fun dozeWithMusic(context: Context): Boolean {
        val p = prefs(context)
        if (p.contains(KEY_DOZE_WITH_MUSIC)) {
            return p.getBoolean(KEY_DOZE_WITH_MUSIC, false)
        }
        return !p.getBoolean(LEGACY_KEY_SKIP_MUSIC, true)
    }

    fun setDozeWithMusic(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_DOZE_WITH_MUSIC, value) }

    fun windowEnabled(context: Context) = prefs(context).getBoolean(KEY_WINDOW_ENABLED, false)

    fun setWindowEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_WINDOW_ENABLED, value) }

    fun window(context: Context): SleepWindow {
        val p = prefs(context)
        return SleepWindow(
            startMinute = p.getInt(KEY_WINDOW_START, 23 * 60)
                .coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1),
            endMinute = p.getInt(KEY_WINDOW_END, 7 * 60)
                .coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1),
        )
    }

    fun setWindow(context: Context, startMinute: Int, endMinute: Int) {
        prefs(context).edit {
            putInt(KEY_WINDOW_START, startMinute.coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1))
            putInt(KEY_WINDOW_END, endMinute.coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1))
        }
    }

    fun pausedUntil(context: Context) = prefs(context).getLong(KEY_PAUSED_UNTIL, 0L)

    fun setPausedUntil(context: Context, value: Long) =
        prefs(context).edit { putLong(KEY_PAUSED_UNTIL, value) }

    fun forced(context: Context) = prefs(context).getBoolean(KEY_FORCED, false)

    fun forcedAt(context: Context) = prefs(context).getLong(KEY_FORCED_AT, 0L)

    fun setForced(context: Context, forced: Boolean, at: Long = 0L) {
        prefs(context).edit {
            putBoolean(KEY_FORCED, forced)
            putLong(KEY_FORCED_AT, if (forced) at else 0L)
        }
    }

    fun records(context: Context): List<SleepRecord> =
        SleepLedger.decode(prefs(context).getString(KEY_RECORDS, null))

    fun setRecords(context: Context, records: List<SleepRecord>) =
        prefs(context).edit { putString(KEY_RECORDS, SleepLedger.encode(records)) }

    fun openRecord(context: Context): SleepRecord? =
        SleepLedger.decode(prefs(context).getString(KEY_OPEN, null)).lastOrNull()

    fun setOpenRecord(context: Context, record: SleepRecord?) =
        prefs(context).edit {
            if (record == null) remove(KEY_OPEN)
            else putString(KEY_OPEN, SleepLedger.encode(listOf(record)))
        }

    fun warningSeen(context: Context) = prefs(context).getBoolean(KEY_WARNING_SEEN, false)

    fun setWarningSeen(context: Context) =
        prefs(context).edit { putBoolean(KEY_WARNING_SEEN, true) }
}
