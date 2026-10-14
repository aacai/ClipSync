package zhiqiu.app.cs.ui

import java.io.File
import java.io.IOException
import zhiqiu.app.cs.core.platformOs

/**
 * 桌面端登录项：macOS 写 LaunchAgent，Windows 写 HKCU 的 Run 键，Linux 写 autostart 桌面项。
 *
 * 只认 jpackage 生成的启动器，从 IDE 或 gradle 直接跑时没有它，此时 [isAvailable] 为 false。
 * 登录项拉起的进程带 [LAUNCH_HIDDEN_ARGUMENT]，窗口不出现，只在托盘里守剪贴板。
 */
internal object SystemAutostart : PlatformAutostart {

    private enum class Kind { Mac, Windows, Linux, None }

    override val isAvailable: Boolean get() = launcherPath() != null && kind() != Kind.None

    override fun setEnabled(enabled: Boolean) {
        val launcher = launcherPath() ?: throw IOException("缺少 jpackage.app-path，只有打包后的应用能设置开机启动")
        val file = File(launcher)
        when (kind()) {
            Kind.Mac -> {
                val bundle = file.appBundle() ?: throw IOException("启动器不在 .app 包里")
                if (enabled) writeLaunchAgent(bundle) else removeLaunchAgent(bundle)
            }

            Kind.Windows -> registry(enabled, file.name, launcher)
            Kind.Linux -> autostartEntry(enabled, file.name, launcher)
            Kind.None -> throw IOException("这个系统没有登录项概念")
        }
    }

    private fun kind(): Kind = when (platformOs()) {
        "macOS" -> Kind.Mac
        "Windows" -> Kind.Windows
        "Linux" -> Kind.Linux
        else -> Kind.None
    }

    private fun launcherPath(): String? = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }

    private fun writeLaunchAgent(bundle: File) {
        val agent = launchAgentFile(bundle)
        val text = launchAgentXml(agentLabel(bundle), bundle.absolutePath)
        agent.parentFile?.mkdirs()
        if (!agent.isFile || agent.readText() != text) agent.writeText(text)
    }

    private fun removeLaunchAgent(bundle: File) {
        val agent = launchAgentFile(bundle)
        if (agent.isFile && !agent.delete()) throw IOException("删不掉 ${agent.absolutePath}")
    }

    private fun registry(enabled: Boolean, exeName: String, launcher: String) {
        val key = """HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run"""
        val command = if (enabled) {
            listOf("reg", "add", key, "/v", exeName, "/t", "REG_SZ", "/d", registryValue(launcher), "/f")
        } else {
            listOf("reg", "delete", key, "/v", exeName, "/f")
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().decodeToString()
        val code = process.waitFor()
        // 键本来就不存在时 delete 会失败，而那正是要的状态，所以只有 add 失败才报。
        if (enabled && code != 0) throw IOException("reg 退出码 $code：${output.take(200)}")
    }

    private fun autostartEntry(enabled: Boolean, fileName: String, launcher: String) {
        val dir = File(System.getProperty("user.home"), ".config/autostart")
        val entry = File(dir, "${fileName.lowercase()}.desktop")
        if (!enabled) {
            if (entry.isFile && !entry.delete()) throw IOException("删不掉 ${entry.absolutePath}")
            return
        }
        dir.mkdirs()
        val text = desktopEntryText(fileName, launcher)
        if (!entry.isFile || entry.readText() != text) entry.writeText(text)
    }
}

private fun launchAgentFile(bundle: File): File =
    File(File(System.getProperty("user.home"), "Library/LaunchAgents"), "${agentLabel(bundle)}.plist")

/** 启动器埋在 Contents/MacOS 底下，往上找才是 .app 本体。 */
private fun File.appBundle(): File? = generateSequence(this) { it.parentFile }.firstOrNull { it.name.endsWith(".app") }

internal fun agentLabel(bundle: File): String = bundle.name.removeSuffix(".app").sanitizeName()

/** macOS 的 LaunchAgent：登录时由 launchd 经 open 拉起 .app，参数交给应用自己。 */
internal fun launchAgentXml(label: String, bundlePath: String): String {
    val strings = listOf("/usr/bin/open", "-a", bundlePath, "--args", LAUNCH_HIDDEN_ARGUMENT)
        .joinToString(separator = "\n") { "\t\t<string>${it.escapeXml()}</string>" }
    return """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        |<plist version="1.0">
        |<dict>
        |	<key>Label</key>
        |	<string>${label.escapeXml()}</string>
        |	<key>ProgramArguments</key>
        |	<array>
        |$strings
        |	</array>
        |	<key>RunAtLoad</key>
        |	<true/>
        |</dict>
        |</plist>
        |""".trimMargin()
}

/** Windows 的 Run 键值：路径带空格要整体加引号，参数留在引号外面。 */
internal fun registryValue(exePath: String): String = """"$exePath" ${LAUNCH_HIDDEN_ARGUMENT}"""

internal fun desktopEntryText(name: String, execPath: String): String =
    """
        |[Desktop Entry]
        |Type=Application
        |Name=${name.sanitizeName()}
        |Exec=${execPath.escapeExec()} ${LAUNCH_HIDDEN_ARGUMENT}
        |Terminal=false
        |""".trimMargin()

private fun String.sanitizeName(): String = filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }

private fun String.escapeXml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun String.escapeExec(): String =
    if (any { it.isWhitespace() || it == '"' || it == '\\' }) {
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    } else {
        this
    }
