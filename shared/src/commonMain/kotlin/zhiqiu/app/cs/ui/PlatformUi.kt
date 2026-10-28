package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.ScreenshotHotkey
import zhiqiu.app.cs.core.deviceName
import zhiqiu.app.cs.core.platformOs
import zhiqiu.app.cs.core.installId
import zhiqiu.app.cs.files.ClipStore

/**
 * 登录项拉起应用时带的参数：进程照常起来守剪贴板，但主窗口先不出现。
 *
 * 登记项（桌面端）和桌面端入口都读它，所以放在两边都够得着的公共 API 里。
 */
const val LAUNCH_HIDDEN_ARGUMENT: String = "--hidden"

/** 没有事件源的平台多久读一次剪贴板。 */
const val CLIPBOARD_POLL_MS = 800L

class ClipboardImage(
    val mime: String,
    val bytes: ByteArray,
    val name: String = "",
)

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

    val canReadImages: Boolean get() = false

    /** 能否读出剪贴板里的文件（CF_HDROP / file URL / content URI）。 */
    val canReadFiles: Boolean get() = false

    /** 剪贴板里是图片而不是文本时取这张图；没有就返回 null。 */
    suspend fun readImage(): ClipboardImage? = null

    /** 剪贴板里是文件而不是文本时取这批文件；只读元信息，字节留给调用方按需取。 */
    suspend fun readFiles(): List<PickedSource> = emptyList()

    /** 剪贴板变化事件；null 表示平台没有事件源，只能轮询。 */
    val changeEvents: Flow<Unit>? get() = null

    /** 只能轮询时多久读一次：界面收进托盘后没必要读得那么勤，那是进程唯一还在醒的源头。 */
    fun pollIntervalMs(): Long = CLIPBOARD_POLL_MS

    suspend fun read(): String?
    suspend fun write(text: String)

    suspend fun writeFiles(paths: List<String>): Unit = throw UnsupportedOperationException()

    suspend fun writeImage(bytes: ByteArray, mime: String): Unit = throw UnsupportedOperationException()
}

fun interface PlatformFileOpener {
    fun open(path: String)

    /** 平台是否有“本地文件”概念；浏览器沙箱里没有，UI 据此隐藏打开/下载入口。 */
    val isAvailable: Boolean get() = true
}

class PickedSource(
    val name: String,
    val mime: String,
    val sizeHint: Long,
    val open: suspend () -> ByteArray,
)

fun interface PlatformFilePicker {
    fun pick(onDone: (List<PickedSource>) -> Unit)

    val isAvailable: Boolean get() = true
}

/**
 * 登录时自动启动：把启动器登记进系统认可的登录项。
 *
 * [isAvailable] 为 false 的平台（手机、浏览器）没有用户可见的登录项，UI 要直接藏掉这一行。
 */
interface PlatformAutostart {
    val isAvailable: Boolean get() = false

    /** 登记（true）或撤销（false）登录项，写不进去就抛，让界面如实报错。 */
    fun setEnabled(enabled: Boolean)
}

/**
 * 把链接交给系统默认浏览器。
 *
 * app 里没有 webview，也拿不到系统代理；下载这类慢活交给浏览器更划算。
 */
interface PlatformBrowser {
    /** 真的交出去了才返回 true；这个平台压根没浏览器概念就是 false。 */
    fun open(url: String): Boolean
}

class PlatformUi(
    val clipboard: PlatformClipboard,
    val picker: PlatformFilePicker,
    val opener: PlatformFileOpener,
    val store: ClipStore,
    val autostart: PlatformAutostart,
    val deviceName: String,
    val deviceOs: String,
    val installId: String,
    val downloads: PlatformDownloads = UnavailableDownloads,
    val browser: PlatformBrowser = UnavailableBrowser,
    val capture: PlatformScreenCapture = UnavailableScreenCapture,
    val hotkeys: PlatformGlobalHotkey = UnavailableGlobalHotkey,
    val overlay: PlatformScreenshotOverlay = UnavailableScreenshotOverlay,
    val window: PlatformWindow = UnavailableWindow,
    /**
     * 有指针悬浮的平台（桌面、网页）：列表行快捷操作默认收起，hover 才展开，减少视觉噪音；
     * 触摸设备（手机）没有 hover 事件，常驻显示，操作效率不打折。
     */
    val hoverRevealsActions: Boolean = false,
)

