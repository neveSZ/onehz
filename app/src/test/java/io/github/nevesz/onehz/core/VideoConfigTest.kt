package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoConfigTest {

    @Test
    fun `missing or invalid data falls back to the defaults`() {
        assertEquals(VideoConfig.DEFAULT, VideoConfig.decode(null))
        assertEquals(VideoConfig.DEFAULT, VideoConfig.decode(""))
        assertEquals(VideoConfig.DEFAULT, VideoConfig.decode("v2|0|0|0|0|com.example"))
        assertEquals(VideoConfig.DEFAULT, VideoConfig.decode("garbage"))
    }

    @Test
    fun `defaults reproduce the original rules`() {
        val config = VideoConfig.DEFAULT
        assertTrue(config.useMediaSession)
        assertTrue(config.pausedSessionBlocks)
        assertTrue(config.requireVideoCodec)
        assertTrue(config.requireStartedAudio)
        assertTrue(config.forcedPackages.isEmpty())
        assertTrue(config.hasPositiveSignal)
        assertEquals(4, config.activeRuleCount)
    }

    @Test
    fun `encoding round trips every rule and the forced list`() {
        val config = VideoConfig(
            useMediaSession = false,
            pausedSessionBlocks = true,
            requireVideoCodec = false,
            requireStartedAudio = true,
            forcedPackages = setOf("com.example.one", "com.example.two")
        )
        val decoded = VideoConfig.decode(config.encode())
        assertEquals(config, decoded)
        assertTrue(config.encode().startsWith("v1|0|1|0|1|"))
    }

    @Test
    fun `invalid and duplicated packages are dropped`() {
        val decoded = VideoConfig.decode("v1|1|1|1|1|com.example.one,not a package,com.example.one,,1com.bad,com.example.two")
        assertEquals(setOf("com.example.one", "com.example.two"), decoded.forcedPackages)
    }

    @Test
    fun `forced list is capped`() {
        val packages = (1..80).map { "com.example.app$it" }.joinToString(",")
        val decoded = VideoConfig.decode("v1|1|1|1|1|$packages")
        assertEquals(VideoConfig.MAX_FORCED_PACKAGES, decoded.forcedPackages.size)
        assertTrue(decoded.forcedPackages.all { it.startsWith("com.example.app") })
    }

    @Test
    fun `no positive signal is reported when every probe is off`() {
        val config = VideoConfig(
            useMediaSession = false,
            pausedSessionBlocks = false,
            requireVideoCodec = false,
            requireStartedAudio = false
        )
        assertFalse(config.hasPositiveSignal)
        assertEquals(0, config.activeRuleCount)
    }

    @Test
    fun `the paused rule only counts while the session is used`() {
        val config = VideoConfig(useMediaSession = false)
        assertEquals(2, config.activeRuleCount)
    }
}
