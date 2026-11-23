package io.github.nevesz.onehz.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PipSignalsTest {
    private val dump = """
          * Task{64b84c0 #260 type=standard A=10219:com.google.android.youtube U=0 visible=true visibleRequested=true mode=pinned translucent=true sz=1}
            mLastReportedPictureInPictureMode=true
    """.trimIndent()

    @Test
    fun `reads the pinned app`() {
        assertEquals("com.google.android.youtube", PipSignals.packageName(dump))
    }

    @Test
    fun `ignores a fullscreen task`() {
        val fullscreen = dump.replace("mode=pinned", "mode=fullscreen")
        assertNull(PipSignals.packageName(fullscreen))
    }

    @Test
    fun `empty dump is ignored`() {
        assertNull(PipSignals.packageName(null))
        assertNull(PipSignals.packageName(""))
    }
}
