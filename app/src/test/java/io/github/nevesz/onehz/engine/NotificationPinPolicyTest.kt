package io.github.nevesz.onehz.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPinPolicyTest {

    @Test
    fun dismissedNotificationStaysHiddenWhenNotPinned() {
        assertFalse(NotificationPinPolicy.canPost(pinned = false, dismissed = true))
        assertFalse(NotificationPinPolicy.restoresAfterDismiss(pinned = false))
    }

    @Test
    fun visibleNotificationCanBeRefreshedWhenNotPinned() {
        assertTrue(NotificationPinPolicy.canPost(pinned = false, dismissed = false))
    }

    @Test
    fun pinnedNotificationIsRestoredAfterDismiss() {
        assertTrue(NotificationPinPolicy.restoresAfterDismiss(pinned = true))
        assertTrue(NotificationPinPolicy.canPost(pinned = true, dismissed = true))
        assertTrue(NotificationPinPolicy.canPost(pinned = true, dismissed = false))
    }
}
