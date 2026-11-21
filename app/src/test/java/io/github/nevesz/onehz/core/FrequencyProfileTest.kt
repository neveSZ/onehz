package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyProfileTest {

    @Test
    fun `serialization round trip`() {
        val profile = FrequencyProfile.of(
            TierStep(120, 1_000L),
            TierStep(60, 10_000L),
            TierStep(10)
        )
        assertEquals(profile, FrequencyProfile.parse(profile.serialize()))
    }

    @Test
    fun `empty string means explicitly cleared`() {
        val parsed = FrequencyProfile.parse("")
        assertTrue(parsed != null && parsed.steps.isEmpty())
    }

    @Test
    fun `absent value stays null`() {
        assertNull(FrequencyProfile.parse(null))
    }

    @Test
    fun `last step cannot have a hold`() {
        assertNull(FrequencyProfile.parse("120:1000"))
    }

    @Test
    fun `steps must descend`() {
        assertNull(FrequencyProfile.parse("60:1000,120"))
    }

    @Test
    fun `more than five steps is invalid`() {
        assertNull(FrequencyProfile.parse("120:500,110:500,100:500,90:500,80:500,70"))
    }

    @Test
    fun `fixed profile has no hold`() {
        val profile = FrequencyProfile.fixed(60)
        assertEquals(1, profile.steps.size)
        assertNull(profile.steps[0].holdMs)
        assertTrue(profile.isFixed)
    }

    @Test
    fun `unsupported rate is reported`() {
        val profile = FrequencyProfile.of(TierStep(120, 1_000L), TierStep(60))
        assertTrue(profile.isValid(listOf(120, 60)))
        assertTrue(ProfileIssue.UNSUPPORTED_RATE in profile.issues(listOf(120, 90)))
    }

    @Test
    fun `hold outside the range is reported`() {
        val profile = FrequencyProfile.of(TierStep(120, 100L), TierStep(60))
        assertTrue(ProfileIssue.HOLD_OUT_OF_RANGE in profile.issues(listOf(120, 60)))
    }

    @Test
    fun `normalize hold snaps to half seconds`() {
        assertEquals(500L, FrequencyProfile.normalizeHold(0L))
        assertEquals(1_000L, FrequencyProfile.normalizeHold(1_300L))
        assertEquals(1_500L, FrequencyProfile.normalizeHold(1_520L))
        assertEquals(60_000L, FrequencyProfile.normalizeHold(99_999L))
    }

    @Test
    fun `device default goes ceiling then 60 then minimum`() {
        val profile = FrequencyProfile.deviceDefault(listOf(10, 24, 30, 48, 60, 80, 120))
        assertEquals(listOf(120, 60, 10), profile.steps.map { it.hz })
        assertEquals(listOf(2_000L, 5_000L, null), profile.steps.map { it.holdMs })
    }

    @Test
    fun `poco default is ceiling then 60 then 30`() {
        val profile = FrequencyProfile.deviceDefault(listOf(30, 60, 90, 120))
        assertEquals(listOf(120, 60, 30), profile.steps.map { it.hz })
        assertEquals(listOf(2_000L, 5_000L, null), profile.steps.map { it.holdMs })
    }

    @Test
    fun `eco default caps at the highest supported rate up to 60`() {
        val with60 = FrequencyProfile.deviceDefault(listOf(10, 24, 30, 48, 60, 90, 120), cap = 60)
        assertEquals(listOf(60, 10), with60.steps.map { it.hz })
        assertEquals(listOf(2_000L, null), with60.steps.map { it.holdMs })

        val with48 = FrequencyProfile.deviceDefault(listOf(10, 30, 48, 120), cap = 60)
        assertEquals(listOf(48, 10), with48.steps.map { it.hz })
    }

    @Test
    fun `panel with a 60 ceiling skips the duplicate middle step`() {
        val profile = FrequencyProfile.deviceDefault(listOf(10, 30, 60))
        assertEquals(listOf(60, 10), profile.steps.map { it.hz })
    }

    @Test
    fun `single rate panel is fixed`() {
        val profile = FrequencyProfile.deviceDefault(listOf(60))
        assertEquals(listOf(60), profile.steps.map { it.hz })
        assertNull(profile.steps[0].holdMs)
    }

    @Test
    fun `empty rate list falls back to a sane staircase`() {
        val profile = FrequencyProfile.deviceDefault(emptyList())
        assertEquals(listOf(120, 60, 10), profile.steps.map { it.hz })
    }

    @Test
    fun `panel without 60 picks the closest rate`() {
        val profile = FrequencyProfile.deviceDefault(listOf(10, 48, 120))
        assertEquals(listOf(120, 48, 10), profile.steps.map { it.hz })
    }
}
