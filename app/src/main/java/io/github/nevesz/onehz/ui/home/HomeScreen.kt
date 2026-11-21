package io.github.nevesz.onehz.ui.home

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.AodBrightnessMath
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.LtpoGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.IdleReason
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import io.github.nevesz.onehz.ui.components.HzPicker
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.components.ToggleRow
import kotlin.math.roundToInt

private data class HomeState(
    val enabled: Boolean,
    val normalEnabled: Boolean,
    val normal: FrequencyProfile,
    val ecoEnabled: Boolean,
    val eco: FrequencyProfile,
    val aodEnabled: Boolean,
    val aodHz: Int,
    val aodBrightnessEnabled: Boolean,
    val aodBrightnessPct: Int,
    val aodBrightnessMinPct: Int,
    val aodBrightnessMaxPct: Int,
    val videoEnabled: Boolean,
    val videoMinHz: Int,
    val ecoActive: Boolean
)

private fun loadState(context: Context, rates: List<Int>): HomeState {
    val storedVideo = OneHzPreferences.videoMinHz(context)
    val videoMinHz = if (rates.isEmpty() || storedVideo in rates) {
        storedVideo
    } else {
        rates.minByOrNull { kotlin.math.abs(it - storedVideo) } ?: storedVideo
    }
    return HomeState(
        enabled = OneHzPreferences.isEnabled(context),
        normalEnabled = OneHzPreferences.normalEnabled(context),
        normal = OneHzPreferences.normalProfile(context),
        ecoEnabled = OneHzPreferences.ecoEnabled(context),
        eco = OneHzPreferences.ecoProfile(context),
        aodEnabled = OneHzPreferences.aodEnabled(context),
        aodHz = OneHzPreferences.aodHz(context),
        aodBrightnessEnabled = OneHzPreferences.aodBrightnessEnabled(context),
        aodBrightnessPct = OneHzPreferences.aodBrightnessPct(context),
        aodBrightnessMinPct = OneHzPreferences.aodBrightnessMinPct(context),
        aodBrightnessMaxPct = OneHzPreferences.aodBrightnessMaxPct(context),
        videoEnabled = OneHzPreferences.videoEnabled(context),
        videoMinHz = videoMinHz,
        ecoActive = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    context: Context,
    rates: List<Int>,
    dataVersion: Int,
    onEditNormal: () -> Unit,
    onEditEco: () -> Unit
) {
    val shizuku by ShizukuGateway.state.collectAsState()
    val gate = remember { LtpoGate.evaluate(context) }
    val activeKey by Diagnostics.activeTierKey.collectAsState()
    val activeStep by Diagnostics.activeStep.collectAsState()
    val activeStepCount by Diagnostics.activeStepCount.collectAsState()
    val appliedHz by Diagnostics.appliedHz.collectAsState()
    val idleReason by Diagnostics.idleReason.collectAsState()
    val videoSource by Diagnostics.videoSource.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var state by remember(refresh, dataVersion) { mutableStateOf(loadState(context, rates)) }

    val activeProfile = when {
        state.ecoActive && state.ecoEnabled -> state.eco
        !state.ecoActive && state.normalEnabled -> state.normal
        else -> null
    }
    val fallbackHz = when {
        activeKey == "AOD" -> state.aodHz
        else -> activeProfile?.steps?.getOrNull((activeStep - 1).coerceAtLeast(0))?.hz ?: 0
    }
    val displayHz = when {
        !state.enabled || activeKey == "OFF" || activeKey == "—" -> 0
        appliedHz > 0 -> appliedHz
        else -> fallbackHz
    }

    if (!gate.eligible) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            item {
                OneHzCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(R.string.unsupported_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.unsupported_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(gate.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(
                state = state,
                appliedHz = displayHz,
                maxHz = rates.maxOrNull() ?: 120,
                statusText = statusText(state, activeKey, activeStep, activeStepCount),
                idleText = idleText(state, idleReason),
                videoPlaying = videoSource == VideoSignals.Source.PLAYING,
                onToggle = { on ->
                    state = state.copy(enabled = on)
                    OneHzPreferences.setEnabled(context, on)
                    if (on) {
                        OneHzKeepAliveService.start(context)
                        OneHzEngine.start(context)
                        OneHzEngine.reapply()
                    } else {
                        OneHzKeepAliveService.stop(context)
                        OneHzEngine.stop()
                        AodBrightnessController.onPrefsChanged()
                    }
                }
            )
        }

        if (shizuku != ShizukuState.READY) {
            item {
                InfoBanner(
                    title = stringResource(R.string.home_shizuku_pending_title),
                    body = stringResource(
                        when (shizuku) {
                            ShizukuState.PERMISSION_REQUIRED -> R.string.home_shizuku_pending_permission
                            ShizukuState.NOT_RUNNING -> R.string.home_shizuku_pending_not_running
                            else -> R.string.home_shizuku_pending_unavailable
                        }
                    ),
                    tone = BannerTone.WARN,
                    icon = Icons.Outlined.Bolt,
                    action = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { ShizukuGateway.refresh() }) {
                                Text(stringResource(R.string.reconnect))
                            }
                            if (shizuku == ShizukuState.PERMISSION_REQUIRED) {
                                Button(onClick = { ShizukuGateway.requestPermission() }) {
                                    Text(stringResource(R.string.allow_shizuku))
                                }
                            }
                        }
                    }
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_section_profiles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            OneHzCard {
                ToggleRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.profile_normal_title),
                    description = if (state.normal.steps.isEmpty()) {
                        stringResource(R.string.profile_need_tiers)
                    } else {
                        profileSummary(state.normal)
                    },
                    checked = state.normalEnabled,
                    enabled = state.enabled && state.normal.steps.isNotEmpty(),
                    onCheckedChange = { on ->
                        state = state.copy(normalEnabled = on)
                        OneHzPreferences.setNormalEnabled(context, on)
                        OneHzEngine.reapply()
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EditProfileButton(onClick = onEditNormal)
                }
            }
        }

        item {
            OneHzCard {
                ToggleRow(
                    icon = Icons.Outlined.BatterySaver,
                    title = stringResource(R.string.profile_eco_title),
                    description = if (state.eco.steps.isEmpty()) {
                        stringResource(R.string.profile_need_tiers)
                    } else {
                        profileSummary(state.eco)
                    },
                    checked = state.ecoEnabled,
                    enabled = state.enabled && state.eco.steps.isNotEmpty(),
                    onCheckedChange = { on ->
                        state = state.copy(ecoEnabled = on)
                        OneHzPreferences.setEcoEnabled(context, on)
                        OneHzEngine.reapply()
                    }
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.ecoActive) {
                        StatusBadge(
                            text = stringResource(R.string.eco_active_now),
                            tone = StatusTone.OK,
                            icon = Icons.Outlined.CheckCircle
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    EditProfileButton(onClick = onEditEco)
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_section_extras),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            OneHzCard {
                ToggleRow(
                    icon = Icons.Outlined.Movie,
                    title = stringResource(R.string.video_mode_title),
                    description = stringResource(R.string.video_mode_desc),
                    checked = state.videoEnabled,
                    enabled = state.enabled,
                    onCheckedChange = { on ->
                        state = state.copy(videoEnabled = on)
                        OneHzPreferences.setVideoEnabled(context, on)
                        OneHzEngine.reapply()
                    }
                )
                AnimatedVisibility(visible = state.videoEnabled) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        HzPicker(
                            label = stringResource(R.string.video_min_hz),
                            value = state.videoMinHz,
                            options = rates,
                            onSelect = { hz ->
                                state = state.copy(videoMinHz = hz)
                                OneHzPreferences.setVideoMinHz(context, hz)
                                OneHzEngine.reapply()
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.video_min_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            AodCard(
                context = context,
                state = state,
                rates = rates,
                onStateChange = { state = it }
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HeroCard(
    state: HomeState,
    appliedHz: Int,
    maxHz: Int,
    statusText: String,
    idleText: String?,
    videoPlaying: Boolean,
    onToggle: (Boolean) -> Unit
) {
    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RateRing(hz = if (state.enabled) appliedHz else 0, max = maxHz)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.enabled) R.string.home_status_active else R.string.home_status_off
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(
                    text = statusText,
                    tone = if (state.enabled) StatusTone.OK else StatusTone.NEUTRAL
                )
                if (videoPlaying) {
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(
                        text = stringResource(R.string.video_badge),
                        tone = StatusTone.OK,
                        icon = Icons.Outlined.Movie
                    )
                }
                idleText?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            icon = Icons.Outlined.Speed,
            title = stringResource(R.string.master_switch),
            description = stringResource(R.string.master_switch_desc),
            checked = state.enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun AodCard(
    context: Context,
    state: HomeState,
    rates: List<Int>,
    onStateChange: (HomeState) -> Unit
) {
    var showLimits by rememberSaveable { mutableStateOf(false) }

    OneHzCard {
        ToggleRow(
            icon = Icons.Outlined.Nightlight,
            title = stringResource(R.string.aod_title),
            description = stringResource(R.string.aod_hint),
            checked = state.aodEnabled,
            enabled = state.enabled,
            onCheckedChange = { on ->
                onStateChange(state.copy(aodEnabled = on))
                OneHzPreferences.setAodEnabled(context, on)
                OneHzEngine.reapply()
            }
        )
        AnimatedVisibility(visible = state.aodEnabled) {
            Column {
                Spacer(Modifier.height(12.dp))
                HzPicker(
                    label = stringResource(R.string.aod_frequency),
                    value = state.aodHz,
                    options = rates,
                    onSelect = { hz ->
                        onStateChange(state.copy(aodHz = hz))
                        OneHzPreferences.setAodHz(context, hz)
                        OneHzEngine.reapply()
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.aod_cost_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ToggleRow(
                    icon = Icons.Outlined.BrightnessMedium,
                    title = stringResource(R.string.aod_brightness_title),
                    description = stringResource(R.string.aod_brightness_desc),
                    checked = state.aodBrightnessEnabled,
                    enabled = state.enabled,
                    onCheckedChange = { on ->
                        onStateChange(state.copy(aodBrightnessEnabled = on))
                        OneHzPreferences.setAodBrightnessEnabled(context, on)
                        AodBrightnessController.onPrefsChanged()
                    }
                )
                if (state.aodBrightnessEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.aod_brightness_pct_label, state.aodBrightnessPct),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = state.aodBrightnessPct.toFloat(),
                        onValueChange = { value ->
                            val pct = value.roundToInt()
                                .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT)
                            onStateChange(state.copy(aodBrightnessPct = pct))
                            OneHzPreferences.setAodBrightnessPct(context, pct)
                        },
                        onValueChangeFinished = { AodBrightnessController.onPrefsChanged() },
                        valueRange = AodBrightnessMath.MIN_PCT.toFloat()..AodBrightnessMath.MAX_PCT.toFloat(),
                        steps = AodBrightnessMath.MAX_PCT - AodBrightnessMath.MIN_PCT - 1,
                        enabled = state.enabled
                    )
                    val effectivePct = state.aodBrightnessPct
                        .coerceIn(state.aodBrightnessMinPct, state.aodBrightnessMaxPct)
                    if (effectivePct != state.aodBrightnessPct) {
                        Text(
                            text = stringResource(
                                R.string.aod_brightness_clamped,
                                state.aodBrightnessPct,
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
                            Text(
                                text = stringResource(
                                    R.string.aod_brightness_range_label,
                                    state.aodBrightnessMinPct,
                                    state.aodBrightnessMaxPct
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            RangeSlider(
                                value = state.aodBrightnessMinPct.toFloat()..state.aodBrightnessMaxPct.toFloat(),
                                onValueChange = { range ->
                                    val min = range.start.roundToInt()
                                        .coerceIn(AodBrightnessMath.MIN_PCT, AodBrightnessMath.MAX_PCT - 1)
                                    val max = range.endInclusive.roundToInt()
                                        .coerceIn(min + 1, AodBrightnessMath.MAX_PCT)
                                    onStateChange(
                                        state.copy(
                                            aodBrightnessMinPct = min,
                                            aodBrightnessMaxPct = max
                                        )
                                    )
                                    OneHzPreferences.setAodBrightnessBand(context, min, max)
                                },
                                onValueChangeFinished = { AodBrightnessController.onPrefsChanged() },
                                valueRange = AodBrightnessMath.MIN_PCT.toFloat()..AodBrightnessMath.MAX_PCT.toFloat(),
                                steps = AodBrightnessMath.MAX_PCT - AodBrightnessMath.MIN_PCT - 1,
                                enabled = state.enabled
                            )
                        }
                    }
                    if (state.aodBrightnessMaxPct >= 30) {
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
                }
            }
        }
    }
}

// Small helper so the preference setters stay in one place without passing the context everywhere.
@Composable
private fun EditProfileButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.profile_edit), Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun statusText(
    state: HomeState,
    activeKey: String,
    activeStep: Int,
    activeStepCount: Int
): String = when {
    !state.enabled -> stringResource(R.string.master_off_short)
    activeKey == "AOD" -> stringResource(R.string.layer_aod)
    activeKey == "OFF" || activeKey == "—" -> stringResource(R.string.no_control)
    activeStepCount > 0 -> stringResource(R.string.step_of, activeStep, activeStepCount)
    else -> stringResource(R.string.no_control)
}

@Composable
private fun profileSummary(profile: FrequencyProfile): String {
    if (profile.steps.isEmpty()) return stringResource(R.string.profile_empty)
    if (profile.isFixed) return stringResource(R.string.profile_fixed, profile.steps[0].hz)
    val chain = profile.steps.joinToString(" → ") { "${it.hz}" }
    val holds = profile.steps.dropLast(1).joinToString(" / ") { formatSeconds(it.holdMs ?: 0L) }
    return "$chain Hz · $holds"
}

@Composable
private fun idleText(state: HomeState, reason: IdleReason?): String? = when {
    !state.enabled -> stringResource(R.string.master_off_hint)
    reason == IdleReason.NORMAL_PROFILE_DISABLED -> stringResource(R.string.idle_normal_off)
    reason == IdleReason.ECO_PROFILE_DISABLED -> stringResource(R.string.idle_eco_off)
    reason == IdleReason.AOD_DISABLED -> stringResource(R.string.idle_aod_off)
    reason == IdleReason.EMPTY_PROFILE -> stringResource(R.string.profile_need_tiers)
    else -> null
}

@Composable
private fun RateRing(hz: Int, max: Int) {
    val fraction = (hz.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, label = "rate")
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hz > 0) hz.toString() else "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Hz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSeconds(ms: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f s", ms / 1000f)
