package zhiqiu.app.cs.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 回声窗口只看假钟：时间逻辑不靠真的等，推一下就该过期。 */
class EchoLogTest {

    @Test
    fun `a text just sent comes back as an echo`() {
        var now = 0L
        val log = EchoLog(windowMs = 10_000, capacity = 32, clock = { now })

        log.remember("first")
        assertTrue(log.isEcho("first"))
        assertFalse(log.isEcho("second"))
    }

    @Test
    fun `the second send does not make the first one forgettable`() {
        var now = 0L
        val log = EchoLog(windowMs = 10_000, capacity = 32, clock = { now })

        log.remember("first")
        now = 1_000
        log.remember("second")

        assertTrue(log.isEcho("first"))
        assertTrue(log.isEcho("second"))
    }

    @Test
    fun `a copy of the same text after the window is a real clip again`() {
        var now = 0L
        val log = EchoLog(windowMs = 10_000, capacity = 32, clock = { now })

        log.remember("note")
        now = 9_999
        assertTrue(log.isEcho("note"))

        now = 10_000
        assertFalse(log.isEcho("note"))
    }

    @Test
    fun `forgetting old sends keeps the log from growing`() {
        var now = 0L
        val log = EchoLog(windowMs = 1_000, capacity = 2, clock = { now })

        log.remember("a")
        now = 10
        log.remember("b")
        now = 20
        log.remember("c")

        assertFalse(log.isEcho("a"))
        assertTrue(log.isEcho("c"))
    }
}
