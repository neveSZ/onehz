package io.github.nevesz.onehz.core.profile

import android.content.Context
import io.github.nevesz.onehz.core.DisplayRates
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.VideoConfig
import java.util.UUID

/**
 * Single source of truth for device profiles, the active profile and per-app
 * assignments. The JSON blob is small and rewritten atomically on every change.
 */
object ProfileStore {

    private const val KEY_STATE = "profiles_v2"

    private val PACKAGE_PATTERN = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

    fun rates(context: Context): List<Int> =
        ProfileDefaults.normalizedRates(DisplayRates.main(context))

    fun state(context: Context): ProfileState =
        state(context, rates(context))

    fun state(context: Context, panelRates: List<Int>): ProfileState {
        val stored = ProfileCodec.decode(
            OneHzPreferences.prefs(context).getString(KEY_STATE, null)
        )
        return if (stored == null) {
            migrate(context, panelRates)
        } else {
            normalize(stored, panelRates)
        }
    }

    fun save(context: Context, state: ProfileState) {
        val normalized = normalize(state, rates(context))
        OneHzPreferences.prefs(context).edit()
            .putString(KEY_STATE, ProfileCodec.encode(normalized))
            .apply()
    }

    fun activeProfile(context: Context): DeviceProfile? =
        state(context).activeProfile()

    fun profile(context: Context, id: String?): DeviceProfile? =
        state(context).profile(id)

    fun profileName(context: Context, id: String?): String? {
        val profile = profile(context, id) ?: return null
        return profile.name ?: profile.kind.canonicalId
    }

    fun select(context: Context, profileId: String) {
        val state = state(context)
        if (state.profiles.none { it.id == profileId }) return
        save(context, state.copy(activeProfileId = profileId))
    }

    fun setAutoEconomy(context: Context, enabled: Boolean) {
        save(context, state(context).copy(autoEconomyOnBatterySaver = enabled))
    }

    fun updateProfile(context: Context, profile: DeviceProfile) {
        val state = state(context)
        if (state.profiles.none { it.id == profile.id }) return
        val updated = if (profile.isBuiltIn) {
            profile.copy(id = profile.kind.canonicalId, name = null)
        } else {
            profile.copy(kind = ProfileKind.CUSTOM, customized = true)
        }
        save(
            context,
            state.copy(profiles = state.profiles.map { if (it.id == updated.id) updated else it })
        )
    }

    fun resetToDefault(context: Context, profileId: String): DeviceProfile? {
        val state = state(context)
        val current = state.profile(profileId) ?: return null
        if (!current.isBuiltIn) return null
        val default = ProfileDefaults.forKind(current.kind, rates(context)) ?: return null
        save(
            context,
            state.copy(profiles = state.profiles.map { if (it.id == profileId) default else it })
        )
        return default
    }

    fun createCustom(
        context: Context,
        name: String,
        templateProfileId: String? = null
    ): DeviceProfile? {
        val cleanName = name.trim().take(ProfileCodec.MAX_NAME_LENGTH)
        if (cleanName.isEmpty()) return null
        val state = state(context)
        if (state.profiles.count { it.kind == ProfileKind.CUSTOM } >=
            ProfileCodec.MAX_PROFILES - 3
        ) {
            return null
        }
        val template = state.profile(templateProfileId)
            ?: state.activeProfile()
            ?: state.balancedProfile()
            ?: return null
        val profile = DeviceProfile(
            id = "custom_" + UUID.randomUUID().toString().replace("-", "").take(16),
            kind = ProfileKind.CUSTOM,
            name = cleanName,
            frequency = template.frequency,
            video = template.video,
            customized = true
        )
        save(context, state.copy(profiles = state.profiles + profile))
        return profile
    }

    fun deleteProfile(
        context: Context,
        profileId: String,
        reassignToId: String? = null
    ): Boolean {
        val state = state(context)
        val target = state.profile(profileId) ?: return false
        if (target.isBuiltIn) return false
        val fallback = state.profile(reassignToId)
            ?: state.balancedProfile()
            ?: state.profiles.firstOrNull { it.id != profileId }
            ?: return false
        val assignments = state.assignments.map {
            if (it.profileId == profileId) it.copy(profileId = fallback.id) else it
        }
        val active = if (state.activeProfileId == profileId) fallback.id else state.activeProfileId
        save(
            context,
            state.copy(
                profiles = state.profiles.filterNot { it.id == profileId },
                activeProfileId = active,
                assignments = assignments
            )
        )
        return true
    }

    fun assignment(context: Context, packageName: String?): AppAssignment? =
        state(context).assignment(packageName)

