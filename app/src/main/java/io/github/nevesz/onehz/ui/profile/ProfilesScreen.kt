package io.github.nevesz.onehz.ui.profile

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileCodec
import io.github.nevesz.onehz.core.profile.ProfileKind
import io.github.nevesz.onehz.core.profile.ProfileState
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.icons.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    context: Context,
    rates: List<Int>,
    dataVersion: Int,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onChanged: () -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    val state = remember(dataVersion, refresh, rates) {
        ProfileStore.state(context, rates)
    }
    var showCreate by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DeviceProfile?>(null) }
    var resetTarget by remember { mutableStateOf<DeviceProfile?>(null) }
    var showMigration by remember {
        mutableStateOf(OneHzPreferences.profilesMigrationNoticePending(context))
    }

    fun changed() {
        refresh++
        onChanged()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_manage)) },
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
            if (showMigration) {
                item {
                    MigrationCard(
                        onDismiss = {
                            showMigration = false
                            OneHzPreferences.setProfilesMigrationNoticePending(context, false)
                        }
                    )
                }
            }
            item {
                OneHzCard {
                    ToggleRow(
                        title = stringResource(R.string.profile_auto_economy_title),
                        description = stringResource(R.string.profile_auto_economy_desc),
                        checked = state.autoEconomyOnBatterySaver,
                        onCheckedChange = { enabled ->
                            ProfileStore.setAutoEconomy(context, enabled)
                            changed()
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profile_auto_economy_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == state.activeProfileId,
                    usageCount = state.appsUsing(profile.id).size,
                    onSelect = {
                        if (profile.id != state.activeProfileId) {
                            ProfileStore.select(context, profile.id)
                            OneHzEngine.reapply()
                            changed()
                        }
                    },
                    onEdit = { onEdit(profile.id) },
                    onReset = if (profile.isBuiltIn) {
                        { resetTarget = profile }
                    } else {
                        null
                    },
                    onDelete = if (profile.kind == ProfileKind.CUSTOM) {
                        { deleteTarget = profile }
                    } else {
                        null
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        createError = false
                        showCreate = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.add(), contentDescription = null)
                    Text(stringResource(R.string.profile_new), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showCreate) {
        CreateProfileDialog(
            template = state.activeProfile(),
            error = createError,
            onDismiss = { showCreate = false },
            onCreate = { name ->
                val created = ProfileStore.createCustom(
                    context,
                    name,
                    state.activeProfileId
                )
                if (created != null) {
                    showCreate = false
                    createError = false
                    changed()
                    onEdit(created.id)
                } else {
                    createError = true
                }
            }
        )
    }

    deleteTarget?.let { profile ->
        val usage = state.appsUsing(profile.id).size
        val fallback = state.balancedProfile()
        val fallbackName = fallback?.let { profileLabel(it) } ?: stringResource(R.string.profile_balanced_title)
        val body = if (usage > 0) {
            pluralStringResource(R.plurals.profile_delete_body, usage, usage, fallbackName)
        } else {
            stringResource(R.string.profile_delete_body_unused)
        }
        val activeNote = if (profile.id == state.activeProfileId) {
            stringResource(R.string.profile_delete_active_note, fallbackName)
        } else {
            null
        }
        ConfirmDialog(
            title = stringResource(R.string.profile_delete_title),
            body = listOfNotNull(body, activeNote).joinToString(" "),
            confirmLabel = stringResource(R.string.profile_delete_confirm),
            onConfirm = {
                ProfileStore.deleteProfile(context, profile.id, fallback?.id)
                OneHzEngine.reapply()
                changed()
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    resetTarget?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.profile_reset_title),
            body = stringResource(R.string.profile_reset_body),
            confirmLabel = stringResource(R.string.profile_reset_confirm),
            onConfirm = {
                ProfileStore.resetToDefault(context, profile.id)
                OneHzEngine.reapply()
                changed()
                resetTarget = null
            },
            onDismiss = { resetTarget = null }
        )
    }
}

@Composable
private fun MigrationCard(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OneHzCard {
        Text(
            text = stringResource(R.string.profile_migration_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.profile_migration_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                uriHandler.openUri("https://github.com/neveSZ/onehz/blob/main/docs/FAQ.md#will-updating-change-my-profiles")
            }) {
                Text(stringResource(R.string.profile_migration_more))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_migration_dismiss))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileRow(
    profile: DeviceProfile,
    active: Boolean,
    usageCount: Int,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onReset: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    OneHzCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = active, role = Role.RadioButton, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = active, onClick = null)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profileLabel(profile),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (active) {
                        StatusBadge(
                            text = stringResource(R.string.profile_active),
                            tone = StatusTone.OK
                        )
                    }
                }
                Text(
                    text = profileSummary(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.profile_video_summary, profile.video.minHz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profile.isBuiltIn) {
                    Text(
                        text = if (profile.customized) {
                            stringResource(R.string.profile_modified)
                        } else {
                            stringResource(R.string.profile_default)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (usageCount > 0) {
                        pluralStringResource(
                            R.plurals.profile_used_by_apps,
                            usageCount,
                            usageCount
                        )
                    } else {
                        stringResource(R.string.profile_no_apps)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = onEdit) {
                Icon(AppIcons.tune(), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.profile_edit), Modifier.padding(start = 6.dp))
            }
            onReset?.let {
                TextButton(onClick = it) {
                    Text(stringResource(R.string.profile_restore_default))
                }
            }
            onDelete?.let {
                TextButton(onClick = it) {
                    Icon(
                        AppIcons.delete(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.profile_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    template: DeviceProfile?,
    error: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val nameState = rememberTextFieldState()
    val name = nameState.text.toString()
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_create_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.profile_create_body,
                        template?.let { profileLabel(it) }
                            ?: stringResource(R.string.profile_balanced_title)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    state = nameState,
                    inputTransformation = InputTransformation.maxLengthTrim(ProfileCodec.MAX_NAME_LENGTH),
                    label = { Text(stringResource(R.string.profile_name)) },
                    placeholder = { Text(stringResource(R.string.profile_name_hint)) },
                    isError = trimmed.isEmpty(),
                    lineLimits = TextFieldLineLimits.SingleLine
                )
                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.profile_limit_reached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(trimmed) },
                enabled = trimmed.isNotEmpty()
            ) {
                Text(stringResource(R.string.profile_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
