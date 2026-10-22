package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 快捷键串是两端的共同语言：这里锁死 Rust 的 `screenshot::parse` 认的那套形状。 */
class ScreenshotHotkeyTest {

    @Test
    fun parses_the_shared_grammar() {
        val hotkey = assertNotNull(ScreenshotHotkey.parse("Alt+A"))
        assertEquals(setOf(HotkeyModifier.Alt), hotkey.modifiers)
        assertEquals("A", hotkey.key)
    }

    @Test
    fun prints_modifiers_in_command_control_alt_shift_order() {
        val spec =
                ScreenshotHotkey(
                                setOf(
                                        HotkeyModifier.Shift,
                                        HotkeyModifier.Alt,
                                        HotkeyModifier.Command,
                                        HotkeyModifier.Control,
                                ),
                                "F5",
                        )
                        .toString()
        assertEquals("Command+Control+Alt+Shift+F5", spec)
    }

    @Test
    fun round_trips_every_written_spec() {
        for (key in ScreenshotHotkey.keys) {
            for (modifier in HotkeyModifier.entries) {
                val spec = ScreenshotHotkey(setOf(modifier), key).toString()
                assertEquals(spec, ScreenshotHotkey.parse(spec)?.toString())
            }
        }
    }

    @Test
    fun rejects_shapes_rust_would_not_register() {
        assertNull(ScreenshotHotkey.parse("A"))
        assertNull(ScreenshotHotkey.parse("A+Shift"))
        assertNull(ScreenshotHotkey.parse("Shift+Shift+A"))
        assertNull(ScreenshotHotkey.parse("Alt+F13"))
        assertNull(ScreenshotHotkey.parse("Alt+"))
    }

    /** global_hotkey 的别名手写时很常见，两端得解析成同一条键。 */
    @Test
    fun accepts_the_aliases_global_hotkey_accepts() {
        assertEquals("Command+A", ScreenshotHotkey.parse("Super+A").toString())
        assertEquals("Command+A", ScreenshotHotkey.parse("Cmd+A").toString())
        assertEquals("Control+Shift+A", ScreenshotHotkey.parse("shift+ctrl+a").toString())
        assertEquals("Alt+A", ScreenshotHotkey.parse("OPTION+A").toString())
    }

    @Test
    fun recording_needs_at_least_one_modifier() {
        assertNull(ScreenshotHotkey.of("A"))
        assertNotNull(ScreenshotHotkey.of("A", command = true))
        assertEquals("Command+Control+Space", ScreenshotHotkey.of("Space", command = true, control = true).toString())
    }

    @Test
    fun the_default_chord_is_one_of_the_ones_we_register() {
        val default = assertNotNull(ScreenshotHotkey.parse(ScreenshotHotkey.DEFAULT_SPEC))
        assertEquals("Alt", default.modifiers.single().label)
        assertEquals("A", default.key)
    }
}
