package io.github.nevesz.onehz.ui.home

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DozePhase
import io.github.nevesz.onehz.core.RestSession
import io.github.nevesz.onehz.core.SleepLedger
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.core.SleepRecord
import io.github.nevesz.onehz.core.SleepUsage
import io.github.nevesz.onehz.core.SleepWindow
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.DozeController
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow
import io.github.nevesz.onehz.ui.components.ToggleRow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SleepUi(
    val enabled: Boolean,
    val deep: Boolean,
    val skipCharging: Boolean,
    val skipMusic: Boolean,
    val windowEnabled: Boolean,
    val window: SleepWindow,
    val pausedUntil: Long,
    val records: List<SleepRecord>,
)

private fun loadSleep(context: Context): SleepUi = SleepUi(
    enabled = SleepPreferences.enabled(context),
    deep = SleepPreferences.deep(context),
    skipCharging = SleepPreferences.skipCharging(context),
    skipMusic = SleepPreferences.skipMusic(context),
    windowEnabled = SleepPreferences.windowEnabled(context),
    window = SleepPreferences.window(context),
    pausedUntil = SleepPreferences.pausedUntil(context),
    records = SleepPreferences.records(context),
)

/**
 * Screen-off behaviour card. It keeps its own state and only receives the OneHZ master
 * switch, so the Home screen does not need to know about sleep preferences.
 */
