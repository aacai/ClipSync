package zhiqiu.app.cs.ui

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * 把遮罩窗口抬到菜单栏之上。
 *
 * macOS 的置顶只到浮动级，压不过菜单栏和程序坞：顶上那一条归系统、鼠标点不到，冻住的画面里那一行也就框不住。
 * 所以直接改 NSWindow 的 level，用屏保那一级（1000）。自家窗口里只有遮罩层叫这个标题，靠标题把它认出来。
 */
internal object MacWindowLevel {

    private const val SCREEN_SAVER_LEVEL = 1000L
    private const val UTF8 = 0x08000100L // kCFStringEncodingUTF8

    private val objc: ObjectiveC? by lazy {
        if (!Mac.isMacOs) null else runCatching {
            Native.load("/usr/lib/libobjc.dylib", ObjectiveC::class.java, OPTIONS)
        }.getOrNull()
    }

    private val foundation: Foundation? by lazy {
        if (!Mac.isMacOs) null else runCatching {
            Native.load("CoreFoundation", Foundation::class.java)
        }.getOrNull()
    }

    /** 选择器和比较用的字符串都是进程级常量：每次现造等于每次多跑一趟符号表，还顺手漏一个。 */
    private val selectors: Selectors? by lazy {
        val lib = objc ?: return@lazy null
        runCatching { Selectors(lib) }.getOrNull()
    }

    private val wanted: Pointer? by lazy {
        runCatching { foundation?.CFStringCreateWithCString(null, OVERLAY_TITLE, UTF8) }.getOrNull()
    }

    /**
     * 抬不动就当这平台没有菜单栏：窗口照常显示，只是顶上那一条归系统。
     *
     * 这里是在 AWT 的事件线程上直接发消息给 AppKit，严格说 AppKit 只认主线程；改一个窗口层级是它少数
     * 从别的线程调用也稳的操作，Java 自己处理置顶窗口也是这么走同一条通道的。
     */
    fun raiseAboveMenuBar() {
        val lib = objc ?: return
        val sel = selectors ?: return
        val cf = foundation ?: return
        val title = wanted ?: return
        val app = lib.msgObject(lib.objc_getClass("NSApplication"), sel.sharedApplication) ?: return
        val windows = lib.msgObject(app, sel.windows) ?: return
        val count = lib.msgLong(windows, sel.count)
        for (index in 0 until count) {
            val window = lib.msgIndexed(windows, sel.objectAtIndex, index) ?: continue
            val name = lib.msgObject(window, sel.title) ?: continue
            if (cf.CFEqual(name, title)) {
                lib.msgSetLevel(window, sel.setLevel, SCREEN_SAVER_LEVEL)
                return
            }
        }
    }

    /** JNA 只按 Java 方法名找符号，而 Objective-C 的消息全走 `objc_msgSend` 一个入口，所以按名字映射一下。 */
    private val OPTIONS: Map<String, Any> = mapOf(
        Library.OPTION_FUNCTION_MAPPER to FunctionMapper { _, method ->
            if (method.name.startsWith("msg")) "objc_msgSend" else method.name
        },
    )

    private interface ObjectiveC : Library {
        fun objc_getClass(className: String): Pointer?
        fun sel_registerName(selectorName: String): Pointer?

        /** 无参消息：`sharedApplication`、`windows`、`title`。 */
        fun msgObject(receiver: Pointer?, selector: Pointer?): Pointer?

        /** 返回整数的无参消息：`count`。 */
        fun msgLong(receiver: Pointer?, selector: Pointer?): Long

        /** 带一个整数参数的消息：`objectAtIndex:`。 */
        fun msgIndexed(receiver: Pointer?, selector: Pointer?, index: Long): Pointer?

        /** 带一个整数参数、不返回东西的消息：`setLevel:`。 */
        fun msgSetLevel(receiver: Pointer?, selector: Pointer?, level: Long): Long
    }

    private interface Foundation : Library {
        fun CFStringCreateWithCString(alloc: Pointer?, cString: String, encoding: Long): Pointer?
        fun CFEqual(left: Pointer?, right: Pointer?): Boolean
    }

    private class Selectors(lib: ObjectiveC) {
        val sharedApplication = sel(lib, "sharedApplication")
        val windows = sel(lib, "windows")
        val count = sel(lib, "count")
        val objectAtIndex = sel(lib, "objectAtIndex:")
        val title = sel(lib, "title")
        val setLevel = sel(lib, "setLevel:")

        private fun sel(lib: ObjectiveC, name: String): Pointer =
            lib.sel_registerName(name) ?: error("sel_registerName($name) 给了空指针")
    }
}
