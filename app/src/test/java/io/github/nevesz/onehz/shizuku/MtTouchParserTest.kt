package io.github.nevesz.onehz.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class MtTouchParserTest {

    private fun abs(name: String, hex: String) = "[  100.000000] EV_ABS       $name   $hex"
    private fun key(name: String, action: String) = "[  100.000000] EV_KEY       $name   $action"

    @Test
    fun `single finger emits one down and one up`() {
        val parser = MtTouchParser()
        assertEquals(1, parser.feed(abs("ABS_MT_TRACKING_ID", "0000000a")))
        assertEquals(0, parser.feed(key("BTN_TOUCH", "DOWN")))
        assertEquals(0, parser.feed(abs("ABS_MT_POSITION_X", "00000100")))
        assertEquals(0, parser.feed(abs("ABS_MT_POSITION_Y", "00000100")))
        assertEquals(1, parser.activePointers)
        assertEquals(-1, parser.feed(abs("ABS_MT_TRACKING_ID", "ffffffff")))
        assertEquals(0, parser.activePointers)
    }

    @Test
    fun `second finger never repeats the down edge`() {
        val parser = MtTouchParser()
        assertEquals(1, parser.feed(abs("ABS_MT_TRACKING_ID", "0000000a")))
        assertEquals(0, parser.feed(abs("ABS_MT_SLOT", "00000000")))
        assertEquals(0, parser.feed(abs("ABS_MT_SLOT", "00000001")))
        assertEquals(0, parser.feed(abs("ABS_MT_TRACKING_ID", "0000000b")))
        assertEquals(2, parser.activePointers)
        // One finger lifts while the other stays: no UP edge yet.
        assertEquals(0, parser.feed(abs("ABS_MT_TRACKING_ID", "ffffffff")))
        assertEquals(1, parser.activePointers)
        // Last finger lifts.
        assertEquals(0, parser.feed(abs("ABS_MT_SLOT", "00000000")))
        assertEquals(-1, parser.feed(abs("ABS_MT_TRACKING_ID", "ffffffff")))
    }

    @Test
    fun `button up is ignored while another finger remains`() {
        val parser = MtTouchParser()
        assertEquals(1, parser.feed(abs("ABS_MT_TRACKING_ID", "00000001")))
        assertEquals(0, parser.feed(abs("ABS_MT_SLOT", "00000001")))
        assertEquals(0, parser.feed(abs("ABS_MT_TRACKING_ID", "00000002")))
        // Some drivers send BTN_TOUCH UP for the first finger even with another one down.
        assertEquals(0, parser.feed(key("BTN_TOUCH", "UP")))
        assertEquals(2, parser.activePointers)
    }

    @Test
    fun `button fallback works without tracking ids`() {
        val parser = MtTouchParser()
        assertEquals(1, parser.feed(key("BTN_TOUCH", "DOWN")))
        assertEquals(-1, parser.feed(key("BTN_TOUCH", "UP")))
    }

    @Test
    fun `reset closes an open gesture`() {
        val parser = MtTouchParser()
        assertEquals(1, parser.feed(key("BTN_TOUCH", "DOWN")))
        assertEquals(-1, parser.reset())
        assertEquals(0, parser.reset())
    }

    @Test
    fun `unrelated lines are ignored`() {
        val parser = MtTouchParser()
        assertEquals(0, parser.feed("[  100.000000] EV_SYN       SYN_REPORT   00000000"))
        assertEquals(0, parser.feed("[  100.000000] EV_ABS       ABS_MT_POSITION_X   00000100"))
    }
}
