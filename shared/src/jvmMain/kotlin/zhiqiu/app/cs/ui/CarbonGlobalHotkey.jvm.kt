package zhiqiu.app.cs.ui

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import zhiqiu.app.cs.core.HotkeyModifier
import zhiqiu.app.cs.core.ScreenshotHotkey

/**
 * Carbon 全局热键：组合键要在别的 app 前面被收到，JVM 里只有这条路。
 *
 * 只做 macOS，其他桌面系统上 [isAvailable] 为假，设置里整组藏掉。
 * 键码表抄的是 Rust 端 global-hotkey 用的同一份 Carbon 表，所以两端同一个键截的是同一个键。
 */
internal object CarbonGlobalHotkey : PlatformGlobalHotkey {

    override val isAvailable: Boolean
        get() = Mac.isMacOs && ensureInstalled()

    private val lock = Any()

    private var handlerRef: Pointer? = null
    private var hotKeyRef: Pointer? = null
    private var registered: ScreenshotHotkey? = null
    private var onTrigger: (() -> Unit)? = null

    /**
     * JNA 只弱引用回调对象，一旦没人强引用它，Carbon 拿到的就是一个悬空指针。
     *
     * 所以这个字段必须「被读」：Release 的 ProGuard 会把只写不读的字段当无用存储删掉，
     * 监听装完就被回收，热键注册照样返回 0、事件却再也进不来（实测踩过）。
     */
    private var callback: HotkeyCallback? = null

    override fun start(onTrigger: () -> Unit) {
        synchronized(lock) { this.onTrigger = onTrigger }
    }

    override fun update(hotkey: ScreenshotHotkey): Boolean {
        if (!isAvailable) return false
        val carbon = Mac.carbon ?: return false
        val code = Mac.keyCode(hotkey.key) ?: return false
        val mask = Mac.modifierMask(hotkey.modifiers)
        if (synchronized(lock) { registered == hotkey && hotKeyRef != null }) return true
        val out = arrayOfNulls<Pointer>(1)
        // 锁要留在主队列外面拿着：回调本身就在主队列上跑、也要拿这把锁，套在一起会互等。
        val status = MacMainQueue.ask {
            val target = Mac.applicationEventTarget() ?: return@ask -1
            runCatching {
                carbon.RegisterEventHotKey(
                    code,
                    mask,
                    Mac.HotKeyId(Mac.SIGNATURE, (mask shl 16) or code),
                    target,
                    0,
                    out,
                )
            }.getOrDefault(-1)
        } ?: return false
        if (status != Mac.NO_ERROR) return false
        synchronized(lock) {
            // 新键已经在了才放掉旧键：反过来做的话，注册失败会让用户一个快捷键都不剩
            val previous = hotKeyRef
            hotKeyRef = out[0]
            registered = hotkey
            if (previous != null) {
                MacMainQueue.post { runCatching { carbon.UnregisterEventHotKey(previous) } }
            }
            return true
        }
    }

    private fun ensureInstalled(): Boolean {
        if (synchronized(lock) { callback != null && handlerRef != null }) return true
        val carbon = Mac.carbon ?: return false
        val listener =
            object : HotkeyCallback {
                override fun invoke(
                    callRef: Pointer?,
                    eventRef: Pointer?,
                    userData: Pointer?,
                ): Int {
                    // 回调跑在 AppKit 主线程上，异常绝不能顺着 C 栈退出去
                    val trigger = synchronized(lock) { onTrigger }
                    runCatching { trigger?.invoke() }
                    return Mac.NO_ERROR
                }
            }
        val spec = Mac.EventTypeSpec(Mac.EVENT_CLASS_KEYBOARD, Mac.EVENT_HOT_KEY_PRESSED)
        val out = arrayOfNulls<Pointer>(1)
        val status = MacMainQueue.ask {
            val target = Mac.applicationEventTarget() ?: return@ask -1
            runCatching {
                carbon.InstallEventHandler(target, listener, 1L, spec, null, out)
            }.getOrDefault(-1)
        } ?: return false
        if (status != Mac.NO_ERROR) return false
        return synchronized(lock) {
            callback = listener
            handlerRef = out[0]
            true
        }
    }
}

/** JNA 的 Carbon 回调原型；返回值是 OSStatus（32 位），不是 Short。 */
internal interface HotkeyCallback : Callback {
    fun invoke(callRef: Pointer?, eventRef: Pointer?, userData: Pointer?): Int
}

internal interface CarbonLibrary : Library {
    fun GetApplicationEventTarget(): Pointer

    fun InstallEventHandler(
        target: Pointer,
        handler: HotkeyCallback,
        numTypes: Long,
        list: Mac.EventTypeSpec,
        userData: Pointer?,
        outRef: Array<Pointer?>,
    ): Int

    fun RemoveEventHandler(handlerRef: Pointer?): Int

    fun RegisterEventHotKey(
        keyCode: Int,
        modifiers: Int,
        hotKeyId: Mac.HotKeyId,
        target: Pointer,
        options: Int,
        outRef: Array<Pointer?>,
    ): Int

    fun UnregisterEventHotKey(hotKeyRef: Pointer?): Int
}

