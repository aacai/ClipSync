package zhiqiu.app.cs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowState
import java.awt.Window as AwtWindow
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.resources.*

fun showFromTray(state: WindowState, window: AwtWindow?) {
    state.isMinimized = false
    window?.isVisible = true
    window?.toFront()
    window?.requestFocus()
}

@Composable
fun ApplicationScope.AppTray(onShowWindow: () -> Unit, onQuit: () -> Unit) {
    Tray(
        icon = rememberVectorPainter(clipSyncIcon(if (isSystemInDarkTheme()) Color.White else Color.Black)),
        tooltip = "ClipSync",
        onAction = onShowWindow,
        menu = {
            Item(stringResource(Res.string.tray_show_window), onClick = onShowWindow)
            Item(stringResource(Res.string.tray_quit), onClick = onQuit)
        },
    )
}
