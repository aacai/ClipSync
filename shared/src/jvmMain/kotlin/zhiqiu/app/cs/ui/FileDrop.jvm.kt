package zhiqiu.app.cs.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.fileDropTarget(
        enabled: Boolean,
        onHovering: (Boolean) -> Unit,
        onPaths: (List<String>) -> Unit,
): Modifier {
    if (!enabled) return this
    val hovering = rememberUpdatedState(onHovering)
    val paths = rememberUpdatedState(onPaths)
    val target =
            remember {
                object : DragAndDropTarget {
                    override fun onStarted(event: DragAndDropEvent) {
                        if (event.dragData() is DragData.FilesList) hovering.value(true)
                    }

                    override fun onEntered(event: DragAndDropEvent) {
                        if (event.dragData() is DragData.FilesList) hovering.value(true)
                    }

                    override fun onExited(event: DragAndDropEvent) {
                        hovering.value(false)
                    }

                    override fun onEnded(event: DragAndDropEvent) {
                        hovering.value(false)
                    }

                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        hovering.value(false)
                        val data = event.dragData() as? DragData.FilesList ?: return false
                        val files = data.readFiles()
                        if (files.isEmpty()) return false
                        paths.value(files)
                        return true
                    }
                }
            }
    return dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
            target = target,
    )
}
