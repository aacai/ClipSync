package zhiqiu.app.cs.files

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** 本地起一个 server 当真托管方：字段名、回退顺序、两家都挂时报谁的错，全走真代码。 */
private class StubHost : AutoCloseable {

    val responses = mutableMapOf<String, Pair<Int, String>>()
    val requests = mutableListOf<Pair<String, String>>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readBytes().decodeToString()
            requests += path to body
            val (status, reply) = responses[path] ?: (500 to "no route")
            val bytes = reply.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    val origin get() = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}

class UploadChannelTest {

    @Test
    fun `x0at hands back a plain text link`() = runBlocking {
        StubHost().use { host ->
            host.responses["/x0"] = 200 to "https://x0.at/cs-ab12_cd34.bin\n"
            val client = FileTransferClient(createSharedHttpClient(), "${host.origin}/x0", "${host.origin}/uguu")

            var sent = 0L
            val link = client.upload("cs-ab12.bin", ByteArray(4096) { 7 }) { current, _ -> sent = current }

            assertEquals("https://x0.at/cs-ab12_cd34.bin", link.url)
            assertEquals(FileTransferClient.X0_AT_TTL_MS, link.ttlMillis)
            assertTrue(sent >= 4096, "sent=$sent")

            val (path, body) = host.requests.single()
            assertEquals("/x0", path)
            assertTrue("name=\"file\"" in body, body.take(300))
            assertTrue("name=\"keep_name\"" in body, body.take(300))
            assertTrue("filename=\"cs-ab12.bin\"" in body, body.take(300))
        }
    }

    @Test
    fun `uguu takes over when x0at refuses`() = runBlocking {
        StubHost().use { host ->
            host.responses["/x0"] = 500 to "<html>x0 down</html>"
            host.responses["/uguu"] =
                    200 to """{"success":true,"files":[{"url":"https://d.uguu.se/fQkJWLTP.bin"}]}"""
            val client = FileTransferClient(createSharedHttpClient(), "${host.origin}/x0", "${host.origin}/uguu")

            val link = client.upload("cs-ab12.bin", ByteArray(64))

            assertEquals("https://d.uguu.se/fQkJWLTP.bin", link.url)
            assertEquals(FileTransferClient.UUGU_TTL_MS, link.ttlMillis)
            assertEquals(listOf("/x0", "/uguu"), host.requests.map { it.first })
            assertTrue("name=\"files[]\"" in host.requests.last().second, host.requests.last().second.take(300))
        }
    }

    @Test
    fun `when both hosts refuse the primary error is what the ui shows`() = runBlocking {
        StubHost().use { host ->
            host.responses["/x0"] = 500 to "<html>x0 down</html>"
            host.responses["/uguu"] = 500 to "<html>uguu down</html>"
            val client = FileTransferClient(createSharedHttpClient(), "${host.origin}/x0", "${host.origin}/uguu")

            val e = assertFailsWith<FileTransferException> { client.upload("cs-ab12.bin", ByteArray(8)) }

            assertTrue(e.message!!.contains("x0 down"), e.message)
        }
    }
}
