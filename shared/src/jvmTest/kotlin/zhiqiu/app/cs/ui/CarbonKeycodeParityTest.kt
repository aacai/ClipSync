package zhiqiu.app.cs.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import zhiqiu.app.cs.core.ScreenshotHotkey

/**
 * commonMain 能录进设置的键名，jvmMain 那张 Carbon 键码表必须一个不落地查得到；
 * 否则用户选完键只会在注册那一刻收到一句「这个键没能用上」，而且 Rust 端还好好写着同一个键。
 */
class CarbonKeycodeParityTest {

    @Test
    fun `every advertised hotkey key has a carbon keycode`() {
        val missing = ScreenshotHotkey.keys.filter { Mac.keyCode(it) == null }.sorted()
        assertEquals(emptyList(), missing)
    }
}
