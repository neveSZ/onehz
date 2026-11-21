package io.github.nevesz.onehz.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures captured from a real SM-S936B (`getevent -lp` / `dumpsys input`), plus a synthetic
 * foldable listing. The selector must never depend on Android classes.
 */
class TouchDeviceSelectorTest {

    private val s25Listing = """
        add device 1: /dev/input/event10
          name:     "akm-digital-hall"
          events:
            REL (0002): REL_WHEEL
          input props:
            <none>
        add device 2: /dev/input/event9
          name:     "sec_touchproximity"
          events:
            ABS (0003): 003e                  : value 255, min 0, max -1, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_DIRECT
        add device 3: /dev/input/event8
          name:     "sec_touchpad"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_X     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
                        ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_POINTER
        add device 4: /dev/input/event7
          name:     "sec_touchscreen"
          events:
            KEY (0001): KEY_WAKEUP            BTN_TOOL_FINGER      BTN_TOUCH
            ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_X     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y     : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
                        ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_DIRECT
        add device 5: /dev/input/event6
          name:     "sec_vibrator_inputff"
          events:
            FF  (0015): FF_PERIODIC           FF_SINE
          input props:
            <none>
    """.trimIndent()

    private val foldListing = """
        add device 1: /dev/input/event3
          name:     "sec_touchscreen"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_POSITION_X     : value 0, min 0, max 4095
                        ABS_MT_POSITION_Y     : value 0, min 0, max 4095
          input props:
            INPUT_PROP_DIRECT
        add device 2: /dev/input/event4
          name:     "sec_touchscreen_cover"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_POSITION_X     : value 0, min 0, max 4095
                        ABS_MT_POSITION_Y     : value 0, min 0, max 4095
          input props:
            INPUT_PROP_DIRECT
        add device 3: /dev/input/event2
          name:     "sec_epen"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_POSITION_X     : value 0, min 0, max 4095
                        ABS_MT_POSITION_Y     : value 0, min 0, max 4095
          input props:
            INPUT_PROP_DIRECT
        add device 4: /dev/input/event5
          name:     "sec_touchpad"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_POSITION_X     : value 0, min 0, max 4095
                        ABS_MT_POSITION_Y     : value 0, min 0, max 4095
          input props:
            INPUT_PROP_POINTER
    """.trimIndent()

    @Test
    fun `real device selects only the panel`() {
        val selected = TouchDeviceSelector.select(s25Listing)
        assertEquals(listOf("/dev/input/event7"), selected.map { it.path })
        assertEquals("sec_touchscreen", selected.single().name)
    }

    @Test
    fun `touchpad is rejected as pointer`() {
        val candidates = TouchDeviceSelector.parse(s25Listing)
        val touchpad = candidates.single { it.device.path == "/dev/input/event8" }
        assertFalse(touchpad.accepted)
        assertEquals("pointer/touchpad", touchpad.reason)
    }

    @Test
    fun `proximity node is rejected without mt axes`() {
        val candidates = TouchDeviceSelector.parse(s25Listing)
        val proximity = candidates.single { it.device.path == "/dev/input/event9" }
        assertFalse(proximity.accepted)
        assertEquals("missing BTN_TOUCH/ABS_MT", proximity.reason)
    }

    @Test
    fun `foldable keeps both panels and drops the pen`() {
        val selected = TouchDeviceSelector.select(foldListing)
        assertEquals(
            listOf("/dev/input/event3", "/dev/input/event4"),
            selected.map { it.path }
        )
        assertTrue(selected.none { it.name.contains("epen") })
    }

    @Test
    fun `only a touchpad means fail closed`() {
        val onlyPad = s25Listing.substringBefore("add device 4:")
        assertTrue(TouchDeviceSelector.select(onlyPad).isEmpty())
    }

    @Test
    fun `dumpsys input maps the display without touching paths`() {
        val dump = """
              4: sec_touchscreen
                Classes: KEYBOARD | TOUCH | TOUCH_MT
                Path: /dev/input/event7
              3: sec_touchpad
                Classes: KEYBOARD | TOUCH | TOUCH_MT | TOUCHPAD
                Path: /dev/input/event8
            Device 6: sec_touchscreen
              Sources: KEYBOARD | TOUCHSCREEN
                Viewport INTERNAL: displayId=0, uniqueId=local:1, port=147, isActive=[1]
            Device 3: sec_touchpad
              Sources: KEYBOARD | MOUSE | TOUCHPAD
        """.trimIndent()

        val ids = TouchDeviceSelector.parseDisplayIds(dump)
        assertEquals(0, ids["sec_touchscreen"])

        val selected = TouchDeviceSelector.select(s25Listing, ids)
        assertEquals(0, selected.single().displayId)
    }

    @Test
    fun `display ordering prefers the active display`() {
        val listing = foldListing
        val ids = mapOf("sec_touchscreen" to 1, "sec_touchscreen_cover" to 0)
        val selected = TouchDeviceSelector.select(listing, ids)
        assertEquals(listOf("/dev/input/event4", "/dev/input/event3"), selected.map { it.path })
    }
}
