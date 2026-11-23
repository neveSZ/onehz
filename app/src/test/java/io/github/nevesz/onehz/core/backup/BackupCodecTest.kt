package io.github.nevesz.onehz.core.backup

import io.github.nevesz.onehz.core.ActivationRules
import io.github.nevesz.onehz.core.profile.AppAssignment
import io.github.nevesz.onehz.core.profile.AppTarget
import io.github.nevesz.onehz.core.profile.ProfileDefaults
import io.github.nevesz.onehz.core.profile.ProfileState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val data = BackupData(
        state = ProfileState(
            profiles = ProfileDefaults.forRates(listOf(30, 60, 90, 120)),
            activeProfileId = "performance",
            assignments = listOf(
                AppAssignment("com.example.game", AppTarget.Profile("performance"))
            ),
            autoEconomyOnBatterySaver = true
        ),
        enabled = true,
        aodMasterEnabled = true,
        aodRateEnabled = true,
        aodBrightnessEnabled = false,
        aodBrightnessPct = 12,
        aodBrightnessMinPct = 6,
        aodBrightnessMaxPct = 25,
        forcedVideoPackages = setOf("com.example.video"),
        sleep = SleepBackup(
            enabled = true,
            deep = false,
            dozeWhileCharging = true,
            dozeWithMusic = true,
            windowEnabled = true,
            windowStartMinute = 22 * 60,
            windowEndMinute = 6 * 60
        ),
        rules = ActivationRules(
            lowBatteryEnabled = true,
            lowBatteryPercent = 20,
            lowBatteryProfileId = "economy",
            chargingEnabled = true,
            chargingProfileId = "performance",
            windowEnabled = true,
            windowStartMinute = 23 * 60,
            windowEndMinute = 7 * 60,
            windowProfileId = "balanced"
        ),
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
            "\"schemaVersion\": 2",
            "\"schemaVersion\": 3"
        )
        assertNull(BackupCodec.decode(raw))
    }

    @Test
    fun `backup without a sleep block keeps sleep absent`() {
        val root = JSONObject(BackupCodec.encode(data))
        root.remove("sleep")
        val decoded = BackupCodec.decode(root.toString())
        assertNotNull(decoded)
        assertNull(decoded?.sleep)
    }

    @Test
    fun `backup with a sleep block preserves it`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(
            SleepBackup(
                enabled = true,
                deep = false,
                dozeWhileCharging = true,
                dozeWithMusic = true,
                windowEnabled = true,
                windowStartMinute = 22 * 60,
                windowEndMinute = 6 * 60
            ),
            decoded?.sleep
        )
    }

    @Test
    fun `old backup derives doze with music from the negative switch`() {
        val root = JSONObject(BackupCodec.encode(data))
        val sleep = root.getJSONObject("sleep")
        sleep.remove("dozeWithMusic")
        sleep.put("skipMusic", false)
        assertEquals(true, BackupCodec.decode(root.toString())?.sleep?.dozeWithMusic)

        sleep.put("skipMusic", true)
        assertEquals(false, BackupCodec.decode(root.toString())?.sleep?.dozeWithMusic)
    }

    @Test
    fun `old backup derives doze while charging from the negative switch`() {
        val root = JSONObject(BackupCodec.encode(data))
        val sleep = root.getJSONObject("sleep")
        sleep.remove("dozeWhileCharging")
        sleep.put("skipCharging", false)
        assertEquals(true, BackupCodec.decode(root.toString())?.sleep?.dozeWhileCharging)

        sleep.put("skipCharging", true)
        assertEquals(false, BackupCodec.decode(root.toString())?.sleep?.dozeWhileCharging)

        sleep.remove("skipCharging")
        assertEquals(true, BackupCodec.decode(root.toString())?.sleep?.dozeWhileCharging)
    }

    @Test
    fun `new backup keeps the legacy charging switch compatible`() {
        val root = JSONObject(BackupCodec.encode(data))
        assertEquals(false, root.getJSONObject("sleep").optBoolean("skipCharging", true))
    }

    @Test
    fun `new backup keeps the legacy music switch compatible`() {
        val root = JSONObject(BackupCodec.encode(data))
        assertEquals(false, root.getJSONObject("sleep").optBoolean("skipMusic", true))
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

    @Test
    fun `old backup derives the AOD master from the sub-options`() {
        val root = JSONObject(BackupCodec.encode(data))
        root.getJSONObject("settings").remove("aodMasterEnabled")
        val decoded = BackupCodec.decode(root.toString())
        assertNotNull(decoded)
        assertTrue(decoded?.aodMasterEnabled == true)
    }

    @Test
    fun `old brightness-only backup derives the AOD master`() {
        val root = JSONObject(BackupCodec.encode(data))
        val settings = root.getJSONObject("settings")
        settings.remove("aodMasterEnabled")
        settings.put("aodEnabled", false)
        settings.put("aodBrightnessEnabled", true)
        val decoded = BackupCodec.decode(root.toString())
        assertNotNull(decoded)
        assertTrue(decoded?.aodMasterEnabled == true)
        assertEquals(false, decoded?.aodRateEnabled)
        assertEquals(true, decoded?.aodBrightnessEnabled)
    }

    @Test
    fun `new backup preserves an explicit AOD master off`() {
        val copy = data.copy(aodMasterEnabled = false, aodRateEnabled = true)
        val decoded = BackupCodec.decode(BackupCodec.encode(copy))
        assertEquals(false, decoded?.aodMasterEnabled)
        assertEquals(true, decoded?.aodRateEnabled)
    }
}
