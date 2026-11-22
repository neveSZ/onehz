package io.github.nevesz.onehz.core.backup

import io.github.nevesz.onehz.core.SleepWindow
import io.github.nevesz.onehz.core.profile.ProfileCodec
import io.github.nevesz.onehz.core.profile.ProfileState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sleep settings that are part of the configuration backup. Transient state (pause,
 * forced state, measurements) and the system Doze whitelist are intentionally excluded.
 */
data class SleepBackup(
    val enabled: Boolean = false,
    val deep: Boolean = true,
    val skipCharging: Boolean = true,
    val skipMusic: Boolean = true,
    val windowEnabled: Boolean = false,
    val windowStartMinute: Int = 23 * 60,
    val windowEndMinute: Int = 7 * 60
)

data class BackupData(
    val state: ProfileState,
    val enabled: Boolean,
    val aodEnabled: Boolean,
    val aodBrightnessEnabled: Boolean,
    val aodBrightnessPct: Int,
    val aodBrightnessMinPct: Int,
    val aodBrightnessMaxPct: Int,
    val forcedVideoPackages: Set<String>,
    val sleep: SleepBackup = SleepBackup(),
    val appVersion: String? = null,
    val deviceRates: List<Int> = emptyList()
)

/** Versioned JSON file format used by manual export/import. */
object BackupCodec {

    const val SCHEMA_VERSION = 1
    const val APP_ID = "io.github.nevesz.onehz"

    fun encode(data: BackupData): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("app", APP_ID)
        data.appVersion?.let { put("appVersion", it) }
        put("deviceRates", JSONArray(data.deviceRates))
        put("settings", JSONObject().apply {
            put("enabled", data.enabled)
            put("aodEnabled", data.aodEnabled)
            put("aodBrightnessEnabled", data.aodBrightnessEnabled)
            put("aodBrightnessPct", data.aodBrightnessPct)
            put("aodBrightnessMinPct", data.aodBrightnessMinPct)
            put("aodBrightnessMaxPct", data.aodBrightnessMaxPct)
            put("forcedVideoPackages", JSONArray(data.forcedVideoPackages.sorted()))
        })
        put("sleep", JSONObject().apply {
            put("enabled", data.sleep.enabled)
            put("deep", data.sleep.deep)
            put("skipCharging", data.sleep.skipCharging)
            put("skipMusic", data.sleep.skipMusic)
            put("windowEnabled", data.sleep.windowEnabled)
            put("windowStartMinute", data.sleep.windowStartMinute)
            put("windowEndMinute", data.sleep.windowEndMinute)
        })
        put("profileState", JSONObject(ProfileCodec.encode(data.state)))
    }.toString(2)

    fun decode(raw: String?): BackupData? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            val root = JSONObject(text)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
            if (root.optString("app") != APP_ID) return null

            val profileJson = root.optJSONObject("profileState") ?: return null
            val state = ProfileCodec.decode(profileJson.toString()) ?: return null
            val settings = root.optJSONObject("settings") ?: JSONObject()

            val forced = mutableSetOf<String>()
            val forcedJson = settings.optJSONArray("forcedVideoPackages")
            if (forcedJson != null) {
                for (index in 0 until minOf(forcedJson.length(), 64)) {
                    val value = forcedJson.optString(index).trim()
                    if (value.isNotEmpty()) forced += value
                }
            }

            val deviceRates = mutableListOf<Int>()
            root.optJSONArray("deviceRates")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optInt(index, 0).takeIf { it > 0 }?.let { deviceRates += it }
                }
            }

            val sleepValue = root.optJSONObject("sleep") ?: JSONObject()
            val sleep = SleepBackup(
                enabled = sleepValue.optBoolean("enabled", false),
                deep = sleepValue.optBoolean("deep", true),
                skipCharging = sleepValue.optBoolean("skipCharging", true),
                skipMusic = sleepValue.optBoolean("skipMusic", true),
                windowEnabled = sleepValue.optBoolean("windowEnabled", false),
                windowStartMinute = sleepValue.optInt("windowStartMinute", 23 * 60)
                    .coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1),
                windowEndMinute = sleepValue.optInt("windowEndMinute", 7 * 60)
                    .coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1)
            )

            BackupData(
                state = state,
                enabled = settings.optBoolean("enabled", true),
                aodEnabled = settings.optBoolean("aodEnabled", false),
                aodBrightnessEnabled = settings.optBoolean("aodBrightnessEnabled", false),
                aodBrightnessPct = settings.optInt("aodBrightnessPct", 10).coerceIn(1, 100),
                aodBrightnessMinPct = settings.optInt("aodBrightnessMinPct", 5).coerceIn(1, 99),
                aodBrightnessMaxPct = settings.optInt("aodBrightnessMaxPct", 30).coerceIn(2, 100),
                forcedVideoPackages = forced,
                sleep = sleep,
                appVersion = root.optString("appVersion").takeIf { it.isNotBlank() },
                deviceRates = deviceRates
            )
        }.getOrNull()
    }
}
