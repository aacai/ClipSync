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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.ClipSyncAppContext
import zhiqiu.app.cs.core.deviceName
import zhiqiu.app.cs.core.platformOs
import zhiqiu.app.cs.core.installId
import zhiqiu.app.cs.files.AndroidClipStore
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.mimeForName
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object ClipSyncRuntime {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val filePicker = RuntimeFilePicker()

    @Volatile
    private var platformRef: PlatformUi? = null

    @Volatile
    private var modelRef: AppModel? = null

    fun platform(context: Context): PlatformUi {
        platformRef?.let { return it }
        synchronized(lock) {
            val app = context.applicationContext
            return platformRef ?: PlatformUi(
                clipboard = AndroidClipboard(app),
                picker = filePicker,
                opener = AndroidFileOpener(app),
                store = AndroidClipStore(app),
                autostart = UnavailableAutostart,
                deviceName = deviceName(),
                deviceOs = platformOs(),
                installId = installId(),
            ).also { platformRef = it }
        }
    }

    fun model(platform: PlatformUi, settings: AppSettings): AppModel {
        modelRef?.let { return it }
        synchronized(lock) {
            return modelRef ?: AppModel(scope, platform, settings).also { modelRef = it }
        }
    }

    fun model(settings: AppSettings): AppModel =
        model(
            platform(
                requireNotNull(ClipSyncAppContext.context) {
                    "ClipSyncAppContext.context must be set by the Application"
                },
            ),
            settings,
        )

    /** 界面还没起来时没什么可踢的，直接当没发生。 */
    fun retryNow() {
        modelRef?.retryNow()
    }
}

class RuntimeFilePicker : PlatformFilePicker {
    var delegate: PlatformFilePicker? by mutableStateOf(null)

    override val isAvailable: Boolean get() = delegate?.isAvailable == true

    override fun pick(onDone: (List<PickedSource>) -> Unit) {
        (delegate ?: UnavailableFilePicker).pick(onDone)
    }
}

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

    val delegate = remember(launcher) {
        PlatformFilePicker { onDone ->
            if (pending[0] == null) {
                pending[0] = onDone
                launcher.launch(arrayOf("*/*"))
            }
        }
    }

    DisposableEffect(delegate) {
        ClipSyncRuntime.filePicker.delegate = delegate
        onDispose { ClipSyncRuntime.filePicker.delegate = null }
    }

    return ClipSyncRuntime.platform(context)
}

@Composable
internal actual fun rememberAppModel(platform: PlatformUi, settings: AppSettings): AppModel =
    ClipSyncRuntime.model(platform, settings)

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
            resolver.openInputStream(this)?.use { it.readBytes() } ?: error("cannot read $name")
        },
    )
}

internal class AndroidClipboard(private val context: Context) : PlatformClipboard {
    override val canWriteFiles: Boolean = true
    override val canReadImages: Boolean = true
    override val canReadFiles: Boolean = true

    private val manager: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** 系统事件驱动，不用轮询：后台零唤醒，比定时读省电。 */
    override val changeEvents: Flow<Unit>
        get() = callbackFlow {
            val listener = ClipboardManager.OnPrimaryClipChangedListener { trySend(Unit) }
            manager.addPrimaryClipChangedListener(listener)
            awaitClose { manager.removePrimaryClipChangedListener(listener) }
        }

    override suspend fun read(): String? = runCatching {
        val clip = clipOrNull() ?: return null
        // 带 content URI 的剪贴板 coerceToText 只会退化成一条地址，那种内容交给 readFiles / readImage
        if (clip.hasContentUri()) return null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    override suspend fun readFiles(): List<PickedSource> = runCatching {
        val clip = clipOrNull() ?: return emptyList()
        if (!clip.hasContentUri()) return emptyList()
        (0 until clip.itemCount)
            .mapNotNull { clip.getItemAt(it).uri }
            .map { it.toPickedSource(context) }
            .filterNot { it.mime.startsWith("image/") }
    }.getOrDefault(emptyList())

    private fun clipOrNull(): ClipData? =
            if (!manager.hasPrimaryClip()) null else manager.primaryClip?.takeIf { it.itemCount > 0 }

    private fun ClipData.hasContentUri(): Boolean {
        if (getItemAt(0).uri == null) return false
        val mimes = (0 until description.mimeTypeCount).map { description.getMimeType(it).orEmpty() }
        return mimes.none { it.startsWith("text/") }
    }

    override suspend fun readImage(): ClipboardImage? = runCatching {
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val description = clip.description
        val mime = (0 until description.mimeTypeCount)
            .map { description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") } ?: return null
        val uri = clip.getItemAt(0).uri ?: return null
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { readCapped(it, MAX_IMAGE_BYTES) }
        } ?: return null
        if (bytes.size > MAX_IMAGE_BYTES) return null
        ClipboardImage(mime = mime, bytes = bytes, name = displayName(uri))
    }.getOrNull()

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull().orEmpty()

    override suspend fun write(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText("ClipSync", text))
    }

    override suspend fun writeFiles(paths: List<String>) {
        val file = paths.map { File(it) }.firstOrNull { it.isFile } ?: error("file is not cached locally")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        // ClipData.newUri 会让系统带上临时读授权，接收方粘贴后可直接访问。
        manager.setPrimaryClip(ClipData.newUri(context.contentResolver, file.name, uri))
    }
}

/** 读满上限就立刻收手：超上限的图不该整份进内存，调用方看返回长度超限即可拒绝。 */
private fun readCapped(stream: InputStream, cap: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() <= cap) {
        val read = stream.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private val MAX_IMAGE_BYTES = FileLimits.MAX_FILE_BYTES.toInt()

/** 通过 FileProvider 拿 content:// URI 打开缓存文件（Android 7+ 禁止 file:// 直接分享）。 */
internal class AndroidFileOpener(private val context: Context) : PlatformFileOpener {
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

internal actual fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit = {}
