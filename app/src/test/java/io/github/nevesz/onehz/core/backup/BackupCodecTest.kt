package io.github.nevesz.onehz.core.backup

import io.github.nevesz.onehz.core.profile.AppAssignment
import io.github.nevesz.onehz.core.profile.ProfileDefaults
import io.github.nevesz.onehz.core.profile.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val data = BackupData(
        state = ProfileState(
            profiles = ProfileDefaults.forRates(listOf(30, 60, 90, 120)),
            activeProfileId = "performance",
            assignments = listOf(AppAssignment("com.example.game", "performance")),
            autoEconomyOnBatterySaver = true
        ),
        enabled = true,
        aodEnabled = true,
        aodBrightnessEnabled = false,
        aodBrightnessPct = 12,
        aodBrightnessMinPct = 6,
        aodBrightnessMaxPct = 25,
        forcedVideoPackages = setOf("com.example.video"),
        appVersion = "1.2.0-beta.1",
        deviceRates = listOf(30, 60, 90, 120)
    )

    @Test
    fun `round trip preserves profiles settings and assignments`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data, decoded)
    }

    @Test
    fun `wrong app id is rejected`() {
        val raw = BackupCodec.encode(data).replace(
            "\"app\": \"io.github.nevesz.onehz\"",
            "\"app\": \"com.example.other\""
        )
        assertNull(BackupCodec.decode(raw))
    }

    @Test
    fun `unknown schema is rejected`() {
        val raw = BackupCodec.encode(data).replace(
            "\"schemaVersion\": 1",
            "\"schemaVersion\": 2"
        )
        assertNull(BackupCodec.decode(raw))
    }

    @Test
    fun `empty input is rejected`() {
        assertNull(BackupCodec.decode(""))
        assertNull(BackupCodec.decode("{}"))
    }

    @Test
    fun `forced packages are preserved`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(data))
        assertTrue(decoded?.forcedVideoPackages?.contains("com.example.video") == true)
    }
}
