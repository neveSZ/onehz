package io.github.nevesz.onehz.ui.apps

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.apps.AppCatalog
import io.github.nevesz.onehz.core.apps.InstalledApp
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.EmptyState
import io.github.nevesz.onehz.ui.components.IconSurface
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AppFilter { ALL, CONFIGURED, SYSTEM }
private enum class Template { NORMAL, ECO, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    context: Context,
    rates: List<Int>,
    dataVersion: Int,
    onEditApp: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(AppFilter.ALL.name) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    var addPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var editPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val filter = AppFilter.valueOf(filterName)

    val configured = remember(refreshTick, dataVersion) {
        OneHzPreferences.configuredPackages(context).toSet()
    }
    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(filter, refreshTick, dataVersion) {
        loading = true
        installed = withContext(Dispatchers.IO) {
            when (filter) {
                AppFilter.SYSTEM -> AppCatalog.load(context, includeSystem = true).filter { it.system }
                AppFilter.ALL -> AppCatalog.load(context, includeSystem = false)
                AppFilter.CONFIGURED -> OneHzPreferences.configuredPackages(context)
                    .map { InstalledApp(it, AppCatalog.label(context, it), false) }
                    .sortedBy { it.label.lowercase() }
            }
        }
        loading = false
    }
    val filtered = installed.filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == AppFilter.ALL, { filterName = AppFilter.ALL.name }, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(filter == AppFilter.CONFIGURED, { filterName = AppFilter.CONFIGURED.name }, label = { Text(stringResource(R.string.filter_configured)) })
                FilterChip(filter == AppFilter.SYSTEM, { filterName = AppFilter.SYSTEM.name }, label = { Text(stringResource(R.string.filter_system)) })
            }
        }
        if (selectionMode) {
            item {
                OneHzCard {
                    Text(
                        pluralStringResource(R.plurals.selected_count, selected.size, selected.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { confirmBulk = true },
                            enabled = selected.isNotEmpty()
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            Text(stringResource(R.string.delete_selected), Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = {
                            selectionMode = false
                            selected = emptySet()
                        }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
        if (!loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (filter == AppFilter.CONFIGURED) R.string.configured_count else R.string.installed_count,
                            filtered.size
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (configured.isNotEmpty() && !selectionMode) {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.select_apps)
                            )
                        }
                    }
                }
            }
        }
        if (loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.empty_apps),
                    body = stringResource(R.string.empty_apps_body)
                )
            }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                val profile = remember(app.packageName, refreshTick, dataVersion) {
                    OneHzPreferences.appProfile(context, app.packageName)
                }
                AppRow(
                    context = context,
                    app = app,
                    configured = app.packageName in configured,
                    ignoreEco = profile.ignoreEco,
                    selectionMode = selectionMode,
                    checked = app.packageName in selected,
                    onClick = {
                        if (selectionMode) {
                            if (app.packageName in configured) {
                                selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                            }
                        } else if (app.packageName in configured) {
                            editPkg = app.packageName
                        } else {
                            addPkg = app.packageName
                        }
                    }
                )
            }
        }
    }

    addPkg?.let { pkg ->
        AddSheet(
            context = context,
            packageName = pkg,
            onDismiss = { addPkg = null },
            onCustomize = {
                addPkg = null
                refreshTick++
                onEditApp(pkg)
            },
            onAdded = {
                addPkg = null
                refreshTick++
                toast(context.getString(R.string.app_added))
            }
        )
    }
    editPkg?.let { pkg ->
        EditSheet(
            context = context,
            packageName = pkg,
            onDismiss = { editPkg = null },
            onEdit = {
                editPkg = null
                onEditApp(pkg)
            },
            onRemoved = {
                editPkg = null
                refreshTick++
                toast(context.getString(R.string.app_removed))
            }
        )
    }

    if (confirmBulk) {
        ConfirmDialog(
            title = stringResource(R.string.delete_selected_confirm_title),
            body = stringResource(R.string.delete_selected_confirm_body),
            confirmLabel = stringResource(R.string.delete_selected),
            onConfirm = {
                selected.forEach { OneHzPreferences.clearAppProfile(context, it) }
                OneHzEngine.reapply()
                toast(context.resources.getQuantityString(R.plurals.removed_count, selected.size, selected.size))
                selected = emptySet()
                selectionMode = false
                refreshTick++
            },
            onDismiss = { confirmBulk = false }
        )
    }
}

