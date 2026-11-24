package io.github.nevesz.onehz.engine

/**
 * Rules shared by the touch pipeline and the foreground monitor.
 *
 * App assignments need foreground updates even when the active profile is a single step and
 * video is off. Without them a fixed app profile could never be left, because no touch event
 * would start the monitor that looks for the next app.
 */
object ForegroundRefreshPolicy {

    /** Long enough to let an activity transition settle, and past the 250 ms cache. */
    const val PROBE_DELAY_MS = 500L

    /** Do not retry a rejected listener on every apply. */
    const val MONITOR_RETRY_MS = 30_000L

    fun shouldMonitorTouch(
        screenOff: Boolean,
        hasAssignments: Boolean,
        stepCount: Int,
        videoEnabled: Boolean
    ): Boolean = !screenOff && (hasAssignments || stepCount > 1 || videoEnabled)

    fun shouldMonitorForeground(
        started: Boolean,
        screenOff: Boolean,
        hasAssignments: Boolean,
        videoEnabled: Boolean
    ): Boolean = started && !screenOff && (hasAssignments || videoEnabled)

    fun shouldProbe(
        started: Boolean,
        screenOff: Boolean,
        hasAssignments: Boolean,
        videoEnabled: Boolean,
        listenerActive: Boolean
    ): Boolean =
        started && !screenOff && (hasAssignments || videoEnabled) && !listenerActive
}
