package io.github.nevesz.onehz.core

/**
 * User-tunable video detection rules. The defaults reproduce the original fixed
 * behavior: MediaSession is used, a paused session vetoes playback, the foreground
 * app must hold a video decoder, and without a session a started USAGE_MEDIA player
 * is required.
 *
 * The same encoding is used for SharedPreferences and for the Shizuku binder payload,
 * so the app and the privileged service always agree on the rules.
 */
data class VideoConfig(
    val useMediaSession: Boolean = true,
    val pausedSessionBlocks: Boolean = true,
    val requireVideoCodec: Boolean = true,
    val requireStartedAudio: Boolean = true,
    val forcedPackages: Set<String> = emptySet()
) {
    /** False when no positive signal is left; only forced apps trigger the floor then. */
    val hasPositiveSignal: Boolean
        get() = useMediaSession || requireVideoCodec || requireStartedAudio

    /** Number of signals currently enabled, for the Home summary. */
    val activeRuleCount: Int
        get() = (if (useMediaSession) 1 else 0) +
            (if (useMediaSession && pausedSessionBlocks) 1 else 0) +
            (if (requireVideoCodec) 1 else 0) +
            (if (requireStartedAudio) 1 else 0)

    fun encode(): String = listOf(
        VERSION,
        flag(useMediaSession),
        flag(pausedSessionBlocks),
        flag(requireVideoCodec),
        flag(requireStartedAudio),
        forcedPackages.asSequence()
            .filter { PACKAGE_NAME.matches(it) }
            .sorted()
            .take(MAX_FORCED_PACKAGES)
            .joinToString(",")
    ).joinToString(SEPARATOR)

    companion object {
        val DEFAULT = VideoConfig()

        const val RULE_COUNT = 4

        private const val VERSION = "v1"
        private const val SEPARATOR = "|"
        const val MAX_FORCED_PACKAGES = 64

        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

        fun decode(raw: String?): VideoConfig {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return DEFAULT
            val parts = text.split(SEPARATOR)
            if (parts.firstOrNull() != VERSION) return DEFAULT
            return VideoConfig(
                useMediaSession = parts.flagAt(1, DEFAULT.useMediaSession),
                pausedSessionBlocks = parts.flagAt(2, DEFAULT.pausedSessionBlocks),
                requireVideoCodec = parts.flagAt(3, DEFAULT.requireVideoCodec),
                requireStartedAudio = parts.flagAt(4, DEFAULT.requireStartedAudio),
                forcedPackages = parseForced(parts.getOrNull(5))
            )
        }

        private fun parseForced(raw: String?): Set<String> =
            raw.orEmpty()
                .split(',')
                .asSequence()
                .map { it.trim() }
                .filter { PACKAGE_NAME.matches(it) }
                .distinct()
                .take(MAX_FORCED_PACKAGES)
                .toSet()

        private fun flag(value: Boolean): String = if (value) "1" else "0"

        private fun List<String>.flagAt(index: Int, fallback: Boolean): Boolean =
            when (getOrNull(index)) {
                "1" -> true
                "0" -> false
                else -> fallback
            }
    }
}
