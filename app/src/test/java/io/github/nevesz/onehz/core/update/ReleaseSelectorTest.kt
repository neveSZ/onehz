package io.github.nevesz.onehz.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSelectorTest {
    private val local = SemanticVersion.parse("1.0.0")!!

    private fun release(tag: String, prerelease: Boolean = tag.contains('-')): ReleaseInfo =
        ReleaseInfo(
            tag = tag,
            url = "https://github.com/neveSZ/onehz/releases/tag/$tag",
            prereleaseFlag = prerelease
        )

    @Test
    fun `stable channel ignores prereleases`() {
        val releases = listOf(release("v1.0.0"), release("v1.1.0-beta.1"))
        assertNull(ReleaseSelector.select(releases, local, UpdateChannel.STABLE))
    }

    @Test
    fun `stable channel offers a newer stable release`() {
        val releases = listOf(release("v1.0.0"), release("v1.0.1"), release("v1.1.0-beta.1"))
        val selected = ReleaseSelector.select(releases, local, UpdateChannel.STABLE)
        assertEquals("v1.0.1", selected?.tag)
    }

    @Test
    fun `prerelease channel offers the highest newer version`() {
        val releases = listOf(
            release("v1.0.1"),
            release("v1.1.0-beta.1"),
            release("v1.1.0-beta.2"),
            release("v1.0.2")
        )
        val selected = ReleaseSelector.select(
            releases,
            local,
            UpdateChannel.STABLE_AND_PRERELEASE
        )
        assertEquals("v1.1.0-beta.2", selected?.tag)
    }

    @Test
    fun `prerelease channel still prefers a newer stable`() {
        val releases = listOf(release("v1.1.0-beta.1"), release("v1.1.0"))
        val selected = ReleaseSelector.select(
            releases,
            local,
            UpdateChannel.STABLE_AND_PRERELEASE
        )
        assertEquals("v1.1.0", selected?.tag)
    }

    @Test
    fun `ignores equal and older versions`() {
        val releases = listOf(release("v1.0.0"), release("v0.9.9"), release("v1.0.0-beta.3"))
        assertNull(ReleaseSelector.select(releases, local, UpdateChannel.STABLE_AND_PRERELEASE))
    }

    @Test
    fun `ignores unparseable tags`() {
        val releases = listOf(
            ReleaseInfo("nightly", "https://github.com/neveSZ/onehz/releases/tag/nightly"),
            release("v1.0.1")
        )
        assertEquals(
            "v1.0.1",
            ReleaseSelector.select(releases, local, UpdateChannel.STABLE)?.tag
        )
    }

    @Test
    fun `a tag with a hyphen is treated as prerelease even when the flag is false`() {
        val releases = listOf(release("v1.1.0-beta.1", prerelease = false))
        assertNull(ReleaseSelector.select(releases, local, UpdateChannel.STABLE))
        assertEquals(
            "v1.1.0-beta.1",
            ReleaseSelector.select(releases, local, UpdateChannel.STABLE_AND_PRERELEASE)?.tag
        )
    }

    @Test
    fun `accepts only release urls from the onehz repository`() {
        assertTrue(
            ReleaseSelector.isAllowedReleaseUrl(
                "https://github.com/neveSZ/onehz/releases/tag/v1.0.0"
            )
        )
        assertTrue(ReleaseSelector.isAllowedReleaseUrl(ReleaseSelector.RELEASES_PAGE))
        assertTrue(
            !ReleaseSelector.isAllowedReleaseUrl("http://github.com/neveSZ/onehz/releases/tag/v1.0.0")
        )
        assertTrue(
            !ReleaseSelector.isAllowedReleaseUrl("https://github.com/other/onehz/releases/tag/v1.0.0")
        )
        assertTrue(
            !ReleaseSelector.isAllowedReleaseUrl("https://github.com/neveSZ/onehz/issues")
        )
        assertTrue(
            !ReleaseSelector.isAllowedReleaseUrl("https://github.com@evil.example/neveSZ/onehz/releases")
        )
        assertTrue(
            !ReleaseSelector.isAllowedReleaseUrl("https://github.com:8443/neveSZ/onehz/releases")
        )
        assertTrue(
            !ReleaseSelector.isAllowedReleaseUrl(
                "https://github.com/neveSZ/onehz/releases/../../other"
            )
        )
    }
}
