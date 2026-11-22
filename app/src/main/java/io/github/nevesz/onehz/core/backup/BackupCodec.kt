package io.github.nevesz.onehz.core.backup

import io.github.nevesz.onehz.core.profile.ProfileCodec
import io.github.nevesz.onehz.core.profile.ProfileState
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val state: ProfileState,
    val enabled: Boolean,
    val aodEnabled: Boolean,
    val aodBrightnessEnabled: Boolean,
    val aodBrightnessPct: Int,
    val aodBrightnessMinPct: Int,
    val aodBrightnessMaxPct: Int,
    val forcedVideoPackages: Set<String>,
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

            BackupData(
                state = state,
                enabled = settings.optBoolean("enabled", true),
                aodEnabled = settings.optBoolean("aodEnabled", false),
                aodBrightnessEnabled = settings.optBoolean("aodBrightnessEnabled", false),
                aodBrightnessPct = settings.optInt("aodBrightnessPct", 10).coerceIn(1, 100),
                aodBrightnessMinPct = settings.optInt("aodBrightnessMinPct", 5).coerceIn(1, 99),
                aodBrightnessMaxPct = settings.optInt("aodBrightnessMaxPct", 30).coerceIn(2, 100),
                forcedVideoPackages = forced,
                appVersion = root.optString("appVersion").takeIf { it.isNotBlank() },
                deviceRates = deviceRates
            )
        }.getOrNull()
    }
}
