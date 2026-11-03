package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppSettingsBackgroundTest {

    private val raw = mutableMapOf<String, Any>()

    private fun newSettings() = AppSettings(MapSettings(raw))

    @Test
    fun nothing_is_painted_before_a_picture_is_chosen() {
        val settings = newSettings()
        assertEquals("", settings.backgroundPath.value)
        assertEquals(AppSettings.DEFAULT_BACKGROUND_DIM, settings.backgroundDim.value)
    }

    @Test
    fun a_written_background_survives_a_restart() {
        newSettings().setBackgroundPath("/cache/background.png")
        newSettings().setBackgroundDim(0.3f)
        assertEquals("/cache/background.png", newSettings().backgroundPath.value)
        assertEquals(0.3f, newSettings().backgroundDim.value)
    }

    @Test
    fun clearing_the_path_frees_the_key() {
        val settings = newSettings()
        settings.setBackgroundPath("/cache/a.png")
        settings.setBackgroundPath("")
        assertEquals("", settings.backgroundPath.value)
        assertFalse(raw.containsKey("ui.background.path"))
    }

    @Test
    fun the_dim_is_clamped_to_the_paintable_range() {
        val settings = newSettings()
        settings.setBackgroundDim(2f)
        assertEquals(AppSettings.MAX_BACKGROUND_DIM, settings.backgroundDim.value)
        settings.setBackgroundDim(-1f)
        assertEquals(0f, settings.backgroundDim.value)
    }

    @Test
    fun a_corrupt_dim_falls_back_instead_of_sticking() {
        raw["ui.background.dim"] = Float.NaN
        assertEquals(AppSettings.DEFAULT_BACKGROUND_DIM, newSettings().backgroundDim.value)
    }
}
