package io.github.nevesz.onehz.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DozeWhitelist
import io.github.nevesz.onehz.core.RestSession
import io.github.nevesz.onehz.core.SleepLedger
import io.github.nevesz.onehz.core.SleepRecord
import io.github.nevesz.onehz.core.apps.AppCatalog
import io.github.nevesz.onehz.core.apps.InstalledApp
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import io.github.nevesz.onehz.ui.apps.AppIcon
import io.github.nevesz.onehz.ui.components.rememberPartialSheetState
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.viewmodel.SleepViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailsSheet(
    records: List<SleepRecord>,
    now: Long,
    onDismiss: () -> Unit,
) {
    // The details sheet lists every recent session; the 30-minute threshold only drives
    // the "last rest" metric on the card.
    val sessions = SleepLedger.restSessions(records, now, minDurationMs = 0L)
        .takeLast(5)
        .asReversed()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberPartialSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.sleep_details_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sleep_details_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.sleep_details_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                sessions.forEach { session ->
                    SleepSessionRow(session, now)
                    Spacer(Modifier.height(12.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.sleep_measure_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SleepSessionRow(session: RestSession, now: Long) {
    val end = if (session.open) now else session.endAt
    val parts = mutableListOf(formatSleepDuration(session.durationMs))
    if (session.levelStart > 0 && session.levelEnd >= 0) {
        parts += stringResource(R.string.sleep_rest_battery, session.levelStart, session.levelEnd)
    }
    session.perHour?.let { parts += stringResource(R.string.sleep_rest_rate, formatSleepRate(it)) }
    parts += stringResource(
        if (session.deep) R.string.sleep_session_deep else R.string.sleep_session_light,
    )
    if (session.maintenance > 0) {
        parts += pluralStringResource(
            R.plurals.sleep_maintenance_count,
            session.maintenance,
            session.maintenance,
        )
    }
    Column {
        Text(
            text = "${formatSleepMoment(session.startAt)} → ${formatSleepMoment(end)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepWhitelistSheet(context: Context, onDismiss: () -> Unit) {
    val vm: SleepViewModel = viewModel()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuState.READY
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var allowed by remember { mutableStateOf<Set<String>>(emptySet()) }
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    var loading by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<Set<String>>(emptySet()) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        loading = true
        apps = withContext(Dispatchers.IO) {
            AppCatalog.load(context, includeSystem = false)
        }
        allowed = withContext(Dispatchers.IO) {
            DozeWhitelist.parse(ShizukuGateway.dozeWhitelist())
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberPartialSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.sleep_whitelist_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sleep_whitelist_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (!ready) {
                Text(
                    text = stringResource(R.string.sleep_whitelist_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                OutlinedTextField(
                    state = queryState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sleep_whitelist_search)) },
                    leadingIcon = {
                        Icon(AppIcons.search(), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                if (failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sleep_whitelist_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    val filtered = apps.filter {
                        it.label.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sleep_whitelist_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(filtered, key = { it.packageName }) { app ->
                                WhitelistRow(
                                    context = context,
                                    app = app,
                                    checked = app.packageName in allowed,
                                    busy = app.packageName in pending,
                                    onToggle = { enabled ->
                                        pending = pending + app.packageName
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                ShizukuGateway.setDozeWhitelisted(app.packageName, enabled)
                                            }
                                            if (ok) {
                                                allowed = if (enabled) {
                                                    allowed + app.packageName
                                                } else {
                                                    allowed - app.packageName
                                                }
                                                failed = false
                                            } else {
                                                failed = true
                                            }
                                            pending = pending - app.packageName
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sleep_whitelist_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WhitelistRow(
    context: Context,
    app: InstalledApp,
    checked: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(context, app.packageName)
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = !busy,
            onCheckedChange = onToggle,
        )
    }
}
