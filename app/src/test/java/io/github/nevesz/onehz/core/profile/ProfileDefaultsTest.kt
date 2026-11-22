package io.github.nevesz.onehz.core.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDefaultsTest {

    private fun hz(profile: DeviceProfile): List<Int> = profile.frequency.steps.map { it.hz }
    private fun holds(profile: DeviceProfile): List<Long?> = profile.frequency.steps.map { it.holdMs }

    @Test
    fun `two rate panel differs only by maximum hold`() {
        val profiles = ProfileDefaults.forRates(listOf(60, 120))
        assertEquals(listOf(120, 60), hz(profiles[0]))
        assertEquals(listOf(120, 60), hz(profiles[1]))
        assertEquals(listOf(120, 60), hz(profiles[2]))
        assertEquals(listOf(1_000L, null), holds(profiles[0]))
        assertEquals(listOf(2_000L, null), holds(profiles[1]))
        assertEquals(listOf(5_000L, null), holds(profiles[2]))
    }

    @Test
    fun `s25 panel creates economy 60 and balanced 80`() {
        val rates = listOf(10, 20, 24, 27, 30, 40, 48, 60, 80, 120)
        val profiles = ProfileDefaults.forRates(rates)
        assertEquals(listOf(120, 60, 10), hz(profiles[0]))
        assertEquals(listOf(120, 80, 10), hz(profiles[1]))
        assertEquals(listOf(120, 80, 60), hz(profiles[2]))
        assertEquals(listOf(1_000L, 6_000L, null), holds(profiles[0]))
        assertEquals(listOf(2_000L, 5_000L, null), holds(profiles[1]))
        assertEquals(listOf(5_000L, 8_000L, null), holds(profiles[2]))
        assertEquals(30, profiles[0].video.minHz)
        assertEquals(60, profiles[1].video.minHz)
        assertEquals(60, profiles[2].video.minHz)
    }

    @Test
    fun `economy uses the highest rate below 60 when 60 is absent`() {
        val profiles = ProfileDefaults.forRates(listOf(30, 48, 120))
        assertEquals(listOf(120, 48, 30), hz(profiles[0]))
        assertEquals(listOf(120, 48, 30), hz(profiles[1]))
        assertEquals(listOf(120, 48), hz(profiles[2]))
    }

    @Test
    fun `balanced falls back to 60 when no rate is above 60 below max`() {
        val profiles = ProfileDefaults.forRates(listOf(30, 60, 120))
        assertEquals(listOf(120, 60, 30), hz(profiles[0]))
        assertEquals(listOf(120, 60, 30), hz(profiles[1]))
        assertEquals(listOf(120, 60), hz(profiles[2]))
    }

    @Test
    fun `panel without 60 and with 90 uses 90 for balanced and performance`() {
        val profiles = ProfileDefaults.forRates(listOf(30, 90, 120))
        assertEquals(listOf(120, 90, 30), hz(profiles[0]))
        assertEquals(listOf(120, 90, 30), hz(profiles[1]))
        assertEquals(listOf(120, 90), hz(profiles[2]))
    }

    @Test
    fun `panel whose minimum is 60 does not add a high middle to economy`() {
        val profiles = ProfileDefaults.forRates(listOf(120, 90, 60))
        assertEquals(listOf(120, 60), hz(profiles[0]))
        assertEquals(listOf(120, 90, 60), hz(profiles[1]))
        assertEquals(listOf(120, 90, 60), hz(profiles[2]))
    }

    @Test
    fun `high refresh panel uses the highest rate above 60 as middle`() {
        val profiles = ProfileDefaults.forRates(listOf(60, 90, 120, 144))
        assertEquals(listOf(144, 60), hz(profiles[0]))
        assertEquals(listOf(144, 120, 60), hz(profiles[1]))
        assertEquals(listOf(144, 120, 60), hz(profiles[2]))
    }

    @Test
    fun `duplicates and unsorted input are normalized`() {
        val profiles = ProfileDefaults.forRates(listOf(120, 60, 120, 30, 60))
        assertEquals(listOf(120, 60, 30), hz(profiles[0]))
        assertEquals(listOf(120, 60, 30), hz(profiles[1]))
        assertEquals(listOf(120, 60), hz(profiles[2]))
    }

    @Test
    fun `single rate panel is fixed`() {
        val profiles = ProfileDefaults.forRates(listOf(60))
        profiles.forEach { profile ->
            assertEquals(listOf(60), hz(profile))
            assertEquals(listOf<Long?>(null), holds(profile))
        }
    }
}
