package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DozeDecisionTest {

    private val base = DozeInput(
        masterEnabled = true,
        featureEnabled = true,
        now = 1_000L,
        pausedUntil = 0L,
        windowEnabled = false,
        minuteOfDay = 12 * 60,
        skipCharging = false,
        charging = false,
        skipMusic = false,
        musicActive = false,
        serviceReady = true,
        deep = true,
    )

    @Test
    fun forcesDeepByDefault() {
        assertEquals(DozeDecision.Force(deep = true), DozeDecisionResolver.resolve(base))
        assertEquals(
            DozeDecision.Force(deep = false),
            DozeDecisionResolver.resolve(base.copy(deep = false)),
        )
    }

    @Test
    fun masterHasPriority() {
        val decision = DozeDecisionResolver.resolve(
            base.copy(masterEnabled = false, featureEnabled = false, charging = true),
        )
        assertEquals(DozeDecision.Skip(DozeSkip.MASTER_OFF), decision)
    }

    @Test
    fun disabledFeatureSkips() {
        assertEquals(
            DozeDecision.Skip(DozeSkip.DISABLED),
            DozeDecisionResolver.resolve(base.copy(featureEnabled = false)),
        )
    }

    @Test
    fun pauseSkipsUntilItExpires() {
        assertEquals(
            DozeDecision.Skip(DozeSkip.PAUSED),
            DozeDecisionResolver.resolve(base.copy(pausedUntil = 2_000L)),
        )
        assertEquals(
            DozeDecision.Force(deep = true),
            DozeDecisionResolver.resolve(base.copy(pausedUntil = 999L)),
        )
    }

    @Test
    fun windowIsOnlyCheckedWhenEnabled() {
        assertEquals(
            DozeDecision.Force(deep = true),
            DozeDecisionResolver.resolve(base.copy(minuteOfDay = 3 * 60)),
        )
        assertEquals(
            DozeDecision.Skip(DozeSkip.OUTSIDE_WINDOW),
            DozeDecisionResolver.resolve(base.copy(windowEnabled = true, minuteOfDay = 12 * 60)),
        )
    }

    @Test
    fun chargingAndMusicOnlySkipWhenRequested() {
        assertEquals(
            DozeDecision.Force(deep = true),
            DozeDecisionResolver.resolve(base.copy(charging = true, musicActive = true)),
        )
        assertEquals(
            DozeDecision.Skip(DozeSkip.CHARGING),
            DozeDecisionResolver.resolve(base.copy(skipCharging = true, charging = true)),
        )
        assertEquals(
            DozeDecision.Skip(DozeSkip.MUSIC),
            DozeDecisionResolver.resolve(base.copy(skipMusic = true, musicActive = true)),
        )
    }

    @Test
    fun missingServiceIsReportedLast() {
        assertEquals(
            DozeDecision.Skip(DozeSkip.CHARGING),
            DozeDecisionResolver.resolve(
                base.copy(serviceReady = false, skipCharging = true, charging = true),
            ),
        )
        assertEquals(
            DozeDecision.Skip(DozeSkip.SERVICE),
            DozeDecisionResolver.resolve(base.copy(serviceReady = false)),
        )
    }
}
