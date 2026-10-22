package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsScreenshotHotkeyTest {

    private val raw = mutableMapOf<String, Any>()

    private fun newSettings() = AppSettings(MapSettings(raw))

    @Test
    fun defaults_to_wechats_chord() {
        assertEquals(ScreenshotHotkey.DEFAULT_SPEC, newSettings().screenshotHotkey.value)
    }

    @Test
    fun stores_the_canonical_spelling() {
        val settings = newSettings()
        settings.setScreenshotHotkey("shift+ctrl+a")
        assertEquals("Control+Shift+A", settings.screenshotHotkey.value)
        assertEquals("Control+Shift+A", raw["screenshot.hotkey"])
    }

    @Test
    fun a_written_value_survives_a_restart() {
        newSettings().setScreenshotHotkey("Command+F5")
        assertEquals("Command+F5", newSettings().screenshotHotkey.value)
    }

    @Test
    fun a_corrupt_value_falls_back_instead_of_sticking() {
        raw["screenshot.hotkey"] = "Ctrl+"
        val settings = newSettings()
        assertEquals(ScreenshotHotkey.DEFAULT_SPEC, settings.screenshotHotkey.value)

        settings.setScreenshotHotkey("Alt+Space")
        assertEquals("Alt+Space", settings.screenshotHotkey.value)
    }

    @Test
    fun a_bare_key_is_refused_not_saved() {
        val settings = newSettings()
        settings.setScreenshotHotkey("A")
        assertEquals(ScreenshotHotkey.DEFAULT_SPEC, settings.screenshotHotkey.value)
    }
}
