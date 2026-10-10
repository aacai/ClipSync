package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.files.JvmClipStore
import zhiqiu.app.cs.files.mimeForName
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = AwtClipboard,
    picker = AwtFilePicker,
    opener = DesktopFileOpener,
    store = remember { JvmClipStore.default() },
)

@Composable
internal actual fun rememberAppModel(platform: PlatformUi, settings: AppSettings): AppModel {
    val scope = rememberCoroutineScope()
    return remember(platform, settings) { AppModel(scope, platform, settings) }
}

/** AWT 系统剪贴板；macOS 上他应用占用剪贴板时读取可能抛异常 → 统一按空处理。 */
private object AwtClipboard : PlatformClipboard {
    override val canWriteFiles: Boolean = true
    override val canWriteImage: Boolean = true

    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    override suspend fun read(): String? = runCatching {
        val contents = clipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()

    override suspend fun write(text: String) {
        val selection = StringSelection(text)
        runCatching { clipboard.setContents(selection, selection) }
            .onFailure { throw ClipboardUnavailable(detail = it.message ?: it::class.simpleName) }
    }

    override suspend fun writeFiles(paths: List<String>) {
        val files = paths.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) throw IOException("文件不在本机：${paths.joinToString()}")
        val urls = files.joinToString("\n") { it.toURI().toString() }
        setContentsSafely(MultiTransferable(text = urls, files = files))
    }

    override suspend fun writeImage(bytes: ByteArray, mime: String) {
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IOException("无法解码图片：$mime")
        setContentsSafely(MultiTransferable(text = null, image = image))
    }

    private fun setContentsSafely(transferable: MultiTransferable) {
        runCatching { clipboard.setContents(transferable, transferable) }
            .onFailure { throw ClipboardUnavailable(detail = it.message ?: it::class.simpleName) }
    }
}

/** 一次写入多种形态：文件（Finder/资源管理器可粘成文件）+ file:// 文本 + 位图。 */
private class MultiTransferable(
    private val text: String?,
    private val files: List<File> = emptyList(),
    private val image: Image? = null,
) : Transferable, ClipboardOwner {
    override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) = Unit

    override fun getTransferDataFlavors(): Array<DataFlavor> =
        buildList {
            if (text != null) add(DataFlavor.stringFlavor)
            if (files.isNotEmpty()) add(DataFlavor.javaFileListFlavor)
            if (image != null) add(DataFlavor.imageFlavor)
        }.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor?) = getTransferDataFlavors().contains(flavor)

    override fun getTransferData(flavor: DataFlavor?): Any =
        when (flavor) {
            DataFlavor.stringFlavor -> text ?: throw UnsupportedFlavorException(flavor)
            DataFlavor.javaFileListFlavor -> files
            DataFlavor.imageFlavor -> image ?: throw UnsupportedFlavorException(flavor)
            else -> throw UnsupportedFlavorException(flavor)
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

internal actual fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit = {}
