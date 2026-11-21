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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayProfileResolver
import io.github.nevesz.onehz.core.DisplayVendor
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.ProfileIssue
import io.github.nevesz.onehz.core.TierStep
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.DurationStepper
import io.github.nevesz.onehz.ui.components.HzPicker
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.ToggleRow

/**
 * Single place to create and maintain one frequency profile.
 * Used for the normal profile, the battery-saver profile and per-app profiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    title: String,
    initial: FrequencyProfile,
    rates: List<Int>,
    defaultProfile: FrequencyProfile,
    onSave: (FrequencyProfile) -> Unit,
    onBack: () -> Unit,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    copyFrom: FrequencyProfile? = null,
    copyLabel: String? = null,
    onRemove: (() -> Unit)? = null
) {
    var steps by rememberSaveable(saver = stepsSaver) { mutableStateOf(initial.steps) }
    val profile = FrequencyProfile(steps)
    val issues = profile.issues(rates).filter { it != ProfileIssue.EMPTY }
    var showDiscard by remember { mutableStateOf(false) }
    var confirmRemoveProfile by remember { mutableStateOf(false) }
    val dirty = steps != initial.steps
    val context = LocalContext.current
    val isXiaomi = remember { DisplayProfileResolver.detect(context).vendor == DisplayVendor.XIAOMI }
    val showsLowRateWarning = isXiaomi && steps.any { it.hz <= 30 } && rates.any { it > 30 }

    fun tryBack() {
        if (dirty) showDiscard = true else onBack()
    }

    BackHandler { tryBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { tryBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSave(profile) },
                    enabled = issues.isEmpty(),
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
            if (enabled != null && onEnabledChange != null) {
                item {
                    OneHzCard {
                        ToggleRow(
                            title = stringResource(R.string.profile_use),
                            checked = enabled,
                            onCheckedChange = onEnabledChange
                        )
                    }
                }
            }

            item {
                OneHzCard {
                    Text(
                        text = preview(profile),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (profile.totalHoldMs > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.profile_rest_total,
                                formatSeconds(profile.totalHoldMs)
                            ),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.profile_low_rate_warning_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
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
                                s.copy(holdMs = FrequencyProfile.normalizeHold((seconds * 1000).toLong()))
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

            if (copyFrom != null && copyLabel != null) {
                item {
                    TextButton(onClick = { steps = copyFrom.steps }, modifier = Modifier.fillMaxWidth()) {
                        Text(copyLabel)
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

            item {
                TextButton(
                    onClick = { steps = defaultProfile.steps },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_restore_default))
                }
            }

            if (onRemove != null) {
                item {
                    OutlinedButton(
                        onClick = { confirmRemoveProfile = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
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

        if (confirmRemoveProfile && onRemove != null) {
            ConfirmDialog(
                title = stringResource(R.string.profile_remove_title),
                body = stringResource(R.string.profile_remove_body),
                confirmLabel = stringResource(R.string.remove),
                onConfirm = onRemove,
                onDismiss = { confirmRemoveProfile = false }
            )
        }
    }
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
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.profile_remove_step))
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
private fun preview(profile: FrequencyProfile): String {
    if (profile.steps.isEmpty()) return stringResource(R.string.profile_empty)
    if (profile.isFixed) return stringResource(R.string.profile_preview_fixed, profile.steps[0].hz)
    val chain = profile.steps.mapIndexed { index, step ->
        val rate = "${step.hz} Hz"
        if (index == profile.steps.lastIndex) {
            rate
        } else {
            "$rate · ${formatSeconds(step.holdMs ?: 0L)}"
        }
    }.joinToString(" → ")
    return stringResource(R.string.profile_preview, chain)
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
    if (steps.isEmpty()) {
        return listOf(TierStep(rates.maxOrNull() ?: 120, null))
    }
    val lastHz = steps.last().hz
    val nextHz = rates.lastOrNull { it < lastHz } ?: lastHz
    val prepared = steps.mapIndexed { index, step ->
        if (index == steps.lastIndex) step.copy(holdMs = step.holdMs ?: FrequencyProfile.DEFAULT_HOLD_MS) else step
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

private fun formatSeconds(ms: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f s", ms / 1000f)

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
