package io.github.nevesz.onehz.engine

import android.content.Context
import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.OneHzPreferences

/** A resolved frequency request for the current step of [ResolvedPolicy.profile]. */
data class TierRequest(val hz: Int, val index: Int, val count: Int, val source: String)

enum class IdleReason {
    AOD_DISABLED,
    ECO_PROFILE_DISABLED,
    NORMAL_PROFILE_DISABLED,
    EMPTY_PROFILE
}

data class ResolvedPolicy(
    val profile: FrequencyProfile,
    val index: Int,
    val layer: String
)

/**
 * Snapshot of everything that influences a policy decision. Keeping it independent from
 * Android lets the precedence rules be unit tested without a device.
 */
data class PolicyInput(
    val screenOff: Boolean,
    val ecoActive: Boolean,
    val aodEnabled: Boolean,
    val aodHz: Int,
    val normalEnabled: Boolean,
    val normalProfile: FrequencyProfile,
    val ecoEnabled: Boolean,
    val ecoProfile: FrequencyProfile,
    val appProfile: OneHzPreferences.AppProfile?
)

sealed interface PolicyDecision {
    data class Apply(val policy: ResolvedPolicy) : PolicyDecision
    data class Idle(val reason: IdleReason) : PolicyDecision
}

/**
 * Each situation has its own independent switch:
 * screen off -> AOD, battery saver -> ECO profile, otherwise -> normal profile.
 * When the switch for the current situation is off, OneHZ leaves the system alone.
 */
object PolicyResolver {

    fun resolve(
        context: Context,
        screenOff: Boolean,
        ecoActive: Boolean,
        foregroundPackage: String?,
        index: Int
    ): PolicyDecision = decide(
        PolicyInput(
            screenOff = screenOff,
            ecoActive = ecoActive,
            aodEnabled = OneHzPreferences.aodEnabled(context),
            aodHz = OneHzPreferences.aodHz(context),
            normalEnabled = OneHzPreferences.normalEnabled(context),
            normalProfile = OneHzPreferences.normalProfile(context),
            ecoEnabled = OneHzPreferences.ecoEnabled(context),
            ecoProfile = OneHzPreferences.ecoProfile(context),
            appProfile = foregroundPackage?.let { OneHzPreferences.appProfile(context, it) }
        ),
        index
    )

    fun decide(input: PolicyInput, index: Int): PolicyDecision {
        if (input.screenOff) {
            if (!input.aodEnabled) return PolicyDecision.Idle(IdleReason.AOD_DISABLED)
            val profile = FrequencyProfile.fixed(input.aodHz)
            return PolicyDecision.Apply(ResolvedPolicy(profile, 0, "AOD"))
        }

        if (input.ecoActive) {
            val app = input.appProfile
            // Explicit per-app exception: this app keeps the normal profile in battery saver.
            if (app != null && app.ignoreEco && input.normalEnabled) {
                return normalDecision(app.profile, app.packageName, input.normalProfile, index)
            }
            if (!input.ecoEnabled) {
                return PolicyDecision.Idle(IdleReason.ECO_PROFILE_DISABLED)
            }
            return profileDecision(input.ecoProfile, "ECO", index)
        }

        if (!input.normalEnabled) {
            return PolicyDecision.Idle(IdleReason.NORMAL_PROFILE_DISABLED)
        }
        return normalDecision(input.appProfile?.profile, input.appProfile?.packageName, input.normalProfile, index)
    }

    private fun normalDecision(
        appProfile: FrequencyProfile?,
        packageName: String?,
        normalProfile: FrequencyProfile,
        index: Int
    ): PolicyDecision {
        if (appProfile != null && appProfile.steps.isNotEmpty()) {
            return PolicyDecision.Apply(
                ResolvedPolicy(appProfile, appProfile.clampedIndex(index), "app:$packageName")
            )
        }
        return profileDecision(normalProfile, "normal", index)
    }

    private fun profileDecision(profile: FrequencyProfile, layer: String, index: Int): PolicyDecision {
        if (profile.steps.isEmpty()) return PolicyDecision.Idle(IdleReason.EMPTY_PROFILE)
        return PolicyDecision.Apply(ResolvedPolicy(profile, profile.clampedIndex(index), layer))
    }
}
