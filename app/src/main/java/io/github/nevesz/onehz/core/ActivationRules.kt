package io.github.nevesz.onehz.core

import android.content.Context
import androidx.core.content.edit
import io.github.nevesz.onehz.core.prefs.PreferencesStore
import org.json.JSONObject

/**
 * Optional activation rules that temporarily choose a saved profile. Rules never copy a
 * profile: they point at one, so editing it changes every rule and app that uses it.
 */
data class ActivationRules(
    val lowBatteryEnabled: Boolean = false,
    val lowBatteryPercent: Int = 15,
    val lowBatteryProfileId: String? = null,
    val chargingEnabled: Boolean = false,
    val chargingProfileId: String? = null,
    val windowEnabled: Boolean = false,
    val windowStartMinute: Int = 23 * 60,
    val windowEndMinute: Int = 7 * 60,
    val windowProfileId: String? = null
)

data class ActivationDecision(val profileId: String, val layer: String)

object ActivationRulesLogic {

    fun inWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val start = startMinute.coerceIn(0, 24 * 60 - 1)
        val end = endMinute.coerceIn(0, 24 * 60 - 1)
        if (start == end) return true
        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }

    /**
     * Guards win over scheduled rules, and both win over the selected profile. Low battery
     * is expected to be already debounced by [BatteryGuard].
     */
    fun evaluate(
        rules: ActivationRules,
        batteryLevel: Int,
        charging: Boolean,
        minuteOfDay: Int,
        lowBatteryActive: Boolean
    ): ActivationDecision? {
        if (rules.lowBatteryEnabled && lowBatteryActive) {
            rules.lowBatteryProfileId?.let { return ActivationDecision(it, "guard:battery") }
        }
        if (rules.windowEnabled &&
            inWindow(minuteOfDay, rules.windowStartMinute, rules.windowEndMinute)
        ) {
            rules.windowProfileId?.let { return ActivationDecision(it, "rule:window") }
        }
        if (rules.chargingEnabled && charging) {
            rules.chargingProfileId?.let { return ActivationDecision(it, "rule:charging") }
        }
        return null
    }
}

/** Small hysteresis so a battery percentage near the threshold cannot oscillate. */
object BatteryGuard {
    const val HYSTERESIS_PERCENT = 4

    fun update(batteryLevel: Int, threshold: Int, wasActive: Boolean): Boolean =
        if (wasActive) {
            batteryLevel <= threshold + HYSTERESIS_PERCENT
        } else {
            batteryLevel <= threshold
        }
}

object ActivationRulesCodec {
    fun encode(rules: ActivationRules): String = JSONObject().apply {
        put("lowBatteryEnabled", rules.lowBatteryEnabled)
        put("lowBatteryPercent", rules.lowBatteryPercent)
        put("lowBatteryProfileId", rules.lowBatteryProfileId)
        put("chargingEnabled", rules.chargingEnabled)
        put("chargingProfileId", rules.chargingProfileId)
        put("windowEnabled", rules.windowEnabled)
        put("windowStartMinute", rules.windowStartMinute)
        put("windowEndMinute", rules.windowEndMinute)
        put("windowProfileId", rules.windowProfileId)
    }.toString()

    fun decode(raw: String?): ActivationRules {
        if (raw.isNullOrBlank()) return ActivationRules()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return ActivationRules()
        return ActivationRules(
            lowBatteryEnabled = root.optBoolean("lowBatteryEnabled", false),
            lowBatteryPercent = root.optInt("lowBatteryPercent", 15).coerceIn(5, 50),
            lowBatteryProfileId = root.optString("lowBatteryProfileId").takeIf { it.isNotBlank() },
            chargingEnabled = root.optBoolean("chargingEnabled", false),
            chargingProfileId = root.optString("chargingProfileId").takeIf { it.isNotBlank() },
            windowEnabled = root.optBoolean("windowEnabled", false),
            windowStartMinute = root.optInt("windowStartMinute", 23 * 60).coerceIn(0, 24 * 60 - 1),
            windowEndMinute = root.optInt("windowEndMinute", 7 * 60).coerceIn(0, 24 * 60 - 1),
            windowProfileId = root.optString("windowProfileId").takeIf { it.isNotBlank() }
        )
    }
}

object ActivationRulesStore {
    private const val KEY = "activation_rules"

    @Volatile
    private var cachedRaw: String? = null

    @Volatile
    private var cached: ActivationRules? = null

    fun get(context: Context): ActivationRules {
        val raw = PreferencesStore.get(context).getString(KEY, null)
        cached?.let { if (cachedRaw == raw) return it }
        val decoded = ActivationRulesCodec.decode(raw)
        cachedRaw = raw
        cached = decoded
        return decoded
    }

    fun set(context: Context, rules: ActivationRules) {
        val encoded = ActivationRulesCodec.encode(rules)
        PreferencesStore.get(context).edit {
            putString(KEY, encoded)
        }
        cachedRaw = encoded
        cached = rules
    }
}
