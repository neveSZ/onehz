package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPauseTest {
    @Test
    fun pausedWhileDeadlineIsInTheFuture() {
        assertTrue(MasterPause.isPaused(until = 2_000L, now = 1_000L))
        assertEquals(1_000L, MasterPause.remainingMs(until = 2_000L, now = 1_000L))
    }

    @Test
    fun expiredAtTheDeadline() {
        assertFalse(MasterPause.isPaused(until = 1_000L, now = 1_000L))
        assertFalse(MasterPause.isPaused(until = 1_000L, now = 1_500L))
        assertEquals(0L, MasterPause.remainingMs(until = 1_000L, now = 1_500L))
    }

    @Test
    fun noDeadlineIsNotPaused() {
        assertFalse(MasterPause.isPaused(until = 0L, now = 1_000L))
    }
}
