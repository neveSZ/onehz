package io.github.nevesz.onehz.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class RateStatsAccumulatorTest {

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `splits time between rates`() {
        val accumulator = RateStatsAccumulator()
        val start = at(23, 10, 0)
        accumulator.note(start, RateSample(RateMode.ON, 60, "balanced"))
        accumulator.note(start + 10 * 60_000, RateSample(RateMode.ON, 30, "balanced"))
        accumulator.note(start + 15 * 60_000, RateSample(RateMode.OFF, 0, null))

        val summary = accumulator.summary(start + 15 * 60_000, 1)
        assertEquals(10 * 60_000L, summary.rates.first { it.hz == 60 }.durationMs)
        assertEquals(5 * 60_000L, summary.rates.first { it.hz == 30 }.durationMs)
        assertEquals(15 * 60_000L, summary.profiles.first { it.profileId == "balanced" }.durationMs)
    }

    @Test
    fun `keeps aod separate from screen-on time`() {
        val accumulator = RateStatsAccumulator()
        val start = at(23, 22, 0)
        accumulator.note(start, RateSample(RateMode.AOD, 10, null))
        accumulator.note(start + 60 * 60_000, RateSample(RateMode.ON, 120, "balanced"))

        val summary = accumulator.summary(start + 60 * 60_000, 1)
        assertEquals(60 * 60_000L, summary.aodMs)
        assertEquals(0L, summary.rates.sumOf { it.durationMs })
    }

    @Test
    fun `splits a span across midnight`() {
        val accumulator = RateStatsAccumulator()
        val start = at(23, 23, 50)
        accumulator.note(start, RateSample(RateMode.ON, 60, "balanced"))
        val end = at(24, 0, 10)
        accumulator.note(end, RateSample(RateMode.OFF, 0, null))

        val today = accumulator.summary(end, 1)
        val twoDays = accumulator.summary(end, 2)
        assertEquals(10 * 60_000L, today.rates.sumOf { it.durationMs })
        assertEquals(20 * 60_000L, twoDays.rates.sumOf { it.durationMs })
    }

    @Test
    fun `round trips through json`() {
        val accumulator = RateStatsAccumulator()
        val start = at(23, 8, 0)
        accumulator.note(start, RateSample(RateMode.ON, 120, "performance"))
        accumulator.note(start + 5 * 60_000, RateSample(RateMode.OFF, 0, null))

        val restored = RateStatsAccumulator()
        restored.decode(accumulator.encode())
        val summary = restored.summary(start + 5 * 60_000, 1)
        assertEquals(5 * 60_000L, summary.rates.first { it.hz == 120 }.durationMs)
    }
}
