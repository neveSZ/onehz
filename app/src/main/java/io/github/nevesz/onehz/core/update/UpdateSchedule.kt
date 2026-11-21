package io.github.nevesz.onehz.core.update

/** Pure interval gate used by the app-open check and by unit tests. */
object UpdateSchedule {
    fun isDue(now: Long, lastAttemptAt: Long, interval: UpdateInterval): Boolean {
        if (lastAttemptAt <= 0L) return true
        return now - lastAttemptAt >= interval.millis
    }
}
