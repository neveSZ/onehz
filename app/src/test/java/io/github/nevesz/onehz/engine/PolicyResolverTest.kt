package io.github.nevesz.onehz.engine

import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.TierStep
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileKind
import io.github.nevesz.onehz.core.profile.VideoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyResolverTest {

    private val balanced = DeviceProfile(
        id = "balanced",
        kind = ProfileKind.BALANCED,
        frequency = FrequencyProfile.of(TierStep(120, 1_000L), TierStep(60))
    )
    private val economy = DeviceProfile(
        id = "economy",
        kind = ProfileKind.ECONOMY,
        frequency = FrequencyProfile.of(TierStep(90, 1_000L), TierStep(30))
    )
    private val performance = DeviceProfile(
        id = "performance",
        kind = ProfileKind.PERFORMANCE,
        frequency = FrequencyProfile.of(TierStep(120, 5_000L), TierStep(60)),
        video = VideoSettings(enabled = true, minHz = 60)
    )
    private val appProfile = DeviceProfile(
        id = "custom_app",
        kind = ProfileKind.CUSTOM,
        name = "App",
        frequency = FrequencyProfile.of(TierStep(96, 1_000L), TierStep(48))
    )

    private fun input(
        screenOff: Boolean = false,
        batterySaver: Boolean = false,
        autoEconomy: Boolean = true,
        aodMasterEnabled: Boolean = false,
        aodRateEnabled: Boolean = false,
        aodHz: Int = 10,
        selectedProfile: DeviceProfile? = balanced,
        economyProfile: DeviceProfile? = economy,
        app: DeviceProfile? = null,
        appIgnoreEco: Boolean = false,
        paused: Boolean = false,
        ruleProfile: DeviceProfile? = null,
        ruleLayer: String? = null,
        appTarget: io.github.nevesz.onehz.core.profile.AppTarget? = null
    ) = PolicyInput(
        screenOff = screenOff,
        batterySaver = batterySaver,
        autoEconomy = autoEconomy,
        aodMasterEnabled = aodMasterEnabled,
        aodRateEnabled = aodRateEnabled,
        aodHz = aodHz,
        selectedProfile = selectedProfile,
        economyProfile = economyProfile,
        appProfile = app,
        appPackageName = app?.let { "com.example.app" },
        appIgnoreEco = appIgnoreEco,
        appTarget = appTarget,
        paused = paused,
        ruleProfile = ruleProfile,
        ruleLayer = ruleLayer
    )

    @Test
    fun `fixed app target pins the rate`() {
        val decision = PolicyResolver.decide(
            input(appTarget = io.github.nevesz.onehz.core.profile.AppTarget.Fixed(60)),
            0
        )
        assertTrue(decision is PolicyDecision.Apply)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("app:fixed:60", policy.layer)
        assertEquals(60, policy.profile.steps.first().hz)
    }

    @Test
    fun `ignored app stops control`() {
        val decision = PolicyResolver.decide(
            input(appTarget = io.github.nevesz.onehz.core.profile.AppTarget.Ignore),
            0
        )
        assertEquals(PolicyDecision.Idle(IdleReason.APP_IGNORED), decision)
    }

    @Test
    fun `activation rule wins over the app and selected profile`() {
        val decision = PolicyResolver.decide(
            input(app = appProfile, ruleProfile = performance, ruleLayer = "rule:window"),
            0
        )
        assertTrue(decision is PolicyDecision.Apply)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("performance", policy.profileId)
        assertEquals("rule:window", policy.layer)
    }

    @Test
    fun `pause wins over an activation rule`() {
        val decision = PolicyResolver.decide(
            input(paused = true, ruleProfile = performance, ruleLayer = "rule:charging"),
            0
        )
        assertEquals(PolicyDecision.Idle(IdleReason.PAUSED), decision)
    }

    @Test
    fun `pause wins over every other policy`() {
        val decision = PolicyResolver.decide(
            input(
                screenOff = true,
                aodMasterEnabled = true,
                aodRateEnabled = true,
                app = appProfile,
                paused = true
            ),
            0
        )
        assertEquals(PolicyDecision.Idle(IdleReason.PAUSED), decision)
    }

    @Test
    fun `pause leaves the system alone while screen is on`() {
        val decision = PolicyResolver.decide(input(paused = true), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.PAUSED), decision)
    }

    @Test
    fun `screen off with AOD disabled leaves the system alone`() {
        val decision = PolicyResolver.decide(input(screenOff = true), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.AOD_DISABLED), decision)
    }

    @Test
    fun `screen off with AOD master off leaves the system alone even if rate is on`() {
        val decision = PolicyResolver.decide(
            input(screenOff = true, aodMasterEnabled = false, aodRateEnabled = true),
            0
        )
        assertEquals(PolicyDecision.Idle(IdleReason.AOD_DISABLED), decision)
    }

    @Test
    fun `screen off with AOD rate off leaves the system alone even if master is on`() {
        val decision = PolicyResolver.decide(
            input(screenOff = true, aodMasterEnabled = true, aodRateEnabled = false),
            0
        )
        assertEquals(PolicyDecision.Idle(IdleReason.AOD_DISABLED), decision)
    }

    @Test
    fun `screen off with AOD enabled uses the panel minimum`() {
        val decision = PolicyResolver.decide(
            input(
                screenOff = true,
                aodMasterEnabled = true,
                aodRateEnabled = true,
                aodHz = 10
            ),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("AOD", policy.layer)
        assertEquals(listOf(10), policy.profile.steps.map { it.hz })
        assertFalse(policy.video.enabled)
    }

    @Test
    fun `battery saver applies economy when automatic economy is on`() {
        val decision = PolicyResolver.decide(input(batterySaver = true), 0)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("economy", policy.layer)
        assertEquals(economy.frequency, policy.profile)
    }

    @Test
    fun `automatic economy can be disabled`() {
        val decision = PolicyResolver.decide(
            input(batterySaver = true, autoEconomy = false),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("profile:balanced", policy.layer)
    }

    @Test
    fun `app profile wins outside battery saver`() {
        val decision = PolicyResolver.decide(input(app = performance), 0)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("app:com.example.app", policy.layer)
        assertEquals(performance.frequency, policy.profile)
        assertEquals(60, policy.video.minHz)
    }

    @Test
    fun `app profile is ignored during automatic economy unless it opts out`() {
        val decision = PolicyResolver.decide(
            input(batterySaver = true, app = performance, appIgnoreEco = false),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("economy", policy.layer)
    }

    @Test
    fun `app exception keeps its profile during automatic economy`() {
        val decision = PolicyResolver.decide(
            input(batterySaver = true, app = performance, appIgnoreEco = true),
            0
        )
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("app:com.example.app", policy.layer)
        assertEquals(performance.frequency, policy.profile)
    }

    @Test
    fun `selected profile is used when no app assignment exists`() {
        val decision = PolicyResolver.decide(input(), 0)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals("profile:balanced", policy.layer)
    }

    @Test
    fun `missing selected profile goes idle`() {
        val decision = PolicyResolver.decide(input(selectedProfile = null), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.PROFILE_DISABLED), decision)
    }

    @Test
    fun `empty selected profile goes idle`() {
        val empty = balanced.copy(frequency = FrequencyProfile())
        val decision = PolicyResolver.decide(input(selectedProfile = empty), 0)
        assertEquals(PolicyDecision.Idle(IdleReason.PROFILE_EMPTY), decision)
    }

    @Test
    fun `index is clamped to the profile size`() {
        val decision = PolicyResolver.decide(input(), 99)
        val policy = (decision as PolicyDecision.Apply).policy
        assertEquals(1, policy.index)
        assertTrue(policy.index <= policy.profile.lastIndex)
    }
}
