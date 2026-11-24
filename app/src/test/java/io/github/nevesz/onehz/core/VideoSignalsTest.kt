package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures captured from a real SM-S936B (Android 17) while YouTube and Instagram played. */
class VideoSignalsTest {

    private val sessions = """
        === PACKAGE SUMMARY ===
        Total sessions: 2 across 2 package(s)

        === DETAILED SESSION INFO ===
          Global priority session is com.android.server.telecom/HeadsetMediaButton/1 (userId=0)
            SUMMARY: package=com.android.server.telecom, uid=1000, controllers=0, active=false
            state=null
          Sessions Stack - have 2 sessions:
            YouTube playerlib com.google.android.youtube/com.google.android.youtube.player/1 (userId=0)
              SUMMARY: package=com.google.android.youtube, uid=10305, controllers=13, active=true
              state=PlaybackState {state=PLAYING(3), position=64841, buffered position=0, speed=1.0, updated=23761950, actions=8615, custom actions=[], active item id=-1, error=null}
            YouTube playerlib com.google.android.apps.youtube.music/YouTube playerlib/2 (userId=0)
              SUMMARY: package=com.google.android.apps.youtube.music, uid=10365, controllers=12, active=true
              state=PlaybackState {state=STOPPED(1), position=0, buffered position=0, speed=1.0, updated=15189947, actions=241335, custom actions=[], active item id=-1, error=null}
    """.trimIndent()

    private val pausedSessions = """
            SUMMARY: package=com.google.android.youtube, uid=10305, controllers=7, active=true
            state=PlaybackState {state=PAUSED(2), position=64730, buffered position=0, speed=1.0, updated=19678892, actions=8615, custom actions=[], active item id=-1, error=null}
            SUMMARY: package=com.google.android.apps.youtube.music, uid=10365, controllers=12, active=true
            state=PlaybackState {state=PLAYING(3), position=43445, buffered position=0, speed=1.0, updated=23786271, actions=2600887, custom actions=[], active item id=126, error=null}
    """.trimIndent()

    /**
     * Android 16 builds (for example HyperOS 3) list the sessions stack without the SUMMARY
     * header; each session block starts with a bare `package=` line.
     */
    private val stackSessions = """
        MEDIA SESSION SERVICE (dumpsys media_session)

        9 sessions listeners.
        Global priority session is com.android.server.telecom/HeadsetMediaButton/1 (userId=0)
          HeadsetMediaButton com.android.server.telecom/HeadsetMediaButton/1 (userId=0)
            ownerPid=1838, ownerUid=1000, userId=0
            package=com.android.server.telecom
            active=false
            state=null
        User Records:
        Record for full_user=0, profile_user=999
          Volume key long-press listener: null
          Volume key long-press listener package: 
          Media button session is com.google.android.youtube/YouTube playerlib/3 (userId=0)
          Sessions Stack - have 2 sessions:
            YouTube playerlib com.google.android.youtube/YouTube playerlib/3 (userId=0)
              ownerPid=17273, ownerUid=10219, userId=0
              package=com.google.android.youtube
              active=true
              state=PlaybackState {state=PAUSED(2), position=60977, buffered position=0, speed=1.0, updated=142780320, actions=8615, custom actions=[], active item id=-1, error=null}
            YouTube playerlib com.google.android.apps.youtube.music/YouTube playerlib/4 (userId=0)
              ownerPid=14486, ownerUid=10218, userId=0
              package=com.google.android.apps.youtube.music
              active=true
              state=PlaybackState {state=STOPPED(1), position=0, buffered position=0, speed=1.0, updated=142748302, actions=241335, custom actions=[], active item id=-1, error=null}
    """.trimIndent()

    private val processes = """
          Processes:
            Pid: 9822
            Priority: 905
              Client:
                Id: -5476376619513466592
                Name: c2.qti.avc.decoder
                Resources:
        non-secure-codec/hw-video-codec:[]:1
              Client:
                Id: -5476376619512889728
                Name: c2.android.aac.decoder
                Resources:
        non-secure-codec/sw-audio-codec:[]:1
            Pid: 30539
            Priority: 0
              Client:
                Id: -5476376619513727376
                Name: c2.qti.av1.decoder
                Resources:
        non-secure-codec/hw-video-codec:[]:1
        battery/hw-video-codec:[]:1
          Process Pid override:
          Events logs (most recent at top):
            11-20 21:52:09 addResource(pid 99999, uid 1 clientId -1, resources non-secure-codec/hw-video-codec:[]:1 )
    """.trimIndent()

    private val instagramProcesses = """
          Processes:
            Pid: 10831
            Priority: 0
              Client:
                Id: -5476376619513063680
                Name: c2.qti.vp9.decoder
                Resources:
        non-secure-codec/hw-video-codec:[]:1
              Client:
                Id: -5476376619512900528
                Name: c2.android.aac.decoder
                Resources:
        non-secure-codec/sw-audio-codec:[]:1
              Client:
                Id: -5476376619512628944
                Name: c2.qti.av1.decoder
                Resources:
        non-secure-codec/hw-video-codec:[]:1
          Process Pid override:
    """.trimIndent()

