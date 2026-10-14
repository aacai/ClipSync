package zhiqiu.app.cs.ui

import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class AutostartTest {

    @Test
    fun `agent label comes from the bundle name`() {
        assertEquals("ClipSync", agentLabel(File("/Applications/ClipSync.app")))
        assertEquals("我的App1.0", agentLabel(File("/Applications/我的 App@#1.0.app")))
    }

    @Test
    fun `launch agent opens the bundle hidden`() {
        assertEquals(
            mapOf(
                "Label" to "ClipSync",
                "ProgramArguments" to
                    listOf("/usr/bin/open", "-a", "/Applications/Clip Sync.app", "--args", "--hidden"),
                "RunAtLoad" to "true",
            ),
            plistDict(launchAgentXml("ClipSync", "/Applications/Clip Sync.app")),
        )
    }

    @Test
    fun `launch agent escapes what would break the xml`() {
        val entries = plistDict(launchAgentXml("A&B", "/Applications/<X> & \"Y\".app"))
        assertEquals("A&B", entries["Label"])
        assertEquals(
            listOf("/usr/bin/open", "-a", "/Applications/<X> & \"Y\".app", "--args", "--hidden"),
            entries["ProgramArguments"],
        )
    }

    @Test
    fun `run key quotes the executable and keeps the argument outside`() {
        assertEquals(
            """"C:\Users\me\ClipSync\ClipSync.exe" --hidden""",
            registryValue("""C:\Users\me\ClipSync\ClipSync.exe"""),
        )
        assertEquals(
            """"C:\Program Files\ClipSync\ClipSync.exe" --hidden""",
            registryValue("""C:\Program Files\ClipSync\ClipSync.exe"""),
        )
    }

    @Test
    fun `autostart entry quotes the exec only when it needs it`() {
        val plain = desktopEntryText("clipsync", "/opt/ClipSync/clipsync")
        assertTrue(plain.contains("Name=clipsync"))
        assertTrue(plain.contains("Exec=/opt/ClipSync/clipsync --hidden"))
        assertTrue(plain.contains("Terminal=false"))

        val spaced = desktopEntryText("clipsync", "/opt/My Apps/clipsync")
        assertTrue(spaced.contains("""Exec="/opt/My Apps/clipsync" --hidden"""))
    }

    @Test
    fun `macOS registration writes the launch agent beside the user library`() {
        withLoginItem("Mac OS X", "ClipSync.app/Contents/MacOS/ClipSync") { home, _ ->
            val agent = File(home, "Library/LaunchAgents/ClipSync.plist")
            assertTrue(SystemAutostart.isAvailable)

            SystemAutostart.setEnabled(true)
            assertEquals(
                mapOf(
                    "Label" to "ClipSync",
                    "ProgramArguments" to
                        listOf("/usr/bin/open", "-a", File(home, "ClipSync.app").absolutePath, "--args", "--hidden"),
                    "RunAtLoad" to "true",
                ),
                plistDict(agent.readText()),
            )

            SystemAutostart.setEnabled(false)
            assertFalse(agent.isFile)
        }
    }

    @Test
    fun `linux registration writes an autostart desktop entry`() {
        withLoginItem("Linux", "apps/clipsync") { home, launcher ->
            val entry = File(home, ".config/autostart/clipsync.desktop")
            SystemAutostart.setEnabled(true)
            assertTrue(entry.readText().contains("Exec=${launcher.absolutePath} --hidden"))
            SystemAutostart.setEnabled(false)
            assertFalse(entry.isFile)
        }
    }

    @Test
    fun `registration needs a launcher`() {
        val home = Files.createTempDirectory("clipsync-home").toFile()
        val oldHome = System.getProperty("user.home")
        val oldPath = System.getProperty("jpackage.app-path")
        try {
            System.setProperty("user.home", home.absolutePath)
            System.clearProperty("jpackage.app-path")
            assertFalse(SystemAutostart.isAvailable)
            assertFailsWith<IOException> { SystemAutostart.setEnabled(true) }
        } finally {
            System.setProperty("user.home", oldHome)
            oldPath?.let { System.setProperty("jpackage.app-path", it) }
            home.deleteRecursively()
        }
    }

    /** 造一个临时 home，把 os.name / user.home / jpackage.app-path 指过去，让登记逻辑真的落盘。 */
    private fun withLoginItem(osName: String, launcherRelativePath: String, block: (File, File) -> Unit) {
        val home = Files.createTempDirectory("clipsync-home").toFile()
        val launcher = File(home, launcherRelativePath).also { it.parentFile?.mkdirs(); it.writeText("") }
        val keys = listOf("os.name", "user.home", "jpackage.app-path")
        val previous = keys.associateWith { System.getProperty(it) }
        try {
            System.setProperty("os.name", osName)
            System.setProperty("user.home", home.absolutePath)
            System.setProperty("jpackage.app-path", launcher.absolutePath)
            block(home, launcher)
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
            home.deleteRecursively()
        }
    }

    private fun plistDict(xml: String): Map<String, Any> {
        val dict = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
            .getElementsByTagName("dict")
            .item(0)
        return dict.childNodes.elements().chunked(2).associate { (key, value) ->
            key.textContent to when (value.nodeName) {
                "array" -> value.childNodes.elements().map { it.textContent }
                "string" -> value.textContent
                else -> value.nodeName
            }
        }
    }

    private fun NodeList.elements(): List<Node> =
        (0 until length).map { item(it) }.filter { it.nodeType == Node.ELEMENT_NODE }
}
