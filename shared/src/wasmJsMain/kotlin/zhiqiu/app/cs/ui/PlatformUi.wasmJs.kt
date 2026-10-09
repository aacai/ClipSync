package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zhiqiu.app.cs.files.LocalWebStorage
import zhiqiu.app.cs.files.WasmClipStore

/**
 * Web 端 MVP：历史存会话内存，剪贴板读/写暂为桩
 * （浏览器剪贴板 API 是异步的，等 Web 端正式支持时补）。
 */
@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = WebClipboard,
    picker = object : PlatformFilePicker {
        override fun pick(onDone: (List<PickedSource>) -> Unit) = onDone(emptyList())
    },
    opener = PlatformFileOpener { },
    store = remember { WasmClipStore(LocalWebStorage()) },
)

private object WebClipboard : PlatformClipboard {
    override fun read(): String? = null
    override fun write(text: String) = Unit
}
