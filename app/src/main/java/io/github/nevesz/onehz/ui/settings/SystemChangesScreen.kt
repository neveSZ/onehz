package io.github.nevesz.onehz.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.SystemChange
import io.github.nevesz.onehz.core.SystemChanges
import io.github.nevesz.onehz.core.SystemChangesSnapshot
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.icons.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemChangesScreen(
    context: Context,
    dataVersion: Int
) {
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snapshotState by produceState<SystemChangesSnapshot?>(null, dataVersion, refresh) {
        value = withContext(Dispatchers.IO) { SystemChanges.read(context) }
    }
    val snapshot = snapshotState
    var showRestore by remember { mutableStateOf(false) }
    val reportName = remember {
        "onehz-panel-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".txt"
    }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(SystemChanges.report(context))
                    } ?: error("no stream")
                }.isSuccess
            }
            if (!ok) {
                Toast.makeText(
                    context,
                    R.string.system_changes_export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) { Diagnostics.externalChange.value = false }

    if (snapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                Text(
                    text = stringResource(R.string.system_changes_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (snapshot.externalChange) {
                item {
                    InfoBanner(
                        body = stringResource(R.string.system_changes_external),
                        tone = BannerTone.WARN
                    )
                }
            }
            if (!snapshot.shizukuReady) {
                item {
                    InfoBanner(
                        body = stringResource(R.string.system_changes_unavailable),
                        tone = BannerTone.INFO
                    )
                }
            } else if (snapshot.changes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.system_changes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(snapshot.changes, key = { it.key }) { change ->
                ChangeCard(change)
            }
            snapshot.dozeWhitelisted?.let { whitelisted ->
                item {
                    OneHzCard {
                        Text(
                            text = stringResource(R.string.system_changes_doze),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (whitelisted) {
                                stringResource(R.string.system_changes_current)
                            } else {
                                stringResource(R.string.system_changes_unknown)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { export.launch(reportName) },
                    enabled = snapshot.shizukuReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.description(), contentDescription = null)
                    Text(
                        text = stringResource(R.string.system_changes_export),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = { showRestore = true },
                    enabled = snapshot.shizukuReady && snapshot.changes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.system_changes_restore))
                }
            }
    }

    if (showRestore) {
        ConfirmDialog(
            title = stringResource(R.string.system_changes_restore_title),
            body = stringResource(R.string.system_changes_restore_body),
            confirmLabel = stringResource(R.string.system_changes_restore_confirm),
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { SystemChanges.restoreAll(context) }
                    showRestore = false
                    refresh++
                    Toast.makeText(
                        context,
                        R.string.system_changes_restored,
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showRestore = false }
        )
    }
}

@Composable
private fun ChangeCard(change: SystemChange) {
    OneHzCard {
        Text(
            text = change.key,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.system_changes_current) + ": " +
                (change.current ?: stringResource(R.string.system_changes_unknown)),
            style = MaterialTheme.typography.bodySmall
        )
        change.original?.let { original ->
            Text(
                text = stringResource(R.string.system_changes_original) + ": $original",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
