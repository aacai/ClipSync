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
import zhiqiu.app.cs.ui.LAUNCH_HIDDEN_ARGUMENT
import zhiqiu.app.cs.ui.showFromTray

fun main(args: Array<String>) {
    applyNativeAppearance()
    // 登录项拉起进程时带这个参数：进程照常守着剪贴板，但窗口先不出现
    val startHidden = LAUNCH_HIDDEN_ARGUMENT in args
    application {
        val windowState = rememberWindowState()
        var windowVisible by remember { mutableStateOf(!startHidden) }
        var hostWindow by remember { mutableStateOf<AwtWindow?>(null) }
        val trayAvailable = remember { SystemTray.isSupported() }

        AppTray(
            onShowWindow = {
                windowVisible = true
                // 显示必须和 toFront 同一条同步路径，等 Compose 的 visible 会晚一个 AWT tick，抬不起窗口
                showFromTray(windowState, hostWindow)
            },
            onQuit = ::exitApplication,
        )

        Window(
            // 关窗口只是收进托盘，进程要继续守着剪贴板；没有托盘的平台才真的退出。
            onCloseRequest = {
                if (trayAvailable) windowVisible = false else exitApplication()
            },
            state = windowState,
            visible = windowVisible,
            title = "ClipSync",
        ) {
            LaunchedEffect(Unit) { hostWindow = window }
            App(onQuit = ::exitApplication)
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
