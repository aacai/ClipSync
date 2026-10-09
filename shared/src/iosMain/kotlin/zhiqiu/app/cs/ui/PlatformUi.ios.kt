package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zhiqiu.app.cs.files.InMemoryClipStore
import platform.UIKit.UIPasteboard

/** iOS MVP：剪贴板走 NSPasteboard，文件选择/打开后续接 UIDocumentPicker。 */
@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = IosClipboard,
    picker = object : PlatformFilePicker {
        override fun pick(onDone: (List<PickedSource>) -> Unit) = onDone(emptyList())
    },
    opener = PlatformFileOpener { },
    store = remember { InMemoryClipStore() },
)

private object IosClipboard : PlatformClipboard {
    override fun read(): String? = UIPasteboard.generalPasteboard.string

    override fun write(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
