package zhiqiu.app.cs.translate

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import zhiqiu.app.cs.files.createSharedHttpClient

enum class TranslateFailure { Request, Malformed, Empty, Rejected }

class TranslationException(
        val failure: TranslateFailure,
        val detail: String? = null,
        cause: Throwable? = null,
) : Exception(failure.name + (detail?.let { ": $it" } ?: ""), cause)

/** 中英互转只有两个方向，方向本身就决定源语言，不需要再问一次接口。 */
enum class TranslateTarget(val source: String, val target: String) {
    English("zh-CN", "en"),
    Chinese("en", "zh-CN"),
}

/** 免注册接口的硬上限，超过就服务端直接拒绝，先自己拦下来。 */
const val MAX_TRANSLATE_CHARS = 500

fun detectTarget(text: String): TranslateTarget =
    if (text.any { it in '一'..'\u9fff' || it in '぀'..'ヿ' }) {
        TranslateTarget.English
    } else {
        TranslateTarget.Chinese
    }

/** MyMemory：免注册、免 key、带 CORS，国内直连可用（Google 的翻译接口在这台机器上直连不通）。 */
class TranslateClient(
    private val http: HttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    suspend fun translate(text: String, target: TranslateTarget): String {
        val raw =
                runCatching {
                        http.get(endpoint) {
                            url {
                                parameters.append("q", text)
                                parameters.append("langpair", "${target.source}|${target.target}")
                            }
                        }
                        .bodyAsText()
                }
                .getOrElse { throw TranslationException(TranslateFailure.Request, it.message, it) }
        return parseReply(raw)
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.mymemory.translated.net/get"
    }
}

internal fun parseReply(raw: String): String {
    val body =
            runCatching { replyJson.decodeFromString<MyMemoryReply>(raw) }
                    .getOrElse { throw TranslationException(TranslateFailure.Malformed, it.message, it) }
    if (body.status != 200) {
        val detail = body.responseDetails?.takeIf { it.isNotBlank() } ?: body.responseData?.translatedText
        throw TranslationException(TranslateFailure.Rejected, detail ?: "HTTP ${body.status}")
    }
    val translated = unescapeHtmlEntities(body.responseData?.translatedText.orEmpty())
    // 额度用完时接口照样回 200，把警告文案当成译文存进历史更奇怪。
    if (translated.startsWith("MYMEMORY WARNING")) throw TranslationException(TranslateFailure.Rejected, translated)
    if (translated.isBlank()) throw TranslationException(TranslateFailure.Empty)
    return translated
}

private val replyJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 出错时 responseStatus 是 "403" 这样的字符串，成功时是 200 这样的数字。 */
@Serializable
private class MyMemoryReply(
        @SerialName("responseData") val responseData: Data? = null,
        @SerialName("responseStatus") val responseStatus: JsonElement? = null,
        @SerialName("responseDetails") val responseDetails: String? = null,
) {
    @Serializable
    class Data(@SerialName("translatedText") val translatedText: String? = null)

    val status: Int
        get() =
                (responseStatus as? JsonPrimitive)
                        ?.content
                        ?.trim()
                        ?.takeWhile { it.isDigit() }
                        ?.toIntOrNull()
                        ?: 0
}

/** 接口把标点回成 HTML 实体，不还原的话英文里会满是 &#39;。 */
internal fun unescapeHtmlEntities(raw: String): String {
    if (!raw.contains('&')) return raw
    return raw.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}

fun createTranslateClient(): TranslateClient = TranslateClient(createSharedHttpClient())
