package io.github.nevesz.onehz.core

/**
 * When to warn that the system may delay the OneHZ notification.
 *
 * The engine keeps applying the rate, but several OEM skins throttle an app that is not
 * exempt from battery optimization, and the notification then lags behind in the background.
 * The warning is only useful while notifications are on and the app is still optimized, so
 * both the banner and the one-time prompt read this object.
 */
object BatteryWarningPolicy {

    fun shouldShowBanner(notificationsEnabled: Boolean, exempt: Boolean): Boolean =
        notificationsEnabled && !exempt

    /**
     * The prompt appears once. Afterwards only the banner remains, so a user who prefers to
     * keep the system defaults is not asked again on every visit to the settings.
     */
    fun shouldPrompt(
        notificationsEnabled: Boolean,
        exempt: Boolean,
        alreadyPrompted: Boolean
    ): Boolean = shouldShowBanner(notificationsEnabled, exempt) && !alreadyPrompted
}
