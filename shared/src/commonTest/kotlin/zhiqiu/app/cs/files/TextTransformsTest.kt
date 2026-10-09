package zhiqiu.app.cs.files

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTransformsTest {

    private val multiline = "  b\na\n\na\n  c "
    private val unsorted = "banana\napple\nCherry\napple"

    @Test
    fun line_operations() {
        assertEquals("b\na\n\na\nc", applyTextOp(multiline, TextOp.Trim))
        assertEquals("  b\na\na\n  c ", applyTextOp(multiline, TextOp.StripBlankLines))
        assertEquals("b a a c", applyTextOp(multiline, TextOp.JoinLines))
        assertEquals("Cherry\napple\napple\nbanana", applyTextOp(unsorted, TextOp.SortLines))
        assertEquals("banana\napple\napple\nCherry", applyTextOp(unsorted, TextOp.SortLinesDesc))
        assertEquals("banana\napple\nCherry", applyTextOp(unsorted, TextOp.UniqueLines))
    }

    @Test
    fun case_operations() {
        assertEquals("ABC", applyTextOp("abc", TextOp.Upper))
        assertEquals("abc", applyTextOp("ABC", TextOp.Lower))
    }

    @Test
    fun url_codec_keeps_unreserved_and_round_trips() {
        val plain = "a-b._~Z0 /?&=ü"
        assertEquals("a-b._~Z0%20%2F%3F%26%3D%C3%BC", applyTextOp(plain, TextOp.UrlEncode))
        assertEquals(plain, applyTextOp(applyTextOp(plain, TextOp.UrlEncode), TextOp.UrlDecode))
        assertEquals("a b", applyTextOp("a+b", TextOp.UrlDecode))
        assertEquals("%zz", applyTextOp("%zz", TextOp.UrlDecode))
    }

    @Test
    fun base64_codec_round_trips() {
        val plain = "剪贴板 / clipboard"
        assertEquals("5Ymq6LS05p2/IC8gY2xpcGJvYXJk", applyTextOp(plain, TextOp.Base64Encode))
        assertEquals(plain, applyTextOp(applyTextOp(plain, TextOp.Base64Encode), TextOp.Base64Decode))
        assertEquals("!!", applyTextOp("!!", TextOp.Base64Decode))
    }
}
