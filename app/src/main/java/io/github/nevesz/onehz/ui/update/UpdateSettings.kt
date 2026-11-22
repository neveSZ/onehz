package io.github.nevesz.onehz.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.update.ReleaseSelector
import io.github.nevesz.onehz.core.update.UpdateChannel
import io.github.nevesz.onehz.core.update.UpdateInterval
import io.github.nevesz.onehz.core.update.UpdateState
import io.github.nevesz.onehz.core.update.UpdateStatus
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.components.rememberFullHeightSheetState
import io.github.nevesz.onehz.ui.icons.AppIcons

/**
 * Settings card for the opt-in GitHub update checker. The toggle is the consent gate:
 * the dialog is shown before the first request, and disabling it stops all future checks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsCard(
    state: UpdateState,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (UpdateInterval) -> Unit,
    onChannelChange: (UpdateChannel) -> Unit,
    onCheckNow: () -> Unit,
    onDismiss: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showConsent by rememberSaveable { mutableStateOf(false) }
    var showInterval by rememberSaveable { mutableStateOf(false) }
    var showChannel by rememberSaveable { mutableStateOf(false) }
    val intervalSheetState = rememberFullHeightSheetState()
    val channelSheetState = rememberFullHeightSheetState()

    val openReleases = {
        runCatching { uriHandler.openUri(ReleaseSelector.RELEASES_PAGE) }
        Unit
    }
    val release = state.available
    val status = updateStatusText(state)
    val statusColor = when (state.status) {
        UpdateStatus.NETWORK_ERROR,
        UpdateStatus.RATE_LIMIT,
        UpdateStatus.HTTP_ERROR,
        UpdateStatus.INVALID_RESPONSE,
        UpdateStatus.INVALID_VERSION -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    OneHzCard {
        ToggleRow(
            icon = AppIcons.systemUpdate(),
            title = stringResource(R.string.update_check_title),
            description = stringResource(R.string.update_check_desc),
            checked = state.enabled,
            onCheckedChange = { enabled ->
                if (enabled) showConsent = true else onEnabledChange(false)
            }
        )

        if (state.enabled) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(
                icon = AppIcons.schedule(),
                title = stringResource(R.string.update_interval_title),
                subtitle = stringResource(state.interval.labelRes()),
                onClick = { showInterval = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(
                icon = AppIcons.science(),
                title = stringResource(R.string.update_channel_title),
                subtitle = stringResource(state.channel.labelRes()),
                onClick = { showChannel = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            if (release != null) {
                InfoBanner(
                    title = stringResource(
                        if (release.isPrerelease) R.string.update_banner_prerelease_title
                        else R.string.update_banner_title
                    ),
                    body = stringResource(
                        if (release.isPrerelease) R.string.update_banner_prerelease_body
                        else R.string.update_banner_body,
                        release.displayVersion
                    ),
                    tone = if (release.isPrerelease) BannerTone.WARN else BannerTone.INFO,
                    icon = if (release.isPrerelease) AppIcons.science() else AppIcons.newReleases(),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { runCatching { uriHandler.openUri(release.url) } }) {
                                Text(stringResource(R.string.update_view_release))
                            }
                            if (release.tag != state.dismissedVersion) {
                                TextButton(onClick = { onDismiss(release.tag) }) {
                                    Text(stringResource(R.string.update_dismiss))
                                }
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.lastAttemptAt > 0L) {
                    stringResource(R.string.update_last_check, formatTimestamp(state.lastAttemptAt))
                } else {
                    stringResource(R.string.update_never_checked)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.update_next_check),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCheckNow, enabled = !state.checking) {
                if (state.checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        AppIcons.refresh(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(
                        if (state.checking) R.string.update_checking else R.string.update_check_now
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        SettingRow(
            icon = AppIcons.openInNew(),
            title = stringResource(R.string.update_open_releases),
            subtitle = stringResource(R.string.update_open_releases_desc),
            onClick = openReleases,
            trailing = {
                Icon(
                    AppIcons.openInNew(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(stringResource(R.string.update_enable_dialog_title)) },
            text = {
                Text(
                    text = stringResource(R.string.update_enable_dialog_body),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConsent = false
                        onEnabledChange(true)
                    }
                ) { Text(stringResource(R.string.update_enable_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) {
                    Text(stringResource(R.string.update_enable_cancel))
                }
            }
        )
    }

    if (showInterval) {
        ModalBottomSheet(
            onDismissRequest = { showInterval = false },
            sheetState = intervalSheetState
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    stringResource(R.string.update_interval_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.update_interval_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                UpdateInterval.entries.forEach { option ->
                    OptionRow(
                        title = stringResource(option.labelRes()),
                        selected = option == state.interval,
                        onClick = {
                            onIntervalChange(option)
                            showInterval = false
                        }
                    )
                }
            }
        }
    }

    if (showChannel) {
        ModalBottomSheet(
            onDismissRequest = { showChannel = false },
            sheetState = channelSheetState
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    stringResource(R.string.update_channel_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.update_channel_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                UpdateChannel.entries.forEach { option ->
                    OptionRow(
                        title = stringResource(option.labelRes()),
                        description = stringResource(option.descriptionRes()),
                        selected = option == state.channel,
                        onClick = {
                            onChannelChange(option)
                            showChannel = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun updateStatusText(state: UpdateState): String = when {
    state.checking -> stringResource(R.string.update_checking)
    state.status == UpdateStatus.AVAILABLE && state.available != null -> {
        val release = state.available
        stringResource(
            if (release.isPrerelease) R.string.update_available_prerelease
            else R.string.update_available,
            release.displayVersion
        )
    }
    state.status == UpdateStatus.UP_TO_DATE -> stringResource(R.string.update_up_to_date)
    state.status == UpdateStatus.NO_RELEASES -> stringResource(R.string.update_no_releases)
    state.status == UpdateStatus.NETWORK_ERROR -> stringResource(R.string.update_error_network)
    state.status == UpdateStatus.RATE_LIMIT -> stringResource(R.string.update_error_rate_limit)
    state.status == UpdateStatus.HTTP_ERROR && state.httpCode != null ->
        stringResource(R.string.update_error_http, state.httpCode)
    state.status == UpdateStatus.HTTP_ERROR -> stringResource(R.string.update_error_http_generic)
    state.status == UpdateStatus.INVALID_RESPONSE ->
        stringResource(R.string.update_error_invalid_response)
    state.status == UpdateStatus.INVALID_VERSION -> stringResource(R.string.update_error_version)
    else -> stringResource(R.string.update_idle)
}

private fun formatTimestamp(timestamp: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(timestamp))

private fun UpdateInterval.labelRes(): Int = when (this) {
    UpdateInterval.HOURS_24 -> R.string.update_interval_24h
    UpdateInterval.DAYS_3 -> R.string.update_interval_3d
    UpdateInterval.DAYS_7 -> R.string.update_interval_7d
    UpdateInterval.DAYS_30 -> R.string.update_interval_30d
}

private fun UpdateChannel.labelRes(): Int = when (this) {
    UpdateChannel.STABLE -> R.string.update_channel_stable
    UpdateChannel.STABLE_AND_PRERELEASE -> R.string.update_channel_all
}

private fun UpdateChannel.descriptionRes(): Int = when (this) {
    UpdateChannel.STABLE -> R.string.update_channel_stable_desc
    UpdateChannel.STABLE_AND_PRERELEASE -> R.string.update_channel_all_desc
}
