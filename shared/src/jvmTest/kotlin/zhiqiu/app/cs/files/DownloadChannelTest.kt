package zhiqiu.app.cs.files

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private class StubDownload(chunked: Boolean) : AutoCloseable {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/") { exchange ->
            if (chunked) {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { out ->
                    var sent = 0
                    while (sent < payload.size) {
                        val take = minOf(CHUNK, payload.size - sent)
                        out.write(payload, sent, take)
                        out.flush()
                        sent += take
                    }
                }
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
        }
        server.start()
    }

    val link get() = "http://127.0.0.1:${server.address.port}/blob"

    override fun close() = server.stop(0)

    companion object {
        const val CHUNK = 50_000
        val payload = ByteArray(200_000) { (it % 251).toByte() }
    }
}

class DownloadChannelTest {

    @Test
    fun `a download with a content length lands whole`() = runBlocking {
        StubDownload(chunked = false).use { host ->
            val client = FileTransferClient(createSharedHttpClient(), "${host.link}/x0", "${host.link}/uguu")
            val progress = mutableListOf<Long>()

            val bytes = client.download(host.link) { received, _ -> progress += received }

            assertContentEquals(StubDownload.payload, bytes)
            assertEquals(StubDownload.payload.size.toLong(), progress.last())
            assertTrue(progress.zipWithNext().all { (from, to) -> to > from }, progress.toString())
        }
    }

    @Test
    fun `a chunked download grows until the stream ends`() = runBlocking {
        StubDownload(chunked = true).use { host ->
            val client = FileTransferClient(createSharedHttpClient(), "${host.link}/x0", "${host.link}/uguu")
            var received = 0L

            val bytes = client.download(host.link) { current, _ -> received = current }

            assertContentEquals(StubDownload.payload, bytes)
            assertEquals(StubDownload.payload.size.toLong(), received)
        }
    }
}
