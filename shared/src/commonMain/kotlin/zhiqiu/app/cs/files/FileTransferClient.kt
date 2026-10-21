package zhiqiu.app.cs.files

import io.ktor.client.HttpClient
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import zhiqiu.app.cs.core.platformHttpClientEngine

/**
 * 文件直链托管：x0.at 主用（1 GiB，链接至少活 3 天），uguu.se 备用（128 MiB，3 小时）。
 * 两家都免注册免 key，上传即得直链；uguu 回 JSON 且会打乱文件名。
 *
 * 注意：这里传的永远是**已用房间密钥加密后的字节**，托管方只能看到密文。
 */
class FileTransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 上传结果：直链 + 托管方保证的存活时长，调用方据此算过期时间。 */
class UploadedLink(val url: String, val ttlMillis: Long) {

    fun expiresAt(nowMs: Long): Long = nowMs + ttlMillis
}

@Serializable
private class UuguResponse(val success: Boolean, val files: List<UuguFile> = emptyList())

@Serializable
private class UuguFile(val url: String)

class FileTransferClient(
    private val http: HttpClient,
    private val x0atUrl: String = X0_AT_UPLOAD_URL,
    private val uuguUrl: String = UUGU_UPLOAD_URL,
) {

    /**
     * 上传 [bytes]（调用方需先加密），返回分享链接。
     *
     * @param onProgress 已发送字节数 / 总字节数
     */
    suspend fun upload(
        fileName: String,
        bytes: ByteArray,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): UploadedLink {
        val primary = runCatching { uploadToX0at(fileName, bytes, onProgress) }
        return primary.getOrNull()
            ?: runCatching { uploadToUugu(fileName, bytes, onProgress) }.getOrElse { throw primary.exceptionOrNull() ?: it }
    }

    private suspend fun uploadToX0at(
        fileName: String,
        bytes: ByteArray,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): UploadedLink {
        val body = postFile(x0atUrl, "file", fileName, bytes, mapOf("keep_name" to "1"), onProgress)
        return UploadedLink(parseX0atLink(body), X0_AT_TTL_MS)
    }

    private suspend fun uploadToUugu(
        fileName: String,
        bytes: ByteArray,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): UploadedLink {
        val body = postFile(uuguUrl, "files[]", fileName, bytes, emptyMap(), onProgress)
        return UploadedLink(parseUuguLink(body), UUGU_TTL_MS)
    }

    private suspend fun postFile(
        url: String,
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        extraFields: Map<String, String>,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): String {
        val response = runCatching {
            http.post(url) {
                onUpload { sent, total -> onProgress(sent, total ?: bytes.size.toLong()) }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            extraFields.forEach { (key, value) -> append(key, value) }
                            append(
                                fieldName,
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/octet-stream")
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=\"$fieldName\"; filename=\"$fileName\"",
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
        return response.bodyAsText()
    }

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
            if (read <= 0) break
            buffer.write(chunk, 0, read)
            received += read
            onProgress(received, total)
        }
        return buffer.readByteArray()
    }

    companion object {
        const val X0_AT_UPLOAD_URL = "https://x0.at/"
        const val UUGU_UPLOAD_URL = "https://uguu.se/upload.php"

        // x0.at 只承诺 3 天起步，按最短的那头算，别给 UI 一个兑现不了的剩余时间
        const val X0_AT_TTL_MS = 72L * 3_600_000L
        const val UUGU_TTL_MS = 3L * 3_600_000L

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseX0atLink(body: String): String {
            val link = body.trim()
            if (!link.startsWith("http")) {
                throw FileTransferException("上传响应不是有效链接：$link")
            }
            return link
        }

        internal fun parseUuguLink(body: String): String {
            val response = runCatching { json.decodeFromString<UuguResponse>(body) }
                .getOrElse { throw FileTransferException("上传响应读不懂：${body.take(200)}", it) }
            val link = response.files.firstOrNull()?.url
            if (!response.success || link == null || !link.startsWith("http")) {
                throw FileTransferException("上传被拒绝：${body.take(200)}")
            }
            return link
        }
    }
}

fun createSharedHttpClient(): HttpClient = HttpClient(platformHttpClientEngine()) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
}
