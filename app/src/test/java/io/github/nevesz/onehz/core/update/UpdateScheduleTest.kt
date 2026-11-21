package io.github.nevesz.onehz.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateScheduleTest {

    @Test
    fun `first check is due`() {
        assertTrue(UpdateSchedule.isDue(now = 1_000_000L, lastAttemptAt = 0L, UpdateInterval.HOURS_24))
    }

    @Test
    fun `exactly one interval is due`() {
        val interval = UpdateInterval.HOURS_24
        val lastAttempt = 1L
        assertTrue(
            UpdateSchedule.isDue(
                now = lastAttempt + interval.millis,
                lastAttemptAt = lastAttempt,
                interval = interval
            )
        )
    }

    @Test
    fun `inside the interval is not due`() {
        val interval = UpdateInterval.DAYS_3
        assertFalse(
            UpdateSchedule.isDue(
                now = interval.millis - 1,
                lastAttemptAt = 1L,
                interval = interval
            )
        )
    }

    @Test
    fun `clock moving backwards does not force a check`() {
        assertFalse(
            UpdateSchedule.isDue(
                now = 100L,
                lastAttemptAt = 10_000L,
                interval = UpdateInterval.HOURS_24
            )
        )
    }

    @Test
    fun `interval presets are the documented values`() {
        assertEquals(24 * 60, UpdateInterval.HOURS_24.minutes)
        assertEquals(3 * 24 * 60, UpdateInterval.DAYS_3.minutes)
        assertEquals(7 * 24 * 60, UpdateInterval.DAYS_7.minutes)
        assertEquals(30 * 24 * 60, UpdateInterval.DAYS_30.minutes)
        assertEquals(UpdateInterval.HOURS_24, UpdateInterval.fromMinutes(999))
    }
}
