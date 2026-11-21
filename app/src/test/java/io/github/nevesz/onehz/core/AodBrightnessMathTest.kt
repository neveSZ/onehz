package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AodBrightnessMathTest {

    @Test
    fun `uses the chosen percentage inside the band`() {
        assertEquals(5, AodBrightnessMath.compute(screenRaw = 52, pct = 10, minPct = 5, maxPct = 25))
        assertEquals(13, AodBrightnessMath.compute(screenRaw = 128, pct = 10, minPct = 5, maxPct = 25))
        assertEquals(26, AodBrightnessMath.compute(screenRaw = 255, pct = 10, minPct = 5, maxPct = 25))
    }

    @Test
    fun `clamps the chosen percentage to the band`() {
        // 30% of 255 = 76, but the band ceiling is 25% -> 64
        assertEquals(64, AodBrightnessMath.compute(screenRaw = 255, pct = 30, minPct = 5, maxPct = 25))
        // 2% of 200 = 4, but the band floor is 5% -> 10
        assertEquals(10, AodBrightnessMath.compute(screenRaw = 200, pct = 2, minPct = 5, maxPct = 25))
    }

    @Test
    fun `band adjusts the effective percentage without touching the chosen one`() {
        // Chosen 10% stays 10%; the band 15-25% makes the effective value 15% of 200 = 30.
        assertEquals(30, AodBrightnessMath.compute(screenRaw = 200, pct = 10, minPct = 15, maxPct = 25))
    }

    @Test
    fun `keeps a readable floor in a dark room`() {
        assertEquals(2, AodBrightnessMath.compute(screenRaw = 9, pct = 10, minPct = 5, maxPct = 25))
        assertEquals(2, AodBrightnessMath.compute(screenRaw = 1, pct = 10, minPct = 5, maxPct = 25))
    }

    @Test
    fun `never exceeds the raw maximum`() {
        // raw is clamped to 255; 50% of it is 128, never 255 * 50 (out of range).
        assertEquals(128, AodBrightnessMath.compute(screenRaw = 300, pct = 50, minPct = 40, maxPct = 50))
    }

    @Test
    fun `tolerates an inverted band`() {
        assertEquals(26, AodBrightnessMath.compute(screenRaw = 255, pct = 10, minPct = 25, maxPct = 5))
    }
}
