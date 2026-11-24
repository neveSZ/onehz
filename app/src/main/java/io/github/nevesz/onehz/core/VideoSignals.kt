package io.github.nevesz.onehz.core

/**
 * Pure parsers for video detection without an app whitelist.
 *
 * The system gives three independent facts:
 *  - the MediaSession state of the foreground app (playing/paused);
 *  - which processes currently hold a video codec (Codec2 ResourceManager);
 *  - which media audio player is actually started (AudioService).
 *
 * With the default [VideoConfig] a video is considered active when the foreground app
 * has a video codec and either a playing MediaSession or a started USAGE_MEDIA player.
 * Audio-only apps have no video codec; camera and games hold the screen but use
 * encoders, not video decoders. Every rule can be disabled by the user, and packages
 * in [VideoConfig.forcedPackages] are always reported as playing.
 */
object VideoSignals {

    enum class Source { PLAYING, PIP, NONE, UNKNOWN }

    data class Verdict(
        val source: Source,
        val packageName: String?,
        val detail: String?
    ) {
        fun encode(): String =
            "${source.name.lowercase()}|${packageName.orEmpty()}|${detail.orEmpty()}"

        companion object {
            fun decode(raw: String?): Verdict {
                val text = raw?.trim().orEmpty()
                if (text.isEmpty()) return Verdict(Source.UNKNOWN, null, null)
                val bits = text.split('|')
                val source = when (bits.getOrNull(0)?.lowercase()) {
                    // "screen" came from the old window-held implementation; map it to playing
                    // so an old UserService during an upgrade cannot leave the floor stuck.
                    "playing", "screen" -> Source.PLAYING
                    "pip" -> Source.PIP
                    "none" -> Source.NONE
                    else -> Source.UNKNOWN
                }
                return Verdict(
                    source = source,
                    packageName = bits.getOrNull(1)?.takeIf { it.isNotBlank() },
                    detail = bits.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    private val SUMMARY = Regex("""SUMMARY:\s*package=([^,\s]+)""")
    private val STATE_NAMED = Regex("""state=PlaybackState \{state=([A-Z_]+)\((\d+)\)""")
    private val STATE_NUMERIC = Regex("""state=PlaybackState \{state=(\d+)\b""")
    private val RESOURCE_PID = Regex("""Pid:\s*(\d+)""")
    private val RESOURCE_NAME = Regex("""Name:\s*(\S+)""")
    private val AUDIO_UID_PID = Regex("""u/pid:\d+/(\d+)""")

    private val ACTIVE_NAMES = setOf(
        "PLAYING",
        "FAST_FORWARDING",
        "REWINDING",
        "BUFFERING",
        "CONNECTING"
    )
    private val ACTIVE_CODES = setOf(3, 4, 5, 6, 8)

    /**
     * State (e.g. "PLAYING(3)") of the first session owned by [packageName]. An active session
     * wins over a paused one when the app has more than one.
     */
    fun sessionState(dump: String, packageName: String): String? {
        var currentPackage: String? = null
        var currentState: String? = null
        var first: String? = null

        fun result(): String? {
            val pkg = currentPackage ?: return null
            val state = currentState ?: return null
            if (pkg != packageName) return null
            if (isActive(state)) return state
            if (first == null) first = state
            return null
        }

        for (line in dump.lineSequence()) {
            val summary = SUMMARY.find(line)
            if (summary != null) {
                result()?.let { return it }
                currentPackage = summary.groupValues[1]
                currentState = null
                continue
            }
            val named = STATE_NAMED.find(line)
            if (named != null) {
                currentState = "${named.groupValues[1]}(${named.groupValues[2]})"
                continue
            }
            val numeric = STATE_NUMERIC.find(line)
            if (numeric != null) {
                currentState = numeric.groupValues[1]
            }
        }
        return result() ?: first
    }

    fun isActive(state: String): Boolean {
        val name = state.substringBefore('(')
        if (name in ACTIVE_NAMES) return true
        val code = if ('(' in state) {
            state.substringAfter('(').substringBefore(')').toIntOrNull()
        } else {
            state.toIntOrNull()
        }
        return code != null && code in ACTIVE_CODES
    }

    fun playingState(dump: String, packageName: String): String? =
        sessionState(dump, packageName)?.takeIf { isActive(it) }

    /**
     * PIDs that currently hold a video codec, mapped to the codec name. Only the live
     * `Processes:` section is read; the historical event log is ignored.
     */
    fun videoCodecPids(dump: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        var inProcesses = false
        var currentPid: Int? = null
        var currentName: String? = null

        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            if (line == "Processes:") {
                inProcesses = true
                continue
            }
            if (!inProcesses) continue
            if (line.startsWith("Process Pid override:")) break

            RESOURCE_PID.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { currentPid = it }
            RESOURCE_NAME.find(line)?.groupValues?.get(1)?.let { currentName = it }
            if (line.contains("video-codec") && currentPid != null) {
                result.putIfAbsent(currentPid, currentName ?: "video-codec")
            }
        }
        return result
    }

    /** PIDs of [packageName], including its private processes (`package:remote`). */
    fun processPids(psDump: String, packageName: String): Set<Int> {
        val result = mutableSetOf<Int>()
        for (raw in psDump.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("PID")) continue
            val space = line.indexOfFirst { it.isWhitespace() }
            if (space <= 0) continue
            val pid = line.substring(0, space).toIntOrNull() ?: continue
            val name = line.substring(space).trimStart()
            if (name == packageName || name.startsWith("$packageName:")) result += pid
        }
        return result
    }

    /** True when one of [pids] has a started USAGE_MEDIA player. */
    fun mediaAudioStarted(audioDump: String, pids: Set<Int>): Boolean {
        if (pids.isEmpty()) return false
        for (line in audioDump.lineSequence()) {
            if (!line.contains("AudioPlaybackConfiguration")) continue
            if (!line.contains("usage=USAGE_MEDIA")) continue
            if (!line.contains("state:started")) continue
            val pid = AUDIO_UID_PID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (pid in pids) return true
        }
        return false
    }

    /**
     * Final decision for the foreground app applying the user-tunable [VideoConfig].
     *
     * With the default config a session that exists but is not playing is authoritative,
     * so a paused YouTube never falls through to the audio heuristic. Each rule can be
     * disabled individually; [VideoConfig.forcedPackages] bypasses every rule.
     */
    fun decide(
        config: VideoConfig = VideoConfig.DEFAULT,
        packageName: String?,
        sessionState: String?,
        pids: Set<Int>,
        codecPids: Map<Int, String>,
        audioStarted: Boolean
    ): Verdict {
        if (packageName.isNullOrBlank()) {
            return Verdict(Source.NONE, null, "no-foreground")
        }
        if (packageName in config.forcedPackages) {
            return Verdict(Source.PLAYING, packageName, "forced")
        }
        if (!config.hasPositiveSignal) {
            return Verdict(Source.NONE, packageName, "no-signal")
        }

        val sessionPlaying = sessionState != null && isActive(sessionState)
        if (config.useMediaSession && sessionState != null && !sessionPlaying &&
            config.pausedSessionBlocks
        ) {
            return Verdict(Source.NONE, packageName, "session:$sessionState")
        }

        val codec = codecPids.entries.firstOrNull { it.key in pids }?.value
        if (config.requireVideoCodec && codec == null) {
            val detail = if (sessionState != null) "audio-only" else "no-video-codec"
            return Verdict(Source.NONE, packageName, detail)
        }
        if (config.useMediaSession && sessionPlaying) {
            return Verdict(Source.PLAYING, packageName, "$sessionState+${codec ?: "session"}")
        }
        if (config.requireStartedAudio && !audioStarted) {
            return Verdict(Source.NONE, packageName, "paused+${codec ?: "media"}")
        }
        if (config.requireStartedAudio) {
            return Verdict(Source.PLAYING, packageName, "audio+${codec ?: "media"}")
        }
        if (codec != null) {
            return Verdict(Source.PLAYING, packageName, "codec+$codec")
        }
        return Verdict(Source.NONE, packageName, "no-signal")
    }

    /** The video rule is a floor: it never lowers a tier, only raises it to the minimum. */
    fun floorHz(hz: Int, minimum: Int): Int = maxOf(hz, minimum)
}
