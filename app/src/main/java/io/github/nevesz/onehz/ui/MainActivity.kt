package io.github.nevesz.onehz.ui

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.graphics.drawable.toDrawable
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.LanguageManager
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.SamsungDisplayRates
import io.github.nevesz.onehz.core.ThemeMode
import io.github.nevesz.onehz.core.apps.AppCatalog
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.ui.about.AboutScreen
import io.github.nevesz.onehz.ui.apps.AppsScreen
import io.github.nevesz.onehz.ui.diagnostics.DiagnosticsScreen
import io.github.nevesz.onehz.ui.home.HomeScreen
import io.github.nevesz.onehz.ui.profile.ProfileEditorScreen
import io.github.nevesz.onehz.ui.settings.SettingsScreen
import io.github.nevesz.onehz.ui.setup.SetupScreen
import io.github.nevesz.onehz.ui.theme.OneHZTheme
import io.github.nevesz.onehz.ui.theme.OneHzAmoledBlack
import io.github.nevesz.onehz.ui.theme.OneHzBg
import io.github.nevesz.onehz.ui.theme.OneHzDarkBg

private sealed interface EditorTarget {
    data object Normal : EditorTarget
    data object Eco : EditorTarget
    data class App(val packageName: String) : EditorTarget
}

private fun editorTarget(key: String?): EditorTarget? = when {
    key == "normal" -> EditorTarget.Normal
    key == "eco" -> EditorTarget.Eco
    key != null && key.startsWith("app:") -> EditorTarget.App(key.removePrefix("app:"))
    else -> null
}

class MainActivity : AppCompatActivity() {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneHzShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var setupDone by remember { mutableStateOf(OneHzPreferences.setupCompleted(context)) }
    var showSetup by rememberSaveable { mutableStateOf(!setupDone) }
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    var diagnosticsEnabled by remember { mutableStateOf(OneHzPreferences.diagnosticsEnabled(context)) }
    val editor = editorTarget(editorKey)
    var dataVersion by remember { mutableIntStateOf(0) }
    val rates = remember { SamsungDisplayRates.main(context).ifEmpty { listOf(60, 96, 120) } }
    val diagnosticsUnlockedMessage = stringResource(R.string.diagnostics_unlocked)

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

    when (val target = editor) {
        EditorTarget.Normal -> {
            ProfileEditorScreen(
                title = stringResource(R.string.profile_normal_title),
                initial = OneHzPreferences.normalProfile(context),
                defaultProfile = OneHzPreferences.defaultNormalProfile(context),
                rates = rates,
                enabled = OneHzPreferences.normalEnabled(context),
                onEnabledChange = { on ->
                    OneHzPreferences.setNormalEnabled(context, on)
                    OneHzEngine.reapply()
                },
                onSave = { profile ->
                    OneHzPreferences.saveNormalProfile(context, profile)
                    OneHzEngine.reapply()
                    dataVersion++
                    editorKey = null
                },
                onBack = { editorKey = null }
            )
            return
        }

        EditorTarget.Eco -> {
            ProfileEditorScreen(
                title = stringResource(R.string.profile_eco_title),
                initial = OneHzPreferences.ecoProfile(context),
                defaultProfile = OneHzPreferences.defaultEcoProfile(context),
                rates = rates,
                enabled = OneHzPreferences.ecoEnabled(context),
                onEnabledChange = { on ->
                    OneHzPreferences.setEcoEnabled(context, on)
                    OneHzEngine.reapply()
                },
                copyFrom = OneHzPreferences.normalProfile(context),
                copyLabel = stringResource(R.string.profile_copy_normal),
                onSave = { profile ->
                    OneHzPreferences.saveEcoProfile(context, profile)
                    OneHzEngine.reapply()
                    dataVersion++
                    editorKey = null
                },
                onBack = { editorKey = null }
            )
            return
        }

        is EditorTarget.App -> {
            val current = OneHzPreferences.appProfile(context, target.packageName)
            ProfileEditorScreen(
                title = AppCatalog.label(context, target.packageName),
                initial = current.profile ?: OneHzPreferences.normalProfile(context),
                defaultProfile = OneHzPreferences.normalProfile(context),
                rates = rates,
                onSave = { profile ->
                    OneHzPreferences.saveAppProfile(
                        context,
                        OneHzPreferences.AppProfile(target.packageName, profile, current.ignoreEco)
                    )
                    OneHzEngine.reapply()
                    dataVersion++
                    editorKey = null
                },
                onBack = { editorKey = null },
                onRemove = {
                    OneHzPreferences.clearAppProfile(context, target.packageName)
                    OneHzEngine.reapply()
                    dataVersion++
                    editorKey = null
                }
            )
            return
        }

        null -> Unit
    }

    val page = settingsPage
    if (page != null) {
        BackHandler { settingsPage = null }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (page == "about") stringResource(R.string.about_title)
                            else stringResource(R.string.settings_diagnostics)
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
                    onEditNormal = { editorKey = "normal" },
                    onEditEco = { editorKey = "eco" }
                )
                1 -> AppsScreen(
                    context = context,
                    rates = rates,
                    dataVersion = dataVersion,
                    onEditApp = { editorKey = "app:$it" }
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
                    onOpenDiagnostics = { settingsPage = "diagnostics" }
                )
            }
        }
    }

    if (showLanguage) {
        LanguageDialog(onDismiss = { showLanguage = false })
    }
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
