package zhiqiu.app.cs.files

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** CopyQ 的 Commands 里对纯文本最有用的那一批，就地作用在条目内容上。 */
enum class TextOp {
    Trim,
    StripBlankLines,
    SortLines,
    SortLinesDesc,
    UniqueLines,
    Upper,
    Lower,
    JoinLines,
    UrlEncode,
    UrlDecode,
    Base64Encode,
    Base64Decode,
}

@OptIn(ExperimentalEncodingApi::class)
fun applyTextOp(source: String, op: TextOp): String =
    when (op) {
        TextOp.Trim -> source.lineSequence().joinToString("\n") { it.trim() }.trim()
        TextOp.StripBlankLines -> source.lines().filter { it.isNotBlank() }.joinToString("\n")
        TextOp.SortLines -> source.lines().sorted().joinToString("\n")
        TextOp.SortLinesDesc -> source.lines().sortedDescending().joinToString("\n")
        TextOp.UniqueLines -> source.lines().distinct().joinToString("\n")
        TextOp.Upper -> source.uppercase()
        TextOp.Lower -> source.lowercase()
        TextOp.JoinLines -> source.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        TextOp.UrlEncode -> urlEncode(source)
        TextOp.UrlDecode -> urlDecode(source)
        TextOp.Base64Encode -> Base64.encode(source.encodeToByteArray())
        TextOp.Base64Decode -> runCatching { Base64.decode(source.trim()).decodeToString() }.getOrElse { source }
    }

private const val URL_UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private const val HEX = "0123456789ABCDEF"

private fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c.toChar() in URL_UNRESERVED) append(c.toChar()) else {
            append('%')
            append(HEX[c shr 4])
            append(HEX[c and 0xF])
        }
    }
}

private fun urlDecode(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        val hi = if (c == '%' && i + 2 < value.length) value[i + 1].digitToIntOrNull(16) else null
        val lo = if (hi != null) value[i + 2].digitToIntOrNull(16) else null
        if (hi != null && lo != null) {
            bytes += ((hi shl 4) or lo).toByte()
            i += 3
        } else if (c == '+') {
            bytes += ' '.code.toByte()
            i++
        } else {
            bytes += c.code.toByte()
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}
