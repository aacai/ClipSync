package zhiqiu.app.cs.ui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import zhiqiu.app.cs.core.platformOs

internal object WindowsClipboard {

    private const val CF_UNICODETEXT = 13
    private const val GMEM_MOVEABLE = 0x0002
    private const val ATTEMPTS = 10
    private const val RETRY_MS = 20L

    private val user32: User32? by lazy { load(User32::class.java) }
    private val kernel32: Kernel32? by lazy { load(Kernel32::class.java) }

    val available: Boolean
        get() = Native.POINTER_SIZE == 8 && user32 != null && kernel32 != null

    fun writeText(text: String): Boolean {
        val user = user32 ?: return false
        val kernel = kernel32 ?: return false
        val bytes = (text + "\u0000").toByteArray(Charsets.UTF_16LE)
        val block = kernel.GlobalAlloc(GMEM_MOVEABLE, bytes.size.toLong()) ?: return false
        val locked = kernel.GlobalLock(block)
        if (locked == null) {
            kernel.GlobalFree(block)
            return false
        }
        locked.write(0, bytes, 0, bytes.size)
        kernel.GlobalUnlock(block)
        if (!open(user)) {
            kernel.GlobalFree(block)
            return false
        }
        val placed =
                try {
                    user.EmptyClipboard()
                    user.SetClipboardData(CF_UNICODETEXT, block) != null
                } finally {
                    user.CloseClipboard()
                }
        if (!placed) kernel.GlobalFree(block)
        return placed
    }

    fun readText(): String? {
        val user = user32 ?: return null
        val kernel = kernel32 ?: return null
        if (!open(user)) return null
        try {
            if (user.IsClipboardFormatAvailable(CF_UNICODETEXT) == 0) return null
            val handle = user.GetClipboardData(CF_UNICODETEXT) ?: return null
            val locked = kernel.GlobalLock(handle) ?: return null
            return try {
                locked.getWideString(0)
            } finally {
                kernel.GlobalUnlock(handle)
            }
        } finally {
            user.CloseClipboard()
        }
    }

    private fun open(user: User32): Boolean {
        repeat(ATTEMPTS) {
            if (user.OpenClipboard(null) != 0) return true
            runCatching { Thread.sleep(RETRY_MS) }
        }
        return false
    }

    private fun <T : Library> load(type: Class<T>): T? {
        if (platformOs() != "Windows") return null
        return runCatching { Native.load(type.simpleName, type) }.getOrNull()
    }
}

private interface User32 : Library {
    fun OpenClipboard(owner: Pointer?): Int

    fun EmptyClipboard(): Int

    fun CloseClipboard(): Int

    fun SetClipboardData(format: Int, data: Pointer?): Pointer?

    fun GetClipboardData(format: Int): Pointer?

    fun IsClipboardFormatAvailable(format: Int): Int
}

private interface Kernel32 : Library {
    fun GlobalAlloc(flags: Int, bytes: Long): Pointer?

    fun GlobalLock(handle: Pointer?): Pointer?

    fun GlobalUnlock(handle: Pointer?): Int

    fun GlobalFree(handle: Pointer?): Pointer?
}
