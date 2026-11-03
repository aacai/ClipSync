package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val THUMB_MAX_SIDE = 256
internal const val ZOOM_MAX_SIDE = 2048

private const val OFFSCREEN_RETAIN = 8
private const val CACHE_BYTES = 32L * 1024 * 1024

/**
 * 按「谁还在用」回收的位图缓存：引用掉零才算离屏，离屏的只留最近 offscreenRetain 份、
 * 且总量不超过 maxBytes 当回收池，免得来回滚反复重解。全部读写都发生在 UI 线程，所以不需要加锁。
 */
internal class RecycleCache<V : Any>(
    private val offscreenRetain: Int,
    private val maxBytes: Long,
    private val weightOf: (V) -> Long,
) {

    private val entries = LinkedHashMap<String, V>()
    private val held = HashMap<String, Int>()
    private val used = HashMap<String, Long>()
    private var bytes = 0L
    private var tick = 0L

    val cachedBytes: Long get() = bytes

    fun acquire(key: String) {
        held[key] = (held[key] ?: 0) + 1
        touch(key)
    }

    fun release(key: String) {
        val live = (held[key] ?: 1) - 1
        if (live > 0) held[key] = live
        else {
            held.remove(key)
            if (!entries.containsKey(key)) used.remove(key)
        }
        trim()
    }

    fun peek(key: String): V? = entries[key]?.also { touch(key) }

    fun put(key: String, value: V) {
        entries.remove(key)?.let { bytes -= weightOf(it) }
        entries[key] = value
        bytes += weightOf(value)
        touch(key)
        trim()
    }

    private fun touch(key: String) {
        tick += 1
        used[key] = tick
    }

    private fun trim() {
        val free = entries.keys.filter { (held[it] ?: 0) == 0 }.sortedBy { used[it] ?: 0L }
        var index = 0
        while (free.size - index > offscreenRetain || bytes > maxBytes) {
            val victim = free.getOrNull(index++) ?: return
            bytes -= weightOf(entries.remove(victim)!!)
            used.remove(victim)
        }
    }
}

private val thumbCache: RecycleCache<ImageBitmap> =
    RecycleCache(OFFSCREEN_RETAIN, CACHE_BYTES) { it.width.toLong() * it.height * 4 }

@Composable
internal fun rememberImageBitmap(
        localPath: String?,
        maxSide: Int,
        load: suspend () -> ByteArray?,
): ImageBitmap? {
    val key = localPath?.let { "$it@$maxSide" }
    DisposableEffect(key) {
        if (key != null) thumbCache.acquire(key)
        onDispose { if (key != null) thumbCache.release(key) }
    }
    val bitmap by
            produceState<ImageBitmap?>(initialValue = null, key) {
                if (key == null) return@produceState
                thumbCache.peek(key)?.let {
                    value = it
                    return@produceState
                }
                val image =
                        runCatching {
                                    withContext(Dispatchers.Default) {
                                        load()?.let { it.decodeToImageBitmap().shrinkTo(maxSide) }
                                    }
                                }
                                .getOrNull()
                if (image != null) thumbCache.put(key, image)
                value = image
            }
    return if (key == null) null else bitmap
}

private fun ImageBitmap.shrinkTo(maxSide: Int): ImageBitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val ratio = maxSide.toFloat() / longest
    val w = (width * ratio).roundToInt().coerceAtLeast(1)
    val h = (height * ratio).roundToInt().coerceAtLeast(1)
    val target = ImageBitmap(w, h)
    val paint = Paint().apply { filterQuality = FilterQuality.High }
    Canvas(target).drawImageRect(this, dstSize = IntSize(w, h), paint = paint)
    return target
}
