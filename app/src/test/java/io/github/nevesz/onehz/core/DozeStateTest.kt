package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DozeStateTest {

    private val active = """
        Settings:
          inactive_to=1800000
        Whitelist system apps:
        Whitelist user apps:
        mState=ACTIVE
        mLightState=ACTIVE
    """.trimIndent()

    private val idle = """
        mState=IDLE
        mLightState=ACTIVE
    """.trimIndent()

    private val maintenance = """
        mState=IDLE_MAINTENANCE
    """.trimIndent()

    private val pending = """
        mState=IDLE_PENDING
    """.trimIndent()

    @Test
    fun parsesActive() {
        assertEquals("ACTIVE", DozeState.parse(active))
        assertFalse(DozeState.isIdle("ACTIVE"))
    }

    @Test
    fun parsesDeepIdle() {
        assertEquals("IDLE", DozeState.parse(idle))
        assertTrue(DozeState.isIdle("IDLE"))
    }

    @Test
    fun maintenanceCountsAsIdle() {
        assertEquals("IDLE_MAINTENANCE", DozeState.parse(maintenance))
        assertTrue(DozeState.isIdle("IDLE_MAINTENANCE"))
    }

    @Test
    fun pendingIsNotIdle() {
        assertEquals("IDLE_PENDING", DozeState.parse(pending))
        assertFalse(DozeState.isIdle("IDLE_PENDING"))
    }

    @Test
    fun missingStateIsEmpty() {
        assertEquals("", DozeState.parse(null))
        assertEquals("", DozeState.parse(""))
        assertFalse(DozeState.isIdle(""))
    }

    @Test
    fun firstStateWins() {
        val mixed = "mState=ACTIVE\nmState=IDLE"
        assertEquals("ACTIVE", DozeState.parse(mixed))
    }

    @Test
    fun encodesDeepAndLightStates() {
        val dump = "mState=INACTIVE\nmLightState=IDLE"
        assertEquals("INACTIVE|IDLE", DozeState.encode(dump))
        assertTrue(DozeState.isIdleEncoded("INACTIVE|IDLE"))
        assertFalse(DozeState.isIdleEncoded("ACTIVE|ACTIVE"))
        assertTrue(DozeState.isIdleEncoded("ACTIVE|IDLE_MAINTENANCE"))
        assertFalse(DozeState.isIdleEncoded(null))
    }

    @Test
    fun parsesLightStateSeparately() {
        val dump = "mState=INACTIVE\nmLightState=IDLE"
        assertEquals("IDLE", DozeState.parseLight(dump))
        assertEquals("INACTIVE", DozeState.parse(dump))
    }
}