/** 整屏截图：拿到的是 PNG 字节，抓不到就抛，让界面如实报错。 */
interface PlatformScreenCapture {
    val isAvailable: Boolean get() = false

    /**
     * 启动时申请一次抓取权限。
     *
     * macOS 上光查不弹也不登记：只有真申请过，app 才会出现在「系统设置 → 录屏」列表里，
     * 用户才有地方可勾。已经给过的、或者没有这个概念的平台直接返回 true。
     */
    fun ensurePermission(): Boolean = true

    suspend fun capture(): ByteArray
}

/**
 * 微信那种框选截屏：先把屏幕冻住，盖一层能拖能缩的遮罩，用户定下哪块就只留哪块。
 *
 * 只有桌面有「一整屏接得住鼠标的无边框窗口」这个概念；别的平台 [isAvailable] 为假，
 * 快捷键退回原来的整屏直存。
 */
interface PlatformScreenshotOverlay {
    val isAvailable: Boolean get() = false

    /** 弹遮罩层等用户框选：切好了返回 PNG 字节，按取消返回 null，抓不到就抛。 */
    suspend fun crop(): ByteArray?

    /** 同一套框选，确认后返回区域和这次工具条上选的录屏选项。取消返回 null。 */
    suspend fun selectRegion(): RecordSelection? = null
}

/**
 * 全局组合键：窗口不在前台也要能截屏，所以不能走 Compose 的按键事件。
 *
 * [isAvailable] 为 false 的平台（手机、浏览器）没有这个概念，设置里那一组要整组藏掉。
 */
interface PlatformGlobalHotkey {
    val isAvailable: Boolean get() = false

    /** 装监听；回调跑在平台自己的线程上，重复调用只装一次。 */
    fun start(onTrigger: () -> Unit)

    /** 换键：新键注册成功才放掉旧键，失败就保持原样并返回 false。 */
    fun update(hotkey: ScreenshotHotkey): Boolean

    /** 录屏快捷键。没有全局热键的平台保持空实现。 */
    fun startRecording(onTrigger: () -> Unit) = Unit

    fun updateRecording(hotkey: ScreenshotHotkey): Boolean = false
}

/** 主屏上的一块区域，单位是点，原点在该屏左上角。 */
data class RecordRegion(val x: Float, val y: Float, val width: Float, val height: Float)

/** 录屏工具条确认后的结果：区域 + 这一次选的画质和声音来源。 */
class RecordSelection(
    val region: RecordRegion,
    val quality: RecordQuality,
    val systemAudio: Boolean,
    val mic: Boolean,
    val micDevice: String,
)

/**
 * 主窗口显隐。录屏时把自家窗口收起来，录完再按原来的样子还回去：
 * 用户是从托盘里按快捷键的，录完也不该突然蹦出一个窗口。
 */
interface PlatformWindow {
    val isAvailable: Boolean get() = false

    /** 记下当前显隐再藏起来；重复调用不覆盖第一次记下的状态。 */
    fun hideForRecording() = Unit

    /** 把 [hideForRecording] 藏起来的窗口还原；本来就没藏过就什么都不做。 */
    fun restoreAfterRecording() = Unit
}

/** 「存到下载目录」：桌面和 Android 写用户自己的 Downloads，浏览器交给它自己的下载行为。 */
interface PlatformDownloads {
    val isAvailable: Boolean get() = false

    /** 写进下载目录，返回真正落盘的名字（重名会让路）；写不进去就抛，让界面如实报错。 */
    suspend fun save(name: String, bytes: ByteArray): String
}

class ClipboardUnavailable(val code: String? = null, detail: String? = null) : Exception(detail ?: code)

