package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DozeWhitelistTest {

    private val dump = """
        Whitelist system apps:
          system,com.android.shell
          system,com.google.android.gms
        Whitelist user apps:
          user,com.whatsapp
          user,org.telegram.messenger
        Whitelist unforced apps:
        """
            .trimIndent()

    @Test
    fun parsesSystemAndUserEntries() {
        assertEquals(
            setOf(
                "com.android.shell",
                "com.google.android.gms",
                "com.whatsapp",
                "org.telegram.messenger",
            ),
            DozeWhitelist.parse(dump),
        )
    }

    @Test
    fun ignoresHeadersAndEmptyInput() {
        assertTrue(DozeWhitelist.parse("Whitelist user apps:").isEmpty())
        assertTrue(DozeWhitelist.parse(null).isEmpty())
        assertTrue(DozeWhitelist.parse("").isEmpty())
    }

    @Test
    fun doesNotMatchPackageTextWithoutPrefix() {
        assertTrue(DozeWhitelist.parse("  com.example.app").isEmpty())
    }
}
