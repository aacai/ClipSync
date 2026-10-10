package zhiqiu.app.cs

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.util.prefs.Preferences

fun main() {
    applyNativeAppearance()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ClipSync",
        ) {
            App()
        }
    }
}

/** AWT 只在进程启动时读一次 macOS 外观，所以按已落盘的主题提前定好；界面里切主题时标题栏下次启动才跟上。 */
private fun applyNativeAppearance() {
    val mode = runCatching {
        Preferences.userRoot().node("clipsync").get("ui.themeMode", "System")
    }.getOrNull()
    val appearance = when (mode) {
        "Light" -> "NSAqua"
        "Dark" -> "NSDarkAqua"
        else -> "system"
    }
    System.setProperty("apple.awt.application.appearance", appearance)
}
