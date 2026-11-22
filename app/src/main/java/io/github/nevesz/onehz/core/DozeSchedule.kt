package io.github.nevesz.onehz.core

/**
 * Wall-clock window in which Sleep is allowed to run. Ranges may cross midnight, which is
 * also how someone who sleeps during the day configures it (for example 08:00 to 16:00).
 */
data class SleepWindow(val startMinute: Int, val endMinute: Int) {

    fun contains(minuteOfDay: Int): Boolean {
        val minute = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        val start = startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        val end = endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        // A full-day window is the least surprising meaning of start == end.
        if (start == end) return true
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val DEFAULT = SleepWindow(23 * 60, 7 * 60)

        fun minuteOfDay(hour: Int, minute: Int): Int =
            Math.floorMod(hour * 60 + minute, MINUTES_PER_DAY)
    }
}
