package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationRulesTest {

    @Test
    fun `window crosses midnight`() {
        assertTrue(ActivationRulesLogic.inWindow(23 * 60 + 30, 23 * 60, 7 * 60))
        assertTrue(ActivationRulesLogic.inWindow(6 * 60 + 59, 23 * 60, 7 * 60))
        assertFalse(ActivationRulesLogic.inWindow(12 * 60, 23 * 60, 7 * 60))
    }

    @Test
    fun `low battery wins over the other rules`() {
        val rules = ActivationRules(
            lowBatteryEnabled = true,
            lowBatteryProfileId = "economy",
            chargingEnabled = true,
            chargingProfileId = "performance",
            windowEnabled = true,
            windowProfileId = "balanced"
        )
        val decision = ActivationRulesLogic.evaluate(
            rules = rules,
            batteryLevel = 10,
            charging = true,
            minuteOfDay = 23 * 60 + 30,
            lowBatteryActive = true
        )
        assertEquals(ActivationDecision("economy", "guard:battery"), decision)
    }

    @Test
    fun `charging is skipped while plugged out`() {
        val rules = ActivationRules(chargingEnabled = true, chargingProfileId = "performance")
        assertNull(
            ActivationRulesLogic.evaluate(rules, 50, false, 12 * 60, false)
        )
    }

    @Test
    fun `battery guard has hysteresis`() {
        assertTrue(BatteryGuard.update(15, 15, wasActive = false))
        assertTrue(BatteryGuard.update(18, 15, wasActive = true))
        assertFalse(BatteryGuard.update(20, 15, wasActive = true))
        assertFalse(BatteryGuard.update(16, 15, wasActive = false))
    }

    @Test
    fun `codec keeps every rule`() {
        val rules = ActivationRules(
            lowBatteryEnabled = true,
            lowBatteryPercent = 20,
            lowBatteryProfileId = "economy",
            chargingEnabled = true,
            chargingProfileId = "performance",
            windowEnabled = true,
            windowStartMinute = 1200,
            windowEndMinute = 420,
            windowProfileId = "balanced"
        )
        assertEquals(rules, ActivationRulesCodec.decode(ActivationRulesCodec.encode(rules)))
    }
}
