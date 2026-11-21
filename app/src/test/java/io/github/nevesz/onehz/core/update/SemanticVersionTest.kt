package io.github.nevesz.onehz.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `accepts a leading v and build metadata`() {
        val release = SemanticVersion.parse("v1.2.3+build.7")
        assertNotNull(release)
        assertEquals(listOf(1, 2, 3), release!!.core)
        assertFalse(release.isPrerelease)
    }

    @Test
    fun `pads missing core components`() {
        assertEquals(0, SemanticVersion.parse("1.0")!!.compareTo(SemanticVersion.parse("1.0.0")!!))
    }

    @Test
    fun `stable release wins over prerelease`() {
        assertTrue(SemanticVersion.parse("1.1.0")!! > SemanticVersion.parse("1.1.0-beta.1")!!)
        assertTrue(SemanticVersion.parse("1.1.0-rc.1")!! < SemanticVersion.parse("1.1.0")!!)
    }

    @Test
    fun `numeric prerelease identifiers compare numerically`() {
        assertTrue(SemanticVersion.parse("1.1.0-beta.10")!! > SemanticVersion.parse("1.1.0-beta.2")!!)
        assertTrue(SemanticVersion.parse("1.1.0-beta.2")!! > SemanticVersion.parse("1.1.0-beta.1")!!)
    }

    @Test
    fun `numeric identifiers sort before alphanumeric ones`() {
        assertTrue(SemanticVersion.parse("1.0.0-1")!! < SemanticVersion.parse("1.0.0-alpha")!!)
    }

    @Test
    fun `fewer prerelease identifiers sort first`() {
        assertTrue(SemanticVersion.parse("1.0.0-alpha")!! < SemanticVersion.parse("1.0.0-alpha.1")!!)
    }

    @Test
    fun `alphanumeric identifiers use lexical order`() {
        assertTrue(SemanticVersion.parse("1.0.0-alpha")!! < SemanticVersion.parse("1.0.0-beta")!!)
    }

    @Test
    fun `huge numeric identifiers do not overflow`() {
        assertTrue(
            SemanticVersion.parse("1.0.0-999999999999999999999")!! >
                SemanticVersion.parse("1.0.0-99999999999999999999")!!
        )
    }

    @Test
    fun `rejects malformed tags`() {
        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("release"))
        assertNull(SemanticVersion.parse("1..0"))
        assertNull(SemanticVersion.parse("1.0.0-"))
        assertNull(SemanticVersion.parse("1.0.0-beta..1"))
        assertNull(SemanticVersion.parse("1.0.0+"))
    }
}
