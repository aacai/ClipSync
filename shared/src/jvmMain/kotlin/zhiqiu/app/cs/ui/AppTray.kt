package zhiqiu.app.cs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowState
import java.awt.Desktop
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.desktop.AppReopenedListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.Window as AwtWindow
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.platformOs
import zhiqiu.app.cs.resources.*

fun showFromTray(state: WindowState, window: AwtWindow?) {
    state.isMinimized = false
    window?.isVisible = true
    requestAppForeground()
    window?.toFront()
    window?.requestFocus()
}

/**
 * 主窗口显隐的唯一来源：托盘、关窗、录屏都改这里，`main.kt` 的 `Window(visible=…)` 跟着它走。
 *
 * 录屏时先把窗口收起来，录完按录之前的样子还回去——从托盘里按的快捷键，录完不该突然蹦出窗口。
 */
object MainWindowVisibility : PlatformWindow {
    override val isAvailable: Boolean = true

    private val visibility = mutableStateOf(true)

    /** 主窗口当前是否该露着；Compose 读它来决定 `Window(visible=…)`。 */
    val visible: Boolean
        get() = visibility.value

    /** 录屏开始前窗口是露着的吗；null 表示这次不是为录屏藏的。 */
    private var beforeRecording: Boolean? = null

    /** 进程启动时定初始状态：登录项拉起应用时窗口不能先闪一下再藏。 */
    fun setInitialVisible(value: Boolean) {
        visibility.value = value
        beforeRecording = null
    }

    fun show() {
        visibility.value = true
    }

    fun hide() {
        visibility.value = false
    }

    override fun hideForRecording() {
        if (beforeRecording == null) beforeRecording = visible
        visibility.value = false
    }

    override fun restoreAfterRecording() {
        val target = beforeRecording ?: return
        beforeRecording = null
        visibility.value = target
    }
}

private fun requestAppForeground() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) desktop.requestForeground(true)
}

@Composable
fun ApplicationScope.AppTray(onShowWindow: () -> Unit, onQuit: () -> Unit) {
    val icon = rememberVectorPainter(clipSyncIcon(if (isSystemInDarkTheme()) Color.White else Color.Black))
    // 关窗后窗口只是藏起来：点 Dock 会走 App Reopen，跟点菜单栏托盘同一条显示路径。
    InstallDockReopen(onShowWindow)
    when (platformOs()) {
        "macOS" -> MacTray(icon, onShowWindow)
        "Windows" -> WindowsTray(icon, onShowWindow, onQuit)
        else ->
            Tray(
                    icon = icon,
                    tooltip = "ClipSync",
                    onAction = onShowWindow,
                    menu = {
                        Item(stringResource(Res.string.tray_show_window), onClick = onShowWindow)
                        Item(stringResource(Res.string.app_quit), onClick = onQuit)
                    },
            )
    }
}

/**
 * 用户点 Dock / 任务栏图标时，系统会发「重新打开应用」。
 * 窗口若已用 visible=false 藏进托盘，不接这个事件就只会把进程激活，窗口还是不出来。
 */
@Composable
private fun InstallDockReopen(onShowWindow: () -> Unit) {
    val currentOnShow by rememberUpdatedState(onShowWindow)
    DisposableEffect(Unit) {
        if (!Desktop.isDesktopSupported()) return@DisposableEffect onDispose {}
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) return@DisposableEffect onDispose {}
        val listener = AppReopenedListener { currentOnShow() }
        desktop.addAppEventListener(listener)
        onDispose { desktop.removeAppEventListener(listener) }
    }
}

@Composable
private fun MacTray(icon: Painter, onShowWindow: () -> Unit) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentOnShow by rememberUpdatedState(onShowWindow)
    val awtIcon = remember(icon, density, layoutDirection) {
        icon.toAwtImage(density, layoutDirection, Size(22f, 22f))
    }
    DisposableEffect(awtIcon) {
        val trayIcon = TrayIcon(awtIcon, "ClipSync").apply {
            isImageAutoSize = true
            addMouseListener(
                object : MouseAdapter() {
                    override fun mousePressed(event: MouseEvent) = currentOnShow()
                },
            )
        }
        SystemTray.getSystemTray().add(trayIcon)
        onDispose { SystemTray.getSystemTray().remove(trayIcon) }
    }
}

@Composable
private fun WindowsTray(icon: Painter, onShowWindow: () -> Unit, onQuit: () -> Unit) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val awtIcon =
            remember(icon, density, layoutDirection) {
                icon.toAwtImage(density, layoutDirection, Size(22f, 22f))
            }
    val showLabel = stringResource(Res.string.tray_show_window)
    val quitLabel = stringResource(Res.string.app_quit)
    val currentOnShow by rememberUpdatedState(onShowWindow)
    val currentOnQuit by rememberUpdatedState(onQuit)
    DisposableEffect(awtIcon, showLabel, quitLabel) {
        val font = trayMenuFont()
        val menu =
                PopupMenu().apply {
                    add(
                            MenuItem(showLabel).apply {
                                this.font = font
                                addActionListener { currentOnShow() }
                            }
                    )
                    addSeparator()
                    add(
                            MenuItem(quitLabel).apply {
                                this.font = font
                                addActionListener { currentOnQuit() }
                            }
                    )
                }
        val trayIcon =
                TrayIcon(awtIcon, "ClipSync").apply {
                    isImageAutoSize = true
                    popupMenu = menu
                    addMouseListener(
                            object : MouseAdapter() {
                                override fun mousePressed(event: MouseEvent) {
                                    if (event.button == MouseEvent.BUTTON1) currentOnShow()
                                }
                            },
                    )
                }
        val tray = SystemTray.getSystemTray()
        runCatching { tray.add(trayIcon) }
        onDispose { tray.remove(trayIcon) }
    }
}

private fun trayMenuFont(): Font {
    val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    val family =
            listOf("Microsoft YaHei UI", "Microsoft YaHei", "SimHei", "SimSun", "Noto Sans SC")
                    .firstOrNull { it in installed }
    return Font(family ?: Font.DIALOG, Font.PLAIN, 12)
}
