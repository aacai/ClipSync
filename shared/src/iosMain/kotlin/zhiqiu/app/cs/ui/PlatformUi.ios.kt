package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zhiqiu.app.cs.files.InMemoryClipStore
import platform.UIKit.UIPasteboard

/** iOS MVP：剪贴板走 NSPasteboard，文件选择/打开后续接 UIDocumentPicker。 */
@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = IosClipboard,
    picker = UnavailableFilePicker,
    opener = UnavailableFileOpener,
    store = remember { InMemoryClipStore() },
)

private object IosClipboard : PlatformClipboard {
    // iOS 16+ 后台读剪贴板会触发权限提示并返回空，自动同步不可靠 → 交给用户点按钮发送。
    override val supportsAutoSync: Boolean = false

    override suspend fun read(): String? = UIPasteboard.generalPasteboard.string

    override suspend fun write(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

internal actual fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit = {}
