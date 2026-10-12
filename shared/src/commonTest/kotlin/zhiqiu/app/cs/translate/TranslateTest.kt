package zhiqiu.app.cs.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslateTest {

    @Test
    fun `chinese goes to english`() {
        assertEquals(TranslateTarget.English, detectTarget("剪贴板同步"))
    }

    @Test
    fun `english goes to chinese`() {
        assertEquals(TranslateTarget.Chinese, detectTarget("clipboard sync"))
    }

    @Test
    fun `mixed text with hanzi goes to english`() {
        assertEquals(TranslateTarget.English, detectTarget("把 clipboard 同步 一下"))
    }

    @Test
    fun `symbols alone are not chinese`() {
        assertEquals(TranslateTarget.Chinese, detectTarget("127.0.0.1:1883 ✂"))
    }

    @Test
    fun `kana counts as needing english`() {
        assertEquals(TranslateTarget.English, detectTarget("コピーボード"))
    }

    @Test
    fun `restores punctuation the api escaped`() {
        assertEquals("don't \"stop\" & go", unescapeHtmlEntities("don&#39;t &quot;stop&quot; &amp; go"))
    }

    @Test
    fun `leaves plain text alone`() {
        assertEquals("a & b 保持", unescapeHtmlEntities("a & b 保持"))
    }

    @Test
    fun `reads a normal reply`() {
        val raw =
                """{"responseData":{"translatedText":"Clipboard sync","match":0.85},"responseStatus":200,"matchingWords":2}"""
        assertEquals("Clipboard sync", parseReply(raw))
    }

    @Test
    fun `unescapes inside the reply`() {
        assertEquals(
                "don't stop",
                parseReply("""{"responseData":{"translatedText":"don&#39;t stop"},"responseStatus":200}"""),
        )
    }

    @Test
    fun `error status comes back as a string`() {
        val error =
                assertFailsWith<TranslationException> {
                    parseReply(
                            """{"responseData":{"translatedText":"QUERY LENGTH LIMIT EXCEEDED"},"responseStatus":"403","responseDetails":"QUERY LENGTH LIMIT EXCEEDED","matches":""}"""
                    )
                }
        assertEquals(TranslateFailure.Rejected, error.failure)
        assertEquals("QUERY LENGTH LIMIT EXCEEDED", error.detail)
    }

    @Test
    fun `a quota warning is not a translation`() {
        val error =
                assertFailsWith<TranslationException> {
                    parseReply(
                            """{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY"},"responseStatus":200}"""
                    )
                }
        assertEquals(TranslateFailure.Rejected, error.failure)
    }

    @Test
    fun `throws when the payload has no translation`() {
        val error = assertFailsWith<TranslationException> { parseReply("""{"responseStatus":200}""") }
        assertEquals(TranslateFailure.Empty, error.failure)
    }

    @Test
    fun `throws on a non json reply`() {
        assertEquals(TranslateFailure.Malformed, assertFailsWith<TranslationException> { parseReply("<html>502 Bad Gateway</html>") }.failure)
    }

    @Test
    fun `a missing status counts as a refusal`() {
        assertEquals(
                TranslateFailure.Rejected,
                assertFailsWith<TranslationException> { parseReply("""{"responseData":{"translatedText":"ok"}}""") }.failure,
        )
    }
}
