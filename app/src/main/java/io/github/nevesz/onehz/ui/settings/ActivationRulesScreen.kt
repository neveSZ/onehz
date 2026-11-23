package io.github.nevesz.onehz.ui.settings

import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.ActivationRules
import io.github.nevesz.onehz.core.ActivationRulesStore
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.profile.profileLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationRulesScreen(
    context: Context,
    dataVersion: Int,
    onBack: () -> Unit
) {
    val profiles = remember(dataVersion) { ProfileStore.state(context).profiles }
    var rules by remember(dataVersion) { mutableStateOf(ActivationRulesStore.get(context)) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    fun update(next: ActivationRules) {
        rules = next
        ActivationRulesStore.set(context, next)
        OneHzEngine.reapply()
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
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
            item {
                Text(
                    text = stringResource(R.string.rules_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OneHzCard {
                    ToggleRow(
                        title = stringResource(R.string.rules_low_battery_title),
                        descriptionOn = stringResource(R.string.rules_low_battery_on),
                        descriptionOff = stringResource(R.string.rules_low_battery_off),
                        checked = rules.lowBatteryEnabled,
                        onCheckedChange = { update(rules.copy(lowBatteryEnabled = it)) }
                    )
                    if (rules.lowBatteryEnabled) {
                        ThresholdRow(
                            percent = rules.lowBatteryPercent,
                            onChange = { update(rules.copy(lowBatteryPercent = it)) }
                        )
                        RuleRow(
                            title = stringResource(R.string.rules_profile),
                            subtitle = profileName(rules.lowBatteryProfileId, profiles),
                            onClick = { picker = PickerTarget.LOW_BATTERY }
                        )
                    }
                }
            }
            item {
                OneHzCard {
                    ToggleRow(
                        title = stringResource(R.string.rules_charging_title),
                        descriptionOn = stringResource(R.string.rules_charging_on),
                        descriptionOff = stringResource(R.string.rules_charging_off),
                        checked = rules.chargingEnabled,
                        onCheckedChange = { update(rules.copy(chargingEnabled = it)) }
                    )
                    if (rules.chargingEnabled) {
                        RuleRow(
                            title = stringResource(R.string.rules_profile),
                            subtitle = profileName(rules.chargingProfileId, profiles),
                            onClick = { picker = PickerTarget.CHARGING }
                        )
                    }
                }
            }
            item {
                OneHzCard {
                    ToggleRow(
                        title = stringResource(R.string.rules_window_title),
                        descriptionOn = stringResource(R.string.rules_window_on),
                        descriptionOff = stringResource(R.string.rules_window_off),
                        checked = rules.windowEnabled,
                        onCheckedChange = { update(rules.copy(windowEnabled = it)) }
                    )
                    if (rules.windowEnabled) {
                        RuleRow(
                            title = stringResource(R.string.rules_start),
                            subtitle = timeLabel(rules.windowStartMinute),
                            onClick = {
                                pickTime(context, rules.windowStartMinute) {
                                    update(rules.copy(windowStartMinute = it))
                                }
                            }
                        )
                        RuleRow(
                            title = stringResource(R.string.rules_end),
                            subtitle = timeLabel(rules.windowEndMinute),
                            onClick = {
                                pickTime(context, rules.windowEndMinute) {
                                    update(rules.copy(windowEndMinute = it))
                                }
                            }
                        )
                        RuleRow(
                            title = stringResource(R.string.rules_profile),
                            subtitle = profileName(rules.windowProfileId, profiles),
                            onClick = { picker = PickerTarget.WINDOW }
                        )
                    }
                }
            }
        }
    }

    picker?.let { target ->
        ProfilePickerDialog(
            profiles = profiles,
            selectedId = when (target) {
                PickerTarget.LOW_BATTERY -> rules.lowBatteryProfileId
                PickerTarget.CHARGING -> rules.chargingProfileId
                PickerTarget.WINDOW -> rules.windowProfileId
            },
            onDismiss = { picker = null },
            onSelect = { id ->
                val next = when (target) {
                    PickerTarget.LOW_BATTERY -> rules.copy(lowBatteryProfileId = id)
                    PickerTarget.CHARGING -> rules.copy(chargingProfileId = id)
                    PickerTarget.WINDOW -> rules.copy(windowProfileId = id)
                }
                update(next)
                picker = null
            }
        )
    }
}

private enum class PickerTarget { LOW_BATTERY, CHARGING, WINDOW }

@Composable
private fun ThresholdRow(percent: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.rules_threshold),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { onChange((percent - 5).coerceAtLeast(5)) },
            enabled = percent > 5
        ) {
            Icon(AppIcons.remove(), contentDescription = null)
        }
        IconButton(
            onClick = { onChange((percent + 5).coerceAtMost(50)) },
            enabled = percent < 50
        ) {
            Icon(AppIcons.add(), contentDescription = null)
        }
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            AppIcons.chevronRight(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfilePickerDialog(
    profiles: List<DeviceProfile>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_pick_profile)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = profile.id == selectedId,
                            onClick = null
                        )
                        Text(
                            text = profileLabel(profile),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun profileName(id: String?, profiles: List<DeviceProfile>): String {
    val profile = profiles.firstOrNull { it.id == id }
    return if (profile == null) {
        stringResource(R.string.rules_no_profile)
    } else {
        profileLabel(profile)
    }
}

private fun timeLabel(minuteOfDay: Int): String =
    String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

private fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initial / 60,
        initial % 60,
        true
    ).show()
}
