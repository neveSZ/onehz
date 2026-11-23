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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.apps.AppCatalog
import io.github.nevesz.onehz.core.apps.InstalledApp
import io.github.nevesz.onehz.core.profile.AppAssignment
import io.github.nevesz.onehz.core.profile.AppTarget
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileKind
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.EmptyState
import io.github.nevesz.onehz.ui.components.IconSurface
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.components.rememberPartialSheetState
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.profile.profileLabel
import io.github.nevesz.onehz.ui.profile.profileSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AppFilter { ALL, SUGGESTED, CONFIGURED, SYSTEM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    context: Context,
    rates: List<Int>,
    dataVersion: Int,
    onEditProfile: (String) -> Unit
) {
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    var filterName by rememberSaveable { mutableStateOf(AppFilter.ALL.name) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    var addPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var editPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val filter = AppFilter.valueOf(filterName)

    val profileState = remember(refreshTick, dataVersion, rates) {
        ProfileStore.state(context, rates)
    }
    val assignments = profileState.assignments.associateBy { it.packageName }
    val configured = assignments.keys

    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(filter, refreshTick, dataVersion) {
        loading = true
        installed = withContext(Dispatchers.IO) {
            when (filter) {
                AppFilter.SYSTEM -> AppCatalog.load(context, includeSystem = true).filter { it.system }
                AppFilter.ALL -> AppCatalog.load(context, includeSystem = false)
                AppFilter.SUGGESTED -> AppCatalog.load(context, includeSystem = false).filter {
                    it.packageName !in configured && (
                        it.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                            it.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO ||
                            it.category == android.content.pm.ApplicationInfo.CATEGORY_IMAGE
                        )
                }
                AppFilter.CONFIGURED -> configured
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
                state = queryState,
                label = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = {
                    Icon(AppIcons.search(), contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { queryState.clearText() }) {
                            Icon(
                                AppIcons.clear(),
                                contentDescription = stringResource(R.string.clear_search)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    filter == AppFilter.ALL,
                    { filterName = AppFilter.ALL.name },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    filter == AppFilter.CONFIGURED,
                    { filterName = AppFilter.CONFIGURED.name },
                    label = { Text(stringResource(R.string.filter_configured)) }
                )
                FilterChip(
                    filter == AppFilter.SUGGESTED,
                    { filterName = AppFilter.SUGGESTED.name },
                    label = { Text(stringResource(R.string.filter_suggested)) }
                )
                FilterChip(
                    filter == AppFilter.SYSTEM,
                    { filterName = AppFilter.SYSTEM.name },
                    label = { Text(stringResource(R.string.filter_system)) }
                )
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
                            Icon(AppIcons.deleteSweep(), contentDescription = null)
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
                            when (filter) {
                                AppFilter.CONFIGURED -> R.string.configured_count
                                AppFilter.SUGGESTED -> R.string.suggested_count
                                AppFilter.SYSTEM -> R.string.system_count
                                AppFilter.ALL -> R.string.installed_count
                            },
                            filtered.size
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (configured.isNotEmpty() && !selectionMode) {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(
                                AppIcons.deleteSweep(),
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
                    icon = AppIcons.searchOff(),
                    title = stringResource(
                        when (filter) {
                            AppFilter.CONFIGURED -> R.string.empty_configured_title
                            AppFilter.SUGGESTED -> R.string.empty_suggested_title
                            AppFilter.SYSTEM -> R.string.empty_system_title
                            AppFilter.ALL -> R.string.empty_apps
                        }
                    ),
                    body = stringResource(
                        when (filter) {
                            AppFilter.CONFIGURED -> R.string.empty_configured_body
                            AppFilter.SUGGESTED -> R.string.empty_suggested_body
                            AppFilter.SYSTEM -> R.string.empty_system_body
                            AppFilter.ALL -> R.string.empty_apps_body
                        }
                    )
                )
            }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                val assignment = assignments[app.packageName]
                val profile = assignment?.let { profileState.profile(it.profileId) }
                AppRow(
                    context = context,
                    app = app,
                    configured = assignment != null,
                    profile = profile,
                    target = assignment?.target,
                    ignoreEco = assignment?.ignoreEco == true,
                    selectionMode = selectionMode,
                    checked = app.packageName in selected,
                    onClick = {
                        if (selectionMode) {
                            if (assignment != null) {
                                selected = if (app.packageName in selected) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            }
                        } else if (assignment != null) {
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
            profiles = profileState.profiles,
            rates = rates,
            activeProfileId = profileState.activeProfileId,
            onDismiss = { addPkg = null },
            onCustomize = { profileId ->
                addPkg = null
                refreshTick++
                onEditProfile(profileId)
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
            profiles = profileState.profiles,
            rates = rates,
            assignment = assignments[pkg],
            onDismiss = { editPkg = null },
            onEditProfile = { profileId ->
                editPkg = null
                onEditProfile(profileId)
            },
            onChanged = { refreshTick++ },
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
                selected.forEach { ProfileStore.removeAssignment(context, it) }
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
    profile: DeviceProfile?,
    target: AppTarget?,
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
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            when {
                configured && target is AppTarget.Profile && profile != null -> Text(
                    text = stringResource(R.string.profile_app_assigned, profileLabel(profile)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                configured && target is AppTarget.Fixed -> Text(
                    text = stringResource(R.string.app_target_fixed_value, target.hz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                configured && target is AppTarget.Ignore -> Text(
                    text = stringResource(R.string.app_target_ignore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            selectionMode && configured -> Checkbox(checked = checked, onCheckedChange = { onClick() })
            configured && ignoreEco -> StatusBadge(
                text = stringResource(R.string.eco_ignored),
                tone = StatusTone.WARN,
                icon = AppIcons.batterySaver()
            )
        }
    }
}

@Composable
fun AppIcon(context: Context, packageName: String) {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { AppCatalog.icon(context, packageName) }
    }
    IconSurface(icon = AppIcons.apps(), bitmap = icon, size = 40)
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSheet(
    context: Context,
    packageName: String,
    profiles: List<DeviceProfile>,
    rates: List<Int>,
    activeProfileId: String,
    onDismiss: () -> Unit,
    onCustomize: (String) -> Unit,
    onAdded: () -> Unit
) {
    var target by remember { mutableStateOf<AppTarget>(AppTarget.Profile(activeProfileId)) }
    val selectedId = (target as? AppTarget.Profile)?.profileId
    val selectedProfile = profiles.firstOrNull { it.id == selectedId }
    val defaultFixed = rates.firstOrNull { it == 60 } ?: rates.firstOrNull() ?: 60
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberPartialSheetState()) {
        Column(
            Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
            Text(stringResource(R.string.app_target_title), style = MaterialTheme.typography.titleSmall)
            TargetChips(
                target = target,
                onProfile = { target = AppTarget.Profile(activeProfileId) },
                onFixed = { target = AppTarget.Fixed(defaultFixed) },
                onIgnore = { target = AppTarget.Ignore }
            )
            when (target) {
                is AppTarget.Profile -> {
                    Text(stringResource(R.string.profile_app_choose), style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileChoiceRow(
                                profile = profile,
                                selected = profile.id == selectedId,
                                onClick = { target = AppTarget.Profile(profile.id) }
                            )
                        }
                    }
                    selectedProfile?.let { profile ->
                        Text(
                            text = stringResource(R.string.profile_reference_hint, profileLabel(profile)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is AppTarget.Fixed -> {
                    Text(
                        text = stringResource(R.string.app_target_fixed_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(rates.sortedDescending()) { hz ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = target == AppTarget.Fixed(hz),
                                        role = Role.RadioButton,
                                        onClick = { target = AppTarget.Fixed(hz) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = target == AppTarget.Fixed(hz), onClick = null)
                                Text(
                                    text = stringResource(R.string.app_target_fixed_value, hz),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
                AppTarget.Ignore -> {
                    Text(
                        text = stringResource(R.string.app_target_ignore_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = {
                    ProfileStore.saveAssignment(context, AppAssignment(packageName, target))
                    OneHzEngine.reapply()
                    onAdded()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(AppIcons.add(), contentDescription = null)
                Text(stringResource(R.string.profile_assign), Modifier.padding(start = 6.dp))
            }
            if (target is AppTarget.Profile) {
                OutlinedButton(
                    onClick = {
                        val created = ProfileStore.createCustom(
                            context,
                            AppCatalog.label(context, packageName),
                            selectedId
                        )
                        if (created != null) {
                            ProfileStore.saveAssignment(
                                context,
                                AppAssignment(packageName, AppTarget.Profile(created.id))
                            )
                            OneHzEngine.reapply()
                            onCustomize(created.id)
                        } else {
                            Toast.makeText(
                                context,
                                R.string.profile_limit_reached,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_app_create))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(
    context: Context,
    packageName: String,
    profiles: List<DeviceProfile>,
    rates: List<Int>,
    assignment: AppAssignment?,
    onDismiss: () -> Unit,
    onEditProfile: (String) -> Unit,
    onChanged: () -> Unit,
    onRemoved: () -> Unit
) {
    if (assignment == null) return
    var target by remember(assignment.target) { mutableStateOf(assignment.target) }
    var ignoreEco by remember(assignment.ignoreEco) { mutableStateOf(assignment.ignoreEco) }
    var confirmRemove by remember { mutableStateOf(false) }
    val selectedId = (target as? AppTarget.Profile)?.profileId
    val selectedProfile = profiles.firstOrNull { it.id == selectedId }
    val canIgnoreEco = target !is AppTarget.Ignore &&
        selectedProfile?.kind != ProfileKind.ECONOMY
    val defaultFixed = rates.firstOrNull { it == 60 } ?: rates.firstOrNull() ?: 60

    fun save(next: AppTarget, ignore: Boolean = ignoreEco) {
        val effectiveIgnore = if (next is AppTarget.Ignore) {
            false
        } else if (profiles.firstOrNull { it.id == (next as? AppTarget.Profile)?.profileId }
                ?.kind == ProfileKind.ECONOMY
        ) {
            false
        } else {
            ignore
        }
        target = next
        ignoreEco = effectiveIgnore
        ProfileStore.saveAssignment(
            context,
            AppAssignment(packageName, next, effectiveIgnore)
        )
        OneHzEngine.reapply()
        onChanged()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberPartialSheetState()) {
        Column(
            Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
            Text(stringResource(R.string.app_target_title), style = MaterialTheme.typography.titleSmall)
            TargetChips(
                target = target,
                onProfile = { save(AppTarget.Profile(profiles.first().id)) },
                onFixed = { save(AppTarget.Fixed(defaultFixed)) },
                onIgnore = { save(AppTarget.Ignore) }
            )
            when (target) {
                is AppTarget.Profile -> {
                    Text(stringResource(R.string.profile_app_choose), style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileChoiceRow(
                                profile = profile,
                                selected = profile.id == selectedId,
                                onClick = { save(AppTarget.Profile(profile.id)) }
                            )
                        }
                    }
                    selectedProfile?.let { profile ->
                        Text(
                            text = stringResource(R.string.profile_reference_hint, profileLabel(profile)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is AppTarget.Fixed -> {
                    Text(
                        text = stringResource(R.string.app_target_fixed_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(rates.sortedDescending()) { hz ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = target == AppTarget.Fixed(hz),
                                        role = Role.RadioButton,
                                        onClick = { save(AppTarget.Fixed(hz)) }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = target == AppTarget.Fixed(hz), onClick = null)
                                Text(
                                    text = stringResource(R.string.app_target_fixed_value, hz),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
                AppTarget.Ignore -> {
                    Text(
                        text = stringResource(R.string.app_target_ignore_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ToggleRow(
                title = stringResource(R.string.ignore_eco_label),
                descriptionOn = stringResource(R.string.ignore_eco_desc_on),
                descriptionOff = stringResource(R.string.ignore_eco_desc_off),
                checked = ignoreEco && canIgnoreEco,
                enabled = canIgnoreEco,
                onCheckedChange = {
                    ignoreEco = it
                    save(target, it)
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target is AppTarget.Profile) {
                    Button(onClick = { onEditProfile(selectedId!!) }) {
                        Text(stringResource(R.string.profile_edit))
                    }
                }
                TextButton(onClick = { confirmRemove = true }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.profile_app_remove_title),
            body = stringResource(
                R.string.profile_app_remove_body,
                AppCatalog.label(context, packageName)
            ),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                ProfileStore.removeAssignment(context, packageName)
                OneHzEngine.reapply()
                onRemoved()
                onDismiss()
            },
            onDismiss = { confirmRemove = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetChips(
    target: AppTarget,
    onProfile: () -> Unit,
    onFixed: () -> Unit,
    onIgnore: () -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = target is AppTarget.Profile,
            onClick = onProfile,
            label = { Text(stringResource(R.string.app_target_profile)) }
        )
        FilterChip(
            selected = target is AppTarget.Fixed,
            onClick = onFixed,
            label = { Text(stringResource(R.string.app_target_fixed)) }
        )
        FilterChip(
            selected = target is AppTarget.Ignore,
            onClick = onIgnore,
            label = { Text(stringResource(R.string.app_target_ignore)) }
        )
    }
}

@Composable
private fun ProfileChoiceRow(
    profile: DeviceProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(profileLabel(profile), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = profileSummary(profile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
