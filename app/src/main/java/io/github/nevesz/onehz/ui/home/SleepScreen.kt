package io.github.nevesz.onehz.ui.home

import android.app.TimePickerDialog
import android.content.Context
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DozePhase
import io.github.nevesz.onehz.core.DozeState
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
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.HowItWorksCard
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.viewmodel.SleepViewModel
import kotlinx.coroutines.delay

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    context: Context,
    masterEnabled: Boolean,
    dataVersion: Int,
    onBack: () -> Unit,
) {
    val vm: SleepViewModel = viewModel()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val activeSince by vm.activeSince.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()

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
    LaunchedEffect(dataVersion, refresh) { ui = loadSleep(context) }

    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showWhitelist by rememberSaveable { mutableStateOf(false) }
    var warningSeen by rememberSaveable {
        mutableStateOf(SleepPreferences.warningSeen(context))
    }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var testOk by rememberSaveable { mutableStateOf<Boolean?>(null) }

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
    LaunchedEffect(testing) {
        if (!testing) return@LaunchedEffect
        DozeController.testNow()
        delay(2_000)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        testOk = pm?.isDeviceIdleMode == true ||
            DozeState.isIdleEncoded(ShizukuGateway.idleState())
        testing = false
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcons.arrowBack(),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!masterEnabled || !ui.enabled) {
                item {
                    OneHzCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        AppIcons.bedtime(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(
                                    if (!masterEnabled) R.string.sleep_requires_master
                                    else R.string.sleep_state_off
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            } else {
                val now = System.currentTimeMillis()
                val paused = ui.pausedUntil > now
                val active = phase == DozePhase.ACTIVE && activeSince > 0
                val exceptional = paused || active ||
                    phase == DozePhase.CHARGING ||
                    phase == DozePhase.MUSIC ||
                    phase == DozePhase.OUTSIDE_WINDOW ||
                    phase == DozePhase.FAILED ||
                    shizuku != ShizukuState.READY
                if (exceptional) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when {
                                    phase == DozePhase.FAILED -> AppIcons.warning()
                                    paused || phase == DozePhase.MUSIC -> AppIcons.pause()
                                    phase == DozePhase.CHARGING -> AppIcons.bolt()
                                    phase == DozePhase.OUTSIDE_WINDOW -> AppIcons.schedule()
                                    active -> AppIcons.bedtime()
                                    else -> AppIcons.bolt()
                                },
                                contentDescription = null,
                                tint = if (phase == DozePhase.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = statusText(masterEnabled, ui, phase, activeSince, shizuku),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (phase == DozePhase.FAILED) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (phase == DozePhase.FAILED) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.sleep_failure_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                HowItWorksCard(
                    title = stringResource(R.string.sleep_how_title),
                    body = stringResource(R.string.sleep_how_body)
                ) {
                    if (!warningSeen) {
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(
                            body = stringResource(R.string.sleep_delay_warning),
                            tone = BannerTone.WARN,
                            action = {
                                TextButton(onClick = {
                                    SleepPreferences.setWarningSeen(context)
                                    warningSeen = true
                                }) {
                                    Text(stringResource(R.string.sleep_warning_dismiss))
                                }
                            }
                        )
                    }
                }
            }

            item {
                OneHzCard {
                    Text(
                        text = stringResource(R.string.sleep_options_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    if (!ui.enabled) {
                        Text(
                            text = stringResource(R.string.sleep_paused_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    ToggleRow(
                        icon = AppIcons.bedtime(),
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
                        icon = AppIcons.bolt(),
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
                        icon = AppIcons.pause(),
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
                        icon = AppIcons.schedule(),
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
                                icon = AppIcons.schedule(),
                                title = stringResource(R.string.sleep_window_start),
                                onClick = { pickStart = true },
                                trailing = { Text(formatSleepMinute(ui.window.startMinute)) },
                            )
                            SettingRow(
                                icon = AppIcons.schedule(),
                                title = stringResource(R.string.sleep_window_end),
                                onClick = { pickEnd = true },
                                trailing = { Text(formatSleepMinute(ui.window.endMinute)) },
                            )
                        }
                    }
                }
            }

            item {
                OneHzCard {
                    SettingRow(
                        icon = AppIcons.notificationsActive(),
                        title = stringResource(R.string.sleep_whitelist_title),
                        subtitle = stringResource(R.string.sleep_whitelist_desc),
                        onClick = { showWhitelist = true },
                    )
                }
            }

            item {
                MeasurementsCard(
                    ui = ui,
                    showPause = ui.enabled,
                    onOpenDetails = { showDetails = true },
                    onPauseResume = {
                        if (ui.pausedUntil > System.currentTimeMillis()) {
                            DozeController.resume()
                            ui = ui.copy(pausedUntil = 0L)
                        } else {
                            val until = System.currentTimeMillis() + 60 * 60 * 1000L
                            DozeController.pauseFor(60 * 60 * 1000L)
                            ui = ui.copy(pausedUntil = until)
                        }
                    }
                )
            }

            if (ui.enabled) {
                item {
                    OneHzCard {
                        Text(
                            text = stringResource(R.string.sleep_advanced_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                testOk = null
                                testing = true
                            },
                            enabled = !testing && shizuku == ShizukuState.READY,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(AppIcons.playArrow(), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (testing) R.string.sleep_test_running else R.string.sleep_test
                                )
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.sleep_test_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        testOk?.let { ok ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    if (ok) R.string.sleep_test_result_ok
                                    else R.string.sleep_test_result_failed
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        SleepDetailsSheet(
            records = ui.records,
            now = System.currentTimeMillis(),
            onDismiss = { showDetails = false },
        )
    }
    if (showWhitelist) {
        SleepWhitelistSheet(context = context, onDismiss = { showWhitelist = false })
    }
}

@Composable
private fun MeasurementsCard(
    ui: SleepUi,
    showPause: Boolean,
    onOpenDetails: () -> Unit,
    onPauseResume: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val usage = SleepLedger.usage(ui.records, now)
    val rest = SleepLedger.lastRest(ui.records, now)
    OneHzCard {
        Text(
            text = stringResource(R.string.sleep_measurements_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        if (usage.partial) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                io.github.nevesz.onehz.ui.components.StatusBadge(
                    text = stringResource(R.string.sleep_partial_badge),
                    tone = io.github.nevesz.onehz.ui.components.StatusTone.WARN,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sleep_partial_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = stringResource(R.string.sleep_usage_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = usageText(usage), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sleep_rest_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = restText(rest), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sleep_rest_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenDetails) {
                Text(stringResource(R.string.sleep_details))
            }
            Spacer(Modifier.weight(1f))
            if (showPause) {
                val paused = ui.pausedUntil > now
                TextButton(onClick = onPauseResume) {
                    Text(stringResource(if (paused) R.string.sleep_resume else R.string.sleep_pause))
                }
            }
        }
        Text(
            text = stringResource(R.string.sleep_measure_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun statusText(
    masterEnabled: Boolean,
    ui: SleepUi,
    phase: DozePhase,
    activeSince: Long,
    shizuku: ShizukuState,
): String {
    val now = System.currentTimeMillis()
    return when {
        !masterEnabled -> stringResource(R.string.sleep_requires_master)
        !ui.enabled -> stringResource(R.string.sleep_state_off)
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
        else -> stringResource(R.string.sleep_state_ready_desc)
    }
}

@Composable
private fun usageText(usage: SleepUsage): String {
    if (!usage.hasData) return stringResource(R.string.sleep_usage_empty_enabled)
    return stringResource(
        R.string.sleep_usage_values,
        formatSleepDuration(usage.screenOffMs),
        formatSleepDuration(usage.idleMs),
    )
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
