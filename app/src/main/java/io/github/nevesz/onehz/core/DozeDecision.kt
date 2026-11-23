package io.github.nevesz.onehz.core

/** Why Sleep did not force idle. The UI turns each value into localized copy. */
enum class DozeSkip {
    MASTER_OFF,
    DISABLED,
    PAUSED,
    OUTSIDE_WINDOW,
    CHARGING,
    MUSIC,
    SERVICE,
}

sealed interface DozeDecision {
    data class Force(val deep: Boolean) : DozeDecision
    data class Skip(val reason: DozeSkip) : DozeDecision
}

/** Everything a decision needs. It is re-evaluated on screen, power, audio and preference events. */
data class DozeInput(
    val masterEnabled: Boolean = true,
    val featureEnabled: Boolean = true,
    val now: Long = 0L,
    val pausedUntil: Long = 0L,
    val windowEnabled: Boolean = false,
    val window: SleepWindow = SleepWindow.DEFAULT,
    val minuteOfDay: Int = 0,
    val skipCharging: Boolean = true,
    val charging: Boolean = false,
    val dozeWithMusic: Boolean = false,
    val musicActive: Boolean = false,
    val serviceReady: Boolean = false,
    val deep: Boolean = true,
)

object DozeDecisionResolver {

    fun resolve(input: DozeInput): DozeDecision {
        if (!input.masterEnabled) return DozeDecision.Skip(DozeSkip.MASTER_OFF)
        if (!input.featureEnabled) return DozeDecision.Skip(DozeSkip.DISABLED)
        if (input.now < input.pausedUntil) return DozeDecision.Skip(DozeSkip.PAUSED)
        if (input.windowEnabled && !input.window.contains(input.minuteOfDay)) {
            return DozeDecision.Skip(DozeSkip.OUTSIDE_WINDOW)
        }
        if (input.skipCharging && input.charging) return DozeDecision.Skip(DozeSkip.CHARGING)
        if (!input.dozeWithMusic && input.musicActive) return DozeDecision.Skip(DozeSkip.MUSIC)
        if (!input.serviceReady) return DozeDecision.Skip(DozeSkip.SERVICE)
        return DozeDecision.Force(input.deep)
    }
}

/** Last known outcome, shown by the Home card and by the diagnostics report. */
enum class DozePhase {
    OFF,
    READY,
    ACTIVE,
    PAUSED,
    CHARGING,
    MUSIC,
    OUTSIDE_WINDOW,
    WAITING_SERVICE,
    FAILED,
}
