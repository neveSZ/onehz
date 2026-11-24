package io.github.nevesz.onehz.ui.tile

import android.content.Context
import io.github.nevesz.onehz.core.OneHzPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live mirror of the Quick Settings tile state.
 *
 * Android has no public API to ask whether the tile is on the grid, so the tile reports its
 * own lifecycle. The stored preference alone is not enough for an open screen: the system
 * add-tile prompt does not pause the activity, so a resume-based read would only notice the
 * change after leaving the screen. The tile service shares this flow instead.
 */
object TileStatus {
    private val _added = MutableStateFlow<Boolean?>(null)

    /** null until the first read; callers fall back to the stored preference. */
    val added: StateFlow<Boolean?> = _added.asStateFlow()

    fun refresh(context: Context) {
        _added.value = OneHzPreferences.tileAdded(context)
    }

    fun set(context: Context, added: Boolean) {
        OneHzPreferences.setTileAdded(context, added)
        _added.value = added
    }
}
