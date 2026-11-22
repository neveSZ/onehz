package io.github.nevesz.onehz.ui.setup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(context: Context, onDone: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuGateway.state.collectAsState()
    val gate = remember { DisplayGate.evaluate(context) }

    var refresh by remember { mutableIntStateOf(0) }
    var dozeWhitelisted by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refresh) {
        dozeWhitelisted = withContext(Dispatchers.IO) {
            ShizukuGateway.isDozeWhitelisted(context.packageName)
        }
    }

    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_onehz_mark),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.setup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OneHzCard {
                StepHeader(number = 1, title = stringResource(R.string.setup_required))
                Spacer(Modifier.height(10.dp))
                SetupCheckItem(
                    icon = Icons.Outlined.Smartphone,
                    title = stringResource(R.string.setup_compat_title),
                    description = gate.summary,
                    statusText = if (gate.eligible) {
                        stringResource(R.string.setup_granted)
                    } else {
                        stringResource(R.string.status_error)
                    },
                    tone = if (gate.eligible) StatusTone.OK else StatusTone.ERROR
                )
                if (!gate.eligible) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.unsupported_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (gate.eligible) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    SetupCheckItem(
                        icon = Icons.Outlined.Bolt,
                        title = stringResource(R.string.setup_shizuku_title),
                        description = stringResource(R.string.setup_shizuku_desc),
                        statusText = when (shizuku) {
                            ShizukuState.READY -> stringResource(R.string.setup_granted)
                            ShizukuState.PERMISSION_REQUIRED -> stringResource(R.string.status_permission)
                            ShizukuState.NOT_RUNNING -> stringResource(R.string.status_not_running)
                            ShizukuState.SERVICE_UNAVAILABLE -> stringResource(R.string.status_unavailable)
                        },
                        tone = if (shizuku == ShizukuState.READY) StatusTone.OK else StatusTone.WARN
                    ) {
                        if (shizuku != ShizukuState.READY) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { ShizukuGateway.refresh() }) {
                                    Text(stringResource(R.string.reconnect))
                                }
                                if (shizuku == ShizukuState.PERMISSION_REQUIRED) {
                                    Button(onClick = { ShizukuGateway.requestPermission() }) {
                                        Text(stringResource(R.string.allow_shizuku))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = {
                            uriHandler.openUri("https://github.com/neveSZ/onehz/blob/main/docs/INSTALL.md")
                        }) {
                            Text(stringResource(R.string.setup_shizuku_help))
                        }
                        Text(
                            text = stringResource(R.string.setup_shizuku_reboot),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (gate.eligible) {
            item {
                OneHzCard {
                    StepHeader(number = 2, title = stringResource(R.string.setup_recommended))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.setup_permissions_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    PermissionItem(
                        icon = Icons.Outlined.BatteryFull,
                        title = stringResource(R.string.setup_battery_title),
                        description = stringResource(R.string.setup_battery_desc),
                        granted = batteryExempt,
                        actionLabel = stringResource(R.string.setup_battery_button),
                        showAction = !batteryExempt,
                        onAction = { requestBatteryExemption(context) }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.Bedtime,
                        title = stringResource(R.string.setup_doze_title),
                        description = stringResource(R.string.setup_doze_desc),
                        granted = dozeWhitelisted,
                        showAction = shizuku == ShizukuState.READY,
                        actionLabel = stringResource(
                            if (dozeWhitelisted) R.string.setup_doze_remove else R.string.setup_doze_button
                        ),
                        onAction = {
                            scope.launch {
                                val target = !dozeWhitelisted
                                val ok = withContext(Dispatchers.IO) {
                                    ShizukuGateway.setDozeWhitelisted(context.packageName, target)
                                }
                                if (ok) dozeWhitelisted = target
                                refresh++
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.setup_notif_title),
                        description = stringResource(R.string.setup_notif_desc),
                        granted = notifications,
                        actionLabel = stringResource(R.string.setup_notif_button),
                        showAction = !notifications,
                        onAction = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings(context)
                            }
                        },
                        secondaryLabel = if (!notifications) {
                            stringResource(R.string.setup_open_settings)
                        } else {
                            null
                        },
                        onSecondary = { openNotificationSettings(context) }
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        OneHzPreferences.setEnabled(context, true)
                        OneHzPreferences.setSetupCompleted(context, true)
                        OneHzEngine.start(context)
                        OneHzEngine.reapply()
                        OneHzKeepAliveService.start(context)
                        onDone()
                    },
                    enabled = shizuku == ShizukuState.READY,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text(stringResource(R.string.setup_finish), Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.setup_finish_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.setup_sleep_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun SetupCheckItem(
    icon: ImageVector,
    title: String,
    description: String,
    statusText: String,
    tone: StatusTone,
    action: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 1.dp)
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            StatusBadge(
                text = statusText,
                tone = tone,
                icon = if (tone == StatusTone.OK) Icons.Outlined.CheckCircle else null
            )
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    showAction: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 1.dp)
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            StatusBadge(
                text = stringResource(
                    if (granted) R.string.setup_granted else R.string.setup_pending
                ),
                tone = if (granted) StatusTone.OK else StatusTone.NEUTRAL,
                icon = if (granted) Icons.Outlined.CheckCircle else null
            )
            Spacer(Modifier.height(6.dp))
            if (showAction || secondaryLabel != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showAction) {
                        OutlinedButton(onClick = onAction) { Text(actionLabel) }
                    }
                    if (secondaryLabel != null) {
                        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                    }
                }
            }
        }
    }
}

// Sideload-only app: the battery exemption is optional and requested explicitly by the user.
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri()
            )
        )
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
}
