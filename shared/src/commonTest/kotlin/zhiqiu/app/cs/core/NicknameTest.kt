package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NicknameTest {

    @Test
    fun `trims surrounding spaces`() {
        assertEquals("MacBook", AppSettings.normalizeNickname("  MacBook  "))
    }

    @Test
    fun `keeps only the first line`() {
        assertEquals("a b", AppSettings.normalizeNickname("a b\nc\nd"))
    }

    @Test
    fun `caps length at 24`() {
        assertEquals("a".repeat(24), AppSettings.normalizeNickname("a".repeat(40)))
    }

    @Test
    fun `blank stays blank`() {
        assertEquals("", AppSettings.normalizeNickname("   \n  "))
    }
}
