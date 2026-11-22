package io.github.nevesz.onehz.ui.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.AodBrightnessMath
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.ToggleRow
import kotlin.math.roundToInt

private data class AodUi(
    val enabled: Boolean,
    val hz: Int,
    val brightnessEnabled: Boolean,
    val brightnessPct: Int,
    val minPct: Int,
    val maxPct: Int,
)

private fun loadAod(context: Context): AodUi = AodUi(
    enabled = OneHzPreferences.aodEnabled(context),
    hz = OneHzPreferences.aodHz(context),
    brightnessEnabled = OneHzPreferences.aodBrightnessEnabled(context),
    brightnessPct = OneHzPreferences.aodBrightnessPct(context),
    minPct = OneHzPreferences.aodBrightnessMinPct(context),
    maxPct = OneHzPreferences.aodBrightnessMaxPct(context),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AodScreen(
    context: Context,
    masterEnabled: Boolean,
    dataVersion: Int,
    onBack: () -> Unit,
) {
    val gate = remember { DisplayGate.evaluate(context) }
    val aodInfo by Diagnostics.aodBrightness.collectAsState()
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
                            Icons.AutoMirrored.Outlined.ArrowBack,
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
            item {
                OneHzCard {
                    ToggleRow(
                        icon = Icons.Outlined.Nightlight,
                        title = stringResource(R.string.aod_rate_title),
                        description = stringResource(R.string.aod_hint),
                        checked = state.enabled,
                        enabled = masterEnabled,
                        onCheckedChange = { on ->
                            state = state.copy(enabled = on)
                            OneHzPreferences.setAodEnabled(context, on)
                            OneHzEngine.reapply()
                        }
                    )
                    AnimatedVisibility(visible = state.enabled) {
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

            if (state.enabled && gate.profile.supportsAodBrightness) {
                item {
                    OneHzCard {
                        ToggleRow(
                            icon = Icons.Outlined.BrightnessMedium,
                            title = stringResource(R.string.aod_brightness_title),
                            description = stringResource(R.string.aod_brightness_desc),
                            checked = state.brightnessEnabled,
                            enabled = masterEnabled,
                            onCheckedChange = { on ->
                                state = state.copy(brightnessEnabled = on)
                                OneHzPreferences.setAodBrightnessEnabled(context, on)
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
                            Slider(
                                modifier = Modifier.semantics {
                                    stateDescription = brightnessLabel
                                },
                                value = state.brightnessPct.toFloat(),
                                onValueChange = { value ->
                                    val pct = value.roundToInt()
                                        .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)
                                    state = state.copy(brightnessPct = pct)
                                    OneHzPreferences.setAodBrightnessPct(context, pct)
                                },
                                onValueChangeFinished = { AodBrightnessController.onPrefsChanged() },
                                valueRange = AodBrightnessMath.MIN_PCT.toFloat()..AodBrightnessMath.MAX_PCT.toFloat(),
                                steps = AodBrightnessMath.MAX_PCT - AodBrightnessMath.MIN_PCT - 1,
                                enabled = masterEnabled
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
                                    if (showLimits) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
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
                                    RangeSlider(
                                        modifier = Modifier.semantics {
                                            stateDescription = limitsLabel
                                        },
                                        value = state.minPct.toFloat()..state.maxPct.toFloat(),
                                        onValueChange = { range ->
                                            val min = range.start.roundToInt()
                                                .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT - 1)
                                            val max = range.endInclusive.roundToInt()
                                                .coerceIn(min + 1, AodBrightnessMath.MAX_PCT)
                                            state = state.copy(minPct = min, maxPct = max)
                                            OneHzPreferences.setAodBrightnessBand(context, min, max)
                                        },
                                        onValueChangeFinished = { AodBrightnessController.onPrefsChanged() },
                                        valueRange = AodBrightnessMath.MIN_PCT.toFloat()..AodBrightnessMath.MAX_PCT.toFloat(),
                                        steps = AodBrightnessMath.MAX_PCT - AodBrightnessMath.MIN_PCT - 1,
                                        enabled = masterEnabled
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
                            if (aodInfo?.applied == false) {
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
                    state = state.copy(brightnessEnabled = false)
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
