package io.github.nevesz.onehz.shizuku

/**
 * Turns `getevent -lt` lines into "at least one finger on this device" edges.
 *
 * Samsung touchscreens speak MT-B: `ABS_MT_TRACKING_ID` marks a new or lifted finger,
 * `ABS_MT_SLOT` selects the slot being updated, and `BTN_TOUCH` is only reliable for the first
 * and last finger. Counting slots is what keeps a second finger (which emits no `BTN_TOUCH
 * DOWN`) from being missed and keeps one finger lifting (no `BTN_TOUCH UP`) from ending a
 * gesture that is still in progress.
 *
 * `feed` returns 1 on a 0 -> 1 finger transition, -1 on 1 -> 0 and 0 when nothing changed.
 * Pure Kotlin so the parsing rules can be unit tested without a device.
 */
class MtTouchParser {

    private val slots = mutableSetOf<Int>()
    private var currentSlot = 0
    private var sawTracking = false
    private var isDown = false

    val down: Boolean
        get() = isDown

    val activePointers: Int
        get() = slots.size

    @Synchronized
    fun feed(line: String): Int {
        when {
            line.contains("ABS_MT_SLOT") -> {
                rawValue(line)?.let { currentSlot = it.toInt() }
            }

            line.contains("ABS_MT_TRACKING_ID") -> {
                val raw = rawValue(line) ?: return 0
                sawTracking = true
                if (raw == LIFTED_TRACKING_ID) slots.remove(currentSlot) else slots.add(currentSlot)
                return transition()
            }

            // Fallback for drivers that do not expose MT-B tracking ids.
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                if (!sawTracking) slots.add(currentSlot)
                return transition()
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                if (!sawTracking) slots.clear()
                return transition()
            }
        }
        return 0
    }

    /** Clears the state without emitting through the normal line path. */
    @Synchronized
    fun reset(): Int {
        slots.clear()
        return transition()
    }

    private fun transition(): Int {
        val nowDown = slots.isNotEmpty()
        if (nowDown == isDown) return 0
        isDown = nowDown
        return if (nowDown) 1 else -1
    }

    private fun rawValue(line: String): Long? =
        RAW_VALUE.find(line)?.groupValues?.get(1)?.toLongOrNull(16)

    companion object {
        /** MT-B "no finger in this slot" marker: `ABS_MT_TRACKING_ID ffffffff`. */
        const val LIFTED_TRACKING_ID = 0xFFFF_FFFFL
        private val RAW_VALUE = Regex("""([0-9a-fA-F]{8})\s*$""")
    }
}
