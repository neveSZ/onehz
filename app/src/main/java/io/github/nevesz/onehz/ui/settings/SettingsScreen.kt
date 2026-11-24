package io.github.nevesz.onehz.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.LanguageManager
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.ThemeMode
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.components.NotificationAccess
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import io.github.nevesz.onehz.ui.components.ToggleRow
import io.github.nevesz.onehz.ui.components.rememberFullHeightSheetState
import io.github.nevesz.onehz.ui.icons.AppIcons
import io.github.nevesz.onehz.ui.tile.TileRequest
import io.github.nevesz.onehz.ui.tile.TileStatus
import io.github.nevesz.onehz.ui.theme.OneHzAmoledBlack
import io.github.nevesz.onehz.ui.theme.OneHzBg
import io.github.nevesz.onehz.ui.theme.OneHzDarkBg
import io.github.nevesz.onehz.ui.theme.OneHzDarkSurface
import io.github.nevesz.onehz.ui.theme.OneHzSurface
import io.github.nevesz.onehz.ui.update.UpdateSettingsCard
import io.github.nevesz.onehz.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSystemChanges: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val vm: SettingsViewModel = viewModel()
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showPinNotificationDialog by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberFullHeightSheetState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    var notificationPinned by remember {
        mutableStateOf(OneHzPreferences.notificationPinned(context))
    }
    var notificationsEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }
    var notificationDetails by remember {
        mutableStateOf(OneHzPreferences.notificationDetails(context))
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = NotificationAccess.isEnabled(context)
        if (!granted) NotificationAccess.openSettings(context)
    }
    var tileAdded by remember { mutableStateOf(OneHzPreferences.tileAdded(context)) }
    val liveTileAdded by TileStatus.added.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { TileStatus.refresh(context) }
    val tileAddedNow = liveTileAdded ?: tileAdded

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tileAdded = OneHzPreferences.tileAdded(context)
                TileStatus.refresh(context)
                notificationsEnabled = NotificationAccess.isEnabled(context)
                notificationDetails = OneHzPreferences.notificationDetails(context)
                val pinned = OneHzPreferences.notificationPinned(context)
                if (!notificationsEnabled && pinned) {
                    OneHzPreferences.setNotificationPinned(context, false)
                    notificationPinned = false
                    OneHzKeepAliveService.pinChanged(context)
                } else {
                    notificationPinned = pinned
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        item {
            SettingsSectionTitle(stringResource(R.string.settings_section_general))
        }
        item {
            OneHzCard {
                SettingRow(
                    icon = AppIcons.palette(),
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(themeMode.labelRes()),
                    onClick = { showAppearance = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = AppIcons.translate(),
                    title = stringResource(R.string.language),
                    subtitle = languageLabel(),
                    onClick = onOpenLanguage
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = AppIcons.rocketLaunch(),
                    title = stringResource(R.string.settings_setup),
                    subtitle = stringResource(R.string.settings_setup_desc),
                    onClick = onOpenSetup
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_quick_controls))
        }
        item {
            OneHzCard {
                SettingRow(
                    icon = AppIcons.notifications(),
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(
                        if (notificationsEnabled) {
                            R.string.settings_notifications_desc_on
                        } else {
                            R.string.settings_notifications_desc_off
                        }
                    ),
                    onClick = {
                        NotificationAccess.request(context) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (tileAddedNow) {
                    SettingRow(
                        icon = AppIcons.checkCircle(),
                        title = stringResource(R.string.settings_tile_added),
                        subtitle = stringResource(R.string.settings_tile_added_desc)
                    )
                } else {
                    SettingRow(
                        icon = AppIcons.add(),
                        title = stringResource(R.string.settings_add_tile),
                        subtitle = stringResource(R.string.settings_add_tile_desc),
                        onClick = { TileRequest.add(context) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    icon = AppIcons.notifications(),
                    title = stringResource(R.string.settings_pin_notification),
                    checked = notificationPinned,
                    enabled = notificationsEnabled,
                    descriptionOn = stringResource(R.string.settings_pin_notification_desc_on),
                    descriptionOff = stringResource(R.string.settings_pin_notification_desc_off),
                    disabledDescription = stringResource(
                        R.string.settings_pin_notification_desc_disabled
                    ),
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showPinNotificationDialog = true
                        } else {
                            OneHzPreferences.setNotificationPinned(context, false)
                            notificationPinned = false
                            OneHzKeepAliveService.pinChanged(context)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    icon = AppIcons.speed(),
                    title = stringResource(R.string.settings_notification_details),
                    checked = notificationDetails,
                    enabled = notificationsEnabled,
                    descriptionOn = stringResource(
                        R.string.settings_notification_details_desc_on
                    ),
                    descriptionOff = stringResource(
                        R.string.settings_notification_details_desc_off
                    ),
                    disabledDescription = stringResource(
                        R.string.settings_notification_details_desc_disabled
                    ),
                    onCheckedChange = { enabled ->
                        OneHzPreferences.setNotificationDetails(context, enabled)
                        notificationDetails = enabled
                        OneHzKeepAliveService.contentChanged(context)
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_automation))
        }
        item {
            OneHzCard {
                SettingRow(
                    icon = AppIcons.bolt(),
                    title = stringResource(R.string.rules_title),
                    subtitle = stringResource(R.string.rules_short),
                    onClick = onOpenRules
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_data))
        }
        item {
            OneHzCard {
                SettingRow(
                    icon = AppIcons.backup(),
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_desc),
                    onClick = onOpenBackup
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_system))
        }
        item {
            OneHzCard {
                SettingRow(
                    icon = AppIcons.code(),
                    title = stringResource(R.string.system_changes_title),
                    subtitle = stringResource(R.string.system_changes_short),
                    onClick = onOpenSystemChanges
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    icon = AppIcons.bugReport(),
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = stringResource(R.string.settings_diagnostics_desc),
                    onClick = onOpenDiagnostics
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.update_section_title))
        }
        item {
            UpdateSettingsCard(
                state = updateState,
                onEnabledChange = { UpdateCenter.setEnabled(context, it) },
                onIntervalChange = { UpdateCenter.setInterval(context, it) },
                onChannelChange = { UpdateCenter.setChannel(context, it) },
                onCheckNow = { UpdateCenter.checkNow(context) },
                onDismiss = { UpdateCenter.dismiss(context, it) }
            )
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(stringResource(R.string.settings_section_about))
        }
        item {
            OneHzCard {
                SettingRow(
                    icon = AppIcons.info(),
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.settings_about_desc),
                    onClick = onOpenAbout
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_onehz_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showAppearance) {
        ModalBottomSheet(
            onDismissRequest = { showAppearance = false },
            sheetState = sheetState
        ) {
            AppearanceSheet(
                themeMode = themeMode,
                onThemeModeChange = { mode ->
                    onThemeModeChange(mode)
                    showAppearance = false
                }
            )
        }
    }

    if (showPinNotificationDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_pin_notification_dialog_title),
            body = stringResource(R.string.settings_pin_notification_dialog_body),
            confirmLabel = stringResource(R.string.settings_pin_notification_confirm),
            onConfirm = {
                OneHzPreferences.setNotificationPinned(context, true)
                notificationPinned = true
                OneHzKeepAliveService.pinChanged(context)
            },
            onDismiss = { showPinNotificationDialog = false },
            destructive = false
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun AppearanceSheet(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.appearance_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        ThemeMode.entries.forEach { mode ->
            ThemeOption(
                mode = mode,
                selected = mode == themeMode,
                onClick = { onThemeModeChange(mode) }
            )
        }
    }
}

@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeSwatch(mode)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(mode.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun ThemeSwatch(mode: ThemeMode) {
    val shapes = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(shapes)
            .background(
                when (mode) {
                    ThemeMode.LIGHT -> OneHzBg
                    ThemeMode.DARK -> OneHzDarkBg
                    ThemeMode.AMOLED -> OneHzAmoledBlack
                    ThemeMode.SYSTEM -> OneHzBg
                }
            )
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shapes),
        contentAlignment = Alignment.Center
    ) {
        if (mode == ThemeMode.SYSTEM) {
            Row(Modifier.fillMaxWidth().height(54.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().background(OneHzBg))
                Box(Modifier.weight(1f).fillMaxWidth().background(OneHzDarkBg))
            }
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 18.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color(0xFF8FE39A))
            )
        } else {
            val card = when (mode) {
                ThemeMode.AMOLED -> Color(0xFF0A0A0A)
                ThemeMode.DARK -> OneHzDarkSurface
                else -> OneHzSurface
            }
            val accent = when (mode) {
                ThemeMode.AMOLED -> Color(0xFF8FE39A)
                ThemeMode.DARK -> Color(0xFFA5D6A7)
                else -> Color(0xFF2E7D32)
            }
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 22.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(card)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
            }
        }
    }
}

@Composable
private fun languageLabel(): String = when (LanguageManager.current()) {
    LanguageManager.PT -> stringResource(R.string.language_pt)
    LanguageManager.EN -> stringResource(R.string.language_en)
    LanguageManager.ES -> stringResource(R.string.language_es)
    else -> stringResource(R.string.language_system)
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.AMOLED -> R.string.theme_amoled
}

private fun ThemeMode.descriptionRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system_desc
    ThemeMode.LIGHT -> R.string.theme_light_desc
    ThemeMode.DARK -> R.string.theme_dark_desc
    ThemeMode.AMOLED -> R.string.theme_amoled_desc
}
