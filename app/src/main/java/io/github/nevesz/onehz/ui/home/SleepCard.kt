package io.github.nevesz.onehz.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DozePhase
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-line status used by the Home screen-off row. The detail screen has the full status
 * with counters and failure explanations.
 */
@Composable
fun sleepQuickStatus(
    context: Context,
    masterEnabled: Boolean,
    sleepEnabled: Boolean,
): String {
    if (!masterEnabled) return stringResource(R.string.sleep_requires_master)
    if (!sleepEnabled) return stringResource(R.string.home_sleep_summary_off)

    val now = System.currentTimeMillis()
    if (SleepPreferences.pausedUntil(context) > now) {
        return stringResource(
            R.string.sleep_state_paused_until,
            formatSleepClock(SleepPreferences.pausedUntil(context)),
        )
    }

    val phase by Diagnostics.dozePhase.collectAsState()
    val activeSince by Diagnostics.dozeActiveSince.collectAsState()
    val shizuku by ShizukuGateway.state.collectAsState()
    return when {
        phase == DozePhase.ACTIVE && activeSince > 0 ->
            stringResource(
                R.string.sleep_state_active,
                formatSleepDuration(now - activeSince),
            )
        shizuku != ShizukuState.READY -> stringResource(R.string.sleep_state_waiting_service)
        phase == DozePhase.FAILED -> stringResource(R.string.sleep_state_failed)
        phase == DozePhase.CHARGING -> stringResource(R.string.sleep_state_charging)
        phase == DozePhase.MUSIC -> stringResource(R.string.sleep_state_music)
        phase == DozePhase.OUTSIDE_WINDOW -> stringResource(R.string.sleep_state_outside_window)
        else -> stringResource(R.string.sleep_state_ready_desc)
    }
}

internal fun formatSleepDuration(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h${rest.toString().padStart(2, '0')}" else "${minutes}min"
}

internal fun formatSleepRate(value: Double): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)

internal fun formatSleepClock(epoch: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epoch))

internal fun formatSleepMoment(epoch: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(epoch))

internal fun formatSleepMinute(minuteOfDay: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
