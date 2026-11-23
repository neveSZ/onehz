package io.github.nevesz.onehz.ui.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.nevesz.onehz.R

/** Asks the system to add the OneHZ tile; the user still confirms in the system UI. */
object TileRequest {
    fun add(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.tile_add_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            Toast.makeText(context, R.string.tile_add_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val component = ComponentName(context, OneHzTileService::class.java)
        runCatching {
            manager.requestAddTileService(
                component,
                context.getString(R.string.app_name),
                Icon.createWithResource(context, R.drawable.ic_stat_onehz),
                ContextCompat.getMainExecutor(context),
                java.util.function.Consumer<Int> {
                    Toast.makeText(context, R.string.tile_add_requested, Toast.LENGTH_SHORT).show()
                }
            )
        }.onFailure {
            Toast.makeText(context, R.string.tile_add_unsupported, Toast.LENGTH_LONG).show()
        }
    }
}
