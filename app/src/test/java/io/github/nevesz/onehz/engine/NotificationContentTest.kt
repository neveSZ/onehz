package io.github.nevesz.onehz.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {

    private fun text(
        kind: NotificationKind,
        profileName: String?,
        hz: Int,
        showRate: Boolean,
        pausedLabel: String? = null
    ): String = NotificationContent.text(
        kind = kind,
        profileName = profileName,
        hz = hz,
        showRate = showRate,
        formatActive = { label, value -> "$label · $value Hz" },
        formatAod = { value -> "AOD · $value Hz" },
        activeLabel = "Ativo",
        aodLabel = "AOD",
        pausedLabel = pausedLabel,
        idleLabel = "Inativo"
    )

    @Test
    fun `pause wins over every other state`() {
        assertEquals(
            NotificationKind.PAUSED,
            NotificationContent.kind(true, null, "LOW", 60)
        )
        assertEquals(
            NotificationKind.PAUSED,
            NotificationContent.kind(true, IdleReason.PAUSED, "AOD", 10)
        )
    }

    @Test
    fun `idle reason means not controlling`() {
        assertEquals(
            NotificationKind.IDLE,
            NotificationContent.kind(false, IdleReason.APP_IGNORED, "LOW", 0)
        )
    }

    @Test
    fun `aod has its own state because it has no profile id`() {
        assertEquals(
            NotificationKind.AOD,
            NotificationContent.kind(false, null, "AOD", 10)
        )
    }

    @Test
    fun `active requires a positive rate`() {
        assertEquals(
            NotificationKind.ACTIVE,
            NotificationContent.kind(false, null, "LOW", 60)
        )
        assertEquals(
            NotificationKind.IDLE,
            NotificationContent.kind(false, null, "OFF", 0)
        )
    }

    @Test
    fun `active shows profile and rate when the setting is on`() {
        assertEquals(
            "Equilibrado · 60 Hz",
            text(NotificationKind.ACTIVE, "Equilibrado", 60, showRate = true)
        )
    }

    @Test
    fun `active shows only the profile when the setting is off`() {
        assertEquals(
            "Equilibrado",
            text(NotificationKind.ACTIVE, "Equilibrado", 60, showRate = false)
        )
    }

    @Test
    fun `active falls back to the label when the profile is unknown`() {
        assertEquals(
            "Ativo · 60 Hz",
            text(NotificationKind.ACTIVE, null, 60, showRate = true)
        )
        assertEquals(
            "Ativo",
            text(NotificationKind.ACTIVE, " ", 60, showRate = false)
        )
    }

    @Test
    fun `active never shows a zero rate`() {
        assertEquals(
            "Equilibrado",
            text(NotificationKind.ACTIVE, "Equilibrado", 0, showRate = true)
        )
    }

    @Test
    fun `aod follows the same rate setting`() {
        assertEquals("AOD · 10 Hz", text(NotificationKind.AOD, null, 10, true))
        assertEquals("AOD", text(NotificationKind.AOD, null, 10, false))
        assertEquals("AOD", text(NotificationKind.AOD, null, 0, true))
    }

    @Test
    fun `paused uses the deadline label and falls back to idle`() {
        assertEquals(
            "Pausado até 14:00",
            text(NotificationKind.PAUSED, "Equilibrado", 60, true, "Pausado até 14:00")
        )
        assertEquals(
            "Inativo",
            text(NotificationKind.PAUSED, null, 0, false, null)
        )
    }

    @Test
    fun `idle ignores the rate`() {
        assertEquals("Inativo", text(NotificationKind.IDLE, "Equilibrado", 60, true))
    }
}
