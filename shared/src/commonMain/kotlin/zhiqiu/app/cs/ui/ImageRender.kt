package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
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

/** 全屏截图解出来是几十 MB，留着小尺寸的成品才敢在列表里来回滚。 */
private const val CACHE_ENTRIES = 48
private val decoded = LinkedHashMap<String, ImageBitmap>()

@Composable
internal fun rememberImageBitmap(
        localPath: String?,
        maxSide: Int,
        load: suspend () -> ByteArray?,
): ImageBitmap? {
    val bitmap by
            produceState<ImageBitmap?>(initialValue = null, localPath, maxSide) {
                val path = localPath
                if (path == null) return@produceState
                val cacheKey = "$path@$maxSide"
                decoded[cacheKey]?.let {
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
                if (image != null) {
                    decoded[cacheKey] = image
                    while (decoded.size > CACHE_ENTRIES) decoded.remove(decoded.keys.first())
                }
                value = image
            }
    return if (localPath == null) null else bitmap
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
