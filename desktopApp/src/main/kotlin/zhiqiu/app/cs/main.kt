package zhiqiu.app.cs

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.SystemTray
import java.awt.Window as AwtWindow
import java.util.prefs.Preferences
import zhiqiu.app.cs.ui.AppTray
import zhiqiu.app.cs.ui.showFromTray

fun main() {
    applyNativeAppearance()
    application {
        val windowState = rememberWindowState()
        var hostWindow by remember { mutableStateOf<AwtWindow?>(null) }
        val trayAvailable = remember { SystemTray.isSupported() }

        AppTray(
            onShowWindow = { showFromTray(windowState, hostWindow) },
            onQuit = ::exitApplication,
        )

        Window(
            // 关窗口只是收进托盘，进程要继续守着剪贴板；没有托盘的平台才真的退出。
            onCloseRequest = {
                val window = hostWindow
                if (trayAvailable && window != null) window.isVisible = false else exitApplication()
            },
            state = windowState,
            title = "ClipSync",
        ) {
            LaunchedEffect(Unit) { hostWindow = window }
            App()
        }
    }
}

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
