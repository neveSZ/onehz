package io.github.nevesz.onehz.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.VideoConfig
import io.github.nevesz.onehz.core.apps.AppCatalog
import io.github.nevesz.onehz.core.apps.InstalledApp
import io.github.nevesz.onehz.ui.apps.AppIcon
import io.github.nevesz.onehz.ui.components.ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class VideoSheetPage { RULES, APPS }

/**
 * Bottom sheet with the video detection rules and the forced app list. Both pages share
 * one sheet so the content never stacks two modal sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoConfigSheet(
    context: Context,
    config: VideoConfig,
    onDismiss: () -> Unit,
    onConfigChange: (VideoConfig) -> Unit
) {
    var pageName by rememberSaveable { mutableStateOf(VideoSheetPage.RULES.name) }
    val page = VideoSheetPage.valueOf(pageName)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (page) {
            VideoSheetPage.RULES -> RulesPage(
                config = config,
                onConfigChange = onConfigChange,
                onOpenApps = { pageName = VideoSheetPage.APPS.name }
            )
            VideoSheetPage.APPS -> AppsPage(
                context = context,
                config = config,
                onConfigChange = onConfigChange,
                onBack = { pageName = VideoSheetPage.RULES.name }
            )
        }
    }
}

@Composable
private fun RulesPage(
    config: VideoConfig,
    onConfigChange: (VideoConfig) -> Unit,
    onOpenApps: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.video_rules_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.video_rules_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = stringResource(R.string.video_rule_session_title),
            description = stringResource(R.string.video_rule_session_desc),
            checked = config.useMediaSession,
            onCheckedChange = { onConfigChange(config.copy(useMediaSession = it)) }
        )
        ToggleRow(
            title = stringResource(R.string.video_rule_paused_title),
            description = stringResource(R.string.video_rule_paused_desc),
            checked = config.pausedSessionBlocks,
            enabled = config.useMediaSession,
            onCheckedChange = { onConfigChange(config.copy(pausedSessionBlocks = it)) }
        )
        ToggleRow(
            title = stringResource(R.string.video_rule_codec_title),
            description = stringResource(R.string.video_rule_codec_desc),
            checked = config.requireVideoCodec,
            onCheckedChange = { onConfigChange(config.copy(requireVideoCodec = it)) }
        )
        ToggleRow(
            title = stringResource(R.string.video_rule_audio_title),
            description = stringResource(R.string.video_rule_audio_desc),
            checked = config.requireStartedAudio,
            onCheckedChange = { onConfigChange(config.copy(requireStartedAudio = it)) }
        )
        if (!config.hasPositiveSignal) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.video_rules_none_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.video_forced_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.video_forced_count,
                        config.forcedPackages.size,
                        config.forcedPackages.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenApps) {
                Text(stringResource(R.string.video_forced_manage))
            }
        }
        Text(
            text = stringResource(R.string.video_forced_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppsPage(
    context: Context,
    config: VideoConfig,
    onConfigChange: (VideoConfig) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var includeSystem by rememberSaveable { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(Dispatchers.IO) { AppCatalog.load(context, includeSystem = true) }
        loading = false
    }

    val installed = remember(apps) { apps.mapTo(mutableSetOf()) { it.packageName } }
    val missing = config.forcedPackages - installed
    val visible = apps.filter { app ->
        (includeSystem || !app.system) &&
            (query.isBlank() ||
                app.label.contains(query, true) ||
                app.packageName.contains(query, true))
    }

    fun toggle(packageName: String) {
        val selected = packageName in config.forcedPackages
        if (!selected && config.forcedPackages.size >= VideoConfig.MAX_FORCED_PACKAGES) {
            Toast.makeText(context, R.string.video_forced_limit, Toast.LENGTH_SHORT).show()
            return
        }
        val updated = if (selected) {
            config.forcedPackages - packageName
        } else {
            config.forcedPackages + packageName
        }
        onConfigChange(config.copy(forcedPackages = updated))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 20.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.video_forced_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.video_forced_count,
                        config.forcedPackages.size,
                        config.forcedPackages.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (config.forcedPackages.isNotEmpty()) {
                TextButton(onClick = { onConfigChange(config.copy(forcedPackages = emptySet())) }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Text(
                        text = stringResource(R.string.video_forced_clear),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = includeSystem,
            onClick = { includeSystem = !includeSystem },
            label = { Text(stringResource(R.string.filter_system)) }
        )
        Spacer(Modifier.height(4.dp))

        if (missing.isNotEmpty()) {
            Text(
                text = stringResource(R.string.video_forced_missing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            missing.sorted().forEach { packageName ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { toggle(packageName) }) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(visible, key = { it.packageName }) { app ->
                    val checked = app.packageName in config.forcedPackages
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { toggle(app.packageName) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(context, app.packageName)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
