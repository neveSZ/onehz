package io.github.nevesz.onehz.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayRates
import io.github.nevesz.onehz.core.LanguageManager
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.ThemeMode
import io.github.nevesz.onehz.core.backup.BackupManager
import io.github.nevesz.onehz.core.backup.ImportResult
import io.github.nevesz.onehz.core.profile.ProfileDefaults
import io.github.nevesz.onehz.core.profile.ProfileKind
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.engine.DozeController
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.about.AboutScreen
import io.github.nevesz.onehz.ui.apps.AppsScreen
import io.github.nevesz.onehz.ui.components.ConfirmDialog
import io.github.nevesz.onehz.ui.diagnostics.DiagnosticsScreen
import io.github.nevesz.onehz.ui.home.AodScreen
import io.github.nevesz.onehz.ui.home.HomeScreen
import io.github.nevesz.onehz.ui.home.SleepScreen
import io.github.nevesz.onehz.ui.profile.ProfileEditorScreen
import io.github.nevesz.onehz.ui.profile.ProfilesScreen
import io.github.nevesz.onehz.ui.settings.BackupScreen
import io.github.nevesz.onehz.ui.settings.SettingsScreen
import io.github.nevesz.onehz.ui.setup.SetupScreen
import io.github.nevesz.onehz.ui.theme.OneHZTheme
import io.github.nevesz.onehz.ui.theme.OneHzAmoledBlack
import io.github.nevesz.onehz.ui.theme.OneHzBg
import io.github.nevesz.onehz.ui.theme.OneHzDarkBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onStart() {
        super.onStart()
        UpdateCenter.maybeCheckOnOpen(this)
    }

    override fun onResume() {
        super.onResume()
        OneHzEngine.refreshSystemState()
        OneHzEngine.refreshForegroundPackage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialMode = OneHzPreferences.themeMode(this)
        applyNightMode(initialMode)
        super.onCreate(savedInstanceState)
        applyWindowColors(initialMode)

        setContent {
            var themeMode by remember { mutableStateOf(initialMode) }
            OneHZTheme(mode = themeMode) {
                ApplySystemBars(themeMode)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OneHzShell(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            OneHzPreferences.setThemeMode(this, mode)
                            applyNightMode(mode)
                            themeMode = mode
                            applyWindowColors(mode)
                        }
                    )
                }
            }
        }
    }

    private fun applyNightMode(mode: ThemeMode) {
        val target = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK, ThemeMode.AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    private fun applyWindowColors(mode: ThemeMode) {
        applyWindowAppearance(window, mode, systemDark())
    }
}

@Suppress("DEPRECATION")
private fun applyWindowAppearance(window: android.view.Window, mode: ThemeMode, systemDark: Boolean) {
    val dark = mode.isDark(systemDark)
    val color = when {
        mode == ThemeMode.AMOLED -> OneHzAmoledBlack
        dark -> OneHzDarkBg
        else -> OneHzBg
    }
    window.setBackgroundDrawable(color.toArgb().toDrawable())
    window.statusBarColor = color.toArgb()
    window.navigationBarColor = color.toArgb()
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

@Composable
private fun ApplySystemBars(mode: ThemeMode) {
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        applyWindowAppearance(window, mode, systemDark)
    }
}

