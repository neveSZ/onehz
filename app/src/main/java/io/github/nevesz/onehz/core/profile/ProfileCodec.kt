package io.github.nevesz.onehz.core.profile

import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.TierStep
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec shared by SharedPreferences and the backup file. The parser is intentionally
 * strict: unknown schema versions or structurally invalid staircases are rejected instead
 * of being applied silently.
 */
object ProfileCodec {

    const val SCHEMA_VERSION = 2
    const val MIN_SCHEMA_VERSION = 1
    const val MAX_PROFILES = 32
    const val MAX_ASSIGNMENTS = 512
    const val MAX_NAME_LENGTH = 40

    private val ID_PATTERN = Regex("""[A-Za-z0-9_-]{1,64}""")
    private val PACKAGE_PATTERN = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

    fun encode(state: ProfileState): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("activeProfileId", state.activeProfileId)
        put("autoEconomyOnBatterySaver", state.autoEconomyOnBatterySaver)
        put("profiles", JSONArray().apply {
            state.profiles.forEach { put(encodeProfile(it)) }
        })
        put("appAssignments", JSONArray().apply {
            state.assignments.forEach { assignment ->
                put(JSONObject().apply {
                    put("packageName", assignment.packageName)
                    when (val target = assignment.target) {
                        is AppTarget.Profile -> put("profileId", target.profileId)
                        is AppTarget.Fixed -> put("fixedHz", target.hz)
                        AppTarget.Ignore -> put("ignore", true)
                    }
                    put("ignoreEco", assignment.ignoreEco)
                })
            }
        })
    }.toString()

    fun encodeProfile(profile: DeviceProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("kind", profile.kind.name)
        if (profile.name != null) put("name", profile.name)
        put("customized", profile.customized)
        put("frequency", JSONObject().apply {
            put("steps", JSONArray().apply {
                profile.frequency.steps.forEach { step ->
                    put(JSONObject().apply {
                        put("hz", step.hz)
                        step.holdMs?.let { put("holdMs", it) }
                    })
                }
            })
        })
        put("video", JSONObject().apply {
            put("enabled", profile.video.enabled)
            put("minHz", profile.video.minHz)
            put("rules", JSONObject().apply {
                put("useMediaSession", profile.video.rules.useMediaSession)
                put("pausedSessionBlocks", profile.video.rules.pausedSessionBlocks)
                put("requireVideoCodec", profile.video.rules.requireVideoCodec)
                put("requireStartedAudio", profile.video.rules.requireStartedAudio)
            })
        })
    }

    fun decode(raw: String?): ProfileState? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            val root = JSONObject(text)
            val schema = root.optInt("schemaVersion", -1)
            if (schema !in MIN_SCHEMA_VERSION..SCHEMA_VERSION) return null
            val profilesJson = root.optJSONArray("profiles") ?: return null
            if (profilesJson.length() == 0 || profilesJson.length() > MAX_PROFILES) return null

            val profiles = mutableListOf<DeviceProfile>()
            for (index in 0 until profilesJson.length()) {
                val profile = decodeProfile(profilesJson.optJSONObject(index) ?: continue) ?: continue
                if (profiles.any { it.id == profile.id }) continue
                if (profile.isBuiltIn && profiles.any { it.kind == profile.kind }) continue
                profiles += profile
            }
            if (profiles.isEmpty()) return null

            val assignments = mutableListOf<AppAssignment>()
            val assignmentsJson = root.optJSONArray("appAssignments")
            if (assignmentsJson != null) {
                val limit = minOf(assignmentsJson.length(), MAX_ASSIGNMENTS)
                for (index in 0 until limit) {
                    val item = assignmentsJson.optJSONObject(index) ?: continue
                    val packageName = item.optString("packageName").trim()
                    if (!PACKAGE_PATTERN.matches(packageName)) continue
                    if (assignments.any { it.packageName == packageName }) continue
                    val target = when {
                        item.optBoolean("ignore", false) -> AppTarget.Ignore
                        item.has("fixedHz") -> {
                            val hz = item.optInt("fixedHz", 0)
                            if (hz <= 0) continue
                            AppTarget.Fixed(hz)
                        }
                        else -> {
                            val profileId = item.optString("profileId").trim()
                            if (profiles.none { it.id == profileId }) continue
                            AppTarget.Profile(profileId)
                        }
                    }
                    assignments += AppAssignment(
                        packageName = packageName,
                        target = target,
                        ignoreEco = item.optBoolean("ignoreEco", false)
                    )
                }
            }

            val active = root.optString("activeProfileId").trim()
                .takeIf { id -> profiles.any { it.id == id } }
                ?: profiles.firstOrNull { it.kind == ProfileKind.BALANCED }?.id
                ?: profiles.first().id

            ProfileState(
                profiles = profiles,
                activeProfileId = active,
                assignments = assignments,
                autoEconomyOnBatterySaver = root.optBoolean("autoEconomyOnBatterySaver", true)
            )
        }.getOrNull()
    }

    fun decodeProfile(json: JSONObject): DeviceProfile? {
        val kind = runCatching { ProfileKind.valueOf(json.optString("kind")) }.getOrNull()
            ?: return null
        val rawId = json.optString("id").trim()
        val id = if (kind == ProfileKind.CUSTOM) {
            rawId.takeIf { ID_PATTERN.matches(it) } ?: return null
        } else {
            kind.canonicalId
        }
        val name = if (kind == ProfileKind.CUSTOM) {
            json.optString("name").trim()
                .takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
                ?: return null
        } else {
            null
        }
        val frequency = decodeFrequency(json.optJSONObject("frequency")) ?: return null
        val video = decodeVideo(json.optJSONObject("video"))
        return DeviceProfile(
            id = id,
            kind = kind,
            name = name,
            frequency = frequency,
            video = video,
            customized = json.optBoolean("customized", false)
        )
    }

    private fun decodeFrequency(json: JSONObject?): FrequencyProfile? {
        val stepsJson = json?.optJSONArray("steps") ?: return null
        if (stepsJson.length() == 0 || stepsJson.length() > FrequencyProfile.MAX_STEPS) return null
        val steps = mutableListOf<TierStep>()
        for (index in 0 until stepsJson.length()) {
            val item = stepsJson.optJSONObject(index) ?: return null
            val hz = item.optInt("hz", 0)
            if (hz <= 0) return null
            val hold = if (item.has("holdMs") && !item.isNull("holdMs")) {
                item.optLong("holdMs", -1L).takeIf { it > 0 } ?: return null
            } else {
                null
            }
            steps += TierStep(hz, hold)
        }
        val profile = FrequencyProfile(steps)
        if (profile.issues(emptyList()).isNotEmpty()) return null
        return profile
    }

    private fun decodeVideo(json: JSONObject?): VideoSettings {
        if (json == null) return VideoSettings()
        val rulesJson = json.optJSONObject("rules")
        val defaults = VideoRules()
        return VideoSettings(
            enabled = json.optBoolean("enabled", true),
            minHz = json.optInt("minHz", 60).takeIf { it > 0 } ?: 60,
            rules = VideoRules(
                useMediaSession = rulesJson?.optBoolean(
                    "useMediaSession",
                    defaults.useMediaSession
                ) ?: defaults.useMediaSession,
                pausedSessionBlocks = rulesJson?.optBoolean(
                    "pausedSessionBlocks",
                    defaults.pausedSessionBlocks
                ) ?: defaults.pausedSessionBlocks,
                requireVideoCodec = rulesJson?.optBoolean(
                    "requireVideoCodec",
                    defaults.requireVideoCodec
                ) ?: defaults.requireVideoCodec,
                requireStartedAudio = rulesJson?.optBoolean(
                    "requireStartedAudio",
                    defaults.requireStartedAudio
                ) ?: defaults.requireStartedAudio
            )
        )
    }

    /**
     * Adapts a staircase to the current panel. Used by backup import and by profile
     * repair after a device rate change.
     */
    fun adaptFrequency(profile: FrequencyProfile, rates: List<Int>): FrequencyProfile {
        val supported = ProfileDefaults.normalizedRates(rates)
        if (supported.isEmpty()) return profile
        val result = mutableListOf<TierStep>()
        for (step in profile.steps) {
            var hz = ProfileDefaults.nearestRate(step.hz, supported)
            val previous = result.lastOrNull()?.hz
            if (previous != null && hz >= previous) {
                hz = supported.lastOrNull { it < previous } ?: continue
            }
            if (result.any { it.hz == hz }) continue
            result += step.copy(hz = hz)
        }
        if (result.isEmpty()) return FrequencyProfile.fixed(supported.last())
        return FrequencyProfile(result.mapIndexed { index, step ->
            if (index == result.lastIndex) {
                step.copy(holdMs = null)
            } else {
                step.copy(holdMs = FrequencyProfile.normalizeHold(
                    step.holdMs ?: FrequencyProfile.DEFAULT_HOLD_MS
                ))
            }
        })
    }
}
