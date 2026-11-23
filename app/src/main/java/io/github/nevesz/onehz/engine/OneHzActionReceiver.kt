package io.github.nevesz.onehz.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.profile.ProfileStore

/**
 * Small, explicit automation surface for Tasker/MacroDroid and similar tools. Every action
 * maps to something the user can already do in the app; there is no privileged operation
 * and no data is returned.
 */
class OneHzActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 60).coerceIn(1, 24 * 60)
                val until = System.currentTimeMillis() + minutes * 60_000L
                OneHzPreferences.setMasterPausedUntil(context, until)
                OneHzEngine.pauseFor(minutes * 60_000L)
            }

            ACTION_RESUME -> {
                OneHzPreferences.setMasterPausedUntil(context, 0L)
                OneHzEngine.resumeNow()
            }

            ACTION_PROFILE -> {
                val id = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
                if (ProfileStore.state(context).profile(id) != null) {
                    ProfileStore.select(context, id)
                    OneHzEngine.reapply()
                }
            }

            ACTION_TOGGLE -> {
                if (OneHzPreferences.isEnabled(context)) {
                    OneHzPreferences.setEnabled(context, false)
                    OneHzKeepAliveService.stop(context)
                    OneHzEngine.stop()
                } else {
                    OneHzPreferences.setEnabled(context, true)
                    OneHzKeepAliveService.start(context)
                    OneHzEngine.start(context)
                    OneHzEngine.reapply()
                }
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "io.github.nevesz.onehz.action.PAUSE_TIMED"
        const val ACTION_RESUME = "io.github.nevesz.onehz.action.RESUME"
        const val ACTION_PROFILE = "io.github.nevesz.onehz.action.SET_PROFILE"
        const val ACTION_TOGGLE = "io.github.nevesz.onehz.action.TOGGLE"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_PROFILE_ID = "profileId"
    }
}
