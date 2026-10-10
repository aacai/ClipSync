@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.suspendCancellableCoroutine
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.files.LocalWebStorage
import zhiqiu.app.cs.files.WasmClipStore

/**
 * Web 端：历史写 localStorage，剪贴板走 navigator.clipboard。
 *
 * 浏览器不允许后台读剪贴板（需要用户手势 + `clipboard-read` 权限），所以
 * [PlatformClipboard.supportsAutoSync] = false：发送由按钮或手动输入触发，收到的内容写回剪贴板。
 */
@Composable
internal actual fun rememberPlatformUi(): PlatformUi = rememberPlatformUi(
    clipboard = WebClipboard,
    picker = UnavailableFilePicker,
    opener = UnavailableFileOpener,
    store = remember { WasmClipStore(LocalWebStorage()) },
)

@Composable
internal actual fun rememberAppModel(platform: PlatformUi, settings: AppSettings): AppModel {
    val scope = rememberCoroutineScope()
    return remember(platform, settings) { AppModel(scope, platform, settings) }
}

private object WebClipboard : PlatformClipboard {
    override val supportsAutoSync: Boolean = false
    override val canWriteImage: Boolean = true

    override suspend fun read(): String? = clipboardReadText()

    override suspend fun write(text: String) {
        clipboardWriteText(text)
    }

    /** 浏览器没有本地文件概念，位图用 ClipboardItem 直接进剪贴板。 */
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun writeImage(bytes: ByteArray, mime: String) {
        clipboardWriteBlob(Base64.encode(bytes), if (mime == "image/jpg") "image/jpeg" else mime)
    }
}

private suspend fun clipboardReadText(): String = suspendCancellableCoroutine { cont ->
    jsClipboardRead(
        { text -> if (cont.isActive) cont.resume(text) },
        { code -> if (cont.isActive) cont.resumeWithException(ClipboardUnavailable(code = code)) },
    )
}

private suspend fun clipboardWriteText(text: String): Unit = suspendCancellableCoroutine { cont ->
    jsClipboardWrite(
        text,
        { if (cont.isActive) cont.resume(Unit) },
        { code -> if (cont.isActive) cont.resumeWithException(ClipboardUnavailable(code = code)) },
    )
}

private suspend fun clipboardWriteBlob(base64: String, mime: String): Unit = suspendCancellableCoroutine { cont ->
    jsClipboardWriteBlob(
        base64,
        mime,
        { if (cont.isActive) cont.resume(Unit) },
        { code -> if (cont.isActive) cont.resumeWithException(ClipboardUnavailable(code = code)) },
    )
}

internal actual fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit {
    jsInstallKeyChords(onChord)
    return { jsUninstallKeyChords() }
}

/**
 * 快捷键挂在 document 上：Compose 的 keydown 只在 canvas 拿到 DOM 焦点时才会触发，
 * 而网页端的焦点几乎总停在隐藏输入框里。defaultPrevented 用来跳过 Compose 已经吃掉的事件，
 * 避免同一个组合键被处理两次。
 */
@JsFun(
    """(onChord) => {
        if (window.__csChords) document.removeEventListener('keydown', window.__csChords);
        window.__csChords = (e) => {
            if (e.defaultPrevented) return;
            if (onChord(String(e.key), e.ctrlKey || e.metaKey, e.shiftKey)) e.preventDefault();
        };
        document.addEventListener('keydown', window.__csChords);
    }"""
)
private external fun jsInstallKeyChords(onChord: (String, Boolean, Boolean) -> Boolean)

@JsFun(
    """() => {
        if (!window.__csChords) return;
        document.removeEventListener('keydown', window.__csChords);
        window.__csChords = null;
    }"""
)
private external fun jsUninstallKeyChords()

/**
 * Promise 的等待留在 JS 侧，回调只传字符串：Kotlin/Wasm 里 `kotlin.String` 与 JS string 同源，
 * 这样不必碰 JsPromise 的类型参数，也不会因为非安全上下文（非 https/localhost）抛未捕获异常。
 *
 * 错误码约定：denied = 权限/手势被拒，insecure = 非 https 且非 localhost，其余原样带回。
 */
@JsFun(
    """(onOk, onErr) => {
        if (!navigator.clipboard || !navigator.clipboard.readText) { onErr('insecure'); return; }
        navigator.clipboard.readText().then((t) => onOk(t), (e) => onErr(e && e.name === 'NotAllowedError' ? 'denied' : String(e)));
    }"""
)
private external fun jsClipboardRead(onOk: (String) -> Unit, onErr: (String) -> Unit)

@JsFun(
    """(text, onOk, onErr) => {
        if (!navigator.clipboard || !navigator.clipboard.writeText) { onErr('insecure'); return; }
        navigator.clipboard.writeText(text).then(() => onOk(), (e) => onErr(e && e.name === 'NotAllowedError' ? 'denied' : String(e)));
    }"""
)
private external fun jsClipboardWrite(text: String, onOk: () -> Unit, onErr: (String) -> Unit)

@JsFun(
    """(b64, mime, onOk, onErr) => {
        if (!navigator.clipboard || !window.ClipboardItem) { onErr('insecure'); return; }
        try {
            const bin = atob(b64);
            const buf = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
            const item = new ClipboardItem({ [mime]: new Blob([buf], { type: mime }) });
            navigator.clipboard.write([item]).then(() => onOk(), (e) => onErr(e && e.name === 'NotAllowedError' ? 'denied' : String(e)));
        } catch (e) { onErr(String(e)); }
    }"""
)
private external fun jsClipboardWriteBlob(base64: String, mime: String, onOk: () -> Unit, onErr: (String) -> Unit)
