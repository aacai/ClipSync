package zhiqiu.app.cs.core

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class LanInfo(
    val port: Int,
    val addrs: List<String>,
    val nonce: String,
)

@Serializable
data class LanBeacon(
    val id: String,
    val name: String,
    val os: String,
    val port: Int,
    val addrs: List<String>,
    val roomHash: String,
    val nonce: String,
)

data class LanPeer(
    val id: String,
    val name: String,
    val os: String,
    val addrs: List<String>,
    val port: Int,
) {
    fun base(): String? = addrs.firstOrNull()?.let { "http://$it:$port" }
}

internal interface LanServer {
    val info: LanInfo
    fun close()
}

internal expect fun createLanServer(
    port: Int,
    identity: LanIdentity,
    roomHash: String,
    blobDir: String,
    onClip: (String) -> Unit,
    onPeer: (LanBeacon) -> Unit,
): LanServer?

data class LanIdentity(
    val id: String,
    val name: String,
    val os: String,
)

internal expect fun lanBlobDir(): String

internal expect fun writeLanBlob(name: String, bytes: ByteArray): Boolean

/** 删除临时目录里超过 maxAgeMs 的文件，返回删除个数。 */
internal expect fun cleanupTempFiles(maxAgeMs: Long): Int

internal class LanSender(
    private val http: HttpClient,
    private val roomHash: String,
) {
    fun close() = http.close()

    suspend fun publish(peers: Collection<LanPeer>, wire: String) {
        val path = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.CH_CLIP}"
        for (peer in peers) {
            for (host in peer.addrs) {
                val ok = runCatching {
                    http.post("http://$host:${peer.port}$path") {
                        contentType(ContentType.Text.Plain)
                        setBody(wire)
                    }.status.isSuccess()
                }.getOrDefault(false)
                if (ok) break
            }
        }
    }
}
