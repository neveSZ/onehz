package io.github.nevesz.onehz.core.profile

import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.TierStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    private val state = ProfileState(
        profiles = ProfileDefaults.forRates(listOf(30, 60, 90, 120)),
        activeProfileId = "balanced",
        assignments = listOf(
            AppAssignment("com.example.game", AppTarget.Profile("performance")),
            AppAssignment("com.example.reader", AppTarget.Profile("economy"), ignoreEco = true),
            AppAssignment("com.example.player", AppTarget.Fixed(60)),
            AppAssignment("com.example.bank", AppTarget.Ignore)
        ),
        autoEconomyOnBatterySaver = false
    )

    @Test
    fun `round trip preserves profiles and assignments`() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode(state))
        assertNotNull(decoded)
        assertEquals(state, decoded)
    }

    @Test
    fun `missing active profile falls back to balanced`() {
        val raw = ProfileCodec.encode(state)
            .replace("\"activeProfileId\":\"balanced\"", "\"activeProfileId\":\"missing\"")
        val decoded = ProfileCodec.decode(raw)
        assertEquals("balanced", decoded?.activeProfileId)
    }

    @Test
    fun `invalid schema is rejected`() {
        val raw = ProfileCodec.encode(state)
            .replace("\"schemaVersion\":2", "\"schemaVersion\":99")
        assertNull(ProfileCodec.decode(raw))
    }

    @Test
    fun `ascending staircase is rejected`() {
        val json = ProfileCodec.encode(state)
            .replace("\"hz\":120", "\"hz\":20")
            .replace("\"hz\":90", "\"hz\":120")
        assertNull(ProfileCodec.decode(json))
    }

    @Test
    fun `assignment to unknown profile is dropped`() {
        val raw = ProfileCodec.encode(state).replace("\"performance\"", "\"missing\"")
        val decoded = ProfileCodec.decode(raw)
        val packages = decoded?.assignments?.map { it.packageName }
        assertNotNull(packages)
        assertTrue("com.example.game" !in packages!!)
        assertTrue("com.example.reader" in packages)
        assertEquals(3, packages.size)
    }

    @Test
    fun `adaptive staircase removes unsupported and duplicate rates`() {
        val original = FrequencyProfile.of(
            TierStep(144, 1_000L),
            TierStep(120, 2_000L),
            TierStep(60, 3_000L),
            TierStep(30)
        )
        val adapted = ProfileCodec.adaptFrequency(original, listOf(30, 60, 90, 120))
        assertEquals(listOf(120, 90, 60, 30), adapted.steps.map { it.hz })
        assertEquals(listOf(1_000L, 2_000L, 3_000L, null), adapted.steps.map { it.holdMs })
    }

    @Test
    fun `adaptive staircase fixes a last hold`() {
        val original = FrequencyProfile.of(
            TierStep(120, 1_000L),
            TierStep(60, 2_000L)
        )
        val adapted = ProfileCodec.adaptFrequency(original, listOf(60, 120))
        assertEquals(listOf(120, 60), adapted.steps.map { it.hz })
        assertEquals(listOf(1_000L, null), adapted.steps.map { it.holdMs })
    }

    @Test
    fun `codec accepts an empty assignment list`() {
        val withoutAssignments = state.copy(assignments = emptyList())
        val decoded = ProfileCodec.decode(ProfileCodec.encode(withoutAssignments))
        assertTrue(decoded?.assignments?.isEmpty() == true)
    }
}
