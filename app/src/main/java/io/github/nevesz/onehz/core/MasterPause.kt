package io.github.nevesz.onehz.core

/**
 * Time-boxed pause for the master switch. While paused the engine releases the system
 * settings and does not apply any profile; it resumes on its own when the deadline passes.
 */
object MasterPause {
    const val ONE_HOUR_MS = 60L * 60L * 1_000L

    fun isPaused(until: Long, now: Long): Boolean = until > now

    fun remainingMs(until: Long, now: Long): Long = (until - now).coerceAtLeast(0L)
}
