package zhiqiu.app.cs.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import zhiqiu.app.cs.files.AndroidClipStore
import zhiqiu.app.cs.files.mimeForName
import java.io.File

@Composable
internal actual fun rememberPlatformUi(): PlatformUi {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<((List<PickedSource>) -> Unit)?>(1) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val callback = pending[0]
        pending[0] = null
        if (callback != null) {
            if (uris.isEmpty()) callback(emptyList())
            else callback(uris.map { it.toPickedSource(context) })
        }
    }

    val picker = remember(launcher) {
        PlatformFilePicker { onDone ->
            if (pending[0] == null) {
                pending[0] = onDone
                launcher.launch(arrayOf("*/*"))
            }
        }
    }

    return rememberPlatformUi(
        clipboard = remember { AndroidClipboard(context) },
        picker = picker,
        opener = remember { AndroidFileOpener(context) },
        store = remember { AndroidClipStore(context) },
    )
}

private fun Uri.toPickedSource(context: Context): PickedSource {
    val resolver = context.contentResolver
    var name = "文件"
    var size = -1L
    runCatching {
        resolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    }
    val mime = runCatching { resolver.getType(this) }.getOrNull() ?: mimeForName(name)
    return PickedSource(
        name = name,
        mime = mime,
        sizeHint = size,
        open = {
            resolver.openInputStream(this)?.use { it.readBytes() } ?: error("无法读取 $name")
        },
    )
}

private class AndroidClipboard(private val context: Context) : PlatformClipboard {
    private val manager: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun read(): String? = runCatching {
        if (!manager.hasPrimaryClip()) return null
        val clip = manager.primaryClip
        if (clip == null || clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    override fun write(text: String) {
        runCatching {
            manager.setPrimaryClip(ClipData.newPlainText("ClipSync", text))
        }
    }
}

/** 通过 FileProvider 拿 content:// URI 打开缓存文件（Android 7+ 禁止 file:// 直接分享）。 */
private class AndroidFileOpener(private val context: Context) : PlatformFileOpener {
    override fun open(path: String) {
        runCatching {
            val file = File(path)
            if (!file.isFile) return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = context.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
