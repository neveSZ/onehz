package io.github.nevesz.onehz.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayProfileResolver
import io.github.nevesz.onehz.core.DisplayVendor
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.ProfileIssue
import io.github.nevesz.onehz.core.TierStep
import io.github.nevesz.onehz.core.VideoConfig
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileCodec
import io.github.nevesz.onehz.core.profile.ProfileKind
import io.github.nevesz.onehz.core.profile.VideoRules
import io.github.nevesz.onehz.core.profile.VideoSettings
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.DurationStepper
import io.github.nevesz.onehz.ui.components.HzPicker
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.home.VideoForcedAppsSheet

/**
 * Editor for one saved profile. Frequency, video and name are saved together so a profile
 * remains a single reusable unit. Built-in profiles expose "Restore default"; custom
 * profiles expose deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    initial: DeviceProfile,
    defaultProfile: DeviceProfile?,
    rates: List<Int>,
    usageCount: Int,
    onSave: (DeviceProfile) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name.orEmpty()) }
    var steps by rememberSaveable(initial.id, saver = stepsSaver) {
        mutableStateOf(initial.frequency.steps)
    }
    var videoEnabled by rememberSaveable(initial.id) { mutableStateOf(initial.video.enabled) }
    var videoMinHz by rememberSaveable(initial.id) { mutableStateOf(initial.video.minHz) }
    var useSession by rememberSaveable(initial.id) {
        mutableStateOf(initial.video.rules.useMediaSession)
    }
    var pausedBlocks by rememberSaveable(initial.id) {
        mutableStateOf(initial.video.rules.pausedSessionBlocks)
    }
    var requireCodec by rememberSaveable(initial.id) {
        mutableStateOf(initial.video.rules.requireVideoCodec)
    }
    var requireAudio by rememberSaveable(initial.id) {
        mutableStateOf(initial.video.rules.requireStartedAudio)
    }
    var showDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showVideoRules by rememberSaveable { mutableStateOf(false) }
    var showForcedApps by rememberSaveable { mutableStateOf(false) }

    val frequency = FrequencyProfile(steps)
    val video = VideoSettings(
        enabled = videoEnabled,
        minHz = videoMinHz,
        rules = VideoRules(
            useMediaSession = useSession,
            pausedSessionBlocks = pausedBlocks,
            requireVideoCodec = requireCodec,
            requireStartedAudio = requireAudio
        )
    )
    val isCustom = initial.kind == ProfileKind.CUSTOM
    val cleanName = name.trim()
    val nameIssue = isCustom && cleanName.isEmpty()
    val issues = frequency.issues(rates).filter { it != ProfileIssue.EMPTY }
    val canSave = issues.isEmpty() && !nameIssue
    val dirty = steps != initial.frequency.steps ||
        video != initial.video ||
        (isCustom && cleanName != initial.name.orEmpty())
    val isXiaomi = remember {
        DisplayProfileResolver.detect(context).vendor == DisplayVendor.XIAOMI
    }
    val showsLowRateWarning = isXiaomi && steps.any { it.hz <= 30 } && rates.any { it > 30 }

    fun tryBack() {
        if (dirty) showDiscard = true else onBack()
    }

    BackHandler { tryBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isCustom) {
                            cleanName.ifBlank { stringResource(R.string.profile_edit) }
                        } else {
                            stringResource(profileTitle(initial))
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { tryBack() }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        val customized = if (initial.isBuiltIn) {
                            defaultProfile == null ||
                                frequency != defaultProfile.frequency ||
                                video != defaultProfile.video
                        } else {
                            true
                        }
                        onSave(
                            initial.copy(
                                name = if (isCustom) cleanName else null,
                                frequency = frequency,
                                video = video,
                                customized = customized
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(stringResource(R.string.profile_save))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isCustom) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(ProfileCodec.MAX_NAME_LENGTH) },
                        label = { Text(stringResource(R.string.profile_name)) },
                        placeholder = { Text(stringResource(R.string.profile_name_hint)) },
                        isError = nameIssue,
                        supportingText = if (nameIssue) {
                            { Text(stringResource(R.string.profile_name_required)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                OneHzCard {
                    Text(
                        text = profileSummary(initial.copy(frequency = frequency)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    profileTimes(initial.copy(frequency = frequency))?.let { times ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = times,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (frequency.totalHoldMs > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.profile_rest_total,
                                formatSeconds(frequency.totalHoldMs)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (initial.isBuiltIn) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (initial.customized) {
                                stringResource(R.string.profile_modified)
                            } else {
                                stringResource(R.string.profile_default)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (usageCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.profile_used_by_apps,
                                usageCount,
                                usageCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.profile_used_by_apps_edit_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OneHzCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.profile_how_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.profile_how_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showsLowRateWarning) {
                item {
                    OneHzCard {
                        Text(
                            text = stringResource(R.string.profile_low_rate_warning_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.profile_low_rate_warning_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (steps.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.profile_empty_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            itemsIndexed(steps) { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    isLast = index == steps.lastIndex,
                    rates = rates,
                    onRateChange = { hz ->
                        steps = steps.mapIndexed { i, s -> if (i == index) s.copy(hz = hz) else s }
                    },
                    onHoldChange = { seconds ->
                        steps = steps.mapIndexed { i, s ->
                            if (i == index) {
                                s.copy(
                                    holdMs = FrequencyProfile.normalizeHold((seconds * 1000).toLong())
                                )
                            } else {
                                s
                            }
                        }
                    },
                    onRemove = { steps = removeAt(steps, index) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { steps = appendStep(steps, rates) },
                    enabled = steps.size < FrequencyProfile.MAX_STEPS,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.profile_add_step), Modifier.padding(start = 8.dp))
                }
            }

            item {
                OneHzCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.profile_video_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_video_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = stringResource(R.string.video_mode_title),
                        description = stringResource(R.string.video_mode_desc),
                        checked = videoEnabled,
                        onCheckedChange = { videoEnabled = it }
                    )
                    if (videoEnabled) {
                        Spacer(Modifier.height(10.dp))
                        HzPicker(
                            label = stringResource(R.string.video_min_hz),
                            value = videoMinHz,
                            options = rates,
                            onSelect = { videoMinHz = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.video_min_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showVideoRules = !showVideoRules }) {
                            Text(stringResource(R.string.profile_video_advanced))
                        }
                        if (showVideoRules) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.video_rules_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(
                                    R.string.video_rules_summary,
                                    video.rules.activeRuleCount,
                                    VideoConfig.RULE_COUNT
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ToggleRow(
                                title = stringResource(R.string.video_rule_session_title),
                                description = stringResource(R.string.video_rule_session_desc),
                                checked = useSession,
                                onCheckedChange = { useSession = it }
                            )
                            ToggleRow(
                                title = stringResource(R.string.video_rule_paused_title),
                                description = stringResource(R.string.video_rule_paused_desc),
                                checked = pausedBlocks,
                                enabled = useSession,
                                onCheckedChange = { pausedBlocks = it }
                            )
                            ToggleRow(
                                title = stringResource(R.string.video_rule_codec_title),
                                description = stringResource(R.string.video_rule_codec_desc),
                                checked = requireCodec,
                                onCheckedChange = { requireCodec = it }
                            )
                            ToggleRow(
                                title = stringResource(R.string.video_rule_audio_title),
                                description = stringResource(R.string.video_rule_audio_desc),
                                checked = requireAudio,
                                onCheckedChange = { requireAudio = it }
                            )
                            if (!video.rules.hasPositiveSignal) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.video_rules_none_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.profile_reference_global),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showForcedApps = true }) {
                            Text(stringResource(R.string.video_forced_manage))
                        }
                    }
                }
            }

            if (issues.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        issues.forEach { issue ->
                            Text(
                                text = issueText(issue),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (defaultProfile != null) {
                item {
                    TextButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.profile_restore_default))
                    }
                }
            }

            if (onDelete != null) {
                item {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (showDiscard) {
            ConfirmDialog(
                title = stringResource(R.string.profile_discard_title),
                body = stringResource(R.string.profile_discard_body),
                confirmLabel = stringResource(R.string.profile_discard),
                dismissLabel = stringResource(R.string.profile_keep_editing),
                onConfirm = onBack,
                onDismiss = { showDiscard = false }
            )
        }

        if (confirmReset && defaultProfile != null) {
            ConfirmDialog(
                title = stringResource(R.string.profile_reset_editor_title),
                body = stringResource(R.string.profile_reset_editor_body),
                confirmLabel = stringResource(R.string.profile_reset_editor_confirm),
                onConfirm = {
                    steps = defaultProfile.frequency.steps
                    videoEnabled = defaultProfile.video.enabled
                    videoMinHz = defaultProfile.video.minHz
                    useSession = defaultProfile.video.rules.useMediaSession
                    pausedBlocks = defaultProfile.video.rules.pausedSessionBlocks
                    requireCodec = defaultProfile.video.rules.requireVideoCodec
                    requireAudio = defaultProfile.video.rules.requireStartedAudio
                },
                onDismiss = { confirmReset = false }
            )
        }

        if (confirmDelete && onDelete != null) {
            val deleteBody = if (usageCount > 0) {
                pluralStringResource(
                    R.plurals.profile_delete_body,
                    usageCount,
                    usageCount,
                    stringResource(R.string.profile_balanced_title)
                )
            } else {
                stringResource(R.string.profile_delete_body_unused)
            }
            ConfirmDialog(
                title = stringResource(R.string.profile_delete_title),
                body = deleteBody,
                confirmLabel = stringResource(R.string.profile_delete_confirm),
                onConfirm = onDelete,
                onDismiss = { confirmDelete = false }
            )
        }

        if (showForcedApps) {
            VideoForcedAppsSheet(context = context, onDismiss = { showForcedApps = false })
        }
    }
}

private fun profileTitle(profile: DeviceProfile): Int = when (profile.kind) {
    ProfileKind.ECONOMY -> R.string.profile_economy_title
    ProfileKind.BALANCED -> R.string.profile_balanced_title
    ProfileKind.PERFORMANCE -> R.string.profile_performance_title
    ProfileKind.CUSTOM -> R.string.profile_edit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCard(
    index: Int,
    step: TierStep,
    isLast: Boolean,
    rates: List<Int>,
    onRateChange: (Int) -> Unit,
    onHoldChange: (Float) -> Unit,
    onRemove: () -> Unit
) {
    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = stringResource(R.string.profile_step_number, index + 1),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.profile_remove_step)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        HzPicker(
            label = stringResource(R.string.profile_rate),
            value = step.hz,
            options = rates,
            modifier = Modifier.fillMaxWidth(),
            onSelect = onRateChange
        )
        Spacer(Modifier.height(8.dp))
        if (isLast) {
            Text(
                text = stringResource(R.string.profile_rest),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.profile_hold_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                DurationStepper(
                    seconds = ((step.holdMs ?: FrequencyProfile.MIN_HOLD_MS).toFloat() / 1000f),
                    onSecondsChange = onHoldChange
                )
            }
        }
    }
}

@Composable
private fun issueText(issue: ProfileIssue): String = when (issue) {
    ProfileIssue.EMPTY -> stringResource(R.string.profile_empty_warning)
    ProfileIssue.TOO_MANY -> stringResource(R.string.profile_issue_too_many)
    ProfileIssue.NOT_DESCENDING -> stringResource(R.string.profile_issue_not_descending)
    ProfileIssue.UNSUPPORTED_RATE -> stringResource(R.string.profile_issue_unsupported)
    ProfileIssue.HOLD_OUT_OF_RANGE -> stringResource(
        R.string.profile_issue_hold,
        FrequencyProfile.MIN_HOLD_MS / 1000f,
        FrequencyProfile.MAX_HOLD_MS / 1000f
    )
    ProfileIssue.LAST_HAS_HOLD -> stringResource(R.string.profile_issue_not_descending)
}

private fun appendStep(steps: List<TierStep>, rates: List<Int>): List<TierStep> {
    if (steps.size >= FrequencyProfile.MAX_STEPS) return steps
    if (steps.isEmpty()) return listOf(TierStep(rates.maxOrNull() ?: 120, null))
    val lastHz = steps.last().hz
    val nextHz = rates.lastOrNull { it < lastHz } ?: lastHz
    val prepared = steps.mapIndexed { index, step ->
        if (index == steps.lastIndex) {
            step.copy(holdMs = step.holdMs ?: FrequencyProfile.DEFAULT_HOLD_MS)
        } else {
            step
        }
    }
    return prepared + TierStep(nextHz, null)
}

private fun removeAt(steps: List<TierStep>, index: Int): List<TierStep> {
    val remaining = steps.filterIndexed { i, _ -> i != index }
    return remaining.mapIndexed { i, step ->
        if (i == remaining.lastIndex) {
            step.copy(holdMs = null)
        } else {
            step.copy(holdMs = step.holdMs ?: FrequencyProfile.DEFAULT_HOLD_MS)
        }
    }
}

/** Keeps the in-progress edition alive across configuration changes. */
private val stepsSaver = listSaver<MutableState<List<TierStep>>, String>(
    save = { state ->
        state.value.map { step -> step.holdMs?.let { "${step.hz}:$it" } ?: step.hz.toString() }
    },
    restore = { saved ->
        mutableStateOf(
            saved.mapNotNull { raw ->
                val bits = raw.split(':')
                val hz = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                TierStep(hz, bits.getOrNull(1)?.toLongOrNull())
            }
        )
    }
)
