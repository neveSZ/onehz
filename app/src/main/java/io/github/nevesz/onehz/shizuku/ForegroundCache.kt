package io.github.nevesz.onehz.shizuku

/**
 * Small time-based cache for the foreground package. Video detection may call the
 * foreground lookup repeatedly in a short window, while policy decisions need an
 * uncached read. Keeping the clock injectable makes the TTL testable.
 */
internal class ForegroundCache(private val ttlMs: Long) {

    private var value: String? = null
    private var storedAt = 0L

    @Synchronized
    fun get(now: Long): String? = value?.takeIf { now - storedAt < ttlMs }

    @Synchronized
    fun put(now: Long, packageName: String) {
        value = packageName
        storedAt = now
    }

    @Synchronized
    fun clear() {
        value = null
        storedAt = 0L
    }
}