/**
 * 平台没有屏幕录制权限。
 *
 * 单独一个类型是因为 macOS 不报错、只把壁纸给你，而引导文案要跟界面语言走，不能让平台层写死。
 */
class ScreenCapturePermissionMissing : Exception()

/** 没有登录项概念的平台（手机沙箱、浏览器）。 */
internal object UnavailableAutostart : PlatformAutostart {
    override fun setEnabled(enabled: Boolean): Unit = throw UnsupportedOperationException("这个平台没有登录项")
}

/** 没有「下载目录」概念的平台（iOS 沙箱）。 */
internal object UnavailableDownloads : PlatformDownloads {
    override suspend fun save(name: String, bytes: ByteArray): String =
            throw UnsupportedOperationException("这个平台没有下载目录")
}

/** 没有系统浏览器概念的平台（浏览器自己就是浏览器）。 */
internal object UnavailableBrowser : PlatformBrowser {
    override fun open(url: String): Boolean = false
}

/** 抓不了屏的平台（手机沙箱、浏览器）。 */
internal object UnavailableScreenCapture : PlatformScreenCapture {
    override suspend fun capture(): ByteArray = throw UnsupportedOperationException("这个平台不能截屏")
}

/** 没有遮罩层框选的平台（手机、浏览器都盖不住别人的整屏）。 */
internal object UnavailableScreenshotOverlay : PlatformScreenshotOverlay {
    override suspend fun crop(): ByteArray? = throw UnsupportedOperationException("这个平台没有框选遮罩层")
}

/** 没有全局组合键概念的平台（手机、浏览器都在别的 app 前面插不进手）。 */
internal object UnavailableGlobalHotkey : PlatformGlobalHotkey {
    override fun start(onTrigger: () -> Unit) = Unit
    override fun update(hotkey: ScreenshotHotkey): Boolean = false
}

/** 没有「主窗口」概念的平台（手机、浏览器由系统管窗口）。 */
internal object UnavailableWindow : PlatformWindow

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
    autostart: PlatformAutostart = UnavailableAutostart,
    downloads: PlatformDownloads = UnavailableDownloads,
    browser: PlatformBrowser = UnavailableBrowser,
    capture: PlatformScreenCapture = UnavailableScreenCapture,
    hotkeys: PlatformGlobalHotkey = UnavailableGlobalHotkey,
    overlay: PlatformScreenshotOverlay = UnavailableScreenshotOverlay,
    window: PlatformWindow = UnavailableWindow,
    hoverRevealsActions: Boolean = false,
): PlatformUi = remember(clipboard, picker, opener, store, autostart, downloads, browser, capture, hotkeys, overlay, window, hoverRevealsActions) {
    PlatformUi(
        clipboard = clipboard,
        picker = picker,
        opener = opener,
        store = store,
        autostart = autostart,
        downloads = downloads,
        browser = browser,
        capture = capture,
        hotkeys = hotkeys,
        overlay = overlay,
        window = window,
        deviceName = deviceName(),
        deviceOs = platformOs(),
        installId = installId(),
        hoverRevealsActions = hoverRevealsActions,
    )
}

/**
 * 监听全局组合键，[onChord] 消费后返回 true。
 *
 * 网页端的 DOM 焦点长期停在 Compose 的隐藏输入框上，Compose 只挂在 canvas 上的
 * keydown 因此收不到任何快捷键，只能在 document 上兜底；原生平台由 Compose 自己派发。
 */
internal expect fun installKeyChords(onChord: (key: String, mod: Boolean, shift: Boolean) -> Boolean): () -> Unit

@Composable
internal expect fun rememberAppModel(platform: PlatformUi, settings: AppSettings): AppModel

/**
 * macOS 藏系统标题栏后，红绿灯还浮在左上角。
 * 内容整体下移躲开即可；再加左边距会把状态区顶歪。其它平台全是 0。
 */
internal object MacChromeInset {
    private val active: Boolean
        get() = platformOs() == "macOS"

    /** 标题栏高度方向：整块顶栏下移，别顶到红绿灯。 */
    val top: Dp
        get() = if (active) 28.dp else 0.dp
}
