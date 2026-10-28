package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** macOS 上拿 `ps` 的 rss 当外线：偏移读错（比如读成 phys_footprint）在这里就会露出来。 */
class ResidentMemoryJvmTest {

    private val mb = 1024L * 1024

    @Test
    fun `this process reports its own resident memory`() {
        val bytes = assertNotNull(platformResidentMemory())
        assertTrue(bytes in (mb + 1)..(4_096 * mb), "implausible reading $bytes")
    }

    @Test
    fun `the reading sits within a round of what ps says`() {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("mac")) return
        val reported = psResidentBytes() ?: return
        val bytes = assertNotNull(platformResidentMemory())
        val slack = maxOf(reported / 10, 4 * mb)
        assertTrue(kotlin.math.abs(bytes - reported) <= slack, "JNA said $bytes but ps said $reported")
    }

    private fun psResidentBytes(): Long? =
            runCatching {
                        val pid = ProcessHandle.current().pid().toString()
                        val process = ProcessBuilder("ps", "-o", "rss=", "-p", pid)
                                .redirectErrorStream(true)
                                .start()
                        val text = process.inputStream.use { it.reader().readText() }
                        process.waitFor()
                        text.trim().toLongOrNull()?.times(1024)
                    }
                    .getOrNull()
}
