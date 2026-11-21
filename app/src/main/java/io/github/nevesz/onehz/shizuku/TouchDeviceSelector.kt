package io.github.nevesz.onehz.shizuku

/**
 * Touchscreen selection based on input capabilities, never on a score or a lucky name.
 *
 * The rules were validated against the real device output:
 * - a real touchscreen has BTN_TOUCH + ABS_MT_POSITION_X/Y and INPUT_PROP_DIRECT;
 * - a touchpad has the same MT capabilities but INPUT_PROP_POINTER, so it must be rejected;
 * - a proximity/stylus node may carry INPUT_PROP_DIRECT without any MT position axis.
 *
 * This file has no Android dependency so it can be unit tested with captured `getevent -lp`
 * and `dumpsys input` output.
 */
data class TouchDevice(
    val path: String,
    val name: String,
    val displayId: Int = UNKNOWN_DISPLAY,
    val eventIndex: Int = Int.MAX_VALUE
) {
    val label: String
        get() = buildString {
            append(path.substringAfterLast('/'))
            append(" · ").append(name.ifBlank { "?" })
            append(" · d=").append(if (displayId == UNKNOWN_DISPLAY) "?" else displayId.toString())
        }

    companion object {
        const val UNKNOWN_DISPLAY = -1
        const val DEFAULT_DISPLAY = 0
    }
}

data class TouchCandidate(
    val device: TouchDevice,
    val accepted: Boolean,
    val reason: String
)

object TouchDeviceSelector {

    /** Upper bound of `getevent` processes we are willing to keep alive. */
    const val MAX_DEVICES = 4

    private const val REASON_OK = "ok"

    private val PATH = Regex("/dev/input/event(\\d+)")
    private val NAME = Regex("name:\\s*\"([^\"]*)\"")
    private val BLOCK = Regex("(?=add device \\d+: /dev/input/event\\d+)")
    private val DEVICE_HEADER = Regex("""^\s*(?:Device\s+)?\d+:\s+(\S.*)$""")
    private val DISPLAY_ID = Regex("""displayId=(-?\d+)""")

    /**
     * Names that never belong to the panel the user is touching. `touchpad` is here as a belt
     * and braces because some firmwares drop INPUT_PROP_POINTER on the touchpad node.
     */
    private val REJECTED_NAME = Regex(
        "epen|spen|s pen|touchkey|touchpad|keyboard|gpio|hall|proximity|" +
            "vibrator|als|meta|pmic|pwrkey|resin|certify"
    )

    /** Every device found in `getevent -lp`, accepted or not, sorted by priority. */
    fun parse(listing: String, displayByName: Map<String, Int> = emptyMap()): List<TouchCandidate> {
        if (listing.isBlank()) return emptyList()
        return listing.split(BLOCK)
            .asSequence()
            .filter { it.contains("add device") }
            .mapNotNull { candidate(it, displayByName) }
            .sortedWith(
                compareByDescending<TouchCandidate> { it.accepted }
                    .thenByDescending { it.device.displayId == TouchDevice.DEFAULT_DISPLAY }
                    .thenByDescending { it.device.name.contains("touchscreen") }
                    .thenBy { it.device.eventIndex }
            )
            .toList()
    }

    /** Monitored devices: every candidate that passes the filter, best display first. */
    fun select(listing: String, displayByName: Map<String, Int> = emptyMap()): List<TouchDevice> =
        parse(listing, displayByName)
            .filter { it.accepted }
            .map { it.device }
            .take(MAX_DEVICES)

    /**
     * Extracts `device name -> displayId` from `dumpsys input`.
     *
     * The path/name pair comes from `getevent -lp`; `dumpsys input` is the only place that says
     * which display a touch device feeds (`Viewport INTERNAL: displayId=N`). When a device has
     * more than one viewport the last one wins, which is fine for diagnostics.
     */
    fun parseDisplayIds(dump: String): Map<String, Int> {
        if (dump.isBlank()) return emptyMap()
        var current: String? = null
        val result = mutableMapOf<String, Int>()
        dump.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            DEVICE_HEADER.find(line)?.groupValues?.get(1)?.trim()?.let { name ->
                if (name.isNotEmpty() && !name.contains('=')) current = name
            }
            val name = current ?: return@forEach
            if (line.contains("Viewport INTERNAL")) {
                DISPLAY_ID.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { result[name] = it }
            }
        }
        return result
    }

    fun describe(candidates: List<TouchCandidate>): String =
        candidates.joinToString("; ") { candidate ->
            val status = if (candidate.accepted) "OK" else "REJ(${candidate.reason})"
            "${candidate.device.path} \"${candidate.device.name}\" $status"
        }

    private fun candidate(block: String, displayByName: Map<String, Int>): TouchCandidate? {
        val pathMatch = PATH.find(block) ?: return null
        val path = pathMatch.value
        val index = pathMatch.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
        val name = NAME.find(block)?.groupValues?.get(1)?.trim().orEmpty()

        val hasButton = block.contains("BTN_TOUCH")
        val hasMtX = block.contains("ABS_MT_POSITION_X")
        val hasMtY = block.contains("ABS_MT_POSITION_Y")
        val direct = block.contains("INPUT_PROP_DIRECT")
        val pointer = block.contains("INPUT_PROP_POINTER")

        val device = TouchDevice(
            path = path,
            name = name,
            displayId = displayByName[name] ?: TouchDevice.UNKNOWN_DISPLAY,
            eventIndex = index
        )

        val reason = when {
            !hasButton || !hasMtX || !hasMtY -> "missing BTN_TOUCH/ABS_MT"
            pointer -> "pointer/touchpad"
            !direct -> "missing INPUT_PROP_DIRECT"
            REJECTED_NAME.containsMatchIn(name) -> "rejected name"
            else -> REASON_OK
        }
        return TouchCandidate(device, reason == REASON_OK, reason)
    }
}
