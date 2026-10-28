package zhiqiu.app.cs.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import zhiqiu.app.cs.files.createSharedHttpClient

/** 连上但不回话、回错误码：这两种都得当场报错，不能挂成「检查中」，更不能当成「已是最新」。 */
class UpdateCheckTimeoutTest {

    private class Server(private val delayMillis: Long, private val status: Int, private val body: String) : AutoCloseable {

        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/") { exchange ->
                if (delayMillis > 0) Thread.sleep(delayMillis)
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        val endpoint get() = "http://127.0.0.1:${server.address.port}/update.json"

        override fun close() {
            server.stop(0)
        }
    }

    @Test
    fun `a silent update server fails with a timeout instead of hanging`() {
        Server(delayMillis = 30_000, status = 200, body = "{}").use { server ->
            val source = ManifestReleases(createSharedHttpClient(), server.endpoint, timeoutMillis = 500)
            val startedAt = System.nanoTime()
            val failure = assertFailsWith<UpdateException> { runBlocking { source.latest() } }
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue("没响应" in failure.message.orEmpty(), failure.message.orEmpty())
            assertTrue(elapsedMillis < 5_000, "elapsed=$elapsedMillis")
        }
    }

    @Test
    fun `an error status is reported as the server answer`() {
        Server(delayMillis = 0, status = 404, body = "There is not a GitHub Page here").use { server ->
            val source = ManifestReleases(createSharedHttpClient(), server.endpoint, timeoutMillis = 5_000)
            val failure = assertFailsWith<UpdateException> { runBlocking { source.latest() } }

            assertTrue("更新服务返回 404" in failure.message.orEmpty(), failure.message.orEmpty())
        }
    }
}