    fun saveAssignment(context: Context, assignment: AppAssignment) {
        if (!PACKAGE_PATTERN.matches(assignment.packageName)) return
        val state = state(context)
        if (state.profiles.none { it.id == assignment.profileId }) return
        val existing = state.assignments.filterNot { it.packageName == assignment.packageName }
        save(context, state.copy(assignments = existing + assignment))
    }

    fun removeAssignment(context: Context, packageName: String) {
        val state = state(context)
        save(
            context,
            state.copy(assignments = state.assignments.filterNot { it.packageName == packageName })
        )
    }

    fun configuredPackages(context: Context): List<String> =
        state(context).assignments.map { it.packageName }.distinct().sorted()

    fun appsUsing(context: Context, profileId: String): List<String> =
        state(context).appsUsing(profileId)

    /**
     * Ensures that the three built-ins exist, that untouched built-ins follow the current
     * panel, and that every reference points to an existing profile.
     */
    fun normalize(state: ProfileState, panelRates: List<Int>): ProfileState {
        val defaults = ProfileDefaults.forRates(panelRates)
        val byKind = state.profiles.associateBy { it.kind }
        val builtIns = listOf(
            ProfileKind.ECONOMY,
            ProfileKind.BALANCED,
            ProfileKind.PERFORMANCE
        ).map { kind ->
            val stored = byKind[kind]
            if (stored != null && stored.customized) stored.copy(
                id = kind.canonicalId,
                kind = kind,
                name = null
            ) else defaults.first { it.kind == kind }
        }
        val seenIds = builtIns.mapTo(mutableSetOf()) { it.id }
        val custom = state.profiles.asSequence()
            .filter { it.kind == ProfileKind.CUSTOM }
            .map { profile ->
                val safeName = (profile.name ?: "Custom").trim()
                    .take(ProfileCodec.MAX_NAME_LENGTH)
                    .ifBlank { "Custom" }
                val safeProfile = profile.copy(name = safeName)
                if (safeProfile.id in seenIds || safeProfile.id.isBlank()) {
                    safeProfile.copy(
                        id = "custom_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    )
                } else {
                    safeProfile
                }
            }
            .filter { profile ->
                if (profile.id in seenIds) {
                    false
                } else {
                    seenIds += profile.id
                    true
                }
            }
            .toList()
        val profiles = builtIns + custom
        val active = profiles.firstOrNull { it.id == state.activeProfileId }?.id
            ?: profiles.firstOrNull { it.kind == ProfileKind.BALANCED }?.id
            ?: profiles.first().id
        val balancedId = profiles.firstOrNull { it.kind == ProfileKind.BALANCED }?.id
            ?: profiles.first().id
        val assignments = state.assignments.asSequence()
            .filter { PACKAGE_PATTERN.matches(it.packageName) }
            .map { assignment ->
                if (profiles.any { it.id == assignment.profileId }) {
                    assignment
                } else {
                    assignment.copy(profileId = balancedId)
                }
            }
            .distinctBy { it.packageName }
            .take(ProfileCodec.MAX_ASSIGNMENTS)
            .toList()
        return state.copy(
            profiles = profiles,
            activeProfileId = active,
            assignments = assignments
        )
    }

    private fun migrate(context: Context, panelRates: List<Int>): ProfileState {
        val prefs = OneHzPreferences.prefs(context)
        val defaults = ProfileDefaults.forRates(panelRates).toMutableList()

        val legacyNormal = FrequencyProfile.parse(prefs.getString(OneHzPreferences.KEY_PROFILE_NORMAL, null))
            ?.takeIf { it.steps.isNotEmpty() }
        val legacyEco = FrequencyProfile.parse(prefs.getString(OneHzPreferences.KEY_PROFILE_ECO, null))
            ?.takeIf { it.steps.isNotEmpty() }
        val hadLegacy = legacyNormal != null || legacyEco != null ||
            prefs.all.keys.any { it.startsWith(OneHzPreferences.APP_PREFIX) }

        if (legacyNormal != null) {
            replaceFrequency(defaults, ProfileKind.BALANCED, legacyNormal)
        }
        if (legacyEco != null) {
            replaceFrequency(defaults, ProfileKind.ECONOMY, legacyEco)
        }

        val legacyRules = if (prefs.contains(OneHzPreferences.KEY_VIDEO_CONFIG)) {
            VideoRules.from(
                VideoConfig.decode(prefs.getString(OneHzPreferences.KEY_VIDEO_CONFIG, null))
            )
        } else {
            VideoRules()
        }
        val legacyVideoEnabled = if (prefs.contains(OneHzPreferences.KEY_VIDEO_ENABLED)) {
            prefs.getBoolean(OneHzPreferences.KEY_VIDEO_ENABLED, false)
        } else {
            true
        }
        val economyMin = ProfileDefaults.nearestRate(
            ProfileDefaults.ECONOMY_VIDEO_HZ,
            panelRates
        )
        val standardMin = ProfileDefaults.nearestRate(
            ProfileDefaults.STANDARD_VIDEO_HZ,
            panelRates
        )
        defaults.replaceAll { profile ->
            val min = if (profile.kind == ProfileKind.ECONOMY) economyMin else standardMin
            if (profile.isBuiltIn) {
                profile.copy(
                    video = profile.video.copy(
                        enabled = legacyVideoEnabled,
                        minHz = min,
                        rules = legacyRules
                    )
                )
            } else {
                profile
            }
        }

        val assignments = mutableListOf<AppAssignment>()
        val customByFrequency = mutableMapOf<String, DeviceProfile>()
        val keys = prefs.all.keys
        for (key in keys) {
            if (!key.startsWith(OneHzPreferences.APP_PREFIX)) continue
            val packageName = when {
                key.endsWith(OneHzPreferences.APP_PROFILE) ->
                    key.removePrefix(OneHzPreferences.APP_PREFIX)
                        .removeSuffix(OneHzPreferences.APP_PROFILE)
                key.endsWith(OneHzPreferences.APP_IGNORE_ECO) ->
                    key.removePrefix(OneHzPreferences.APP_PREFIX)
                        .removeSuffix(OneHzPreferences.APP_IGNORE_ECO)
                else -> continue
            }
            if (!PACKAGE_PATTERN.matches(packageName)) continue
            if (assignments.any { it.packageName == packageName }) continue
            val ignoreEco = prefs.getBoolean(
                OneHzPreferences.APP_PREFIX + packageName + OneHzPreferences.APP_IGNORE_ECO,
                false
            )
            val legacyProfile = FrequencyProfile.parse(
                prefs.getString(
                    OneHzPreferences.APP_PREFIX + packageName + OneHzPreferences.APP_PROFILE,
                    null
                )
            )?.takeIf { it.steps.isNotEmpty() }

            val profileId = if (legacyProfile != null) {
                defaults.firstOrNull { it.frequency == legacyProfile }?.id
                    ?: customByFrequency.getOrPut(legacyProfile.serialize()) {
                        val custom = DeviceProfile(
                            id = "custom_" + UUID.randomUUID().toString()
                                .replace("-", "")
                                .take(16),
                            kind = ProfileKind.CUSTOM,
                            name = packageName.take(ProfileCodec.MAX_NAME_LENGTH),
                            frequency = legacyProfile,
                            video = VideoSettings(
                                enabled = legacyVideoEnabled,
                                minHz = standardMin,
                                rules = legacyRules
                            ),
                            customized = true
                        )
                        defaults += custom
                        custom
                    }.id
            } else if (ignoreEco) {
                defaults.first { it.kind == ProfileKind.BALANCED }.id
            } else {
                null
            }
            if (profileId != null) {
                assignments += AppAssignment(packageName, profileId, ignoreEco)
            }
        }

        val normalEnabled = prefs.getBoolean(OneHzPreferences.KEY_PROFILE_NORMAL_ENABLED, true)
        val ecoEnabled = prefs.getBoolean(OneHzPreferences.KEY_PROFILE_ECO_ENABLED, true)
        var activeId = ProfileKind.BALANCED.canonicalId
        var autoEconomy = true
        when {
            !normalEnabled && !ecoEnabled -> {
                OneHzPreferences.setEnabled(context, false)
            }
            !normalEnabled && ecoEnabled -> {
                activeId = ProfileKind.ECONOMY.canonicalId
                autoEconomy = true
            }
            normalEnabled && !ecoEnabled -> {
                autoEconomy = false
            }
        }

        val state = ProfileState(
            profiles = defaults,
            activeProfileId = activeId,
            assignments = assignments,
            autoEconomyOnBatterySaver = autoEconomy
        )
        save(context, state)
        OneHzPreferences.setProfilesMigrationNoticePending(context, hadLegacy)
        return normalize(state, panelRates)
    }

    private fun replaceFrequency(
        profiles: MutableList<DeviceProfile>,
        kind: ProfileKind,
        frequency: FrequencyProfile
    ) {
        val index = profiles.indexOfFirst { it.kind == kind }
        if (index >= 0) {
            profiles[index] = profiles[index].copy(frequency = frequency, customized = true)
        }
    }
}
