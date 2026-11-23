package io.github.nevesz.onehz.ui.tile

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState

/**
 * Quick Settings entry point. A click enables OneHZ when it is off and disables it when it
 * is on. Pause and resume stay in the notification, Home and the launcher shortcut, so the
 * tile has one predictable action.
 */
class OneHzTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        if (OneHzPreferences.isEnabled(context)) disable(context) else enable(context)
        updateTile()
    }

    private fun enable(context: Context) {
        OneHzPreferences.setEnabled(context, true)
        OneHzKeepAliveService.start(context)
        OneHzEngine.start(context)
        OneHzEngine.reapply()
    }

    private fun disable(context: Context) {
        OneHzPreferences.setEnabled(context, false)
        OneHzKeepAliveService.stop(context)
        OneHzEngine.stop()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val context = applicationContext
        val enabled = OneHzPreferences.isEnabled(context)
        val paused = enabled && OneHzPreferences.isPaused(context)
        val ready = ShizukuGateway.state.value == ShizukuState.READY

        tile.icon = Icon.createWithResource(context, R.drawable.ic_stat_onehz)
        tile.label = getString(R.string.app_name)
        tile.state = when {
            !enabled -> Tile.STATE_INACTIVE
            paused -> Tile.STATE_INACTIVE
            !ready -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_ACTIVE
        }
        tile.subtitle = when {
            !enabled -> getString(R.string.tile_off)
            paused -> getString(R.string.tile_paused)
            !ready -> getString(R.string.tile_unavailable)
            else -> getString(R.string.tile_on)
        }
        tile.updateTile()
    }
}
