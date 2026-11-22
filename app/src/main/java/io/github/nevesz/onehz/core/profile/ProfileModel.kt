package io.github.nevesz.onehz.core.profile

import io.github.nevesz.onehz.core.FrequencyProfile
import io.github.nevesz.onehz.core.VideoConfig

enum class ProfileKind {
    ECONOMY,
    BALANCED,
    PERFORMANCE,
    CUSTOM;

    val canonicalId: String get() = name.lowercase()
}

/**
 * Video detection rules owned by a device profile. The forced package list is global and
 * is merged only when the binder payload is built.
 */
data class VideoRules(
    val useMediaSession: Boolean = true,
    val pausedSessionBlocks: Boolean = true,
    val requireVideoCodec: Boolean = true,
    val requireStartedAudio: Boolean = false
) {
    val hasPositiveSignal: Boolean
        get() = useMediaSession || requireVideoCodec || requireStartedAudio

    val activeRuleCount: Int
        get() = (if (useMediaSession) 1 else 0) +
            (if (useMediaSession && pausedSessionBlocks) 1 else 0) +
            (if (requireVideoCodec) 1 else 0) +
            (if (requireStartedAudio) 1 else 0)

    fun toVideoConfig(forcedPackages: Set<String> = emptySet()): VideoConfig = VideoConfig(
        useMediaSession = useMediaSession,
        pausedSessionBlocks = pausedSessionBlocks,
        requireVideoCodec = requireVideoCodec,
        requireStartedAudio = requireStartedAudio,
        forcedPackages = forcedPackages
    )

    companion object {
        fun from(config: VideoConfig): VideoRules = VideoRules(
            useMediaSession = config.useMediaSession,
            pausedSessionBlocks = config.pausedSessionBlocks,
            requireVideoCodec = config.requireVideoCodec,
            requireStartedAudio = config.requireStartedAudio
        )
    }
}

data class VideoSettings(
    val enabled: Boolean = true,
    val minHz: Int = 60,
    val rules: VideoRules = VideoRules()
)

/**
 * A complete, reusable profile. Built-in profiles keep a canonical id and may be reset to
 * the device defaults; custom profiles are user-created and can be renamed or deleted.
 */
data class DeviceProfile(
    val id: String,
    val kind: ProfileKind,
    val name: String? = null,
    val frequency: FrequencyProfile,
    val video: VideoSettings = VideoSettings(),
    val customized: Boolean = false
) {
    val isBuiltIn: Boolean get() = kind != ProfileKind.CUSTOM
    val isFixed: Boolean get() = frequency.isFixed
}

/** A package assigned to a saved profile. The profile itself is never copied. */
data class AppAssignment(
    val packageName: String,
    val profileId: String,
    val ignoreEco: Boolean = false
)

data class ProfileState(
    val profiles: List<DeviceProfile>,
    val activeProfileId: String,
    val assignments: List<AppAssignment> = emptyList(),
    val autoEconomyOnBatterySaver: Boolean = true
) {
    fun profile(id: String?): DeviceProfile? =
        if (id == null) null else profiles.firstOrNull { it.id == id }

    fun activeProfile(): DeviceProfile? = profile(activeProfileId)

    fun assignment(packageName: String?): AppAssignment? =
        if (packageName.isNullOrBlank()) null
        else assignments.firstOrNull { it.packageName == packageName }

    fun appsUsing(profileId: String): List<String> =
        assignments.filter { it.profileId == profileId }.map { it.packageName }

    fun economyProfile(): DeviceProfile? =
        profiles.firstOrNull { it.kind == ProfileKind.ECONOMY }

    fun balancedProfile(): DeviceProfile? =
        profiles.firstOrNull { it.kind == ProfileKind.BALANCED }

    fun performanceProfile(): DeviceProfile? =
        profiles.firstOrNull { it.kind == ProfileKind.PERFORMANCE }
}