    private val ps = """
        PID NAME
        10831 com.instagram.android
        11138 com.instagram.android:fbns
        25660 com.google.android.apps.youtube.music
        30539 com.google.android.youtube
        9822 com.whatsapp
        4242 com.instagram.androidx
    """.trimIndent()

    private val audio = """
          players:
          AudioPlaybackConfiguration piid:751 deviceIds:[152] type:android.media.AudioTrack u/pid:10305/30539 state:paused attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00(FLAG_DEEP_BUFFER+FLAG_MUTE_HAPTIC)  tags= bundle=null sessionId:761 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x3, sampleRate=48000}
          AudioPlaybackConfiguration piid:823 deviceIds:[152] type:android.media.AudioTrack u/pid:10365/25660 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00(FLAG_DEEP_BUFFER+FLAG_MUTE_HAPTIC)  tags= bundle=null sessionId:89 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x3, sampleRate=48000}
          AudioPlaybackConfiguration piid:847 deviceIds:[152] type:android.media.AudioTrack u/pid:10438/10831 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0xA00(FLAG_DEEP_BUFFER+FLAG_MUTE_HAPTIC)  tags= bundle=null sessionId:841 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x3, sampleRate=44100}
          AudioPlaybackConfiguration piid:848 deviceIds:[] type:android.media.SoundPool u/pid:10438/10831 state:started attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800(FLAG_MUTE_HAPTIC)  tags= bundle=null sessionId:0 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x0, sampleRate=0}
          ducked players piids:
    """.trimIndent()

    private val pausedAudio = audio.replace(
        "u/pid:10438/10831 state:started attr:AudioAttributes: usage=USAGE_MEDIA",
        "u/pid:10438/10831 state:paused attr:AudioAttributes: usage=USAGE_MEDIA"
    )

    @Test
    fun `session state reads playing and paused`() {
        assertEquals("PLAYING(3)", VideoSignals.sessionState(sessions, "com.google.android.youtube"))
        assertEquals("PAUSED(2)", VideoSignals.sessionState(pausedSessions, "com.google.android.youtube"))
        assertEquals(
            "PLAYING(3)",
            VideoSignals.sessionState(pausedSessions, "com.google.android.apps.youtube.music")
        )
        assertNull(VideoSignals.sessionState(sessions, "com.instagram.android"))
    }

    @Test
    fun `session state also reads the stack format without the summary header`() {
        assertEquals("PAUSED(2)", VideoSignals.sessionState(stackSessions, "com.google.android.youtube"))
        assertEquals(
            "STOPPED(1)",
            VideoSignals.sessionState(stackSessions, "com.google.android.apps.youtube.music")
        )
        assertNull(VideoSignals.sessionState(stackSessions, "com.instagram.android"))
    }

