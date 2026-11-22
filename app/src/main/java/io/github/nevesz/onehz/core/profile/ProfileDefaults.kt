package io.github.nevesz.onehz.core.profile

import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.TierStep
import kotlin.math.abs

/**
 * Device-aware defaults for the three built-in profiles.
 *
 * The staircase always descends: maximum -> optional middle -> resting rate. On a panel
 * with only two rates the only difference between the profiles is how long the maximum is
 * held; the display then rests at the minimum until the next touch.
 */
object ProfileDefaults {

    const val ECONOMY_MAX_HOLD_MS = 1_000L
    const val ECONOMY_MID_HOLD_MS = 6_000L
    const val BALANCED_MAX_HOLD_MS = 2_000L
    const val BALANCED_MID_HOLD_MS = 5_000L
    const val PERFORMANCE_MAX_HOLD_MS = 5_000L
    const val PERFORMANCE_MID_HOLD_MS = 8_000L

    const val ECONOMY_VIDEO_HZ = 30
    const val STANDARD_VIDEO_HZ = 60

    /** Fallback used only when a caller has no panel information at all. */
    val FALLBACK_RATES = listOf(10, 24, 30, 48, 60, 80, 120)

    fun normalizedRates(rates: List<Int>): List<Int> =
        rates.filter { it > 0 }.distinct().sorted().ifEmpty { FALLBACK_RATES.sorted() }

    fun forRates(rates: List<Int>): List<DeviceProfile> {
        val sorted = normalizedRates(rates)
        return listOf(
            economy(sorted),
            balanced(sorted),
            performance(sorted)
        )
    }

    fun forKind(kind: ProfileKind, rates: List<Int>): DeviceProfile? = when (kind) {
        ProfileKind.ECONOMY -> economy(normalizedRates(rates))
        ProfileKind.BALANCED -> balanced(normalizedRates(rates))
        ProfileKind.PERFORMANCE -> performance(normalizedRates(rates))
        ProfileKind.CUSTOM -> null
    }

    fun nearestRate(target: Int, rates: List<Int>): Int {
        val sorted = normalizedRates(rates)
        return sorted.minByOrNull { abs(it - target) } ?: target
    }

    private fun economy(rates: List<Int>): DeviceProfile {
        val (min, max) = endpoints(rates)
        // When the panel minimum is already 60+ there is nothing below it to save, so
        // the economy profile rests directly at the minimum instead of adding a high middle.
        val middle = when {
            rates.size <= 2 || min >= 60 -> null
            60 in rates && 60 < max -> 60
            rates.any { it < 60 && it > min } -> rates.last { it < 60 && it > min }
            else -> rates.getOrNull(rates.size / 2)?.takeIf { it > min && it < max }
        }
        return profile(
            id = ProfileKind.ECONOMY.canonicalId,
            kind = ProfileKind.ECONOMY,
            frequency = staircase(max, ECONOMY_MAX_HOLD_MS, middle, ECONOMY_MID_HOLD_MS, min),
            videoMin = nearestRate(ECONOMY_VIDEO_HZ, rates)
        )
    }

    private fun balanced(rates: List<Int>): DeviceProfile {
        val (min, max) = endpoints(rates)
        val middle = rates.lastOrNull { it > 60 && it < max }
            ?: rates.lastOrNull { it == 60 && it != min && it != max }
            ?: rates.lastOrNull { it < 60 && it > min }
            ?: rates.getOrNull(rates.size / 2)?.takeIf { it != min && it != max }
        return profile(
            id = ProfileKind.BALANCED.canonicalId,
            kind = ProfileKind.BALANCED,
            frequency = staircase(max, BALANCED_MAX_HOLD_MS, middle, BALANCED_MID_HOLD_MS, min),
            videoMin = nearestRate(STANDARD_VIDEO_HZ, rates)
        )
    }

    private fun performance(rates: List<Int>): DeviceProfile {
        val (min, max) = endpoints(rates)
        val middle = rates.lastOrNull { it > 60 && it < max }
        val resting = when {
            60 in rates && 60 < max -> 60
            else -> rates.firstOrNull { it >= 60 && it < max }
                ?: rates.lastOrNull { it < max }
                ?: max
        }
        val usableMiddle = middle?.takeIf { it > resting && it < max }
        return profile(
            id = ProfileKind.PERFORMANCE.canonicalId,
            kind = ProfileKind.PERFORMANCE,
            frequency = staircase(
                max,
                PERFORMANCE_MAX_HOLD_MS,
                usableMiddle,
                PERFORMANCE_MID_HOLD_MS,
                resting
            ),
            videoMin = nearestRate(STANDARD_VIDEO_HZ, rates)
        )
    }

    private fun profile(
        id: String,
        kind: ProfileKind,
        frequency: FrequencyProfile,
        videoMin: Int
    ): DeviceProfile = DeviceProfile(
        id = id,
        kind = kind,
        name = null,
        frequency = frequency,
        video = VideoSettings(
            enabled = true,
            minHz = videoMin,
            rules = VideoRules(
                useMediaSession = true,
                pausedSessionBlocks = true,
                requireVideoCodec = true,
                requireStartedAudio = false
            )
        ),
        customized = false
    )

    private fun endpoints(rates: List<Int>): Pair<Int, Int> = rates.first() to rates.last()

    private fun staircase(
        max: Int,
        maxHold: Long,
        middle: Int?,
        middleHold: Long,
        resting: Int
    ): FrequencyProfile {
        if (max <= resting) return FrequencyProfile(listOf(TierStep(max)))
        val steps = buildList {
            add(TierStep(max, maxHold))
            if (middle != null && middle < max && middle > resting) {
                add(TierStep(middle, middleHold))
            }
            add(TierStep(resting))
        }
        return FrequencyProfile(steps)
    }
}
