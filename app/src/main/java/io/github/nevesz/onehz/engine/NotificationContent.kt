package io.github.nevesz.onehz.engine

/**
 * Pure model for the foreground notification text.
 *
 * The notification has two modes: the profile is always shown when the engine is controlling,
 * and the applied rate is optional (the user setting added in 1.3.1). Keeping the decision
 * and the composition free of Android types makes the mapping unit-testable and keeps the
 * service to a single place where resources are resolved.
 */
enum class NotificationKind { ACTIVE, AOD, PAUSED, IDLE }

object NotificationContent {

    /**
     * Pause wins over everything: the engine releases control and the notification shows the
     * deadline. An idle reason means the engine is not controlling the display, even when a
     * rate is still cached. AOD is its own state because the AOD profile has no profile id.
     */
    fun kind(
        paused: Boolean,
        idleReason: IdleReason?,
        tierKey: String,
        appliedHz: Int
    ): NotificationKind = when {
        paused -> NotificationKind.PAUSED
        idleReason != null -> NotificationKind.IDLE
        tierKey == "AOD" -> NotificationKind.AOD
        appliedHz > 0 -> NotificationKind.ACTIVE
        else -> NotificationKind.IDLE
    }

    /**
     * [formatActive] and [formatAod] are the localized string resources resolved by the
     * caller. The fallbacks guarantee that the notification never shows an empty text or a
     * "null" profile name.
     */
    fun text(
        kind: NotificationKind,
        profileName: String?,
        hz: Int,
        showRate: Boolean,
        formatActive: (String, Int) -> String,
        formatAod: (Int) -> String,
        activeLabel: String,
        aodLabel: String,
        pausedLabel: String?,
        idleLabel: String
    ): String = when (kind) {
        NotificationKind.ACTIVE -> {
            val label = profileName?.takeIf { it.isNotBlank() } ?: activeLabel
            if (showRate && hz > 0) formatActive(label, hz) else label
        }

        NotificationKind.AOD -> if (showRate && hz > 0) formatAod(hz) else aodLabel
        NotificationKind.PAUSED -> pausedLabel?.takeIf { it.isNotBlank() } ?: idleLabel
        NotificationKind.IDLE -> idleLabel
    }
}
