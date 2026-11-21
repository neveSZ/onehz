package io.github.nevesz.onehz.engine

import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.TierStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyResolverTest {

    private val normal = FrequencyProfile.of(TierStep(120, 1_000L), TierStep(60))
    private val eco = FrequencyProfile.of(TierStep(90, 1_000L), TierStep(30))
    private val app = FrequencyProfile.of(TierStep(96, 1_000L), TierStep(48))

    private fun input(
        screenOff: Boolean = false,
        ecoActive: Boolean = false,
        aodEnabled: Boolean = false,
        aodHz: Int = 30,
        normalEnabled: Boolean = true,
        normalProfile: FrequencyProfile = normal,
        ecoEnabled: Boolean = true,
        ecoProfile: FrequencyProfile = eco,
        appProfile: OneHzPreferences.AppProfile? = null
    ) = PolicyInput(
        screenOff = screenOff,
        ecoActive = ecoActive,
        aodEnabled = aodEnabled,
        aodHz = aodHz,
        normalEnabled = normalEnabled,
        normalProfile = normalProfile,
        ecoEnabled = ecoEnabled,
        ecoProfile = ecoProfile,
        appProfile = appProfile
    )

    @Test
    fun `screen off with AOD disabled leaves the system alone`() {
        val decision = PolicyResolver.decide(input(screenOff = true), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.AOD_DISABLED), decision)
    }

    @Test
    fun `screen off with AOD enabled uses a fixed AOD rate`() {
        val decision = PolicyResolver.decide(input(screenOff = true, aodEnabled = true, aodHz = 10), 0)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("AOD", policy.layer)
        assertEquals(listOf(10), policy.profile.steps.map { it.hz })
    }

    @Test
    fun `eco disabled leaves the system alone when battery saver is active`() {
        val decision = PolicyResolver.decide(input(ecoActive = true, ecoEnabled = false), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.ECO_PROFILE_DISABLED), decision)
    }

    @Test
    fun `eco profile wins over an app profile without an exception`() {
        val decision = PolicyResolver.decide(
            input(
                ecoActive = true,
                appProfile = OneHzPreferences.AppProfile("com.example.app", app)
            ),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("ECO", policy.layer)
        assertEquals(eco, policy.profile)
    }

    @Test
    fun `an app exception keeps its own profile during battery saver`() {
        val decision = PolicyResolver.decide(
            input(
                ecoActive = true,
                appProfile = OneHzPreferences.AppProfile("com.example.app", app, ignoreEco = true)
            ),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("app:com.example.app", policy.layer)
        assertEquals(app, policy.profile)
    }

    @Test
    fun `normal profile disabled leaves the system alone`() {
        val decision = PolicyResolver.decide(input(normalEnabled = false), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.NORMAL_PROFILE_DISABLED), decision)
    }

    @Test
    fun `app profile wins over the normal profile`() {
        val decision = PolicyResolver.decide(
            input(appProfile = OneHzPreferences.AppProfile("com.example.app", app)),
            1
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("app:com.example.app", policy.layer)
        assertEquals(48, policy.profile.hzAt(policy.index))
    }

    @Test
    fun `empty app profile falls back to the normal profile`() {
        val decision = PolicyResolver.decide(
            input(appProfile = OneHzPreferences.AppProfile("com.example.app", FrequencyProfile())),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("normal", policy.layer)
        assertEquals(normal, policy.profile)
    }

    @Test
    fun `empty normal profile goes idle`() {
        val decision = PolicyResolver.decide(input(normalProfile = FrequencyProfile()), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.EMPTY_PROFILE), decision)
    }

    @Test
    fun `index is clamped to the profile size`() {
        val decision = PolicyResolver.decide(input(), 99)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals(1, policy.index)
        assertTrue(policy.index <= policy.profile.lastIndex)
    }
}
