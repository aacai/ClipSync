package zhiqiu.app.cs.core

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File

private val osName = System.getProperty("os.name").orEmpty().lowercase()
private val isMac = osName.contains("mac") || osName.contains("darwin")
private val isLinux = osName.contains("nix") || osName.contains("nux")
private val isWindows = osName.contains("win")

/**
 * 状态卡片上那个数：本进程当前占的物理内存，跟 Rust 端同一口径（常驻页 / 工作集），不是托管堆。
 *
 * JVM 自己没法问到一个跨版本可用的 RSS：`getPhysicalMemoryUsage` 是 JDK 14 才有，而这一版
 * 编译目标是 11，反射到各家的 MXBean 上也找不到那个方法。所以直接问内核。
 */
internal actual fun platformResidentMemory(): Long? =
        when {
            isMac -> macResident()
            isLinux -> linuxResident()
            isWindows -> windowsResident()
            else -> null
        }

private fun macResident(): Long? {
    val kernel = MachKernel.bridge ?: return null
    val task = MachKernel.taskSelf ?: return null
    val info = Memory(MACH_BASIC_INFO_SIZE)
    val count = intArrayOf(MACH_BASIC_INFO_COUNT)
    if (kernel.task_info(task, MACH_TASK_BASIC_INFO, info, count) != KERN_SUCCESS) return null
    return info.getLong(RESIDENT_SIZE_OFFSET).takeIf { it > 0 }
}

private object MachKernel {

    /** 任务端口是 libSystem 里的一个全局变量，不是函数——`task_get_self` 在 arm64 上并不存在。 */
    val taskSelf: Int? by lazy {
        runCatching {
                    NativeLibrary.getInstance(LIBRARY).getGlobalVariableAddress("mach_task_self_").getInt(0)
                }
                .getOrNull()
    }

    val bridge: MacSystem? by lazy {
        if (!isMac) null
        else runCatching { Native.load(LIBRARY, MacSystem::class.java) }.getOrNull()
    }

    private const val LIBRARY = "System"
}

private interface MacSystem : Library {
    fun task_info(task: Int, flavor: Int, info: Pointer, count: IntArray): Int
}

private const val MACH_TASK_BASIC_INFO = 20
private const val MACH_BASIC_INFO_COUNT = 12
private const val MACH_BASIC_INFO_SIZE = 48L
private const val RESIDENT_SIZE_OFFSET = 8L
private const val KERN_SUCCESS = 0

/** 内核算好的 `VmRSS: 88420 kB`，直接拿，不用自己乘 page size——ARM 板上可能是 16K 页。 */
private fun linuxResident(): Long? {
    val value =
            runCatching { File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") } }
                    .getOrNull()
                    ?.substringAfter(':')
                    ?.trim()
                    ?.removeSuffix("kB")
                    ?.trim()
    return value?.toLongOrNull()?.takeIf { it > 0 }?.times(1024)
}

private fun windowsResident(): Long? {
    val kernel = WinKernel.bridge ?: return null
    // 32 位上 SIZE_T 只有 4 字节，整套偏移都要重算；桌面包只出 x64，其它位数宁可不显示。
    if (Native.POINTER_SIZE != 8) return null
    val counters = Memory(WIN_COUNTERS_SIZE.toLong())
    counters.setInt(0, WIN_COUNTERS_SIZE)
    val ok = kernel.K32GetProcessMemoryInfo(kernel.GetCurrentProcess(), counters, WIN_COUNTERS_SIZE)
    if (!ok) return null
    return counters.getLong(WIN_WORKING_SET_OFFSET).takeIf { it > 0 }
}

private object WinKernel {
    val bridge: Kernel32? by lazy {
        if (!isWindows) null
        else runCatching { Native.load("Kernel32", Kernel32::class.java) }.getOrNull()
    }
}

private interface Kernel32 : Library {
    fun GetCurrentProcess(): Pointer

    fun K32GetProcessMemoryInfo(process: Pointer, counters: Pointer, cb: Int): Boolean
}

private const val WIN_COUNTERS_SIZE = 72
private const val WIN_WORKING_SET_OFFSET = 16L
