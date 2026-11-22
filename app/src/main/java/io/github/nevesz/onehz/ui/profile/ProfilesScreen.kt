package io.github.nevesz.onehz.ui.profile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
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
    var deleteTarget by remember { mutableStateOf<DeviceProfile?>(null) }
    var resetTarget by remember { mutableStateOf<DeviceProfile?>(null) }

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
                            Icons.AutoMirrored.Outlined.ArrowBack,
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
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.profile_new), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showCreate) {
        CreateProfileDialog(
            template = state.activeProfile(),
            onDismiss = { showCreate = false },
            onCreate = { name ->
                val created = ProfileStore.createCustom(
                    context,
                    name,
                    state.activeProfileId
                )
                showCreate = false
                if (created != null) {
                    changed()
                    onEdit(created.id)
                }
            }
        )
    }

    deleteTarget?.let { profile ->
        val usage = state.appsUsing(profile.id).size
        val fallback = state.balancedProfile()
        val fallbackName = fallback?.let { profileLabel(it) } ?: stringResource(R.string.profile_balanced_title)
        ConfirmDialog(
            title = stringResource(R.string.profile_delete_title),
            body = if (usage > 0) {
                stringResource(R.string.profile_delete_body, usage, fallbackName)
            } else {
                stringResource(R.string.profile_delete_body_unused)
            },
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = onSelect)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profileLabel(profile),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (active) {
                        Spacer(Modifier.size(8.dp))
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        Icons.Outlined.Delete,
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
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
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
                    value = name,
                    onValueChange = { name = it.take(ProfileCodec.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    placeholder = { Text(stringResource(R.string.profile_name_hint)) },
                    isError = trimmed.isEmpty(),
                    singleLine = true
                )
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
