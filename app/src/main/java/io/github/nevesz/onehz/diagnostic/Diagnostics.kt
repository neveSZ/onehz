package io.github.nevesz.onehz.diagnostic

import io.github.nevesz.onehz.core.VideoSignals
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val ltpoState = MutableStateFlow("—")
    val ltpoEligible = MutableStateFlow(false)
    val controlMethod = MutableStateFlow("Settings: mode 2 + peak/min")
    val activeTier = MutableStateFlow("—")
    val activeTierKey = MutableStateFlow("—")
    val activeStep = MutableStateFlow(0)
    val activeStepCount = MutableStateFlow(0)
    val idleReason = MutableStateFlow<io.github.nevesz.onehz.engine.IdleReason?>(null)
    val appliedHz = MutableStateFlow(0)
    val observedHz = MutableStateFlow(0)
    val lastWrite = MutableStateFlow("—")
    val videoSource = MutableStateFlow(VideoSignals.Source.NONE)
    val videoDetail = MutableStateFlow("—")
    val aodBrightness = MutableStateFlow<AodBrightnessInfo?>(null)
    val inputState = MutableStateFlow(InputMonitorState.UNKNOWN)
    val inputDevice = MutableStateFlow("—")
    val inputCandidates = MutableStateFlow<List<String>>(emptyList())
    val lastTouchAt = MutableStateFlow("—")

    private fun timeNow(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    @Synchronized
    fun log(tag: String, message: String) {
        if (paused.value) return
        val entry = LogEntry(timeNow(), tag, message)
        entries.value = (listOf(entry) + entries.value).take(MAX_ENTRIES)
    }

    fun clear() {
        entries.value = emptyList()
        lastWrite.value = "—"
    }

    fun setPaused(value: Boolean) {
        paused.value = value
    }

    fun report(): String = buildString {
        appendLine("OneHZ diagnostics")
        appendLine("Shizuku: ${shizukuState.value}")
        appendLine("LTPO: ${ltpoState.value}")
        appendLine("Active tier: ${activeTier.value}")
        appendLine("Step: ${activeStep.value}/${activeStepCount.value}")
        appendLine("Idle: ${idleReason.value ?: "controlling"}")
        appendLine("Last write: ${lastWrite.value}")
        appendLine("Control method: ${controlMethod.value}")
        appendLine("Video: ${videoSource.value} (${videoDetail.value})")
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
