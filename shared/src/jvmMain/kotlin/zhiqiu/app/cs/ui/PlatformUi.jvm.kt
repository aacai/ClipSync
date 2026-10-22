package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.files.JvmClipStore
import zhiqiu.app.cs.files.mimeForName
import zhiqiu.app.cs.files.numberedName
import zhiqiu.app.cs.files.sanitizeFileName
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

private const val TRAY_POLL_MS = 5_000L

/** AWT 不派发剪贴板变化事件、也没有公开的 changeCount，只能整份读；窗口收进托盘后放慢到 5 秒。 */
internal fun clipboardPollIntervalMs(): Long =
    if (Window.getWindows().any { it.isDisplayable && it.isVisible }) CLIPBOARD_POLL_MS else TRAY_POLL_MS

@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = AwtClipboard,
    picker = AwtFilePicker,
    opener = DesktopFileOpener,
    store = remember { JvmClipStore.default() },
    autostart = SystemAutostart,
    downloads = DesktopDownloads,
    capture = RobotScreenCapture,
    hotkeys = CarbonGlobalHotkey,
    overlay = ScreenshotOverlay,
    hoverRevealsActions = true,
)

@Composable
internal actual fun rememberAppModel(platform: PlatformUi, settings: AppSettings): AppModel {
    val scope = rememberCoroutineScope()
    return remember(platform, settings) { AppModel(scope, platform, settings) }
}

private object AwtClipboard : PlatformClipboard {
    override val canWriteFiles: Boolean = true
    override val canWriteImage: Boolean = true
    override val canReadImages: Boolean = true
    override val canReadFiles: Boolean = true

    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    /** 同一份内容每次轮询都返回同一个 Transferable：认得出来就别再把整张位图取一遍编一次。 */
    private var lastImageContents: Transferable? = null
    private var lastFilesContents: Transferable? = null

    override fun pollIntervalMs(): Long = clipboardPollIntervalMs()

    override suspend fun read(): String? = runCatching {
        val contents = clipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()

    override suspend fun readFiles(): List<PickedSource> = runCatching {
        val contents = clipboard.getContents(null) ?: return emptyList()
        if (contents === lastFilesContents) return emptyList()
        lastFilesContents = contents
        if (!contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
        val files = contents.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return emptyList()
        files.filterIsInstance<File>().filter { it.isFile }.map { file ->
            PickedSource(
                    name = file.name,
                    mime = mimeForName(file.name),
                    sizeHint = file.length(),
                    open = { withContext(Dispatchers.IO) { file.readBytes() } },
            )
        }
    }.getOrDefault(emptyList())

    override suspend fun readImage(): ClipboardImage? = runCatching {
        val contents = clipboard.getContents(null) ?: return null
        if (contents === lastImageContents) return null
        lastImageContents = contents
        if (!contents.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
        val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image ?: return null
        val png = withContext(Dispatchers.Default) { encodePng(image) } ?: return null
        ClipboardImage(mime = "image/png", bytes = png)
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

    private fun encodePng(image: Image): ByteArray? {
        val buffered =
                if (image is RenderedImage) {
                    image
                } else {
                    val width = image.getWidth(null).coerceAtLeast(1)
                    val height = image.getHeight(null).coerceAtLeast(1)
                    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
                        val g = createGraphics()
                        g.drawImage(image, 0, 0, null)
                        g.dispose()
                    }
                }
        val out = ByteArrayOutputStream()
        return if (ImageIO.write(buffered, "png", out)) out.toByteArray() else null
    }
}

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

/** 三家的默认落点都是 ~/Downloads；Linux 上 XDG 改了名字就跟着改。 */
private object DesktopDownloads : PlatformDownloads {
    override val isAvailable: Boolean = true

    override suspend fun save(name: String, bytes: ByteArray): String =
            withContext(Dispatchers.IO) {
                val safe = sanitizeFileName(name)
                if (safe.isBlank()) throw IOException("没有可用的文件名")
                val dir = directory()
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("建不出下载目录：$dir")
                val written = numberedName(safe) { File(dir, it).exists() }
                File(dir, written).writeBytes(bytes)
                written
            }

    private fun directory(): File {
        val xdg = System.getenv("XDG_DOWNLOAD_DIR")?.removePrefix("\"")?.removeSuffix("\"")
        return xdg?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), "Downloads")
    }
}

internal actual fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit = {}
