package io.github.nevesz.onehz.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundCacheTest {

    @Test
    fun `returns a value inside the ttl`() {
        val cache = ForegroundCache(ttlMs = 250)
        cache.put(now = 1_000, packageName = "com.example.app")

        assertEquals("com.example.app", cache.get(now = 1_249))
    }

    @Test
    fun `expires at the ttl boundary`() {
        val cache = ForegroundCache(ttlMs = 250)
        cache.put(now = 1_000, packageName = "com.example.app")

        assertNull(cache.get(now = 1_250))
    }

    @Test
    fun `clear invalidates the value`() {
        val cache = ForegroundCache(ttlMs = 250)
        cache.put(now = 1_000, packageName = "com.example.app")
        cache.clear()

        assertNull(cache.get(now = 1_001))
    }
}
