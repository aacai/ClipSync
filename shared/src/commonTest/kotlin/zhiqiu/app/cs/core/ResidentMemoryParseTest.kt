package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Linux 和 Android 都从 /proc/self/status 的 VmRSS 读数，这里拿真格式的行喂它。 */
class ResidentMemoryParseTest {

    private val status =
            """
            Name:	clipsync.clip
            Umask:	0077
            State:	S (sleeping)
            Tgid:	12841
            VmPeak:	  746520 kB
            VmSize:	  746520 kB
            VmLck:	       0 kB
            VmRSS:	   96312 kB
            RssAnon:	   52104 kB
            Threads:	24
            """.trimIndent()

    @Test
    fun `takes the resident line out of a status dump`() {
        assertEquals(96_312L * 1024L, parseResidentMemory(status))
    }

    @Test
    fun `a kernel without that line has no reading`() {
        assertNull(parseResidentMemory("Name:\tclipsync.clip\nVmSize:\t  746520 kB\n"))
    }

    @Test
    fun `zero and junk are not a reading`() {
        assertNull(parseResidentMemory("VmRSS:\t0 kB"))
        assertNull(parseResidentMemory("VmRSS:\tunknown"))
        assertNull(parseResidentMemory(""))
    }
}
