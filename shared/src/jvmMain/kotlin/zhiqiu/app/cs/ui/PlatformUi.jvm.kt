package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zhiqiu.app.cs.files.JvmClipStore
import zhiqiu.app.cs.files.mimeForName
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = AwtClipboard,
    picker = AwtFilePicker,
    opener = DesktopFileOpener,
    store = remember { JvmClipStore.default() },
)

/** AWT 系统剪贴板；macOS 上他应用占用剪贴板时读取可能抛异常 → 统一按空处理。 */
private object AwtClipboard : PlatformClipboard {
    override fun read(): String? = runCatching {
        val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()

    override fun write(text: String) {
        runCatching {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }
}

/** 原生文件对话框（支持多选）；在后台线程弹出，选完回调。 */
private object AwtFilePicker : PlatformFilePicker {
    override fun pick(onDone: (List<PickedSource>) -> Unit) {
        Thread(
            {
                val result = runCatching {
                    val dialog = FileDialog(null as Frame?, "选择要同步的文件", FileDialog.LOAD)
                    dialog.isMultipleMode = true
                    dialog.isVisible = true
                    val dir: String = dialog.directory ?: ""
                    val names = dialog.files?.toList() ?: emptyList()
                    names.mapNotNull { name ->
                        if (dir.isEmpty()) return@mapNotNull null
                        val file = File("$dir${File.separatorChar}$name")
                        if (!file.isFile) return@mapNotNull null
                        PickedSource(
                            name = file.name,
                            mime = mimeForName(file.name),
                            sizeHint = file.length(),
                            open = { file.readBytes() },
                        )
                    }
                }.getOrElse { emptyList() }
                onDone(result)
            },
            "clipsync-file-picker",
        ).apply { isDaemon = true }.start()
    }
}

private object DesktopFileOpener : PlatformFileOpener {
    override fun open(path: String) {
        runCatching {
            val file = File(path)
            if (file.isFile) Desktop.getDesktop().open(file)
        }
    }
}