@Composable
fun SleepCard(context: Context, masterEnabled: Boolean, dataVersion: Int) {
    val phase by Diagnostics.dozePhase.collectAsState()
    val activeSince by Diagnostics.dozeActiveSince.collectAsState()
    val shizuku by ShizukuGateway.state.collectAsState()

    var ui by remember(dataVersion) { mutableStateOf(loadSleep(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(dataVersion, refresh) {
        ui = loadSleep(context)
    }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showWhitelist by rememberSaveable { mutableStateOf(false) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val status = sleepStatus(masterEnabled, ui, phase, activeSince, shizuku, now)

    val window = ui.window
    LaunchedEffect(pickStart) {
        if (!pickStart) return@LaunchedEffect
        showTimePicker(context, window.startMinute) { minute ->
            SleepPreferences.setWindow(context, minute, window.endMinute)
            ui = ui.copy(window = SleepWindow(minute, window.endMinute))
            DozeController.onPrefsChanged()
        }.also { it.setOnDismissListener { pickStart = false } }.show()
    }
    LaunchedEffect(pickEnd) {
        if (!pickEnd) return@LaunchedEffect
        showTimePicker(context, window.endMinute) { minute ->
            SleepPreferences.setWindow(context, window.startMinute, minute)
            ui = ui.copy(window = SleepWindow(window.startMinute, minute))
            DozeController.onPrefsChanged()
        }.also { it.setOnDismissListener { pickEnd = false } }.show()
    }

    OneHzCard {
        ToggleRow(
            icon = Icons.Outlined.Bedtime,
            title = stringResource(R.string.sleep_title),
            description = when {
                !masterEnabled -> stringResource(R.string.sleep_requires_master)
                !ui.enabled -> stringResource(R.string.sleep_hint)
                else -> status
            },
            checked = ui.enabled,
            enabled = masterEnabled,
            onCheckedChange = { on ->
                ui = ui.copy(enabled = on)
                SleepPreferences.setEnabled(context, on)
                DozeController.onPrefsChanged()
            },
        )

        AnimatedVisibility(visible = ui.enabled) {
            Column {
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = stringResource(R.string.sleep_deep_title),
                    description = stringResource(R.string.sleep_deep_desc),
                    checked = ui.deep,
                    onCheckedChange = { on ->
                        ui = ui.copy(deep = on)
                        SleepPreferences.setDeep(context, on)
                        DozeController.onPrefsChanged()
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.sleep_skip_charging_title),
                    description = stringResource(R.string.sleep_skip_charging_desc),
                    checked = ui.skipCharging,
                    onCheckedChange = { on ->
                        ui = ui.copy(skipCharging = on)
                        SleepPreferences.setSkipCharging(context, on)
                        DozeController.onPrefsChanged()
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.sleep_skip_music_title),
                    description = stringResource(R.string.sleep_skip_music_desc),
                    checked = ui.skipMusic,
                    onCheckedChange = { on ->
                        ui = ui.copy(skipMusic = on)
                        SleepPreferences.setSkipMusic(context, on)
                        DozeController.onPrefsChanged()
                    },
                )
                ToggleRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.sleep_window_title),
                    description = stringResource(R.string.sleep_window_desc),
                    checked = ui.windowEnabled,
                    onCheckedChange = { on ->
                        ui = ui.copy(windowEnabled = on)
                        SleepPreferences.setWindowEnabled(context, on)
                        DozeController.onPrefsChanged()
                    },
                )
                if (ui.windowEnabled) {
                    Column(Modifier.padding(start = 4.dp)) {
                        SettingRow(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.sleep_window_start),
                            onClick = { pickStart = true },
                            trailing = { Text(formatSleepMinute(ui.window.startMinute)) },
                        )
                        SettingRow(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.sleep_window_end),
                            onClick = { pickEnd = true },
                            trailing = { Text(formatSleepMinute(ui.window.endMinute)) },
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                SettingRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.sleep_whitelist_title),
                    subtitle = stringResource(R.string.sleep_whitelist_desc),
                    onClick = { showWhitelist = true },
                )

                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                val usage = SleepLedger.usage(ui.records, now)
                val rest = SleepLedger.lastRest(ui.records, now)
                Text(
                    text = stringResource(R.string.sleep_usage_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = usageText(usage), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.sleep_rest_title) + " " +
                        stringResource(R.string.sleep_rest_min_suffix),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = restText(rest), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sleep_rest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showDetails = true }) {
                        Text(stringResource(R.string.sleep_details))
                    }
                    Spacer(Modifier.weight(1f))
                    val paused = ui.pausedUntil > System.currentTimeMillis()
                    TextButton(
                        onClick = {
                            if (paused) {
                                DozeController.resume()
                                ui = ui.copy(pausedUntil = 0L)
                            } else {
                                val until = System.currentTimeMillis() + 60 * 60 * 1000L
                                DozeController.pauseFor(60 * 60 * 1000L)
                                ui = ui.copy(pausedUntil = until)
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (paused) R.string.sleep_resume else R.string.sleep_pause,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showDetails) {
        SleepDetailsSheet(
            records = ui.records,
            now = now,
            onDismiss = { showDetails = false },
            onTest = { DozeController.testNow() },
        )
    }
    if (showWhitelist) {
        SleepWhitelistSheet(context = context, onDismiss = { showWhitelist = false })
    }
}

@Composable
private fun sleepStatus(
    masterEnabled: Boolean,
    ui: SleepUi,
    phase: DozePhase,
    activeSince: Long,
    shizuku: ShizukuState,
    now: Long,
): String = when {
    !masterEnabled -> stringResource(R.string.sleep_requires_master)
    !ui.enabled -> stringResource(R.string.sleep_hint)
    ui.pausedUntil > now -> stringResource(
        R.string.sleep_state_paused_until,
        formatSleepClock(ui.pausedUntil),
    )
    phase == DozePhase.ACTIVE && activeSince > 0 ->
        stringResource(R.string.sleep_state_active, formatSleepDuration(now - activeSince))
    shizuku != ShizukuState.READY -> stringResource(R.string.sleep_state_waiting_service)
    phase == DozePhase.FAILED -> stringResource(R.string.sleep_state_failed)
    phase == DozePhase.CHARGING -> stringResource(R.string.sleep_state_charging)
    phase == DozePhase.MUSIC -> stringResource(R.string.sleep_state_music)
    phase == DozePhase.OUTSIDE_WINDOW -> stringResource(R.string.sleep_state_outside_window)
    else -> stringResource(R.string.sleep_state_ready)
}

@Composable
private fun usageText(usage: SleepUsage): String {
    if (!usage.hasData) return stringResource(R.string.sleep_usage_empty)
    val value = stringResource(
        R.string.sleep_usage_values,
        formatSleepDuration(usage.screenOffMs),
        formatSleepDuration(usage.idleMs),
    )
    return if (usage.partial) stringResource(R.string.sleep_partial) + value else value
}

@Composable
private fun restText(rest: RestSession?): String {
    if (rest == null) return stringResource(R.string.sleep_rest_none)
    val parts = mutableListOf(formatSleepDuration(rest.durationMs))
    rest.perHour?.let { parts += stringResource(R.string.sleep_rest_rate, formatSleepRate(it)) }
    if (rest.levelStart > 0 && rest.levelEnd >= 0) {
        parts += stringResource(R.string.sleep_rest_battery, rest.levelStart, rest.levelEnd)
    }
    return parts.joinToString(" · ")
}

private fun showTimePicker(
    context: Context,
    minuteOfDay: Int,
    onPicked: (Int) -> Unit,
): TimePickerDialog = TimePickerDialog(
    context,
    { _, hour, minute -> onPicked(SleepWindow.minuteOfDay(hour, minute)) },
    minuteOfDay / 60,
    minuteOfDay % 60,
    true,
)

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
