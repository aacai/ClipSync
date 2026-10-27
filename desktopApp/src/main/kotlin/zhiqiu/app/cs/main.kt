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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.awt.SystemTray
import java.awt.Window as AwtWindow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.prefs.Preferences
import kotlin.concurrent.thread
import zhiqiu.app.cs.ui.AppTray
import zhiqiu.app.cs.ui.LAUNCH_HIDDEN_ARGUMENT
import zhiqiu.app.cs.ui.MacChrome
import zhiqiu.app.cs.ui.MainWindowVisibility
import zhiqiu.app.cs.ui.ScreenshotOverlay
import zhiqiu.app.cs.ui.showFromTray

// 本机回环端口充当单实例互斥锁：能绑定说明本进程是唯一的，绑定失败说明已有实例在跑
private const val SINGLE_INSTANCE_PORT = 48123

fun main(args: Array<String>) {
    applyNativeAppearance()
    // 第二个实例：通知已有实例把窗口抬起来，自己直接退出
    if (!tryAcquireInstanceLock()) {
        notifyExistingInstance()
        return
    }
    val showSignal = Channel<Unit>(Channel.CONFLATED)
    startInstanceServer(showSignal)
    // 登录项拉起进程时带这个参数：进程照常守着剪贴板，但窗口先不出现
    val startHidden = LAUNCH_HIDDEN_ARGUMENT in args
    // 显隐只有 MainWindowVisibility 一个来源：托盘、关窗、录屏都改它，窗口跟着它走
    MainWindowVisibility.setInitialVisible(!startHidden)
    application {
        val windowState = rememberWindowState()
        var hostWindow by remember { mutableStateOf<AwtWindow?>(null) }
        val trayAvailable = remember { SystemTray.isSupported() }

        // 别的实例想打开窗口时，这里收到信号把窗口抬起来
        LaunchedEffect(Unit) {
            showSignal.consumeEach {
                MainWindowVisibility.show()
                showFromTray(windowState, hostWindow)
            }
        }

        AppTray(
            onShowWindow = {
                MainWindowVisibility.show()
                // 显示必须和 toFront 同一条同步路径，等 Compose 的 visible 会晚一个 AWT tick，抬不起窗口
                showFromTray(windowState, hostWindow)
            },
            onQuit = ::exitApplication,
        )

        // 截屏遮罩层：整个进程就这一个窗口，平时藏着，框完还藏回去——关掉只会关掉 Compose 那一半。
        ScreenshotOverlay.OverlayWindow()

        Window(
            // 关窗口只是收进托盘，进程要继续守着剪贴板；没有托盘的平台才真的退出。
            onCloseRequest = {
                if (trayAvailable) MainWindowVisibility.hide() else exitApplication()
            },
            state = windowState,
            visible = MainWindowVisibility.visible,
            title = "ClipSync",
        ) {
            LaunchedEffect(Unit) {
                hostWindow = window
                MacChrome.hideSystemTitleBar(window)
            }
            App(onQuit = ::exitApplication)
        }
    }
}

// 返回 true 表示成功拿到锁（本进程是唯一的），false 表示端口已被占用即已有实例
private fun tryAcquireInstanceLock(): Boolean =
    runCatching {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), SINGLE_INSTANCE_PORT))
        INSTANCE_SOCKET = socket
    }.isSuccess

private var INSTANCE_SOCKET: ServerSocket? = null

private fun startInstanceServer(signal: Channel<Unit>) {
    thread(name = "clipsync-instance-server", isDaemon = true) {
        val socket = INSTANCE_SOCKET ?: return@thread
        try {
            while (true) {
                // 第二个实例连进来即视为「请把窗口显示出来」
                socket.accept().use {}
                signal.trySend(Unit)
            }
        } catch (_: Exception) {
            // 套接字关闭，进程退出
        }
    }
}

private fun notifyExistingInstance() {
    runCatching {
        Socket(InetAddress.getByName("127.0.0.1"), SINGLE_INSTANCE_PORT).use { it.getOutputStream().write(1) }
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
