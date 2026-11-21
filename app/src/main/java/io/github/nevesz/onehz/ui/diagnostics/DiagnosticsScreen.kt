package io.github.nevesz.onehz.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.VideoConfig
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.diagnostic.InputMonitorState
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.ui.components.MetricBox
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.StatusBadge
import io.github.nevesz.onehz.ui.components.StatusTone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val entries by Diagnostics.entries.collectAsState()
    val paused by Diagnostics.paused.collectAsState()
    val shizuku by Diagnostics.shizukuState.collectAsState()
    val ltpo by Diagnostics.ltpoState.collectAsState()
    val ltpoEligible by Diagnostics.ltpoEligible.collectAsState()
    val controlMethod by Diagnostics.controlMethod.collectAsState()
    val tierKey by Diagnostics.activeTierKey.collectAsState()
    val observedHz by Diagnostics.observedHz.collectAsState()
    val activeStep by Diagnostics.activeStep.collectAsState()
    val activeStepCount by Diagnostics.activeStepCount.collectAsState()
    val last by Diagnostics.lastWrite.collectAsState()
    val inputState by Diagnostics.inputState.collectAsState()
    val inputDevice by Diagnostics.inputDevice.collectAsState()
    val inputCandidates by Diagnostics.inputCandidates.collectAsState()
    val lastTouch by Diagnostics.lastTouchAt.collectAsState()
    val aodInfo by Diagnostics.aodBrightness.collectAsState()
    val videoSource by Diagnostics.videoSource.collectAsState()
    val videoDetail by Diagnostics.videoDetail.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("all") }
    var videoTest by remember { mutableStateOf<String?>(null) }
    val tags = listOf("all", "touch", "screen", "display", "timer", "engine", "wake", "arbiter", "eco", "aod", "video")
    val tierText = when {
        tierKey == "AOD" -> stringResource(R.string.layer_aod)
        tierKey == "OFF" -> stringResource(R.string.no_control)
        activeStepCount > 0 -> stringResource(R.string.step_of, activeStep, activeStepCount)
        else -> stringResource(R.string.not_available)
    }
    val filtered = entries.filter { entry ->
        (tag == "all" || entry.tag == tag) &&
            (query.isBlank() || entry.message.contains(query, true) || entry.tag.contains(query, true))
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.summary_tab)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.log_tab)) })
        }

        if (tab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBox(Icons.Outlined.Bolt, stringResource(R.string.diag_shizuku), shizuku, Modifier.weight(1f))
                        MetricBox(
                            Icons.Outlined.Shield,
                            stringResource(R.string.diag_ltpo),
                            if (ltpoEligible) stringResource(R.string.status_ok) else stringResource(R.string.status_error),
                            Modifier.weight(1f),
                            accent = if (ltpoEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBox(Icons.Outlined.Speed, stringResource(R.string.diag_tier), tierText, Modifier.weight(1f))
                        MetricBox(Icons.Outlined.BugReport, stringResource(R.string.diag_last_write), last, Modifier.weight(1f))
                    }
                }
                if (observedHz > 0) {
                    item {
                        Text(
                            text = stringResource(R.string.diag_observed, observedHz),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                item {
                    OneHzCard {
                        Text(stringResource(R.string.diag_ltpo), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(ltpo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    OneHzCard {
                        Text(
                            stringResource(R.string.diag_control_method),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = controlMethod,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                item {
                    OneHzCard {
                        Text(stringResource(R.string.input_monitor), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("${stringResource(R.string.input_status)}: ${inputStateLabel(inputState)}")
                        Text("${stringResource(R.string.input_device)}: $inputDevice")
                        Text("${stringResource(R.string.last_touch)}: $lastTouch")
                        if (inputCandidates.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = inputCandidates.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { ShizukuGateway.restartInputMonitoring() }) {
                            Text(stringResource(R.string.restart_input))
                        }
                    }
                }
                item {
                    OneHzCard {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.BrightnessMedium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.diag_aod_brightness), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        val info = aodInfo
                        if (info == null) {
                            Text(
                                stringResource(R.string.diag_not_available),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                stringResource(
                                    R.string.aod_brightness_now,
                                    info.target,
                                    info.screenRaw,
                                    info.effectivePct
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val pctText = if (info.pct != info.effectivePct) {
                                stringResource(R.string.aod_brightness_clamped, info.pct, info.effectivePct)
                            } else {
                                stringResource(R.string.aod_brightness_effective, info.effectivePct)
                            }
                            val statusText = if (info.applied) {
                                stringResource(R.string.status_ok)
                            } else {
                                stringResource(R.string.diag_not_available)
                            }
                            Text(
                                "$pctText · ${info.minPct}% – ${info.maxPct}% · $statusText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { AodBrightnessController.restoreSystem(context) }) {
                            Text(stringResource(R.string.aod_brightness_restore))
                        }
                    }
                }
                item {
                    OneHzCard {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.OndemandVideo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.video_diag_title), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${stringResource(R.string.video_diag_state)}: ${videoSourceLabel(videoSource)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = videoDetail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val videoConfig = OneHzPreferences.videoConfig(context)
                        Text(
                            text = stringResource(
                                R.string.video_diag_config,
                                stringResource(
                                    R.string.video_rules_summary,
                                    videoConfig.activeRuleCount,
                                    VideoConfig.RULE_COUNT
                                ),
                                videoConfig.forcedPackages.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            val verdict = ShizukuGateway.videoPlayback(OneHzPreferences.videoConfig(context))
                            videoTest = verdict.encode()
                            Diagnostics.log("video", "manual test: ${verdict.encode()}")
                        }) {
                            Text(stringResource(R.string.video_diag_test))
                        }
                        videoTest?.let { result ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.video_diag_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { copyReport(context) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Text(stringResource(R.string.copy_report), Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = { OneHzEngine.refreshSystemState() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text(stringResource(R.string.update_info), Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { OneHzEngine.testMax() }) {
                            Text(stringResource(R.string.test_max))
                        }
                        OutlinedButton(onClick = { OneHzEngine.testLow() }) {
                            Text(stringResource(R.string.test_low))
                        }
                        OutlinedButton(onClick = { OneHzEngine.testCycle() }) {
                            Text(stringResource(R.string.test_cycle))
                        }
                        OutlinedButton(onClick = {
                            val report = ShizukuGateway.verifySettings() + " · " + ShizukuGateway.serviceStats()
                            Diagnostics.lastWrite.value = report
                            Diagnostics.log("arbiter", "verification: $report")
                        }) {
                            Text(stringResource(R.string.verify_settings))
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.log_search)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { option ->
                            FilterChip(
                                selected = tag == option,
                                onClick = { tag = option },
                                label = { Text(if (option == "all") stringResource(R.string.log_filter_all) else tagLabel(option)) }
                            )
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            Diagnostics.setPaused(!paused)
                            OneHzPreferences.setLogsPaused(context, !paused)
                        }) {
                            Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null)
                            Text(
                                text = stringResource(if (paused) R.string.resume_logs else R.string.pause_logs),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        OutlinedButton(onClick = { Diagnostics.clear() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Text(stringResource(R.string.clear_log), Modifier.padding(start = 6.dp))
                        }
                        Button(onClick = { copyReport(context) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Text(stringResource(R.string.copy_report), Modifier.padding(start = 6.dp))
                        }
                    }
                }
                if (paused) {
                    item { StatusBadge(stringResource(R.string.logs_paused), tone = StatusTone.WARN) }
                }
                items(filtered) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "${entry.time}  [${entry.tag}]",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (entry.tag) {
                                "probe" -> MaterialTheme.colorScheme.tertiary
                                "arbiter" -> MaterialTheme.colorScheme.primary
                                "touch" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun copyReport(context: Context) {
    val report = Diagnostics.report()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("OneHZ diagnostics", report))
    Toast.makeText(context, context.getString(R.string.report_copied), Toast.LENGTH_SHORT).show()
}

@Composable
private fun inputStateLabel(state: InputMonitorState): String = when (state) {
    InputMonitorState.UNKNOWN -> stringResource(R.string.not_available)
    InputMonitorState.STOPPED -> stringResource(R.string.input_state_stopped)
    InputMonitorState.NO_SERVICE -> stringResource(R.string.input_state_no_service)
    InputMonitorState.LIST_FAILED -> stringResource(R.string.input_state_list_failed)
    InputMonitorState.EMPTY_LIST -> stringResource(R.string.input_state_empty_list)
    InputMonitorState.NO_TOUCHSCREEN -> stringResource(R.string.input_state_no_touchscreen)
    InputMonitorState.ACTIVE -> stringResource(R.string.input_state_active)
    InputMonitorState.START_FAILED -> stringResource(R.string.input_state_start_failed)
}

@Composable
private fun videoSourceLabel(source: VideoSignals.Source): String = when (source) {
    VideoSignals.Source.PLAYING -> stringResource(R.string.video_state_playing)
    else -> stringResource(R.string.video_state_idle)
}

@Composable
private fun tagLabel(tag: String): String = when (tag) {
    "touch" -> stringResource(R.string.log_tag_touch)
    "screen" -> stringResource(R.string.log_tag_screen)
    "display" -> stringResource(R.string.log_tag_display)
    "timer" -> stringResource(R.string.log_tag_timer)
    "engine" -> stringResource(R.string.log_tag_engine)
    "wake" -> stringResource(R.string.log_tag_wake)
    "arbiter" -> stringResource(R.string.log_tag_arbiter)
    "eco" -> stringResource(R.string.log_tag_eco)
    "aod" -> stringResource(R.string.log_tag_aod)
    "video" -> stringResource(R.string.log_tag_video)
    else -> tag
}
