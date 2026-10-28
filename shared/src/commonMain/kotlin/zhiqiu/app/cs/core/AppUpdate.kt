package zhiqiu.app.cs.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import zhiqiu.app.cs.files.FileTransferClient
import zhiqiu.app.cs.files.createSharedHttpClient

class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
)

class Release(
    val version: String,
    val assets: List<ReleaseAsset>,
)

class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 取最新版本信息、下载它的安装包；测试从这里换掉真网络。 */
interface ReleaseSource {
    suspend fun latest(): Release
    suspend fun download(asset: ReleaseAsset, onProgress: (received: Long, total: Long) -> Unit): ByteArray
}

/** 直接问 GitHub Releases：最新版本就是 Releases 页上那条，不再手工维护清单文件。 */
class GithubReleases(
    private val http: HttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) : ReleaseSource {

    override suspend fun latest(): Release {
        val (status, raw) = runCatching {
            withTimeout(timeoutMillis) {
                val response = http.get(endpoint) {
                    header(HttpHeaders.UserAgent, "ClipSync")
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                }
                response.status.value to response.bodyAsText()
            }
        }.getOrElse {
            val reason = if (it is TimeoutCancellationException) {
                "发布页 ${timeoutMillis / 1000} 秒没响应"
            } else {
                it.message ?: "连不上发布页"
            }
            throw UpdateException(reason, it)
        }
        if (!HttpStatusCode.fromValue(status).isSuccess()) {
            throw UpdateException("发布页拒绝了这个请求：${rejectDetail(raw, status)}")
        }
        return decodeRelease(raw)
    }

    override suspend fun download(
        asset: ReleaseAsset,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray = FileTransferClient(http).download(asset.url, onProgress)

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.github.com/repos/aacai/ClipSync/releases/latest"
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}

internal object DefaultReleaseSource : ReleaseSource {
    private val client: GithubReleases by lazy { GithubReleases(createSharedHttpClient()) }

    override suspend fun latest(): Release = client.latest()

    override suspend fun download(
        asset: ReleaseAsset,
        onProgress: (received: Long, total: Long) -> Unit,
    ): ByteArray = client.download(asset, onProgress)
}

internal fun decodeRelease(raw: String): Release {
    val dto = runCatching { releaseJson.decodeFromString<ReleaseDto>(raw) }
        .getOrElse { throw UpdateException("发布信息读不懂", it) }
    val version = dto.tag.orEmpty().trim().removePrefix("v").removePrefix("V")
    if (version.isBlank()) {
        throw UpdateException(dto.message?.takeIf { it.isNotBlank() } ?: "发布信息里没有版本号")
    }
    return Release(
        version = version,
        assets = dto.assets
            .filter { it.url.startsWith("http") && it.name.isNotBlank() }
            .map { ReleaseAsset(it.name, it.url, it.size) },
    )
}

private fun rejectDetail(raw: String, status: Int): String =
    runCatching { releaseJson.decodeFromString<MessageDto>(raw).message }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $status"

private val releaseJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private class ReleaseDto(
    @SerialName("tag_name") val tag: String? = null,
    val message: String? = null,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private class AssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

@Serializable
private class MessageDto(val message: String? = null)

/** 版本号逐段比数字：0.9 比 0.10 旧，缺的段当 0。 */
internal fun isBehind(current: String, latest: String): Boolean {
    val mine = versionParts(current)
    val theirs = versionParts(latest)
    val width = maxOf(mine.size, theirs.size)
    for (i in 0 until width) {
        val a = mine.getOrElse(i) { 0 }
        val b = theirs.getOrElse(i) { 0 }
        if (a != b) return a < b
    }
    return false
}

private fun versionParts(version: String): List<Int> =
    version.trim().removePrefix("v").removePrefix("V").split('.')
        .map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

/** 同一个扩展名可能有几个架构的包：先挑本机那个，再退到不分架构的那份。 */
internal fun Release.assetFor(os: String, arch: String): ReleaseAsset? {
    val extension = installerExtension(os) ?: return null
    val candidates = assets.filter { it.name.endsWith(".$extension", ignoreCase = true) }
    if (candidates.isEmpty()) return null
    val universal = candidates.firstOrNull { it.archTag().isBlank() }
    val tagged = candidates.filter { it.archTag().isNotBlank() }
    if (tagged.isEmpty()) return universal
    val mine = if (arch.isBlank()) null else tagged.firstOrNull { it.archTag().equals(arch, ignoreCase = true) }
    return mine ?: universal
}

private fun installerExtension(os: String): String? = when {
    os.equals("macOS", ignoreCase = true) || os.contains("mac", ignoreCase = true) ||
        os.contains("darwin", ignoreCase = true) -> "dmg"
    os.contains("win", ignoreCase = true) -> "msi"
    os.contains("linux", ignoreCase = true) || os.contains("nux", ignoreCase = true) -> "deb"
    os.contains("android", ignoreCase = true) -> "apk"
    else -> null
}

private fun ReleaseAsset.archTag(): String = when {
    name.contains("-arm64", ignoreCase = true) -> "arm64"
    name.contains("-x64", ignoreCase = true) -> "x64"
    else -> ""
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: Release, val asset: ReleaseAsset?) : UpdateState
    data class Downloading(val asset: ReleaseAsset, val received: Long) : UpdateState
    data class Downloaded(val name: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}
