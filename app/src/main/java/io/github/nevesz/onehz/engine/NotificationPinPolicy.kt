package io.github.nevesz.onehz.engine

/**
 * Decides whether the foreground notification may be posted after the user acted on it.
 *
 * Android 14 and later let the user dismiss foreground-service notifications even with
 * `setOngoing(true)`. When the notification is pinned, the service restores it; otherwise the
 * dismissal is respected until the app is opened again.
 */
internal object NotificationPinPolicy {

    /** True when a state change may post the notification again. */
    fun canPost(pinned: Boolean, dismissed: Boolean): Boolean = pinned || !dismissed

    /** True when a dismissed notification comes back on its own. */
    fun restoresAfterDismiss(pinned: Boolean): Boolean = pinned
}
