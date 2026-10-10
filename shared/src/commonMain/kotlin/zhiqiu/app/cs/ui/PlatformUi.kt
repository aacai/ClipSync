package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zhiqiu.app.cs.core.deviceName
import zhiqiu.app.cs.core.installId
import zhiqiu.app.cs.files.ClipStore

/**
 * 系统剪贴板。
 *
 * [supportsAutoSync] 为 false 时平台无法在后台读剪贴板（浏览器要求用户手势 + 权限），
 * UI 必须据此改用手动发送，而不是假装自动同步在工作。
 */
interface PlatformClipboard {
    val supportsAutoSync: Boolean get() = true

    /** 能否把本地文件以“文件”形态放进剪贴板（CF_HDROP / file URL / content URI）。 */
    val canWriteFiles: Boolean get() = false

    val canWriteImage: Boolean get() = false

    suspend fun read(): String?
    suspend fun write(text: String)

    /** 文件进剪贴板，同时附带 file:// 文本，方便只认文本的目标粘贴出路径。 */
    suspend fun writeFiles(paths: List<String>): Unit = throw UnsupportedOperationException()

    suspend fun writeImage(bytes: ByteArray, mime: String): Unit = throw UnsupportedOperationException()
}

fun interface PlatformFileOpener {
    fun open(path: String)

    /** 平台是否有“本地文件”概念；浏览器沙箱里没有，UI 据此隐藏打开/下载入口。 */
    val isAvailable: Boolean get() = true
}

/** 待发布文件：字节懒读取，读之前先做大小预检。 */
class PickedSource(
    val name: String,
    val mime: String,
    val sizeHint: Long,
    val open: suspend () -> ByteArray,
)

fun interface PlatformFilePicker {
    /** 弹出系统选择器；用户取消时回调空列表。可能在后台线程回调。 */
    fun pick(onDone: (List<PickedSource>) -> Unit)

    /** 平台是否真的能挑文件；false 时 UI 会禁用入口并说明原因。 */
    val isAvailable: Boolean get() = true
}

class PlatformUi(
    val clipboard: PlatformClipboard,
    val picker: PlatformFilePicker,
    val opener: PlatformFileOpener,
    val store: ClipStore,
    val deviceName: String,
    val installId: String,
)

/** 平台剪贴板拒绝读/写时抛出。[code] 交给 UI 本地化，[detail] 是平台原始信息。 */
class ClipboardUnavailable(val code: String? = null, detail: String? = null) : Exception(detail ?: code)

/** 尚未接入系统选择器的平台：入口保留但明确告知不可用，不做静默无反应。 */
internal object UnavailableFilePicker : PlatformFilePicker {
    override val isAvailable: Boolean = false
    override fun pick(onDone: (List<PickedSource>) -> Unit) = onDone(emptyList())
}

/** 没有“本地文件”概念的平台（浏览器沙箱）。 */
internal object UnavailableFileOpener : PlatformFileOpener {
    override val isAvailable: Boolean = false
    override fun open(path: String) = Unit
}

@Composable
internal expect fun rememberPlatformUi(): PlatformUi

@Composable
internal fun rememberPlatformUi(
    clipboard: PlatformClipboard,
    picker: PlatformFilePicker,
    opener: PlatformFileOpener,
    store: ClipStore,
): PlatformUi = remember(clipboard, picker, opener, store) {
    PlatformUi(
        clipboard = clipboard,
        picker = picker,
        opener = opener,
        store = store,
        deviceName = deviceName(),
        installId = installId(),
    )
}

/**
 * 监听全局组合键，[onChord] 消费后返回 true。
 *
 * 网页端的 DOM 焦点长期停在 Compose 的隐藏输入框上，Compose 只挂在 canvas 上的
 * keydown 因此收不到任何快捷键，只能在 document 上兜底；原生平台由 Compose 自己派发。
 */
internal expect fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit
