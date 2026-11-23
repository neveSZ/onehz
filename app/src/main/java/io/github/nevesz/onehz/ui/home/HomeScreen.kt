package io.github.nevesz.onehz.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.DisplayProfileResolver
import io.github.nevesz.onehz.core.DisplayVendor
import io.github.nevesz.onehz.core.MasterPause
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.core.apps.AppCatalog
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileState
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.DozeController
import io.github.nevesz.onehz.engine.IdleReason
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.components.rememberPartialSheetState
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.profile.profileLabel
import io.github.nevesz.onehz.ui.profile.profileSummary
import io.github.nevesz.onehz.ui.update.UpdateBanner
import io.github.nevesz.onehz.ui.viewmodel.HomeViewModel
import java.util.Date
import kotlin.math.roundToInt

private data class HomeState(
    val enabled: Boolean,
    val profileState: ProfileState,
    val aodMasterEnabled: Boolean,
    val aodRateEnabled: Boolean,
    val aodHz: Int,
    val aodBrightnessEnabled: Boolean,
    val aodBrightnessPct: Int,
    val aodBrightnessMinPct: Int,
    val aodBrightnessMaxPct: Int,
    val sleepEnabled: Boolean,
    val ecoActive: Boolean,
    val pausedUntil: Long
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
        aodMasterEnabled = OneHzPreferences.aodMasterEnabled(context),
        aodRateEnabled = OneHzPreferences.aodRateEnabled(context),
        aodHz = OneHzPreferences.aodHz(context),
        aodBrightnessEnabled = OneHzPreferences.aodBrightnessEnabled(context),
        aodBrightnessPct = OneHzPreferences.aodBrightnessPct(context),
        aodBrightnessMinPct = OneHzPreferences.aodBrightnessMinPct(context),
        aodBrightnessMaxPct = OneHzPreferences.aodBrightnessMaxPct(context),
        sleepEnabled = SleepPreferences.enabled(context),
        ecoActive = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true,
        pausedUntil = OneHzPreferences.masterPausedUntil(context)
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
    val vm: HomeViewModel = viewModel()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val gate = remember { DisplayGate.evaluate(context) }
    val activeKey by vm.activeTierKey.collectAsStateWithLifecycle()
    val activeStep by vm.activeStep.collectAsStateWithLifecycle()
    val activeStepCount by vm.activeStepCount.collectAsStateWithLifecycle()
    val appliedHz by vm.appliedHz.collectAsStateWithLifecycle()
    val idleReason by vm.idleReason.collectAsStateWithLifecycle()
    val videoSource by vm.videoSource.collectAsStateWithLifecycle()
    val activeLayer by vm.activeLayer.collectAsStateWithLifecycle()
    val activeProfileId by vm.activeProfileId.collectAsStateWithLifecycle()
    val activeAppPackage by vm.activeAppPackage.collectAsStateWithLifecycle()

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
    val paused = idleReason == IdleReason.PAUSED ||
        MasterPause.isPaused(state.pausedUntil, System.currentTimeMillis())
    val pausedUntilLabel = remember(state.pausedUntil) {
        if (state.pausedUntil <= 0L) ""
        else DateFormat.getTimeFormat(context).format(Date(state.pausedUntil))
    }
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
    val activeAppLabel = remember(activeAppPackage) {
        activeAppPackage?.let { runCatching { AppCatalog.label(context, it) }.getOrDefault(it) }
    }
    val lastAssignment = state.profileState.assignments.lastOrNull()
    val lastAssignedAppLabel = remember(lastAssignment?.packageName) {
        lastAssignment?.packageName?.let {
            runCatching { AppCatalog.label(context, it) }.getOrDefault(it)
        }
    }
    val lastAssignedProfile = lastAssignment?.let { state.profileState.profile(it.profileId) }
    val appliedProfile = activeProfileId?.let { state.profileState.profile(it) }
    val videoPlaying = videoSource == VideoSignals.Source.PLAYING
    val sourceText = when {
        !state.enabled -> stringResource(R.string.master_off_hint)
        paused -> stringResource(R.string.home_paused_until, pausedUntilLabel)
        shizuku != ShizukuState.READY -> null
        idleReason == IdleReason.PROFILE_DISABLED -> stringResource(R.string.profile_idle_off)
        idleReason == IdleReason.PROFILE_EMPTY -> stringResource(R.string.profile_need_tiers)
        idleReason == IdleReason.AOD_DISABLED -> stringResource(R.string.idle_aod_off)
        activeKey == "AOD" -> stringResource(R.string.home_source_aod, state.aodHz)
        activeAppPackage != null && activeAppLabel != null -> {
            val profileName = appliedProfile?.let { profileLabel(it) }
            if (profileName != null) {
                stringResource(R.string.home_source_app, activeAppLabel, profileName) +
                    if (videoPlaying) stringResource(R.string.home_source_video_suffix) else ""
            } else {
                stringResource(R.string.home_source_profile, activeAppLabel)
            }
        }
        activeLayer == "economy" -> stringResource(R.string.home_source_economy)
        videoPlaying && appliedProfile != null ->
            stringResource(R.string.home_source_video, appliedProfile.video.minHz)
        appliedProfile != null ->
            stringResource(R.string.home_source_profile, profileLabel(appliedProfile))
        activeProfile != null ->
            stringResource(R.string.home_source_profile, profileLabel(activeProfile))
        else -> stringResource(R.string.profile_idle_off)
    }
    val lastAppText = if (
        state.enabled &&
        shizuku == ShizukuState.READY &&
        activeAppPackage == null &&
        lastAssignedAppLabel != null &&
        lastAssignedProfile != null
    ) {
        stringResource(
            R.string.home_last_app,
            lastAssignedAppLabel,
            profileLabel(lastAssignedProfile)
        )
    } else {
        null
    }

    if (!gate.eligible) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            item {
                OneHzCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.errorOutline(),
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
                activeStep = activeStep,
                activeStepCount = activeStepCount,
                idleReason = idleReason,
                sourceText = sourceText,
                lastAppText = lastAppText,
                paused = paused,
                pausedUntilLabel = pausedUntilLabel,
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
                },
                onPause = {
                    val until = System.currentTimeMillis() + MasterPause.ONE_HOUR_MS
                    state = state.copy(pausedUntil = until)
                    OneHzPreferences.setMasterPausedUntil(context, until)
                    OneHzEngine.pauseFor(MasterPause.ONE_HOUR_MS)
                },
                onResume = {
                    state = state.copy(pausedUntil = 0L)
                    OneHzPreferences.setMasterPausedUntil(context, 0L)
                    OneHzEngine.resumeNow()
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
                    icon = AppIcons.bolt(),
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
                context = context,
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
                    icon = AppIcons.nightlight(),
                    title = stringResource(R.string.aod_title),
                    subtitle = aodSummary(state, gate.profile.supportsAodBrightness),
                    onClick = onOpenAod,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.aodMasterEnabled,
                                enabled = state.enabled,
                                modifier = Modifier.semantics {
                                    contentDescription = context.getString(R.string.aod_master_title)
                                },
                                onCheckedChange = { on ->
                                    OneHzPreferences.setAodMasterEnabled(context, on)
                                    state = loadState(context, rates)
                                    OneHzEngine.reapply()
                                    AodBrightnessController.onPrefsChanged()
                                }
                            )
                            Icon(
                                AppIcons.chevronRight(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = AppIcons.bedtime(),
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
                                AppIcons.chevronRight(),
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
    if (!state.aodMasterEnabled) return stringResource(R.string.home_aod_summary_off)
    val rate = state.aodRateEnabled
    val brightness = supportsBrightness && state.aodBrightnessEnabled
    return when {
        rate && brightness -> stringResource(
            R.string.home_aod_summary_on,
            state.aodHz,
            state.aodBrightnessPct
        )
        rate -> stringResource(R.string.home_aod_summary_rate, state.aodHz)
        brightness -> stringResource(R.string.home_aod_summary_brightness, state.aodBrightnessPct)
        else -> stringResource(R.string.home_aod_summary_off)
    }
}

@Composable
private fun sleepRowSummary(context: Context, masterEnabled: Boolean, sleepEnabled: Boolean): String =
    sleepQuickStatus(context, masterEnabled, sleepEnabled)

@Composable
private fun ProfileCard(
    context: Context,
    state: HomeState,
    selected: DeviceProfile?,
    active: DeviceProfile?,
    onOpenSwitcher: () -> Unit,
    onManageProfiles: () -> Unit,
    onEditProfile: (String) -> Unit
) {
    val isXiaomi = remember { DisplayProfileResolver.detect(context).vendor == DisplayVendor.XIAOMI }
    val lowRate = selected?.frequency?.steps?.any { it.hz <= 30 } == true
    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.tune(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selected?.let { profileLabel(it) }
                            ?: stringResource(R.string.profile_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selected != null) {
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
                    val automatic = state.ecoActive && state.profileState.autoEconomyOnBatterySaver
                    Text(
                        text = if (automatic) {
                            stringResource(R.string.profile_in_use_auto, profileLabel(active))
                        } else {
                            stringResource(R.string.profile_in_use, profileLabel(active))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isXiaomi && lowRate) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.profile_low_rate_home),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
                    AppIcons.movie(),
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
                    AppIcons.chevronRight(),
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberPartialSheetState()) {
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
    activeStep: Int,
    activeStepCount: Int,
    idleReason: IdleReason?,
    sourceText: String?,
    lastAppText: String?,
    paused: Boolean,
    pausedUntilLabel: String,
    onToggle: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val control = when {
        !state.enabled -> null
        paused -> stringResource(R.string.home_control_paused)
        shizuku != ShizukuState.READY || idleReason != null ->
            stringResource(R.string.home_control_none)
        else -> stringResource(R.string.home_control_on)
    }
    val tone = when {
        !state.enabled -> StatusTone.NEUTRAL
        paused -> StatusTone.WARN
        shizuku != ShizukuState.READY || idleReason != null -> StatusTone.WARN
        else -> StatusTone.OK
    }
    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RateRing(hz = if (state.enabled && !paused) appliedHz else 0, max = maxHz)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when {
                            paused -> R.string.home_status_paused
                            state.enabled -> R.string.home_status_on
                            else -> R.string.home_status_off
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                if (control != null) {
                    StatusBadge(text = control, tone = tone)
                }
                if (sourceText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lastAppText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = lastAppText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (
                    state.enabled &&
                    shizuku == ShizukuState.READY &&
                    idleReason == null &&
                    activeStepCount > 0
                ) {
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
            icon = AppIcons.speed(),
            title = stringResource(R.string.master_switch),
            descriptionOn = stringResource(R.string.master_switch_desc_on),
            descriptionOff = stringResource(R.string.master_switch_desc_off),
            checked = state.enabled,
            onCheckedChange = onToggle
        )
        if (state.enabled) {
            Spacer(Modifier.height(4.dp))
            if (paused) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onResume) {
                        Icon(AppIcons.playArrow(), contentDescription = null)
                        Text(
                            text = stringResource(R.string.home_resume_action),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            } else {
                TextButton(onClick = onPause) {
                    Icon(AppIcons.pause(), contentDescription = null)
                    Text(
                        text = stringResource(R.string.home_pause_action),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RateRing(hz: Int, max: Int) {
    val fraction = (hz.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, label = "rate")
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val ringLabel = if (hz > 0) "$hz Hz" else "—"
    Box(
        modifier = Modifier
            .size(120.dp)
            .semantics { contentDescription = ringLabel },
        contentAlignment = Alignment.Center
    ) {
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
