package zhiqiu.app.cs.ui

import androidx.compose.ui.geometry.Rect
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage

/** 冻结一帧屏幕：整屏像素 + 那一刻屏上窗口的位置。
 *
 * 像素走 AWT 的 [Robot]，窗口列表只有 CoreGraphics 给得出来（微信那种「点一下就选中那个窗口」靠它）。
 * 抓之前先把自家窗口收起来，否则冻住的画面里会有一块自己的窗口。
 */
internal class Freeze(val image: BufferedImage, val screen: FrozenScreen)

/** 整屏（含菜单栏）在 AWT 里是主显示器的设备边界，不是去掉菜单栏的可视区。 */
internal fun displayBounds(): Rectangle =
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
        .bounds

internal object ScreenFreeze {

    /** 自家窗口收起来到合成器真的把它撤下要一点时间，抢太快冻住的还是上一帧。 */
    private const val HIDE_SETTLE_MS = 140L

    /**
     * 抓一帧，会阻塞 [HIDE_SETTLE_MS]，所以只能在 IO 线程上调用。
     *
     * 没给屏幕录制权限时 macOS 不报错、只把壁纸给你，所以先探一次再决定要不要按下去。
     */
    fun grab(): Freeze {
        if (Mac.isMacOs && !Mac.requestScreenCapture()) throw ScreenCapturePermissionMissing()
        val bounds = displayBounds()
        val shown = Window.getWindows().filter { it.isDisplayable && it.isVisible }
        onEdt { shown.forEach { window -> window.isVisible = false } }
        try {
            Thread.sleep(HIDE_SETTLE_MS)
            val image = Robot().createScreenCapture(bounds)
            val scale = if (bounds.width == 0) 1f else image.width.toFloat() / bounds.width
            return Freeze(image, FrozenScreen(image.width, image.height, scale, MacWindowList.onScreen(bounds)))
        } finally {
            onEdt { shown.forEach { window -> window.isVisible = true } }
        }
    }

    /** AWT 的窗口显隐归事件线程管，别的线程只能排队等它做完。 */
    private fun onEdt(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeAndWait(action)
    }
}

private const val CF_UTF8 = 0x08000100L // kCFStringEncodingUTF8

/**
 * `CGWindowListCopyWindowInfo`：屏上窗口的边界，点为单位、左上角为原点，和 AWT 同一套坐标。
 *
 * 只留普通层（layer 0）的窗口：菜单栏、程序坞、桌面图标那些对框选没意义。
 */
private object MacWindowList {

    private const val ON_SCREEN_ONLY = 1 shl 1
    private const val EXCLUDE_DESKTOP_ELEMENTS = 1 shl 4
    private const val NULL_WINDOW_ID = 0L
    private const val NUMBER_DOUBLE = 13 // kCFNumberDoubleType

    private val coreGraphics: CoreGraphics? by lazy {
        runCatching { Native.load("CoreGraphics", CoreGraphics::class.java) }.getOrNull()
    }

    private val coreFoundation: CoreFoundation? by lazy {
        runCatching { Native.load("CoreFoundation", CoreFoundation::class.java) }.getOrNull()
    }

    /** CFString 键只造一次：它们是进程级常量，每次调用现造就是每次漏。 */
    private val names: CfKeys? by lazy {
        val cf = coreFoundation ?: return@lazy null
        runCatching { CfKeys(cf) }.getOrNull()
    }

    /** 屏上窗口，[bounds] 的本地坐标、从前到后；拿不到 CoreGraphics 的平台就是空表，框选照样能用。 */
    fun onScreen(bounds: Rectangle): List<Rect> {
        val cg = coreGraphics ?: return emptyList()
        val cf = coreFoundation ?: return emptyList()
        val keys = names ?: return emptyList()
        val ourPid = runCatching { ProcessHandle.current().pid() }.getOrNull()
        val list = runCatching {
            cg.CGWindowListCopyWindowInfo(ON_SCREEN_ONLY or EXCLUDE_DESKTOP_ELEMENTS, NULL_WINDOW_ID)
        }.getOrNull() ?: return emptyList()
        return runCatching { read(list, cf, keys, ourPid, bounds) }.getOrDefault(emptyList())
    }

    private fun read(list: Pointer, cf: CoreFoundation, keys: CfKeys, ourPid: Long?, bounds: Rectangle): List<Rect> {
        val count = cf.CFArrayGetCount(list).toInt()
        val out = ArrayList<Rect>(count)
        for (index in 0 until count) {
            val info = cf.CFArrayGetValueAtIndex(list, index.toLong()) ?: continue
            if (ourPid != null && number(cf, cf.CFDictionaryGetValue(info, keys.pid))?.toLong() == ourPid) continue
            if (number(cf, cf.CFDictionaryGetValue(info, keys.layer)) != 0.0) continue
            val frame = cf.CFDictionaryGetValue(info, keys.bounds) ?: continue
            val x = number(cf, cf.CFDictionaryGetValue(frame, keys.x)) ?: continue
            val y = number(cf, cf.CFDictionaryGetValue(frame, keys.y)) ?: continue
            val width = number(cf, cf.CFDictionaryGetValue(frame, keys.width)) ?: continue
            val height = number(cf, cf.CFDictionaryGetValue(frame, keys.height)) ?: continue
            if (width <= 0.0 || height <= 0.0) continue
            val left = maxOf((x - bounds.x).toFloat(), 0f)
            val top = maxOf((y - bounds.y).toFloat(), 0f)
            val right = minOf((x + width - bounds.x).toFloat(), bounds.width.toFloat())
            val bottom = minOf((y + height - bounds.y).toFloat(), bounds.height.toFloat())
            if (right > left && bottom > top) out += Rect(left, top, right, bottom)
        }
        return out
    }

    private fun number(cf: CoreFoundation, value: Pointer?): Double? {
        val number = value ?: return null
        val out = DoubleArray(1)
        return if (cf.CFNumberGetValue(number, NUMBER_DOUBLE, out)) out[0] else null
    }

    private interface CoreGraphics : Library {
        fun CGWindowListCopyWindowInfo(option: Int, relativeToWindowID: Long): Pointer?
    }

    private interface CoreFoundation : Library {
        fun CFStringCreateWithCString(alloc: Pointer?, cString: String, encoding: Long): Pointer?
        fun CFArrayGetCount(array: Pointer): Long
        fun CFArrayGetValueAtIndex(array: Pointer, index: Long): Pointer?
        fun CFDictionaryGetValue(dictionary: Pointer, key: Pointer): Pointer?
        fun CFNumberGetValue(number: Pointer, numberType: Int, valuePtr: DoubleArray): Boolean
    }

    private class CfKeys(cf: CoreFoundation) {
        val layer = name(cf, "kCGWindowLayer")
        val pid = name(cf, "kCGWindowOwnerPID")
        val bounds = name(cf, "kCGWindowBounds")
        val x = name(cf, "X")
        val y = name(cf, "Y")
        val width = name(cf, "Width")
        val height = name(cf, "Height")

        private fun name(cf: CoreFoundation, value: String): Pointer =
            cf.CFStringCreateWithCString(null, value, CF_UTF8)
                ?: error("CFStringCreateWithCString($value) 给了空指针")
    }
}
