package zhiqiu.app.cs.ui

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AppKit 与 Carbon 只认「跑事件循环的那条线程」。
 *
 * AWT 的事件线程是另起的一条：在它上面改窗口层级会被队列断言当场打死，注册热键倒是返回成功，
 * 只是事件永远派不过来。所以抬层级、装处理器、注册注销热键，全甩到主队列上去。
 */
internal object MacMainQueue {

    private const val DISPATCH = "/usr/lib/system/libdispatch.dylib"

    /** 等主队列跑完的上限：等不到就当这条路没有，宁可退回原样也别把界面线程挂死。 */
    private const val WAIT_MS = 2_000L

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

    /** 闭包还没跑就被回收的话，主队列拿到的就是个悬空指针；跑完再按先进先出摘掉。 */
    private val queued = ConcurrentLinkedQueue<Work>()

    /** 甩出去就不管。主队列没接通时当场跑一遍，别让调用方白等。 */
    fun post(action: () -> Unit) {
        val dispatcher = dispatch
        val queue = mainQueue
        if (dispatcher == null || queue == null) {
            runCatching(action)
            return
        }
        send(action, dispatcher, queue)
    }

    /** 要返回值的那一类：投到主队列、原地等它跑完。等不到就返回 null，调用方按「这个平台没这能力」办。 */
    fun <T : Any> ask(action: () -> T): T? {
        val dispatcher = dispatch ?: return null
        val queue = mainQueue ?: return null
        val latch = CountDownLatch(1)
        var answer: T? = null
        if (!send({ answer = action(); latch.countDown() }, dispatcher, queue)) return null
        return if (latch.awaitQuietly()) answer else null
    }

    private fun send(
        action: () -> Unit,
        dispatcher: Dispatch,
        queue: Pointer,
    ): Boolean {
        val work = Work {
            runCatching(action)
            queued.poll()
        }
        queued.add(work)
        val sent = runCatching { dispatcher.dispatch_async_f(queue, null, work) }.isSuccess
        if (!sent) queued.remove(work)
        return sent
    }

    private fun CountDownLatch.awaitQuietly(): Boolean =
        runCatching { await(WAIT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)

    /** `dispatch_function_t` 就是 `void (void *)`：JNA 会把 Callback 当成真函数指针递过去。 */
    private fun interface Work : Callback {
        fun run(context: Pointer?)
    }

    private interface Dispatch : Library {
        /** `dispatch_async_f(queue, context, work)`：上下文用不上，留个 `void *` 的位置。 */
        fun dispatch_async_f(queue: Pointer, context: Pointer?, work: Work)
    }
}
