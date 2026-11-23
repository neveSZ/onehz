package io.github.nevesz.onehz.ui.home

import android.content.Context
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.AodBrightnessMath
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.OneHzEngine
import java.util.Locale
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.viewmodel.AodViewModel
import kotlin.math.roundToInt

private data class AodUi(
    val controlEnabled: Boolean,
    val rateEnabled: Boolean,
    val hz: Int,
    val brightnessEnabled: Boolean,
    val brightnessPct: Int,
    val minPct: Int,
    val maxPct: Int,
    val scheduleEnabled: Boolean,
    val scheduleStart: Int,
    val scheduleEnd: Int,
    val schedulePct: Int,
)

private fun loadAod(context: Context): AodUi = AodUi(
    controlEnabled = OneHzPreferences.aodMasterEnabled(context),
    rateEnabled = OneHzPreferences.aodRateEnabled(context),
    hz = OneHzPreferences.aodHz(context),
    brightnessEnabled = OneHzPreferences.aodBrightnessEnabled(context),
    brightnessPct = OneHzPreferences.aodBrightnessPct(context),
    minPct = OneHzPreferences.aodBrightnessMinPct(context),
    maxPct = OneHzPreferences.aodBrightnessMaxPct(context),
    scheduleEnabled = OneHzPreferences.aodScheduleEnabled(context),
    scheduleStart = OneHzPreferences.aodScheduleStart(context),
    scheduleEnd = OneHzPreferences.aodScheduleEnd(context),
    schedulePct = OneHzPreferences.aodSchedulePct(context),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AodScreen(
    context: Context,
    oneHzEnabled: Boolean,
    dataVersion: Int,
    onBack: () -> Unit,
) {
    val vm: AodViewModel = viewModel()
    val gate = remember { DisplayGate.evaluate(context) }
    val aodInfo by vm.aodInfo.collectAsStateWithLifecycle()
    var state by remember(dataVersion) { mutableStateOf(loadAod(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    androidx.compose.runtime.LaunchedEffect(refresh, dataVersion) {
        state = loadAod(context)
    }

    var showLimits by rememberSaveable { mutableStateOf(false) }
    var showRestore by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aod_title)) },
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
            if (!oneHzEnabled || !state.controlEnabled) {
                item {
                    OneHzCard {
                        Text(
                            text = stringResource(
                                if (!oneHzEnabled) R.string.aod_requires_master
                                else R.string.aod_control_off
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OneHzCard {
                    ToggleRow(
                        icon = AppIcons.nightlight(),
                        title = stringResource(R.string.aod_rate_title),
                        descriptionOn = stringResource(R.string.aod_hint_on),
                        descriptionOff = stringResource(R.string.aod_hint_off),
                        checked = state.rateEnabled,
                        enabled = oneHzEnabled,
                        onCheckedChange = { on ->
                            OneHzPreferences.setAodRateEnabled(context, on)
                            state = loadAod(context)
                            OneHzEngine.reapply()
                        }
                    )
                    if (state.rateEnabled || !gate.profile.supportsAodBrightness) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.aod_minimum, state.hz),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.aod_minimum_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (gate.profile.supportsAodBrightness) {
                item {
                    OneHzCard {
                        ToggleRow(
                            icon = AppIcons.brightnessMedium(),
                            title = stringResource(R.string.aod_brightness_title),
                            descriptionOn = stringResource(
                                R.string.aod_brightness_desc_on,
                                state.brightnessPct,
                            ),
                            descriptionOff = stringResource(R.string.aod_brightness_desc_off),
                            checked = state.brightnessEnabled,
                            enabled = oneHzEnabled,
                            onCheckedChange = { on ->
                                OneHzPreferences.setAodBrightnessEnabled(context, on)
                                state = loadAod(context)
                                AodBrightnessController.onPrefsChanged()
                            }
                        )
                        if (state.brightnessEnabled) {
                            Spacer(Modifier.height(10.dp))
                            val brightnessLabel = stringResource(
                                R.string.aod_brightness_pct_label,
                                state.brightnessPct
                            )
                            Text(
                                text = brightnessLabel,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val brightnessSliderState = remember {
                                SliderState(
                                    value = state.brightnessPct.toFloat(),
                                    steps = AodBrightnessMath.MAX_PCT - AodBrightnessMath.MIN_PCT - 1,
                                    trackRange = AodBrightnessMath.MIN_PCT.toFloat()..
                                        AodBrightnessMath.MAX_PCT.toFloat(),
                                )
                            }
                            LaunchedEffect(state.brightnessPct) {
                                brightnessSliderState.value = state.brightnessPct.toFloat()
                            }
                            Slider(
                                modifier = Modifier.semantics {
                                    stateDescription = brightnessLabel
                                },
                                state = brightnessSliderState,
                                onValueChange = { value ->
                                    val pct = value.roundToInt()
                                        .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)
                                    state = state.copy(brightnessPct = pct)
                                    OneHzPreferences.setAodBrightnessPct(context, pct)
                                },
                                onValueChangeFinished = { AodBrightnessController.onPrefsChanged() },
                                enabled = oneHzEnabled
                            )
                            val effectivePct = state.brightnessPct
                                .coerceIn(state.minPct, state.maxPct)
                            if (effectivePct != state.brightnessPct) {
                                Text(
                                    text = stringResource(
                                        R.string.aod_brightness_clamped,
                                        state.brightnessPct,
                                        effectivePct
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(onClick = { showLimits = !showLimits }) {
                                Icon(
                                    if (showLimits) AppIcons.expandLess() else AppIcons.expandMore(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    stringResource(
                                        if (showLimits) R.string.aod_brightness_hide_limits
                                        else R.string.aod_brightness_show_limits
                                    ),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            AnimatedVisibility(visible = showLimits) {
                                Column {
                                    val limitsLabel = stringResource(
                                        R.string.aod_brightness_range_label,
                                        state.minPct,
                                        state.maxPct
                                    )
                                    Text(
                                        text = limitsLabel,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val limitsRangeState = remember {
                                        RangeSliderState(
                                            startValue = state.minPct.toFloat(),
                                            endValue = state.maxPct.toFloat(),
                                            steps = AodBrightnessMath.MAX_PCT -
                                                AodBrightnessMath.MIN_PCT - 1,
                                            trackRange = AodBrightnessMath.MIN_PCT.toFloat()..
                                                AodBrightnessMath.MAX_PCT.toFloat(),
                                        )
                                    }
                                    LaunchedEffect(state.minPct, state.maxPct) {
                                        limitsRangeState.startValue = state.minPct.toFloat()
                                        limitsRangeState.endValue = state.maxPct.toFloat()
                                    }
                                    RangeSlider(
                                        modifier = Modifier.semantics {
                                            stateDescription = limitsLabel
                                        },
                                        state = limitsRangeState,
                                        onValueChange = { range ->
                                            val min = range.start.roundToInt()
                                                .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT - 1)
                                            val max = range.endInclusive.roundToInt()
                                                .coerceIn(min + 1, AodBrightnessMath.MAX_PCT)
                                            state = state.copy(minPct = min, maxPct = max)
                                            OneHzPreferences.setAodBrightnessBand(context, min, max)
                                        },
                                        onValueChangeFinished = { AodBrightnessController.onPrefsChanged() },
                                        enabled = oneHzEnabled
                                    )
                                }
                            }
                            if (state.maxPct >= 30) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.aod_brightness_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = stringResource(R.string.aod_brightness_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.controlEnabled && aodInfo?.applied == false) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.aod_brightness_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showRestore = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.aod_brightness_restore))
                            }
                        }
                    }
                }
                item {
                    OneHzCard {
                        ToggleRow(
                            icon = AppIcons.nightlight(),
                            title = stringResource(R.string.aod_schedule_title),
                            descriptionOn = stringResource(
                                R.string.aod_schedule_on,
                                state.schedulePct
                            ),
                            descriptionOff = stringResource(R.string.aod_schedule_off),
                            checked = state.scheduleEnabled,
                            enabled = oneHzEnabled,
                            onCheckedChange = { on ->
                                OneHzPreferences.setAodScheduleEnabled(context, on)
                                state = state.copy(scheduleEnabled = on)
                                AodBrightnessController.onPrefsChanged()
                            }
                        )
                        if (state.scheduleEnabled) {
                            ScheduleRow(
                                title = stringResource(R.string.aod_schedule_start),
                                minute = state.scheduleStart,
                                onPick = { minute ->
                                    OneHzPreferences.setAodScheduleWindow(
                                        context,
                                        minute,
                                        state.scheduleEnd
                                    )
                                    state = state.copy(scheduleStart = minute)
                                    AodBrightnessController.onPrefsChanged()
                                }
                            )
                            ScheduleRow(
                                title = stringResource(R.string.aod_schedule_end),
                                minute = state.scheduleEnd,
                                onPick = { minute ->
                                    OneHzPreferences.setAodScheduleWindow(
                                        context,
                                        state.scheduleStart,
                                        minute
                                    )
                                    state = state.copy(scheduleEnd = minute)
                                    AodBrightnessController.onPrefsChanged()
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                            val scheduleLabel = stringResource(
                                R.string.aod_schedule_pct
                            ) + " " + state.schedulePct + "%"
                            Text(
                                text = scheduleLabel,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val scheduleSlider = remember {
                                SliderState(
                                    value = state.schedulePct.toFloat(),
                                    steps = AodBrightnessMath.MAX_PCT -
                                        AodBrightnessMath.MIN_PCT - 1,
                                    trackRange = AodBrightnessMath.MIN_PCT.toFloat()..
                                        AodBrightnessMath.MAX_PCT.toFloat(),
                                )
                            }
                            LaunchedEffect(state.schedulePct) {
                                scheduleSlider.value = state.schedulePct.toFloat()
                            }
                            Slider(
                                modifier = Modifier.semantics {
                                    stateDescription = scheduleLabel
                                },
                                state = scheduleSlider,
                                onValueChange = { value ->
                                    val pct = value.roundToInt().coerceIn(
                                        AodBrightnessMath.MIN_PCT,
                                        AodBrightnessMath.MAX_PCT
                                    )
                                    state = state.copy(schedulePct = pct)
                                    OneHzPreferences.setAodSchedulePct(context, pct)
                                },
                                onValueChangeFinished = {
                                    AodBrightnessController.onPrefsChanged()
                                },
                                enabled = oneHzEnabled
                            )
                            Text(
                                text = stringResource(R.string.aod_schedule_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    OneHzCard {
                        ToggleRow(
                            icon = AppIcons.brightnessMedium(),
                            title = stringResource(R.string.aod_brightness_title),
                            description = stringResource(R.string.aod_brightness_unsupported),
                            checked = false,
                            enabled = false,
                            onCheckedChange = {}
                        )
                    }
                }
            }
        }
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text(stringResource(R.string.aod_brightness_restore_confirm_title)) },
            text = { Text(stringResource(R.string.aod_brightness_restore_body)) },
            confirmButton = {
                Button(onClick = {
                    AodBrightnessController.restoreSystem(context)
                    state = loadAod(context)
                    showRestore = false
                }) {
                    Text(stringResource(R.string.aod_brightness_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ScheduleRow(title: String, minute: Int, onPick: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hour, selectedMinute -> onPick(hour * 60 + selectedMinute) },
                    minute / 60,
                    minute % 60,
                    true
                ).show()
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Text(
            text = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
