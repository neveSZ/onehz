package io.github.nevesz.onehz.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DozeScheduleTest {

    private val night = SleepWindow(startMinute = 23 * 60, endMinute = 7 * 60)
    private val day = SleepWindow(startMinute = 8 * 60, endMinute = 16 * 60)

    @Test
    fun nightWindowCrossesMidnight() {
        assertTrue(night.contains(SleepWindow.minuteOfDay(23, 0)))
        assertTrue(night.contains(SleepWindow.minuteOfDay(3, 30)))
        assertTrue(night.contains(SleepWindow.minuteOfDay(6, 59)))
        assertFalse(night.contains(SleepWindow.minuteOfDay(7, 0)))
        assertFalse(night.contains(SleepWindow.minuteOfDay(12, 0)))
        assertFalse(night.contains(SleepWindow.minuteOfDay(22, 59)))
    }

    @Test
    fun dayWindowWorksForNightShifts() {
        assertTrue(day.contains(SleepWindow.minuteOfDay(8, 0)))
        assertTrue(day.contains(SleepWindow.minuteOfDay(15, 59)))
        assertFalse(day.contains(SleepWindow.minuteOfDay(16, 0)))
        assertFalse(day.contains(SleepWindow.minuteOfDay(2, 0)))
    }

    @Test
    fun equalBoundsMeanAllDay() {
        val allDay = SleepWindow(0, 0)
        assertTrue(allDay.contains(0))
        assertTrue(allDay.contains(12 * 60))
        assertTrue(allDay.contains(23 * 60 + 59))
    }

    @Test
    fun minuteOfDayWraps() {
        assertTrue(night.contains(-60))
        assertTrue(night.contains(SleepWindow.MINUTES_PER_DAY + 60))
    }
}