@Composable
private fun AppRow(
    context: Context,
    app: InstalledApp,
    configured: Boolean,
    ignoreEco: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(context, app.packageName)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        when {
            selectionMode && configured -> Checkbox(checked = checked, onCheckedChange = { onClick() })
            configured -> StatusBadge(
                text = stringResource(R.string.profile_badge),
                tone = StatusTone.OK,
                icon = Icons.Outlined.CheckCircle
            )
            ignoreEco -> StatusBadge(
                text = stringResource(R.string.eco_ignored),
                tone = StatusTone.WARN,
                icon = Icons.Outlined.BatterySaver
            )
        }
    }
}

@Composable
fun AppIcon(context: Context, packageName: String) {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { AppCatalog.icon(context, packageName) }
    }
    IconSurface(icon = Icons.Outlined.Apps, bitmap = icon, size = 40)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSheet(
    context: Context,
    packageName: String,
    onDismiss: () -> Unit,
    onCustomize: () -> Unit,
    onAdded: () -> Unit
) {
    var template by remember { mutableStateOf(Template.NORMAL) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(context, packageName)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(AppCatalog.label(context, packageName), style = MaterialTheme.typography.titleMedium)
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(stringResource(R.string.template_title), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(template == Template.NORMAL, { template = Template.NORMAL }, label = { Text(stringResource(R.string.template_default)) })
                FilterChip(template == Template.ECO, { template = Template.ECO }, label = { Text(stringResource(R.string.template_eco)) })
                FilterChip(template == Template.CUSTOM, { template = Template.CUSTOM }, label = { Text(stringResource(R.string.template_custom)) })
            }
            Text(
                text = when (template) {
                    Template.NORMAL -> stringResource(R.string.template_default_hint)
                    Template.ECO -> stringResource(R.string.template_eco_hint)
                    Template.CUSTOM -> stringResource(R.string.template_custom_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                when (template) {
                    Template.NORMAL -> OneHzPreferences.saveAppProfile(
                        context,
                        OneHzPreferences.AppProfile(packageName, OneHzPreferences.normalProfile(context))
                    )
                    Template.ECO -> OneHzPreferences.saveAppProfile(
                        context,
                        OneHzPreferences.AppProfile(packageName, OneHzPreferences.ecoProfile(context))
                    )
                    Template.CUSTOM -> OneHzPreferences.saveAppProfile(
                        context,
                        OneHzPreferences.AppProfile(packageName, OneHzPreferences.normalProfile(context))
                    )
                }
                OneHzEngine.reapply()
                if (template == Template.CUSTOM) {
                    onCustomize()
                } else {
                    onAdded()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(
                    stringResource(
                        if (template == Template.CUSTOM) R.string.app_add_and_customize else R.string.add
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    context: Context,
    packageName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemoved: () -> Unit
) {
    val profile = remember(packageName) { OneHzPreferences.appProfile(context, packageName) }
    var ignoreEco by remember { mutableStateOf(profile.ignoreEco) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(context, packageName)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(AppCatalog.label(context, packageName), style = MaterialTheme.typography.titleMedium)
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = profile.profile?.let { summary(it) } ?: stringResource(R.string.profile_empty),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ignoreEco, onCheckedChange = { checked ->
                    ignoreEco = checked
                    OneHzPreferences.saveAppProfile(context, profile.copy(ignoreEco = checked))
                    OneHzEngine.reapply()
                })
                Text(stringResource(R.string.ignore_eco_label))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text(stringResource(R.string.profile_edit)) }
                TextButton(onClick = { confirmRemove = true }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.remove_app_title),
            body = stringResource(R.string.remove_app_body, AppCatalog.label(context, packageName)),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                OneHzPreferences.clearAppProfile(context, packageName)
                OneHzEngine.reapply()
                onRemoved()
            },
            onDismiss = { confirmRemove = false }
        )
    }
}

@Composable
private fun summary(profile: FrequencyProfile): String {
    if (profile.steps.isEmpty()) return stringResource(R.string.profile_empty)
    if (profile.isFixed) return stringResource(R.string.profile_fixed, profile.steps[0].hz)
    return profile.steps.joinToString(" → ") { "${it.hz}" } + " Hz"
}
