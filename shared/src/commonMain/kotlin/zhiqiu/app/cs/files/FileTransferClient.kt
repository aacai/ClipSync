package zhiqiu.app.cs.files

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

/**
 * Litterbox（catbox 临时文件托管）：
 * - 免注册、免 API key，上传即得直链；
 * - 链接 1h/12h/24h/72h 后到期自动删除，下载不限次数。
 *
 * 注意：这里传的永远是**已用房间密钥加密后的字节**，托管方只能看到密文。
 */
class FileTransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

class FileTransferClient(
    private val http: HttpClient,
    private val uploadUrl: String = DEFAULT_UPLOAD_URL,
) {

    /**
     * 上传 [bytes]（调用方需先加密），返回分享链接。
     *
     * @param onProgress 已发送字节数 / 总字节数
     */
    suspend fun upload(
        fileName: String,
        bytes: ByteArray,
        expires: String = DEFAULT_EXPIRES,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): String {
        val response = runCatching {
            http.post(uploadUrl) {
                onUpload { sent, total -> onProgress(sent, total ?: bytes.size.toLong()) }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("reqtype", "fileupload")
                            append("time", expires.toLitterboxTime())
                            append(
                                "fileToUpload",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/octet-stream")
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=\"fileToUpload\"; filename=\"$fileName\"",
                                    )
                                },
                            )
                        },
                    ),
                )
            }
        }.getOrElse { throw FileTransferException("上传失败：${it.message}", it) }

        if (!response.status.isSuccess()) {
            val detail = runCatching { response.bodyAsText().take(200) }.getOrDefault("")
            throw FileTransferException("上传被拒绝（HTTP ${response.status.value}）$detail")
        }
        val link = response.bodyAsText().trim()
        if (!link.startsWith("http")) {
            throw FileTransferException("上传响应不是有效链接：$link")
        }
        return link
    }

    /**
     * 按 [link] 取回字节（仍是密文，由调用方解密）。
     *
     * @param onProgress 已接收字节数 / 总字节数（总大小未知时为 -1）
     */
    suspend fun download(
        link: String,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray {
        val response = runCatching { http.get(link) }
            .getOrElse { throw FileTransferException("下载失败：${it.message}", it) }
        if (!response.status.isSuccess()) {
            val detail = runCatching { response.bodyAsText().take(200) }.getOrDefault("")
            throw FileTransferException("下载被拒绝（HTTP ${response.status.value}）$detail")
        }

        val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        var received = 0L
        val buffer = Buffer()
        val channel = response.bodyAsChannel()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = channel.readAvailable(chunk)
            if (read <= 0) break // -1 = EOF
            buffer.write(chunk, 0, read)
            received += read
            onProgress(received, total)
        }
        return buffer.readByteArray()
    }

    companion object {
        const val DEFAULT_UPLOAD_URL = "https://litterbox.catbox.moe/resources/internals/api.php"
        const val DEFAULT_EXPIRES = "72h"
    }
}

/** 把任意有效期描述映射到 Litterbox 支持的档位。 */
internal fun String.toLitterboxTime(): String = when (lowercase()) {
    "1h" -> "1h"
    "12h" -> "12h"
    "24h" -> "24h"
    "72h" -> "72h"
    else -> "72h"
}

/** 与 Destiny 一致的共享 Ktor 客户端。 */
fun createSharedHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
}
