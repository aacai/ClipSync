package zhiqiu.app.cs.ui

import com.sun.jna.Callback
import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
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
     * AppKit 只认主线程，而这里是在 AWT 的事件线程上被调的——直接发消息过去会被窗口管理器
     * 的队列断言当场打死（实测 SIGTRAP），所以整段都甩到主队列上去跑。
     */
    fun raiseAboveMenuBar() {
        val dispatcher = dispatch ?: return
        val queue = mainQueue ?: return
        runCatching {
            val work = Work { raiseNow() }
            pending = work
            dispatcher.dispatch_async_f(queue, null, work)
        }
    }

    /** 这一段整条都在 AppKit 主队列上跑，出了岔子也只当没抬起来。 */
    private fun raiseNow() {
        runCatching {
            val lib = objc ?: return@runCatching
            val sel = selectors ?: return@runCatching
            val cf = foundation ?: return@runCatching
            val title = wanted ?: return@runCatching
            val app = lib.msgObject(lib.objc_getClass("NSApplication"), sel.sharedApplication) ?: return@runCatching
            val windows = lib.msgObject(app, sel.windows) ?: return@runCatching
            val count = lib.msgLong(windows, sel.count)
            for (index in 0 until count) {
                val window = lib.msgIndexed(windows, sel.objectAtIndex, index) ?: continue
                val name = lib.msgObject(window, sel.title) ?: continue
                if (cf.CFEqual(name, title)) {
                    lib.msgSetLevel(window, sel.setLevel, SCREEN_SAVER_LEVEL)
                    return@runCatching
                }
            }
        }
    }

    /** JNA 只按 Java 方法名找符号，而 Objective-C 的消息全走 `objc_msgSend` 一个入口，所以按名字映射一下。 */
    private val OPTIONS: Map<String, Any> = mapOf(
        Library.OPTION_FUNCTION_MAPPER to FunctionMapper { _, method ->
            if (method.name.startsWith("msg")) "objc_msgSend" else method.name
        },
    )

    /** `dispatch_function_t` 就是 `void (void *)`：JNA 会把 Callback 当成真函数指针递过去。 */
    private fun interface Work : Callback {
        fun run(context: Pointer?)
    }

    private interface Dispatch : Library {
        /** `dispatch_async_f(queue, context, work)`：上下文用不上，留个 `void *` 的位置。 */
        fun dispatch_async_f(queue: Pointer, context: Pointer?, work: Work)
    }

    private const val DISPATCH = "/usr/lib/system/libdispatch.dylib"

    private val dispatch: Dispatch? by lazy {
        if (!Mac.isMacOs) null else runCatching {
            Native.load(DISPATCH, Dispatch::class.java)
        }.getOrNull()
    }

    /** `dispatch_get_main_queue()` 在新系统上是个宏，函数表里查不到，主队列得按全局变量 `_dispatch_main_q` 取地址。 */
    private val mainQueue: Pointer? by lazy {
        if (!Mac.isMacOs) null else runCatching {
            NativeLibrary.getInstance(DISPATCH).getGlobalVariableAddress("_dispatch_main_q")
        }.getOrNull()
    }

    /** 闭包还没跑就被回收的话，主队列拿到的就是个悬空指针。 */
    private var pending: Work? = null

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
