package zhiqiu.app.cs.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 测试进程里没开过任何窗口，等于「收进托盘」那一档：还在守剪贴板，但不该醒得勤。 */
class ClipboardPollIntervalTest {

    @Test
    fun `no window on screen means the slow tick`() {
        assertEquals(5_000L, clipboardPollIntervalMs())
    }
}