    @Test
    fun `a paused youtube in the stack format does not keep the floor`() {
        val verdict = VideoSignals.decide(
            packageName = "com.google.android.youtube",
            sessionState = VideoSignals.sessionState(stackSessions, "com.google.android.youtube"),
            pids = setOf(17273),
            codecPids = mapOf(17273 to "c2.mtk.av1.decoder"),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
    }

    @Test
    fun `session active states`() {
        assertTrue(VideoSignals.isActive("PLAYING(3)"))
        assertTrue(VideoSignals.isActive("BUFFERING(6)"))
        assertFalse(VideoSignals.isActive("PAUSED(2)"))
        assertFalse(VideoSignals.isActive("STOPPED(1)"))
        assertTrue(VideoSignals.isActive("3"))
    }

    @Test
    fun `video codecs are read from the live processes section only`() {
        val codecs = VideoSignals.videoCodecPids(processes)
        assertEquals("c2.qti.avc.decoder", codecs[9822])
        assertEquals("c2.qti.av1.decoder", codecs[30539])
        assertNull(codecs[99999])
    }

    @Test
    fun `instagram holds video decoders`() {
        val codecs = VideoSignals.videoCodecPids(instagramProcesses)
        assertEquals("c2.qti.vp9.decoder", codecs[10831])
        assertEquals(1, codecs.size)
    }

    @Test
    fun `process lookup matches the app and its private processes`() {
        assertEquals(setOf(10831, 11138), VideoSignals.processPids(ps, "com.instagram.android"))
        assertEquals(setOf(30539), VideoSignals.processPids(ps, "com.google.android.youtube"))
        assertEquals(emptySet<Int>(), VideoSignals.processPids(ps, "com.instagram.android.foo"))
    }

    @Test
    fun `started media audio is detected for the app pid`() {
        val pids = VideoSignals.processPids(ps, "com.instagram.android")
        assertTrue(VideoSignals.mediaAudioStarted(audio, pids))
        assertFalse(VideoSignals.mediaAudioStarted(pausedAudio, pids))
    }

    @Test
    fun `audio from another app does not count`() {
        val instagram = VideoSignals.processPids(ps, "com.instagram.android")
        assertFalse(VideoSignals.mediaAudioStarted(audio, instagram - 10831))
    }

    @Test
    fun `youtube playing needs only the session and the codec`() {
        val pids = VideoSignals.processPids(ps, "com.google.android.youtube")
        val verdict = VideoSignals.decide(
            packageName = "com.google.android.youtube",
            sessionState = "PLAYING(3)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
        assertTrue(verdict.detail.orEmpty().contains("av1"))
    }

    @Test
    fun `youtube paused is authoritative`() {
        val pids = VideoSignals.processPids(ps, "com.google.android.youtube")
        val verdict = VideoSignals.decide(
            packageName = "com.google.android.youtube",
            sessionState = "PAUSED(2)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
    }

    @Test
    fun `youtube music is audio only`() {
        val pids = VideoSignals.processPids(ps, "com.google.android.apps.youtube.music")
        val verdict = VideoSignals.decide(
            packageName = "com.google.android.apps.youtube.music",
            sessionState = "PLAYING(3)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
        assertEquals("audio-only", verdict.detail)
    }

    @Test
    fun `instagram playing uses the audio player and the codec`() {
        val pids = VideoSignals.processPids(ps, "com.instagram.android")
        val verdict = VideoSignals.decide(
            packageName = "com.instagram.android",
            sessionState = null,
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(instagramProcesses),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
        assertTrue(verdict.detail.orEmpty().startsWith("audio+"))
    }

    @Test
    fun `instagram paused does not activate the floor`() {
        val pids = VideoSignals.processPids(ps, "com.instagram.android")
        val verdict = VideoSignals.decide(
            packageName = "com.instagram.android",
            sessionState = null,
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(instagramProcesses),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
        assertTrue(verdict.detail.orEmpty().startsWith("paused+"))
    }

    @Test
    fun `camera and games have no video decoder`() {
        val pids = VideoSignals.processPids(ps, "com.sec.android.app.camera")
        val verdict = VideoSignals.decide(
            packageName = "com.sec.android.app.camera",
            sessionState = null,
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
        assertEquals("no-video-codec", verdict.detail)
    }

    @Test
    fun `forced app is video even with a paused session and no codec`() {
        val config = VideoConfig.DEFAULT.copy(forcedPackages = setOf("com.google.android.youtube"))
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.google.android.youtube",
            sessionState = "PAUSED(2)",
            pids = emptySet(),
            codecPids = emptyMap(),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
        assertEquals("forced", verdict.detail)
    }

    @Test
    fun `forced apps still work when every signal is disabled`() {
        val config = VideoConfig(
            useMediaSession = false,
            pausedSessionBlocks = false,
            requireVideoCodec = false,
            requireStartedAudio = false,
            forcedPackages = setOf("com.instagram.android")
        )
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.instagram.android",
            sessionState = null,
            pids = emptySet(),
            codecPids = emptyMap(),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
    }

    @Test
    fun `a paused session can fall back to the audio signal`() {
        val config = VideoConfig.DEFAULT.copy(pausedSessionBlocks = false)
        val pids = VideoSignals.processPids(ps, "com.google.android.youtube")
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.google.android.youtube",
            sessionState = "PAUSED(2)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
        assertTrue(verdict.detail.orEmpty().startsWith("audio+"))
    }

    @Test
    fun `the media session can be ignored entirely`() {
        val config = VideoConfig.DEFAULT.copy(useMediaSession = false)
        val pids = VideoSignals.processPids(ps, "com.google.android.youtube")
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.google.android.youtube",
            sessionState = "PLAYING(3)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
    }

    @Test
    fun `the video codec requirement can be disabled`() {
        val config = VideoConfig.DEFAULT.copy(requireVideoCodec = false)
        val pids = VideoSignals.processPids(ps, "com.google.android.apps.youtube.music")
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.google.android.apps.youtube.music",
            sessionState = "PLAYING(3)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
    }

    @Test
    fun `a decoder alone can be accepted without a session`() {
        val config = VideoConfig.DEFAULT.copy(requireStartedAudio = false)
        val pids = VideoSignals.processPids(ps, "com.instagram.android")
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.instagram.android",
            sessionState = null,
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(instagramProcesses),
            audioStarted = false
        )
        assertEquals(VideoSignals.Source.PLAYING, verdict.source)
        assertTrue(verdict.detail.orEmpty().startsWith("codec+"))
    }

    @Test
    fun `no positive signal never detects video`() {
        val config = VideoConfig(
            useMediaSession = false,
            pausedSessionBlocks = false,
            requireVideoCodec = false,
            requireStartedAudio = false
        )
        val pids = VideoSignals.processPids(ps, "com.google.android.youtube")
        val verdict = VideoSignals.decide(
            config = config,
            packageName = "com.google.android.youtube",
            sessionState = "PLAYING(3)",
            pids = pids,
            codecPids = VideoSignals.videoCodecPids(processes),
            audioStarted = true
        )
        assertEquals(VideoSignals.Source.NONE, verdict.source)
        assertEquals("no-signal", verdict.detail)
    }

    @Test
    fun `video rule is a floor and never lowers a tier`() {
        assertEquals(20, VideoSignals.floorHz(10, 20))
        assertEquals(60, VideoSignals.floorHz(60, 20))
        assertEquals(120, VideoSignals.floorHz(120, 20))
    }
}
