package io.github.nevesz.onehz.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.RateStats
import io.github.nevesz.onehz.core.RateStatsSummary
import io.github.nevesz.onehz.core.RestSession
import io.github.nevesz.onehz.core.SleepLedger
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.core.SleepUsage
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.ui.components.MetricRow
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.home.SleepDetailsSheet
import io.github.nevesz.onehz.ui.home.formatSleepDuration
import io.github.nevesz.onehz.ui.home.formatSleepRate
import io.github.nevesz.onehz.ui.profile.profileLabel

@Composable
fun UsageScreen(
    context: Context,
    dataVersion: Int
) {
    var refresh by remember { mutableIntStateOf(0) }
    val today = remember(dataVersion, refresh) { RateStats.summary(context, 1) }
    val week = remember(dataVersion, refresh) { RateStats.summary(context, 7) }
    var showSleepDetails by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val records = remember(dataVersion, refresh) { SleepPreferences.records(context) }
    val sleepUsage = remember(records, now) { SleepLedger.usage(records, now) }
    val lastRest = remember(records, now) { SleepLedger.lastRest(records, now) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                Text(
                    text = stringResource(R.string.usage_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                UsageSection(
                    title = stringResource(R.string.usage_today),
                    summary = today
                )
            }
            item {
                UsageSection(
                    title = stringResource(R.string.usage_week),
                    summary = week
                )
            }
            item {
                SleepUsageCard(
                    usage = sleepUsage,
                    rest = lastRest,
                    onDetails = { showSleepDetails = true }
                )
            }
    }

    if (showSleepDetails) {
        SleepDetailsSheet(
            records = records,
            now = now,
            onDismiss = { showSleepDetails = false }
        )
    }
}

@Composable
private fun SleepUsageCard(
    usage: SleepUsage,
    rest: RestSession?,
    onDetails: () -> Unit
) {
    OneHzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sleep_doze_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (usage.partial) {
                StatusBadge(
                    text = stringResource(R.string.sleep_partial_badge),
                    tone = StatusTone.WARN
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sleep_usage_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MetricRow(
            label = stringResource(R.string.sleep_row_screen_off),
            value = formatSleepDuration(usage.screenOffMs)
        )
        MetricRow(
            label = stringResource(R.string.sleep_row_doze),
            value = formatSleepDuration(usage.idleMs)
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.sleep_rest_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rest == null) {
            Text(
                text = stringResource(R.string.sleep_rest_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            MetricRow(
                label = stringResource(R.string.sleep_row_duration),
                value = formatSleepDuration(rest.durationMs)
            )
            rest.perHour?.let {
                MetricRow(
                    label = stringResource(R.string.sleep_row_consumption),
                    value = stringResource(R.string.sleep_value_rate, formatSleepRate(it))
                )
            }
            if (rest.levelStart > 0 && rest.levelEnd >= 0) {
                MetricRow(
                    label = stringResource(R.string.sleep_row_battery),
                    value = stringResource(
                        R.string.sleep_value_battery,
                        rest.levelStart,
                        rest.levelEnd
                    )
                )
            }
        }
        TextButton(onClick = onDetails) {
            Text(stringResource(R.string.sleep_details))
        }
    }
}

@Composable
private fun UsageSection(title: String, summary: RateStatsSummary) {
    val context = LocalContext.current
    OneHzCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        if (summary.rates.isEmpty() && summary.aodMs == 0L && summary.offMs == 0L) {
            Text(
                text = stringResource(R.string.usage_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@OneHzCard
        }
        summary.rates.forEach { bucket ->
            UsageRow(
                label = stringResource(R.string.usage_rate, bucket.hz),
                durationMs = bucket.durationMs
            )
        }
        if (summary.aodMs > 0) {
            UsageRow(
                label = stringResource(R.string.usage_aod),
                durationMs = summary.aodMs
            )
        }
        if (summary.offMs > 0) {
            UsageRow(
                label = stringResource(R.string.usage_uncontrolled),
                durationMs = summary.offMs
            )
        }
        if (summary.profiles.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            summary.profiles.forEach { bucket ->
                val profile = ProfileStore.profile(context, bucket.profileId)
                UsageRow(
                    label = if (profile != null) {
                        profileLabel(profile)
                    } else {
                        bucket.profileId
                    },
                    durationMs = bucket.durationMs
                )
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, durationMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = durationLabel(durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun durationLabel(durationMs: Long): String {
    val minutes = (durationMs / 60_000L).coerceAtLeast(0L)
    return if (minutes >= 60) {
        stringResource(R.string.usage_duration, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.usage_duration_min, minutes)
    }
}
