package zhiqiu.app.cs.ui

import kotlin.time.TimeSource

private const val ECHO_WINDOW_MS = 10_000L
private const val MAX_ECHO_HISTORY = 32
private val ECHO_ORIGIN = TimeSource.Monotonic.markNow()

/**
 * 这台设备刚发出去过的文本。屋里另一台机器、或同机另一个 ClipSync，会把它当新拷贝推回来，
 * 那份回声再写进剪贴板盖掉的就是用户真正刚复制的一份，所以发出去的内容要记一小会儿。
 *
 * 记窗口不记最后一条：只记一条的话，第二次复制就把第一条忘了，回声照样能盖。
 */
internal class EchoLog(
    private val windowMs: Long = ECHO_WINDOW_MS,
    private val capacity: Int = MAX_ECHO_HISTORY,
    private val clock: () -> Long = { ECHO_ORIGIN.elapsedNow().inWholeMilliseconds },
) {
    private val seen = ArrayDeque<Pair<String, Long>>()

    fun remember(text: String) {
        val at = clock()
        seen.removeAll { at - it.second >= windowMs }
        seen.addLast(text to at)
        while (seen.size > capacity) seen.removeFirst()
    }

    fun isEcho(text: String): Boolean = seen.any { it.first == text && clock() - it.second < windowMs }
}
