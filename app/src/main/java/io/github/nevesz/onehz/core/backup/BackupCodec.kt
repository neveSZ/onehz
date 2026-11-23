package io.github.nevesz.onehz.core.backup

import io.github.nevesz.onehz.core.ActivationRules
import io.github.nevesz.onehz.core.ActivationRulesCodec
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
    val dozeWhileCharging: Boolean = true,
    val dozeWithMusic: Boolean = false,
    val windowEnabled: Boolean = false,
    val windowStartMinute: Int = 23 * 60,
    val windowEndMinute: Int = 7 * 60
)

data class BackupData(
    val state: ProfileState,
    val enabled: Boolean,
    val aodMasterEnabled: Boolean,
    val aodRateEnabled: Boolean,
    val aodBrightnessEnabled: Boolean,
    val aodBrightnessPct: Int,
    val aodBrightnessMinPct: Int,
    val aodBrightnessMaxPct: Int,
    val forcedVideoPackages: Set<String>,
    /** Null means the file was created before Sleep settings were part of the backup. */
    val sleep: SleepBackup? = null,
    /** Null means the file was created before activation rules existed. */
    val rules: ActivationRules? = null,
    val appVersion: String? = null,
    val deviceRates: List<Int> = emptyList()
)

/** Versioned JSON file format used by manual export/import. */
object BackupCodec {

    const val SCHEMA_VERSION = 2
    const val MIN_SCHEMA_VERSION = 1
    const val APP_ID = "io.github.nevesz.onehz"

    fun encode(data: BackupData): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("app", APP_ID)
        data.appVersion?.let { put("appVersion", it) }
        put("deviceRates", JSONArray(data.deviceRates))
        put("settings", JSONObject().apply {
            put("enabled", data.enabled)
            put("aodMasterEnabled", data.aodMasterEnabled)
            put("aodEnabled", data.aodRateEnabled)
            put("aodBrightnessEnabled", data.aodBrightnessEnabled)
            put("aodBrightnessPct", data.aodBrightnessPct)
            put("aodBrightnessMinPct", data.aodBrightnessMinPct)
            put("aodBrightnessMaxPct", data.aodBrightnessMaxPct)
            put("forcedVideoPackages", JSONArray(data.forcedVideoPackages.sorted()))
        })
        data.sleep?.let { sleep ->
            put("sleep", JSONObject().apply {
                put("enabled", sleep.enabled)
                put("deep", sleep.deep)
                put("dozeWhileCharging", sleep.dozeWhileCharging)
                // Kept so a 1.2.x install can restore a backup made by this version.
                put("skipCharging", !sleep.dozeWhileCharging)
                put("dozeWithMusic", sleep.dozeWithMusic)
                // Kept so a 1.2.x install can restore a backup made by this version.
                put("skipMusic", !sleep.dozeWithMusic)
                put("windowEnabled", sleep.windowEnabled)
                put("windowStartMinute", sleep.windowStartMinute)
                put("windowEndMinute", sleep.windowEndMinute)
            })
        }
        data.rules?.let { put("rules", JSONObject(ActivationRulesCodec.encode(it))) }
        put("profileState", JSONObject(ProfileCodec.encode(data.state)))
    }.toString(2)

    fun decode(raw: String?): BackupData? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            val root = JSONObject(text)
            if (root.optInt("schemaVersion", -1) !in MIN_SCHEMA_VERSION..SCHEMA_VERSION) {
                return null
            }
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

            val sleepValue = root.optJSONObject("sleep")
            val sleep = sleepValue?.let {
                SleepBackup(
                    enabled = it.optBoolean("enabled", false),
                    deep = it.optBoolean("deep", true),
                    dozeWhileCharging = if (it.has("dozeWhileCharging")) {
                        it.optBoolean("dozeWhileCharging", true)
                    } else if (it.has("skipCharging")) {
                        // Old backups stored the negative switch.
                        !it.optBoolean("skipCharging", true)
                    } else {
                        true
                    },
                    dozeWithMusic = if (it.has("dozeWithMusic")) {
                        it.optBoolean("dozeWithMusic", false)
                    } else {
                        // Old backups stored the negative switch.
                        !it.optBoolean("skipMusic", true)
                    },
                    windowEnabled = it.optBoolean("windowEnabled", false),
                    windowStartMinute = it.optInt("windowStartMinute", 23 * 60)
                        .coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1),
                    windowEndMinute = it.optInt("windowEndMinute", 7 * 60)
                        .coerceIn(0, SleepWindow.MINUTES_PER_DAY - 1)
                )
            }

            BackupData(
                state = state,
                enabled = settings.optBoolean("enabled", true),
                aodMasterEnabled = if (settings.has("aodMasterEnabled")) {
                    settings.optBoolean("aodMasterEnabled", false)
                } else {
                    settings.optBoolean("aodEnabled", false) ||
                        settings.optBoolean("aodBrightnessEnabled", false)
                },
                aodRateEnabled = settings.optBoolean("aodEnabled", false),
                aodBrightnessEnabled = settings.optBoolean("aodBrightnessEnabled", false),
                aodBrightnessPct = settings.optInt("aodBrightnessPct", 10).coerceIn(1, 100),
                aodBrightnessMinPct = settings.optInt("aodBrightnessMinPct", 5).coerceIn(1, 99),
                aodBrightnessMaxPct = settings.optInt("aodBrightnessMaxPct", 30).coerceIn(2, 100),
                forcedVideoPackages = forced,
                sleep = sleep,
                rules = root.optJSONObject("rules")?.let {
                    ActivationRulesCodec.decode(it.toString())
                },
                appVersion = root.optString("appVersion").takeIf { it.isNotBlank() },
                deviceRates = deviceRates
            )
        }.getOrNull()
    }
}
