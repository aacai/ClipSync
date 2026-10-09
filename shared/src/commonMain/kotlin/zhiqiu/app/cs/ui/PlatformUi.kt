package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zhiqiu.app.cs.core.deviceName
import zhiqiu.app.cs.core.installId
import zhiqiu.app.cs.files.ClipStore

/** 系统剪贴板（读用于监视变化、写用于收到即上屏）。 */
interface PlatformClipboard {
    fun read(): String?
    fun write(text: String)
}

fun interface PlatformFileOpener {
    /** 打开一个已缓存的本地文件。 */
    fun open(path: String)
}

/** 一次被选中的待发布文件：字节懒读取，读之前先做 50MB 预检。 */
class PickedSource(
    val name: String,
    val mime: String,
    val sizeHint: Long,
    val open: suspend () -> ByteArray,
)

fun interface PlatformFilePicker {
    /** 弹出系统选择器；用户取消时回调空列表。可能在后台线程回调。 */
    fun pick(onDone: (List<PickedSource>) -> Unit)
}

/** 各平台注入给共享 UI 的系统能力集合。 */
class PlatformUi(
    val clipboard: PlatformClipboard,
    val picker: PlatformFilePicker,
    val opener: PlatformFileOpener,
    val store: ClipStore,
    val deviceName: String,
    val installId: String,
)

@Composable
internal expect fun rememberPlatformUi(): PlatformUi

/** 各平台共用的默认构造（actual 只需补平台对象）。 */
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
