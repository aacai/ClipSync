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

data class LanPeer(
    val id: String,
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
    roomHash: String,
    blobDir: String,
    onClip: (String) -> Unit,
): LanServer?

internal expect fun lanBlobDir(): String

internal expect fun writeLanBlob(name: String, bytes: ByteArray): Boolean

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
