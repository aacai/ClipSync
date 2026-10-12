package zhiqiu.app.cs.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceLabelTest {

    @Test
    fun `appends the system tag`() {
        assertEquals("MacBook · macOS", deviceLabel("MacBook", "macOS"))
    }

    @Test
    fun `skips a tag the name already carries`() {
        assertEquals("Web", deviceLabel("Web", "Web"))
        assertEquals("我的 macOS 机器", deviceLabel("我的 macOS 机器", "macOS"))
    }

    @Test
    fun `name alone when the peer is an old client`() {
        assertEquals("Phone", deviceLabel("Phone", ""))
    }
}
