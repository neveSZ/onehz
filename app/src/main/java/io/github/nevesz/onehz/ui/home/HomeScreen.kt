package io.github.nevesz.onehz.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileState
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.DozeController
import io.github.nevesz.onehz.engine.IdleReason
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.profile.profileLabel
import io.github.nevesz.onehz.ui.profile.profileSummary
import io.github.nevesz.onehz.ui.update.UpdateBanner
import kotlin.math.roundToInt

private data class HomeState(
    val enabled: Boolean,
    val profileState: ProfileState,
    val aodEnabled: Boolean,
    val aodHz: Int,
    val aodBrightnessEnabled: Boolean,
    val aodBrightnessPct: Int,
    val aodBrightnessMinPct: Int,
    val aodBrightnessMaxPct: Int,
    val sleepEnabled: Boolean,
    val ecoActive: Boolean
) {
    val selectedProfile: DeviceProfile? get() = profileState.activeProfile()
    val activeProfile: DeviceProfile?
        get() = if (ecoActive && profileState.autoEconomyOnBatterySaver) {
            profileState.economyProfile() ?: profileState.activeProfile()
        } else {
            profileState.activeProfile()
        }
}

private fun loadState(context: Context, rates: List<Int>): HomeState {
    val profileState = ProfileStore.state(context, rates)
    return HomeState(
        enabled = OneHzPreferences.isEnabled(context),
        profileState = profileState,
        aodEnabled = OneHzPreferences.aodEnabled(context),
        aodHz = OneHzPreferences.aodHz(context),
        aodBrightnessEnabled = OneHzPreferences.aodBrightnessEnabled(context),
        aodBrightnessPct = OneHzPreferences.aodBrightnessPct(context),
        aodBrightnessMinPct = OneHzPreferences.aodBrightnessMinPct(context),
        aodBrightnessMaxPct = OneHzPreferences.aodBrightnessMaxPct(context),
        sleepEnabled = SleepPreferences.enabled(context),
        ecoActive = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    context: Context,
    rates: List<Int>,
    dataVersion: Int,
    onEditProfile: (String) -> Unit,
    onManageProfiles: () -> Unit,
    onOpenAod: () -> Unit,
    onOpenSleep: () -> Unit
) {
    val shizuku by ShizukuGateway.state.collectAsState()
    val update by UpdateCenter.state.collectAsState()
    val gate = remember { DisplayGate.evaluate(context) }
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
    var showProfileSwitcher by rememberSaveable { mutableStateOf(false) }
    var showSleepConfirm by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                refresh++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val activeProfile = state.activeProfile
    val selectedProfile = state.selectedProfile
    val fallbackHz = when {
        activeKey == "AOD" -> state.aodHz
        else -> activeProfile?.frequency?.steps
            ?.getOrNull((activeStep - 1).coerceAtLeast(0))?.hz ?: 0
    }
    val displayHz = when {
        !state.enabled || shizuku != ShizukuState.READY ||
            activeKey == "OFF" || activeKey == "—" -> 0
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
                shizuku = shizuku,
                activeKey = activeKey,
                activeStep = activeStep,
                activeStepCount = activeStepCount,
                idleReason = idleReason,
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
                        io.github.nevesz.onehz.engine.AodBrightnessController.onPrefsChanged()
                    }
                }
            )
        }

        if (update.showBanner) {
            item {
                UpdateBanner(
                    state = update,
                    onDismiss = { UpdateCenter.dismiss(context, it) }
                )
            }
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
            ProfileCard(
                state = state,
                selected = selectedProfile,
                active = activeProfile,
                onOpenSwitcher = { showProfileSwitcher = true },
                onManageProfiles = onManageProfiles,
                onEditProfile = onEditProfile
            )
        }

        item {
            Text(
                text = stringResource(R.string.home_section_screen_off),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            OneHzCard {
                SettingRow(
                    icon = Icons.Outlined.Nightlight,
                    title = stringResource(R.string.aod_title),
                    subtitle = aodSummary(state, gate.profile.supportsAodBrightness),
                    onClick = onOpenAod,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.aodEnabled,
                                enabled = state.enabled,
                                onCheckedChange = { on ->
                                    state = state.copy(aodEnabled = on)
                                    OneHzPreferences.setAodEnabled(context, on)
                                    OneHzEngine.reapply()
                                }
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = Icons.Outlined.Bedtime,
                    title = stringResource(R.string.sleep_title),
                    subtitle = sleepRowSummary(context, state.enabled, state.sleepEnabled),
                    onClick = onOpenSleep,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.sleepEnabled,
                                enabled = state.enabled,
                                onCheckedChange = { on ->
                                    if (!on) {
                                        state = state.copy(sleepEnabled = false)
                                        SleepPreferences.setEnabled(context, false)
                                        DozeController.onPrefsChanged()
                                    } else if (SleepPreferences.warningSeen(context)) {
                                        state = state.copy(sleepEnabled = true)
                                        SleepPreferences.setEnabled(context, true)
                                        DozeController.onPrefsChanged()
                                    } else {
                                        showSleepConfirm = true
                                    }
                                }
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showProfileSwitcher) {
        ProfileSwitcherSheet(
            state = state.profileState,
            batterySaverActive = state.ecoActive,
            onDismiss = { showProfileSwitcher = false },
            onSelect = { profile ->
                ProfileStore.select(context, profile.id)
                OneHzEngine.reapply()
                state = loadState(context, rates)
                showProfileSwitcher = false
            },
            onManage = {
                showProfileSwitcher = false
                onManageProfiles()
            }
        )
    }

    if (showSleepConfirm) {
        AlertDialog(
            onDismissRequest = { showSleepConfirm = false },
            title = { Text(stringResource(R.string.sleep_confirm_title)) },
            text = { Text(stringResource(R.string.sleep_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    SleepPreferences.setWarningSeen(context)
                    state = state.copy(sleepEnabled = true)
                    SleepPreferences.setEnabled(context, true)
                    DozeController.onPrefsChanged()
                    showSleepConfirm = false
                }) {
                    Text(stringResource(R.string.sleep_confirm_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleepConfirm = false }) {
                    Text(stringResource(R.string.sleep_confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun aodSummary(state: HomeState, supportsBrightness: Boolean): String {
    if (!state.aodEnabled) return stringResource(R.string.home_aod_summary_off)
    return if (supportsBrightness && state.aodBrightnessEnabled) {
        stringResource(R.string.home_aod_summary_on, state.aodHz, state.aodBrightnessPct)
    } else {
        stringResource(R.string.aod_minimum, state.aodHz)
    }
}

@Composable
private fun sleepRowSummary(context: Context, masterEnabled: Boolean, sleepEnabled: Boolean): String =
    sleepQuickStatus(context, masterEnabled, sleepEnabled)

@Composable
private fun ProfileCard(
    state: HomeState,
    selected: DeviceProfile?,
    active: DeviceProfile?,
    onOpenSwitcher: () -> Unit,
    onManageProfiles: () -> Unit,
    onEditProfile: (String) -> Unit
) {
    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selected?.let { profileLabel(it) }
                            ?: stringResource(R.string.profile_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selected != null) {
                        Spacer(Modifier.size(8.dp))
                        StatusBadge(
                            text = stringResource(R.string.profile_active),
                            tone = StatusTone.NEUTRAL
                        )
                    }
                }
                Text(
                    text = selected?.let { profileSummary(it) }
                        ?: stringResource(R.string.profile_need_tiers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.enabled && selected != null && active != null && active.id != selected.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.profile_in_use, profileLabel(active)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.ecoActive && state.profileState.autoEconomyOnBatterySaver) {
                            Spacer(Modifier.size(6.dp))
                            StatusBadge(
                                text = stringResource(R.string.profile_auto_economy_badge),
                                tone = StatusTone.OK
                            )
                        }
                    }
                }
            }
        }

        if (selected != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditProfile(selected.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.profile_video_link, selected.video.minHz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(onClick = onOpenSwitcher) {
                Text(stringResource(R.string.profile_select))
            }
            TextButton(onClick = onManageProfiles) {
                Text(stringResource(R.string.profile_manage))
            }
            if (selected != null) {
                TextButton(onClick = { onEditProfile(selected.id) }) {
                    Text(stringResource(R.string.profile_edit))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitcherSheet(
    state: ProfileState,
    batterySaverActive: Boolean,
    onDismiss: () -> Unit,
    onSelect: (DeviceProfile) -> Unit,
    onManage: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.home_section_profiles),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Text(
                text = stringResource(R.string.profile_switch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
            if (batterySaverActive && state.autoEconomyOnBatterySaver) {
                Text(
                    text = stringResource(R.string.profile_battery_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            state.profiles.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = profile.id == state.activeProfileId,
                            role = Role.RadioButton,
                            onClick = { onSelect(profile) }
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = profile.id == state.activeProfileId,
                        onClick = null
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = profileLabel(profile),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = profileSummary(profile),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_video_link,
                                profile.video.minHz
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onManage, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_manage))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: HomeState,
    appliedHz: Int,
    maxHz: Int,
    shizuku: ShizukuState,
    activeKey: String,
    activeStep: Int,
    activeStepCount: Int,
    idleReason: IdleReason?,
    videoPlaying: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val control = when {
        !state.enabled -> null
        shizuku != ShizukuState.READY -> stringResource(R.string.home_control_waiting)
        idleReason != null -> stringResource(R.string.home_control_none)
        else -> stringResource(R.string.home_control_on)
    }
    val tone = when {
        !state.enabled -> StatusTone.NEUTRAL
        shizuku != ShizukuState.READY || idleReason != null -> StatusTone.WARN
        else -> StatusTone.OK
    }
    val active = state.activeProfile
    val source = when {
        !state.enabled -> stringResource(R.string.master_off_hint)
        shizuku != ShizukuState.READY -> null
        idleReason == IdleReason.PROFILE_DISABLED -> stringResource(R.string.profile_idle_off)
        idleReason == IdleReason.PROFILE_EMPTY -> stringResource(R.string.profile_need_tiers)
        idleReason == IdleReason.AOD_DISABLED -> stringResource(R.string.idle_aod_off)
        activeKey == "AOD" -> stringResource(R.string.home_source_aod, state.aodHz)
        state.ecoActive && state.profileState.autoEconomyOnBatterySaver ->
            stringResource(R.string.home_source_economy)
        videoPlaying && active != null ->
            stringResource(R.string.home_source_video, active.video.minHz)
        active != null ->
            stringResource(R.string.home_source_profile, profileLabel(active))
        else -> stringResource(R.string.profile_idle_off)
    }

    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RateRing(hz = if (state.enabled) appliedHz else 0, max = maxHz)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.enabled) R.string.home_status_on else R.string.home_status_off
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                if (control != null) {
                    StatusBadge(text = control, tone = tone)
                }
                if (source != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.enabled && activeStepCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.step_of, activeStep, activeStepCount),
                        style = MaterialTheme.typography.labelLarge,
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
