package io.github.nevesz.onehz.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryWarningPolicyTest {

    @Test
    fun `the banner needs notifications and a missing exemption`() {
        assertTrue(BatteryWarningPolicy.shouldShowBanner(notificationsEnabled = true, exempt = false))
        assertFalse(BatteryWarningPolicy.shouldShowBanner(notificationsEnabled = true, exempt = true))
        assertFalse(BatteryWarningPolicy.shouldShowBanner(notificationsEnabled = false, exempt = false))
    }

    @Test
    fun `the prompt appears once and never when notifications are off`() {
        assertTrue(
            BatteryWarningPolicy.shouldPrompt(
                notificationsEnabled = true,
                exempt = false,
                alreadyPrompted = false
            )
        )
        assertFalse(
            BatteryWarningPolicy.shouldPrompt(
                notificationsEnabled = true,
                exempt = false,
                alreadyPrompted = true
            )
        )
        assertFalse(
            BatteryWarningPolicy.shouldPrompt(
                notificationsEnabled = false,
                exempt = false,
                alreadyPrompted = false
            )
        )
        assertFalse(
            BatteryWarningPolicy.shouldPrompt(
                notificationsEnabled = true,
                exempt = true,
                alreadyPrompted = false
            )
        )
    }
}
