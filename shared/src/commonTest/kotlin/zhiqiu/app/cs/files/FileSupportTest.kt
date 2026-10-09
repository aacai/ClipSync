package zhiqiu.app.cs.files

import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.FileIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FileSupportTest {

    @Test
    fun validate_rejects_empty_and_oversize() {
        assertEquals(FileRejectReason.Empty, validateOutgoingFile("a.png", 0, 0))
        assertEquals(FileRejectReason.TooLarge, validateOutgoingFile("a.png", FileLimits.MAX_FILE_BYTES + 1, 1))
        assertNull(validateOutgoingFile("a.png", 1024, 1024))
        assertEquals(FileLimits.MAX_FILE_BYTES, MAX_FILE_BYTES)
    }

    @Test
    fun validate_rejects_unusable_names() {
        val reason = validateOutgoingFile("../../../", 10, 10)
        assertNotNull(reason)
        assertTrue(reason is FileRejectReason.InvalidName)
        assertNull(validateOutgoingFile("照片.png", 10, 10))
    }

    @Test
    fun sanitize_strips_path_traversal() {
        assertEquals("passwd", sanitizeFileName("../../../etc/passwd"))
        assertEquals("c.png", sanitizeFileName("a/b\\c.png"))
        assertEquals("script.sh", sanitizeFileName("/tmp/evil/script.sh"))
        assertTrue(sanitizeFileName("..").none { it == '.' })
    }

    @Test
    fun sanitize_scrubs_reserved_and_control_chars() {
        assertEquals("_con.txt", sanitizeFileName("con.txt"))
        assertEquals("_LPT1.dat", sanitizeFileName("LPT1.dat"))
        assertEquals("b_c.txt", sanitizeFileName("a/b:c.txt")) // 只取 basename，再净化
        assertEquals("we_ird_name", sanitizeFileName("we:ird*name"))
        assertEquals("tab_name", sanitizeFileName("tab\tname"))
        assertEquals("", sanitizeFileName("   "))
        assertEquals("file", sanitizeFileName("file."))
        assertEquals(120, sanitizeFileName("x".repeat(500)).length)
    }

    @Test
    fun mime_detection_covers_images_and_common_types() {
        assertEquals("image/png", mimeForName("a.PNG"))
        assertEquals("image/jpeg", mimeForName("a.jpeg"))
        assertEquals("image/gif", mimeForName("a.gif"))
        assertEquals("application/pdf", mimeForName("a.pdf"))
        assertEquals("text/plain", mimeForName("a.txt"))
        assertEquals("application/zip", mimeForName("a.zip"))
        assertEquals("application/octet-stream", mimeForName("a.unknown"))
        assertEquals("application/octet-stream", mimeForName("noext"))
        assertTrue(isImage("image/png"))
        assertTrue(!isImage("video/mp4"))
    }

    @Test
    fun display_and_size_format() {
        val file = FileClip(
            id = "1", name = "shot.png", mime = "image/png", size = 10,
            sha256 = "00", link = "http://x", expiresAt = 0, ts = 0,
            senderId = "d", senderName = "n",
        )
        assertEquals("shot.png", displayTitle(file))
        val doc = file.copy(name = "doc.pdf", mime = "application/pdf")
        assertEquals("doc.pdf", displayTitle(doc))

        assertEquals("0 B", humanSize(0))
        assertEquals("1.0 KB", humanSize(1024))
        assertEquals("5.0 MB", humanSize(5L * 1024 * 1024))
        assertEquals("1.5 GB", humanSize(1536L * 1024 * 1024))
    }

    @Test
    fun expiry_defaults_to_72h_and_unknown_falls_back() {
        val now = 1_700_000_000_000L
        assertEquals(now + 3_600_000L, ClipSyncEngine.expiryFrom("1h", now))
        assertEquals(now + 12L * 3_600_000L, ClipSyncEngine.expiryFrom("12h", now))
        assertEquals(now + 24L * 3_600_000L, ClipSyncEngine.expiryFrom("24h", now))
        assertEquals(now + 72L * 3_600_000L, ClipSyncEngine.expiryFrom("72h", now))
        assertEquals(now + 72L * 3_600_000L, ClipSyncEngine.expiryFrom("999h", now))
    }

    @Test
    fun rejected_issue_carries_limit_for_the_ui_to_render() {
        val issue = FileIssue.Rejected(FileRejectReason.TooLarge, "a.png")
        assertEquals(50, issue.maxMb)
        assertEquals("a.png", issue.name)
    }

    @Test
    fun file_clip_fields_are_serializable_metadata() = runTest {
        val file = FileClip(
            id = "m1", name = "a.png", mime = "image/png", size = 3,
            sha256 = "abcd", link = "https://litterbox/x", expiresAt = 42,
            ts = 7, senderId = "dev1", senderName = "Pixel",
        )
        assertEquals("a.png", file.name)
        assertTrue(file.cached == false)
        assertNull(file.localPath)
    }
}
