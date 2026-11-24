package io.github.nevesz.onehz.diagnostic

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import io.github.nevesz.onehz.core.DisplayProfileResolver
import io.github.nevesz.onehz.core.DisplayVendor
import io.github.nevesz.onehz.core.DozePhase
import io.github.nevesz.onehz.core.VideoSignals
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

data class LogEntry(val time: String, val tag: String, val message: String)

data class AodBrightnessInfo(
    val screenRaw: Int,
    val target: Int,
    val pct: Int,
    val effectivePct: Int,
    val minPct: Int,
    val maxPct: Int,
    val applied: Boolean
)

/** Stable keys so the UI can localize every monitor state. */
enum class InputMonitorState {
    UNKNOWN,
    STOPPED,
    NO_SERVICE,
    LIST_FAILED,
    EMPTY_LIST,
    NO_TOUCHSCREEN,
    ACTIVE,
    START_FAILED
}

/** In-memory diagnostics only. Nothing is uploaded or persisted. */
object Diagnostics {
    private const val MAX_ENTRIES = 500

    val entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val paused = MutableStateFlow(false)
    val shizukuState = MutableStateFlow("—")
    val displayState = MutableStateFlow("—")
    val displayEligible = MutableStateFlow(false)
    val controlMethod = MutableStateFlow("Settings: mode 2 + peak/min")
    val activeTier = MutableStateFlow("-")
    val activeTierKey = MutableStateFlow("-")
    val activeLayer = MutableStateFlow<String?>(null)
    val activeProfileId = MutableStateFlow<String?>(null)
    val activeAppPackage = MutableStateFlow<String?>(null)
    val foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundSource = MutableStateFlow("off")
    val activeStep = MutableStateFlow(0)
    val activeStepCount = MutableStateFlow(0)
    val idleReason = MutableStateFlow<io.github.nevesz.onehz.engine.IdleReason?>(null)
    val appliedHz = MutableStateFlow(0)
    val observedHz = MutableStateFlow(0)
    val lastWrite = MutableStateFlow("—")
    val videoSource = MutableStateFlow(VideoSignals.Source.NONE)
    val videoDetail = MutableStateFlow("—")
    val dozePhase = MutableStateFlow(DozePhase.OFF)
    val dozeDetail = MutableStateFlow("—")
    val dozeActiveSince = MutableStateFlow(0L)
    val aodBrightness = MutableStateFlow<AodBrightnessInfo?>(null)
    val inputState = MutableStateFlow(InputMonitorState.UNKNOWN)
    val inputDevice = MutableStateFlow("-")
    val inputCandidates = MutableStateFlow<List<String>>(emptyList())
    val lastTouchAt = MutableStateFlow("-")
    val screenBrightness = MutableStateFlow("-")
    val batterySaver = MutableStateFlow(false)
    val displayRoute = MutableStateFlow("-")
    val externalChange = MutableStateFlow(false)

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    private fun timeNow(): String = timeFormat.get()!!.format(Date())

    @Synchronized
    fun log(tag: String, message: String) {
        if (paused.value) return
        val entry = LogEntry(timeNow(), tag, message)
        val previous = entries.value
        val keep = minOf(previous.size, MAX_ENTRIES - 1)
        val next = ArrayList<LogEntry>(keep + 1)
        next.add(entry)
        next.addAll(previous.subList(0, keep))
        entries.value = next
    }

    fun clear() {
        entries.value = emptyList()
        lastWrite.value = "-"
    }

    /**
     * Re-reads values owned by the system (brightness, battery saver, route) so the
     * diagnostic report does not depend on the engine having logged them.
     */
    fun refreshSystem(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        batterySaver.value = powerManager?.isPowerSaveMode == true
        val resolver = context.contentResolver
        val mode = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrDefault(-1)
        val raw = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(-1)
        val modeLabel = when (mode) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> "auto"
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> "manual"
            else -> "unknown"
        }
        screenBrightness.value = "raw=$raw mode=$modeLabel"
        val profile = DisplayProfileResolver.detect(context)
        displayRoute.value = when (profile.vendor) {
            DisplayVendor.XIAOMI -> "secure/${profile.hyperOsKey ?: "?"}"
            DisplayVendor.SAMSUNG -> "secure/refresh_rate_mode + peak/min"
            DisplayVendor.UNSUPPORTED -> "unsupported"
        }
    }

    fun setPaused(value: Boolean) {
        paused.value = value
    }

    fun report(): String = buildString {
        appendLine("OneHZ diagnostics")
        appendLine("Shizuku: ${shizukuState.value}")
        appendLine("Display: ${displayState.value}")
        appendLine("Active tier: ${activeTier.value}")
        appendLine("Step: ${activeStep.value}/${activeStepCount.value}")
        appendLine("Idle: ${idleReason.value ?: "controlling"}")
        appendLine("Foreground app: ${foregroundPackage.value ?: "—"}")
        appendLine("Foreground source: ${foregroundSource.value}")
        appendLine("Active profile: ${activeProfileId.value ?: "—"}")
        appendLine("Active layer: ${activeLayer.value ?: "—"}")
        appendLine("Active app: ${activeAppPackage.value ?: "—"}")
        appendLine("Last write: ${lastWrite.value}")
        appendLine("Control method: ${controlMethod.value}")
        appendLine("Display route: ${displayRoute.value}")
        appendLine("Brightness: ${screenBrightness.value}")
        appendLine("Battery saver: ${if (batterySaver.value) "on" else "off"}")
        appendLine("Video: ${videoSource.value} (${videoDetail.value})")
        appendLine("Sleep: ${dozePhase.value} (${dozeDetail.value})")
        aodBrightness.value?.let {
            appendLine(
                "AOD brightness: screen=${it.screenRaw} target=${it.target} " +
                    "pct=${it.pct}% effective=${it.effectivePct}% " +
                    "band=${it.minPct}-${it.maxPct}% applied=${it.applied}"
            )
        }
        appendLine("Input monitor: ${inputState.value} (${inputDevice.value.replace("\n", " | ")})")
        if (inputCandidates.value.isNotEmpty()) {
            appendLine("Input candidates: " + inputCandidates.value.joinToString(" | "))
        }
        appendLine()
        appendLine("Log (${entries.value.size} entries)")
        entries.value.forEach { entry ->
            appendLine("${entry.time} [${entry.tag}] ${entry.message}")
        }
    }
}
