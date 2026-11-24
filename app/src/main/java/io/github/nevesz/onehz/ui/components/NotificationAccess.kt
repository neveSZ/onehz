package io.github.nevesz.onehz.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.nevesz.onehz.engine.OneHzKeepAliveService

/**
 * Single source of truth for "can OneHZ show its notification?".
 *
 * The setup flow and the settings screen must agree, so both read this object. The app-level
 * toggle alone is not enough: the user may keep the app allowed but block only the OneHZ
 * channel, and then pinning or changing the notification text would have no visible effect.
 */
object NotificationAccess {

    fun isEnabled(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        // A channel that does not exist yet is not blocked.
        val channel = manager.getNotificationChannel(OneHzKeepAliveService.CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Requests POST_NOTIFICATIONS on Android 13 and later. When the permission is already
     * denied for good (or the app-level switch is off) the system page is the only path, so
     * open it directly instead of showing a dialog that cannot appear.
     */
    fun request(context: Context, launchPermission: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) launchPermission() else openSettings(context)
    }

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
}
