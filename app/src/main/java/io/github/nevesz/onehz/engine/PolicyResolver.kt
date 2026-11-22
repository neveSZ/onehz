package io.github.nevesz.onehz.engine

import android.content.Context
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.core.profile.VideoSettings

/** A resolved frequency request for the current step of [ResolvedPolicy.profile]. */
data class TierRequest(val hz: Int, val index: Int, val count: Int, val source: String)

enum class IdleReason {
    AOD_DISABLED,
    PROFILE_DISABLED,
    PROFILE_EMPTY
}

data class ResolvedPolicy(
    val profile: FrequencyProfile,
    val index: Int,
    val layer: String,
    val video: VideoSettings,
    val profileId: String? = null,
    val appPackage: String? = null
)

/**
 * Snapshot of everything that influences a policy decision. Keeping it independent from
 * Android lets the precedence rules be unit tested without a device.
 */
data class PolicyInput(
    val screenOff: Boolean,
    val batterySaver: Boolean,
    val autoEconomy: Boolean,
    val aodMasterEnabled: Boolean,
    val aodRateEnabled: Boolean,
    val aodHz: Int,
    val selectedProfile: DeviceProfile?,
    val economyProfile: DeviceProfile?,
    val appProfile: DeviceProfile? = null,
    val appPackageName: String? = null,
    val appIgnoreEco: Boolean = false
)

sealed interface PolicyDecision {
    data class Apply(val policy: ResolvedPolicy) : PolicyDecision
    data class Idle(val reason: IdleReason) : PolicyDecision
}

/**
 * Precedence:
 * 1. screen off -> AOD;
 * 2. battery saver + automatic economy -> Economy (unless the app ignores it);
 * 3. foreground app with an assignment -> the assigned profile;
 * 4. the manually selected profile.
 */
object PolicyResolver {

    fun resolve(
        context: Context,
        screenOff: Boolean,
        ecoActive: Boolean,
        foregroundPackage: String?,
        index: Int
    ): PolicyDecision {
        val state = ProfileStore.state(context)
        val assignment = state.assignment(foregroundPackage)
        val appProfile = assignment?.let { state.profile(it.profileId) }
        return decide(
            PolicyInput(
                screenOff = screenOff,
                batterySaver = ecoActive,
                autoEconomy = state.autoEconomyOnBatterySaver,
                aodMasterEnabled = io.github.nevesz.onehz.core.OneHzPreferences.aodMasterEnabled(context),
                aodRateEnabled = io.github.nevesz.onehz.core.OneHzPreferences.aodRateEnabled(context),
                aodHz = ProfileStore.rates(context).first(),
                selectedProfile = state.activeProfile(),
                economyProfile = state.economyProfile(),
                appProfile = appProfile,
                appPackageName = assignment?.packageName,
                appIgnoreEco = assignment?.ignoreEco == true
            ),
            index
        )
    }

    fun decide(input: PolicyInput, index: Int): PolicyDecision {
        if (input.screenOff) {
            if (!input.aodMasterEnabled || !input.aodRateEnabled) {
                return PolicyDecision.Idle(IdleReason.AOD_DISABLED)
            }
            return PolicyDecision.Apply(
                ResolvedPolicy(
                    profile = FrequencyProfile.fixed(input.aodHz),
                    index = 0,
                    layer = "AOD",
                    video = VideoSettings(enabled = false, minHz = input.aodHz)
                )
            )
        }

        val app = input.appProfile?.takeIf { it.frequency.steps.isNotEmpty() }
        if (input.batterySaver && input.autoEconomy) {
            if (app != null && input.appIgnoreEco) {
                return applyProfile(
                    app,
                    "app:${input.appPackageName ?: app.id}",
                    index,
                    appPackage = input.appPackageName
                )
            }
            val economy = input.economyProfile?.takeIf { it.frequency.steps.isNotEmpty() }
                ?: input.selectedProfile?.takeIf { it.frequency.steps.isNotEmpty() }
            return applyProfile(economy, "economy", index)
        }

        if (app != null) {
            return applyProfile(
                app,
                "app:${input.appPackageName ?: app.id}",
                index,
                appPackage = input.appPackageName
            )
        }
        return applyProfile(input.selectedProfile, "profile", index)
    }

    private fun applyProfile(
        profile: DeviceProfile?,
        layer: String,
        index: Int,
        appPackage: String? = null
    ): PolicyDecision {
        if (profile == null) return PolicyDecision.Idle(IdleReason.PROFILE_DISABLED)
        if (profile.frequency.steps.isEmpty()) {
            return PolicyDecision.Idle(IdleReason.PROFILE_EMPTY)
        }
        return PolicyDecision.Apply(
            ResolvedPolicy(
                profile = profile.frequency,
                index = profile.frequency.clampedIndex(index),
                layer = if (layer == "profile") "profile:${profile.id}" else layer,
                video = profile.video,
                profileId = profile.id,
                appPackage = appPackage
            )
        )
    }
}
