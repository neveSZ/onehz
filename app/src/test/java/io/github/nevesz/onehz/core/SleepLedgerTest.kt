package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepLedgerTest {

    private val h = 60 * 60 * 1000L
    private val m = 60 * 1000L
    private val base = 1_700_000_000_000L

    @Test
    fun roundTripsClosedRecord() {
        val record = SleepRecord(
            startAt = base,
            endAt = base + 8 * h,
            levelStart = 100,
            levelEnd = 92,
            deep = true,
            idle = listOf(
                IdleSpan(base + m, base + 4 * h),
                IdleSpan(base + 4 * h + m, base + 7 * h),
            ),
            maintenance = 1,
        )
        assertEquals(listOf(record), SleepLedger.decode(SleepLedger.encode(listOf(record))))
    }

    @Test
    fun roundTripsOpenRecord() {
        val record = SleepRecord(
            startAt = base,
            endAt = 0L,
            levelStart = 96,
            levelEnd = -1,
            deep = false,
            idle = listOf(IdleSpan(base + 2 * m, 0L)),
            maintenance = 0,
        )
        assertEquals(listOf(record), SleepLedger.decode(SleepLedger.encode(listOf(record))))
    }

    @Test
    fun usageClipsEverySpanToTheWindow() {
        val now = base
        val records = listOf(
            SleepRecord(base - 2 * h, base - h, 90, 89, true, listOf(IdleSpan(base - 2 * h, base - 90 * m)), 0),
            SleepRecord(base - 30 * h, base - 28 * h, 90, 88, true, emptyList(), 0),
            SleepRecord(base - 25 * h, base - 23 * h, 88, 87, true, emptyList(), 0),
            SleepRecord(base - 30 * m, 0L, 87, -1, true, listOf(IdleSpan(base - 20 * m, 0L)), 0),
        )

        val usage = SleepLedger.usage(records, now)

        assertEquals(2 * h + 30 * m, usage.screenOffMs)
        assertEquals(30 * m + 20 * m, usage.idleMs)
        assertEquals(4, usage.sessions)
        assertTrue(usage.hasData)
        assertFalse(usage.partial)
    }

    @Test
    fun usageIsPartialWhenTheLedgerStartsInsideTheWindow() {
        val usage = SleepLedger.usage(
            listOf(SleepRecord(base - 2 * h, base - h, 90, 89, true, emptyList(), 0)),
            base,
        )
        assertTrue(usage.partial)
        assertTrue(usage.hasData)
    }

    @Test
    fun emptyLedgerHasNoData() {
        val usage = SleepLedger.usage(emptyList(), base)
        assertEquals(0L, usage.screenOffMs)
        assertEquals(0L, usage.idleMs)
        assertTrue(usage.partial)
        assertFalse(usage.hasData)
    }

    @Test
    fun restSessionsMergeShortScreenOnGaps() {
        val now = base
        val records = listOf(
            SleepRecord(base - 10 * h, base - 6 * h, 100, 96, true, emptyList(), 0),
            SleepRecord(base - 6 * h + m, base - 2 * h, 96, 92, true, emptyList(), 0),
            SleepRecord(base - h, base - 30 * m, 92, 91, true, emptyList(), 0),
        )

        val sessions = SleepLedger.restSessions(records, now)

        assertEquals(2, sessions.size)
        assertEquals(8 * h, sessions[0].durationMs)
        assertEquals(100, sessions[0].levelStart)
        assertEquals(92, sessions[0].levelEnd)
        assertEquals(30 * m, sessions[1].durationMs)
        assertEquals(30 * m, SleepLedger.lastRest(records, now)?.durationMs)
    }

    @Test
    fun shortSessionsAreNotRest() {
        val record = SleepRecord(base - 20 * m, base - 10 * m, 90, 89, true, emptyList(), 0)
        assertTrue(SleepLedger.restSessions(listOf(record), base).isEmpty())
        assertNull(SleepLedger.lastRest(listOf(record), base))
    }

    @Test
    fun maintenanceCountsReEntries() {
        var record: SleepRecord? = SleepLedger.start(base, 90, deep = true)
        record = SleepLedger.enterIdle(record, base + m)
        record = SleepLedger.leaveIdle(record, base + 2 * m)
        record = SleepLedger.enterIdle(record, base + 3 * m)
        record = SleepLedger.close(record, base + 4 * m, 89)

        assertEquals(1, record?.maintenance)
        assertEquals(2 * m, record?.idle?.sumOf { it.endAt - it.startAt })
    }

    @Test
    fun closeEndsTheOpenIdleSpan() {
        var record: SleepRecord? = SleepLedger.start(base, 90, deep = true)
        record = SleepLedger.enterIdle(record, base + m)
        record = SleepLedger.close(record, base + 3 * m, 89)

        assertEquals(base + 3 * m, record?.idle?.single()?.endAt)
        assertEquals(base + 3 * m, record?.endAt)
    }

    @Test
    fun rateUsesTheSessionBatteryDelta() {
        val session = RestSession(
            startAt = base,
            endAt = base + 8 * h,
            levelStart = 100,
            levelEnd = 92,
            deep = true,
            idleMs = 7 * h,
            maintenance = 1,
            open = false,
        )
        assertEquals(1.0, session.perHour ?: 0.0, 0.0001)
    }

    @Test
    fun rateIsNullWhenTheSampleIsNotUsable() {
        val short = RestSession(base, base + 10 * m, 100, 99, true, 0, 0, false)
        val charged = RestSession(base, base + 8 * h, 90, 95, true, 0, 0, false)
        assertNull(short.perHour)
        assertNull(charged.perHour)
        assertEquals(
            0.0,
            RestSession(base, base + 8 * h, 90, 90, true, 0, 0, false).perHour ?: 0.0,
            0.0001,
        )
    }

    @Test
    fun decodeIgnoresGarbage() {
        assertTrue(SleepLedger.decode(null).isEmpty())
        assertTrue(SleepLedger.decode("").isEmpty())
        assertTrue(SleepLedger.decode("x").isEmpty())
        assertTrue(SleepLedger.decode("1|2|3").isEmpty())
        assertTrue(SleepLedger.decode("0|0|90|89|1|0|").isEmpty())
        assertTrue(SleepLedger.decode("$base|${base - h}|90|89|1|0|").isEmpty())
    }
}
