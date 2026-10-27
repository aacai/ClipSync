package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.fileDropTarget(
        enabled: Boolean,
        onHovering: (Boolean) -> Unit,
        onPaths: (List<String>) -> Unit,
): Modifier = this
