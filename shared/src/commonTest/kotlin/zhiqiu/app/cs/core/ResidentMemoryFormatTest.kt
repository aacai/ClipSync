package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

class ResidentMemoryFormatTest {

    @Test
    fun `below a megabyte it speaks kilobytes`() {
        assertEquals("512 KB", formatResidentMemory(512 * KB))
        assertEquals("1023 KB", formatResidentMemory(MB - 1))
    }

    @Test
    fun `keeps one decimal only while the reading is small`() {
        assertEquals("1.0 MB", formatResidentMemory(MB))
        assertEquals("8.5 MB", formatResidentMemory(8 * MB + MB / 2))
        assertEquals("10 MB", formatResidentMemory(10 * MB))
    }

    @Test
    fun `rounds down to whole megabytes and switches to gigabytes`() {
        assertEquals("84 MB", formatResidentMemory(84 * MB + 400 * KB))
        assertEquals("1023 MB", formatResidentMemory(1023 * MB + MB / 2))
        assertEquals("2.0 GB", formatResidentMemory(2 * GB))
    }
}
