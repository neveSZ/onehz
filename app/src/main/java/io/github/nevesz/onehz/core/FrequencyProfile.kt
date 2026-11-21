package io.github.nevesz.onehz.core

/**
 * One step of a descending refresh-rate staircase.
 * [holdMs] is how long the display stays at this rate after the last interaction.
 * The last step has a null hold: it is the resting rate.
 */
data class TierStep(val hz: Int, val holdMs: Long? = null)

enum class ProfileIssue {
    EMPTY,
    TOO_MANY,
    NOT_DESCENDING,
    UNSUPPORTED_RATE,
    HOLD_OUT_OF_RANGE,
    LAST_HAS_HOLD
}

/**
 * A user-configured schedule: touch applies the first step, then each hold elapses
 * and the next step is applied until the last one, which is the resting rate.
 *
 * Serialized form: "120:1000,96:10000,60" (hz:holdMs, last without hold).
 * An empty profile is stored as an empty string and means "no control configured".
 */
data class FrequencyProfile(val steps: List<TierStep> = emptyList()) {

    val isFixed: Boolean get() = steps.size == 1
    val lastIndex: Int get() = (steps.size - 1).coerceAtLeast(0)

    /** Total time from a touch until the resting step. */
    val totalHoldMs: Long
        get() = steps.dropLast(1).sumOf { it.holdMs ?: 0L }

    fun clampedIndex(index: Int): Int = index.coerceIn(0, lastIndex)

    fun hzAt(index: Int): Int? = steps.getOrNull(index)?.hz

    fun holdAt(index: Int): Long? = steps.getOrNull(index)?.holdMs

    fun serialize(): String = steps.joinToString(",") { step ->
        step.holdMs?.let { "${step.hz}:$it" } ?: step.hz.toString()
    }

    /** Empty list means "explicitly cleared", which is different from "never configured". */
    fun issues(supportedRates: List<Int>): List<ProfileIssue> = buildList {
        if (steps.isEmpty()) add(ProfileIssue.EMPTY)
        if (steps.size > MAX_STEPS) add(ProfileIssue.TOO_MANY)
        steps.forEachIndexed { index, step ->
            if (supportedRates.isNotEmpty() && step.hz !in supportedRates) {
                add(ProfileIssue.UNSUPPORTED_RATE)
            }
            if (index == steps.lastIndex) {
                if (step.holdMs != null) add(ProfileIssue.LAST_HAS_HOLD)
            } else {
                val hold = step.holdMs
                if (hold == null || hold < MIN_HOLD_MS || hold > MAX_HOLD_MS) {
                    add(ProfileIssue.HOLD_OUT_OF_RANGE)
                }
            }
            if (index > 0 && step.hz >= steps[index - 1].hz) add(ProfileIssue.NOT_DESCENDING)
        }
    }.distinct()

    fun isValid(supportedRates: List<Int>): Boolean = issues(supportedRates).isEmpty()

    companion object {
        const val MIN_STEPS = 1
        const val MAX_STEPS = 5
        const val MIN_HOLD_MS = 500L
        const val MAX_HOLD_MS = 60_000L
        const val HOLD_STEP_MS = 500L
        const val DEFAULT_HOLD_MS = 5_000L

        fun fixed(hz: Int): FrequencyProfile = FrequencyProfile(listOf(TierStep(hz)))

        /**
         * Default staircase for a fresh install: device ceiling -> ~60 Hz -> device minimum.
         * [cap] limits the ceiling (used by the battery saver profile).
         */
        fun deviceDefault(rates: List<Int>, cap: Int? = null): FrequencyProfile {
            val sorted = rates.filter { it > 0 }.distinct().sorted()
                .ifEmpty { listOf(10, 24, 30, 48, 60, 90, 120) }
            val deviceMin = sorted.first()
            val ceiling = cap?.let { limit -> sorted.lastOrNull { it <= limit } } ?: sorted.last()
            if (ceiling <= deviceMin) return fixed(ceiling)
            // Only add the 60 Hz step when the ceiling is above it; otherwise it is a duplicate.
            val middle = if (ceiling > 60) {
                sorted.filter { it > deviceMin && it < ceiling }.minByOrNull { kotlin.math.abs(it - 60) }
            } else {
                null
            }
            val steps = buildList {
                add(TierStep(ceiling, 1_000L))
                if (middle != null) add(TierStep(middle, 10_000L))
                add(TierStep(deviceMin))
            }
            return FrequencyProfile(steps)
        }

        /** Returns null only when there is no stored value at all. */
        fun parse(raw: String?): FrequencyProfile? {
            if (raw == null) return null
            val text = raw.trim()
            if (text.isEmpty()) return FrequencyProfile(emptyList())
            val steps = text.split(',').map { part ->
                val bits = part.trim().split(':')
                val hz = bits.getOrNull(0)?.toIntOrNull() ?: return null
                val hold = when (bits.size) {
                    1 -> null
                    2 -> bits[1].toLongOrNull() ?: return null
                    else -> return null
                }
                TierStep(hz, hold)
            }
            val profile = FrequencyProfile(steps)
            val structural = setOf(
                ProfileIssue.EMPTY,
                ProfileIssue.TOO_MANY,
                ProfileIssue.NOT_DESCENDING,
                ProfileIssue.HOLD_OUT_OF_RANGE,
                ProfileIssue.LAST_HAS_HOLD
            )
            return profile.takeIf { p -> p.issues(emptyList()).none { it in structural } }
        }

        fun of(vararg steps: TierStep): FrequencyProfile = FrequencyProfile(steps.toList())

        fun normalizeHold(holdMs: Long): Long =
            (holdMs.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS) / HOLD_STEP_MS) * HOLD_STEP_MS
    }
}