private interface CoreGraphicsLibrary : Library {
    fun CGPreflightScreenCaptureAccess(): Boolean
    fun CGRequestScreenCaptureAccess(): Boolean
}

/** Carbon 那一层的常量、键码表和加载，全放这里，出问题时一眼能看完。 */
internal object Mac {

    val isMacOs: Boolean =
        System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)

    const val NO_ERROR = 0
    const val EVENT_CLASS_KEYBOARD = 0x6B657962 // 'keyb'
    const val EVENT_HOT_KEY_PRESSED = 5
    const val SIGNATURE = 0x636C7073 // 'clps'

    private const val COMMAND_MASK = 0x0100
    private const val SHIFT_MASK = 0x0200
    private const val OPTION_MASK = 0x0800
    private const val CONTROL_MASK = 0x1000

    val carbon: CarbonLibrary? by lazy {
        runCatching { Native.load("Carbon", CarbonLibrary::class.java) }.getOrNull()
    }

    private val coreGraphics: CoreGraphicsLibrary? by lazy {
        runCatching { Native.load("CoreGraphics", CoreGraphicsLibrary::class.java) }.getOrNull()
    }

    /**
     * 没有屏幕录制权限时 macOS 不报错，只会把桌面壁纸给你，所以抓之前先问一次。
     *
     * 光用 preflight 查是不够的：它不弹窗、也不会把 app 登记进「系统设置 → 录屏」的列表，
     * 用户连去哪儿勾选都找不到。所以没权限时真去申请一次，把系统的授权弹窗拉起来。
     * 返回「现在到底能不能抓」；刚批下来的权限要重启进程才生效，那一下仍然是不能。
     */
    fun requestScreenCapture(): Boolean {
        val cg = coreGraphics ?: return true
        if (cg.CGPreflightScreenCaptureAccess()) return true
        return runCatching { cg.CGRequestScreenCaptureAccess() }.getOrDefault(false)
    }

    fun applicationEventTarget(): Pointer? =
        runCatching { carbon?.GetApplicationEventTarget() }.getOrNull()

    fun modifierMask(modifiers: Set<HotkeyModifier>): Int =
        modifiers.fold(0) { mask, modifier ->
            mask or
                when (modifier) {
                    HotkeyModifier.Command -> COMMAND_MASK
                    HotkeyModifier.Control -> CONTROL_MASK
                    HotkeyModifier.Alt -> OPTION_MASK
                    HotkeyModifier.Shift -> SHIFT_MASK
                }
        }

    fun keyCode(key: String): Int? = KEY_CODES[key]

    /** JNA 直接反射读字段（只认 public），Kotlin 的 var 默认是私有 backing field，必须 @JvmField 摊平。 */
    @Structure.FieldOrder("signature", "id")
    class HotKeyId(signature: Int, id: Int) : Structure(), Structure.ByValue {
        @JvmField var signature: Int = signature
        @JvmField var id: Int = id
    }

    @Structure.FieldOrder("eventClass", "eventKind")
    class EventTypeSpec(eventClass: Int, eventKind: Int) : Structure() {
        @JvmField var eventClass: Int = eventClass
        @JvmField var eventKind: Int = eventKind
    }

    private val KEY_CODES: Map<String, Int> =
        mapOf(
            // 字母与数字（Carbon 虚拟键码，本来就不是 qwerty 顺序）
            "A" to 0x00, "S" to 0x01, "D" to 0x02, "F" to 0x03, "H" to 0x04,
            "G" to 0x05, "Z" to 0x06, "X" to 0x07, "C" to 0x08, "V" to 0x09,
            "B" to 0x0B, "Q" to 0x0C, "W" to 0x0D, "E" to 0x0E, "R" to 0x0F,
            "Y" to 0x10, "T" to 0x11, "O" to 0x1F, "U" to 0x20, "I" to 0x22,
            "P" to 0x23, "L" to 0x25, "J" to 0x26, "K" to 0x28, "N" to 0x2D,
            "M" to 0x2E,
            "1" to 0x12, "2" to 0x13, "3" to 0x14, "4" to 0x15, "6" to 0x16,
            "5" to 0x17, "9" to 0x19, "7" to 0x1A, "8" to 0x1C, "0" to 0x1D,
            "F1" to 0x7A, "F2" to 0x78, "F3" to 0x63, "F4" to 0x76, "F5" to 0x60,
            "F6" to 0x61, "F7" to 0x62, "F8" to 0x64, "F9" to 0x65, "F10" to 0x6D,
            "F11" to 0x67, "F12" to 0x6F,
            "Space" to 0x31, "Enter" to 0x24, "Tab" to 0x30, "Backspace" to 0x33,
            "Delete" to 0x75, "Home" to 0x73, "End" to 0x77, "PageUp" to 0x74,
            "PageDown" to 0x79, "ArrowUp" to 0x7E, "ArrowDown" to 0x7D,
            "ArrowLeft" to 0x7B, "ArrowRight" to 0x7C, "Minus" to 0x1B,
            "Equal" to 0x18, "BracketLeft" to 0x21, "BracketRight" to 0x1E,
            "Backslash" to 0x2A, "Semicolon" to 0x29, "Quote" to 0x27,
            "Comma" to 0x2B, "Period" to 0x2F, "Slash" to 0x2C, "Backquote" to 0x32,
        )
}
