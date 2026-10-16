package zhiqiu.app.cs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.Window as AwtWindow
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.platformOs
import zhiqiu.app.cs.resources.*

fun showFromTray(state: WindowState, window: AwtWindow?) {
    state.isMinimized = false
    window?.isVisible = true
    window?.toFront()
    window?.requestFocus()
}

@Composable
fun ApplicationScope.AppTray(onShowWindow: () -> Unit, onQuit: () -> Unit) {
    val icon = rememberVectorPainter(clipSyncIcon(if (isSystemInDarkTheme()) Color.White else Color.Black))
    if (platformOs() == "macOS") {
        MacTray(icon, onShowWindow)
    } else {
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
