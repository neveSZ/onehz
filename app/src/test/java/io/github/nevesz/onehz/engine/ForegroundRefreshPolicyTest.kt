package io.github.nevesz.onehz.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRefreshPolicyTest {

    @Test
    fun `app assignments keep the touch monitor alive for a fixed profile`() {
        assertTrue(
            ForegroundRefreshPolicy.shouldMonitorTouch(
                screenOff = false,
                hasAssignments = true,
                stepCount = 1,
                videoEnabled = false
            )
        )
    }

    @Test
    fun `a fixed profile without assignments or video releases the touch monitor`() {
        assertFalse(
            ForegroundRefreshPolicy.shouldMonitorTouch(
                screenOff = false,
                hasAssignments = false,
                stepCount = 1,
                videoEnabled = false
            )
        )
    }

    @Test
    fun `multi-step profiles and video still require the touch monitor`() {
        assertTrue(
            ForegroundRefreshPolicy.shouldMonitorTouch(
                screenOff = false,
                hasAssignments = false,
                stepCount = 2,
                videoEnabled = false
            )
        )
        assertTrue(
            ForegroundRefreshPolicy.shouldMonitorTouch(
                screenOff = false,
                hasAssignments = false,
                stepCount = 1,
                videoEnabled = true
            )
        )
    }

    @Test
    fun `screen off always releases the touch monitor`() {
        assertFalse(
            ForegroundRefreshPolicy.shouldMonitorTouch(
                screenOff = true,
                hasAssignments = true,
                stepCount = 3,
                videoEnabled = true
            )
        )
    }

    @Test
    fun `foreground monitoring needs assignments and an interactive screen`() {
        assertTrue(
            ForegroundRefreshPolicy.shouldMonitorForeground(
                started = true,
                screenOff = false,
                hasAssignments = true,
                videoEnabled = false
            )
        )
        assertFalse(
            ForegroundRefreshPolicy.shouldMonitorForeground(
                started = true,
                screenOff = true,
                hasAssignments = true,
                videoEnabled = false
            )
        )
        assertFalse(
            ForegroundRefreshPolicy.shouldMonitorForeground(
                started = true,
                screenOff = false,
                hasAssignments = false,
                videoEnabled = false
            )
        )
        assertTrue(
            ForegroundRefreshPolicy.shouldMonitorForeground(
                started = true,
                screenOff = false,
                hasAssignments = false,
                videoEnabled = true
            )
        )
    }

    @Test
    fun `touch probe is only the fallback when the listener is inactive`() {
        assertTrue(
            ForegroundRefreshPolicy.shouldProbe(
                started = true,
                screenOff = false,
                hasAssignments = true,
                videoEnabled = false,
                listenerActive = false
            )
        )
        assertFalse(
            ForegroundRefreshPolicy.shouldProbe(
                started = true,
                screenOff = false,
                hasAssignments = true,
                videoEnabled = false,
                listenerActive = true
            )
        )
        assertFalse(
            ForegroundRefreshPolicy.shouldProbe(
                started = true,
                screenOff = false,
                hasAssignments = false,
                videoEnabled = false,
                listenerActive = false
            )
        )
        assertTrue(
            ForegroundRefreshPolicy.shouldProbe(
                started = true,
                screenOff = false,
                hasAssignments = false,
                videoEnabled = true,
                listenerActive = false
            )
        )
    }

    @Test
    fun `probe delay bypasses the foreground cache window`() {
        assertTrue(ForegroundRefreshPolicy.PROBE_DELAY_MS > 250L)
    }
}
