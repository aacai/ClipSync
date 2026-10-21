package zhiqiu.app.cs.files

import zhiqiu.app.cs.core.FileIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(50, FileLimits.maxMb)
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
        assertEquals("b_c.txt", sanitizeFileName("a/b:c.txt"))
        assertEquals("we_ird_name", sanitizeFileName("we:ird*name"))
        assertEquals("tab_name", sanitizeFileName("tab\tname"))
        assertEquals("", sanitizeFileName("   "))
        assertEquals("file", sanitizeFileName("file."))
        assertEquals(120, sanitizeFileName("x".repeat(500)).length)
    }

    @Test
    fun numbered_name_inserts_before_the_extension() {
        assertEquals("a.png", numberedName("a.png") { false })
        assertEquals("a (1).png", numberedName("a.png") { it == "a.png" })
        assertEquals("a (2).png", numberedName("a.png") { it == "a.png" || it == "a (1).png" })
        assertEquals("noext (1)", numberedName("noext") { it == "noext" })
        assertEquals("a.b.png", numberedName("a.b.png") { it == "a.png" })
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
    fun x0at_reply_is_a_plain_text_link() {
        assertEquals(
            "https://x0.at/cs-ab12_cd34.bin",
            FileTransferClient.parseX0atLink("https://x0.at/cs-ab12_cd34.bin\n"),
        )
        assertFailsWith<FileTransferException> { FileTransferClient.parseX0atLink("<html>500 Bad Gateway</html>") }
    }

    @Test
    fun uguu_reply_is_the_first_url_in_the_json() {
        val body =
                """
                {"success":true,"files":[{"hash":"f23f6083ea8cc63d","filename":"fQkJWLTP.bin",
                 "url":"https://d.uguu.se/fQkJWLTP.bin","size":300000,"dupe":false}]}
                """
                    .trimIndent()
        assertEquals("https://d.uguu.se/fQkJWLTP.bin", FileTransferClient.parseUuguLink(body))
        assertFailsWith<FileTransferException> { FileTransferClient.parseUuguLink("""{"success":false,"files":[]}""") }
        assertFailsWith<FileTransferException> { FileTransferClient.parseUuguLink("""{"success":true,"files":[]}""") }
        assertFailsWith<FileTransferException> { FileTransferClient.parseUuguLink("<!doctype html>500") }
    }

    @Test
    fun expiry_comes_from_the_host_that_took_the_upload() {
        val now = 1_700_000_000_000L
        val x0at = UploadedLink("https://x0.at/a.bin", FileTransferClient.X0_AT_TTL_MS)
        val uguu = UploadedLink("https://d.uguu.se/a.bin", FileTransferClient.UUGU_TTL_MS)
        assertEquals(now + 72L * 3_600_000L, x0at.expiresAt(now))
        assertEquals(now + 3L * 3_600_000L, uguu.expiresAt(now))
    }

    @Test
    fun rejected_issue_carries_limit_for_the_ui_to_render() {
        val issue = FileIssue.Rejected(FileRejectReason.TooLarge, "a.png")
        assertEquals(FileRejectReason.TooLarge, issue.reason)
        assertEquals(50, FileLimits.maxMb)
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
