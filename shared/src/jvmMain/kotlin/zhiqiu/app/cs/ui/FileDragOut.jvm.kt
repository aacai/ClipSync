package zhiqiu.app.cs.ui

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.nio.file.Files

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.fileDragOutSource(paths: () -> List<String>): Modifier {
    val pathsState = rememberUpdatedState(paths)
    return dragAndDropSource(
            transferData = {
                val files =
                        pathsState
                                .value()
                                .map { File(it) }
                                .filter { it.isFile }
                if (files.isEmpty()) {
                    null
                } else {
                    DragAndDropTransferData(
                            transferable = DragAndDropTransferable(FileListTransferable(files)),
                            supportedActions = listOf(DragAndDropTransferAction.Copy),
                    )
                }
            },
    )
}

internal actual fun writeDragOutTemp(fileName: String, bytes: ByteArray): String? =
        runCatching {
                    val dir = File(System.getProperty("java.io.tmpdir"), "clipsync-drag-out").apply { mkdirs() }
                    val target = File(dir, fileName)
                    Files.write(target.toPath(), bytes)
                    target.absolutePath
                }
                .getOrNull()

/** 仅暴露 javaFileListFlavor，访达/资源管理器按文件列表接收。 */
private class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
            flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor != DataFlavor.javaFileListFlavor) throw UnsupportedFlavorException(flavor)
        if (files.isEmpty()) throw IOException("empty file list")
        return files
    }
}