private fun editorProfileId(key: String?): String? =
    key?.takeIf { it.startsWith("profile:") }?.removePrefix("profile:")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneHzShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var setupDone by remember { mutableStateOf(OneHzPreferences.setupCompleted(context)) }
    var showSetup by rememberSaveable { mutableStateOf(!setupDone) }
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    var returnToProfiles by rememberSaveable { mutableStateOf(false) }
    var homePage by rememberSaveable { mutableStateOf<String?>(null) }
    var diagnosticsEnabled by remember { mutableStateOf(OneHzPreferences.diagnosticsEnabled(context)) }
    var dataVersion by remember { mutableIntStateOf(0) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    val rates = remember { DisplayRates.main(context).ifEmpty { ProfileDefaults.FALLBACK_RATES } }
    val diagnosticsUnlockedMessage = stringResource(R.string.diagnostics_unlocked)
    val exportSuccessMessage = stringResource(R.string.backup_export_success)
    val exportFailedMessage = stringResource(R.string.backup_export_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    BackupManager.export(context, uri, rates)
                }
                Toast.makeText(
                    context,
                    if (ok) exportSuccessMessage else exportFailedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            body = stringResource(R.string.backup_import_confirm_body) + " " +
                stringResource(R.string.backup_import_master_note),
            confirmLabel = stringResource(R.string.backup_import),
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        BackupManager.import(context, uri, rates)
                    }
                    importResult = result
                    dataVersion++
                    if (OneHzPreferences.isEnabled(context)) {
                        OneHzEngine.start(context)
                        OneHzEngine.reapply()
                    } else {
                        OneHzEngine.reapply()
                    }
                    DozeController.onPrefsChanged()
                }
            },
            onDismiss = { pendingImport = null }
        )
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = {
                Text(
                    stringResource(
                        if (result.success) R.string.backup_import_success_title
                        else R.string.backup_import_error_title
                    )
                )
            },
            text = { Text(importResultMessage(context, result)) },
            confirmButton = {
                TextButton(onClick = { importResult = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    BackHandler(enabled = showSetup && setupDone) { showSetup = false }

    if (showSetup) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { BrandTitle() },
                    navigationIcon = {
                        if (setupDone) {
                            IconButton(onClick = { showSetup = false }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                SetupScreen(context) {
                    setupDone = true
                    showSetup = false
                    diagnosticsEnabled = OneHzPreferences.diagnosticsEnabled(context)
                    dataVersion++
                }
            }
        }
        return
    }

    val editingId = editorProfileId(editorKey)
    if (editingId != null) {
        val profile = ProfileStore.profile(context, editingId)
        if (profile == null) {
            LaunchedEffect(editingId) { editorKey = null }
            return
        }
        val defaultProfile = if (profile.isBuiltIn) {
            ProfileDefaults.forKind(profile.kind, rates)
        } else {
            null
        }
        ProfileEditorScreen(
            initial = profile,
            defaultProfile = defaultProfile,
            rates = rates,
            usageCount = ProfileStore.appsUsing(context, profile.id).size,
            onSave = { updated ->
                ProfileStore.updateProfile(context, updated)
                OneHzEngine.reapply()
                dataVersion++
                editorKey = null
                if (returnToProfiles) {
                    showProfiles = true
                    returnToProfiles = false
                }
            },
            onBack = {
                editorKey = null
                if (returnToProfiles) {
                    showProfiles = true
                    returnToProfiles = false
                }
            },
            onDelete = if (profile.kind == ProfileKind.CUSTOM) {
                {
                    ProfileStore.deleteProfile(context, profile.id)
                    OneHzEngine.reapply()
                    dataVersion++
                    editorKey = null
                    if (returnToProfiles) {
                        showProfiles = true
                        returnToProfiles = false
                    }
                }
            } else {
                null
            }
        )
        return
    }

    if (showProfiles) {
        ProfilesScreen(
            context = context,
            rates = rates,
            dataVersion = dataVersion,
            onBack = { showProfiles = false },
            onEdit = { profileId ->
                showProfiles = false
                returnToProfiles = true
                editorKey = "profile:$profileId"
            },
            onChanged = { dataVersion++ }
        )
        return
    }

    when (homePage) {
        "aod" -> {
            AodScreen(
                context = context,
                oneHzEnabled = OneHzPreferences.isEnabled(context),
                dataVersion = dataVersion,
                onBack = { homePage = null }
            )
            return
        }
        "sleep" -> {
            SleepScreen(
                context = context,
                masterEnabled = OneHzPreferences.isEnabled(context),
                dataVersion = dataVersion,
                onBack = { homePage = null }
            )
            return
        }
    }

    val page = settingsPage
    if (page != null) {
        BackHandler { settingsPage = null }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (page) {
                                "about" -> stringResource(R.string.about_title)
                                "backup" -> stringResource(R.string.settings_backup)
                                else -> stringResource(R.string.settings_diagnostics)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { settingsPage = null }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                when (page) {
                    "about" -> AboutScreen(
                        diagnosticsEnabled = diagnosticsEnabled,
                        onUnlockDiagnostics = {
                            OneHzPreferences.setDiagnosticsEnabled(context, true)
                            diagnosticsEnabled = true
                            Toast.makeText(
                                context,
                                diagnosticsUnlockedMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    "diagnostics" -> DiagnosticsScreen()
                    "backup" -> BackupScreen(
                        onExport = { exportLauncher.launch(backupFileName()) },
                        onImport = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream")
                            )
                        }
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (tab) {
                        0 -> BrandTitle()
                        1 -> Text(stringResource(R.string.tab_apps))
                        else -> Text(stringResource(R.string.tab_settings))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_apps)) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    context = context,
                    rates = rates,
                    dataVersion = dataVersion,
                    onEditProfile = { profileId ->
                        returnToProfiles = false
                        editorKey = "profile:$profileId"
                    },
                    onManageProfiles = { showProfiles = true },
                    onOpenAod = { homePage = "aod" },
                    onOpenSleep = { homePage = "sleep" }
                )
                1 -> AppsScreen(
                    context = context,
                    rates = rates,
                    dataVersion = dataVersion,
                    onEditProfile = { profileId ->
                        returnToProfiles = false
                        editorKey = "profile:$profileId"
                    }
                )
                else -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    diagnosticsEnabled = diagnosticsEnabled,
                    onDiagnosticsEnabledChange = { enabled ->
                        OneHzPreferences.setDiagnosticsEnabled(context, enabled)
                        diagnosticsEnabled = enabled
                    },
                    onOpenLanguage = { showLanguage = true },
                    onOpenSetup = { showSetup = true },
                    onOpenAbout = { settingsPage = "about" },
                    onOpenDiagnostics = { settingsPage = "diagnostics" },
                    onOpenBackup = { settingsPage = "backup" }
                )
            }
        }
    }

    if (showLanguage) {
        LanguageDialog(onDismiss = { showLanguage = false })
    }
}

private fun importResultMessage(context: android.content.Context, result: ImportResult): String {
    if (!result.success) {
        return when (result.error) {
            "invalid-backup" -> context.getString(R.string.backup_error_invalid)
            else -> context.getString(R.string.backup_error_unreadable)
        }
    }
    return buildList {
        add(
            context.getString(
                R.string.backup_import_success,
                result.profiles,
                result.assignments
            )
        )
        if (result.adapted > 0) {
            add(context.getString(R.string.backup_import_adapted, result.adapted))
        }
        if (result.missingAssignments > 0) {
            add(context.getString(R.string.backup_import_missing, result.missingAssignments))
        }
        if (result.sleepIncluded) {
            add(context.getString(R.string.backup_import_sleep))
        } else {
            add(context.getString(R.string.backup_import_sleep_not_included))
        }
    }.joinToString(" ")
}

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "onehz-backup-$stamp.json"
}

@Composable
private fun BrandTitle() {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_onehz_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp)
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(LanguageManager.current()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    LanguageManager.SYSTEM to stringResource(R.string.language_system),
                    LanguageManager.PT to stringResource(R.string.language_pt),
                    LanguageManager.EN to stringResource(R.string.language_en),
                    LanguageManager.ES to stringResource(R.string.language_es)
                ).forEach { (tag, label) ->
                    TextButton(
                        onClick = {
                            selected = tag
                            LanguageManager.set(tag)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(text = label, modifier = Modifier.weight(1f))
                        if (selected == tag) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
