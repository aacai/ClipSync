package zhiqiu.app.cs.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import zhiqiu.app.cs.files.createSharedHttpClient

/** 连上但不回话、回错误码：这两种都得当场报错，不能挂成「检查中」，更不能当成「已是最新」。 */
class UpdateCheckTimeoutTest {

    private class Server(private val delayMillis: Long, private val status: Int, private val body: String) : AutoCloseable {

        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        @Volatile
        var userAgent: String? = null
            private set

        @Volatile
        var accept: String? = null
            private set

        init {
            server.createContext("/") { exchange ->
                userAgent = exchange.requestHeaders.getFirst("User-agent")
                accept = exchange.requestHeaders.getFirst("Accept")
                if (delayMillis > 0) Thread.sleep(delayMillis)
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        val endpoint get() = "http://127.0.0.1:${server.address.port}/releases/latest"

        override fun close() {
            server.stop(0)
        }
    }

    @Test
    fun `a silent release page fails with a timeout instead of hanging`() {
        Server(delayMillis = 30_000, status = 200, body = "{}").use { server ->
            val source = GithubReleases(createSharedHttpClient(), server.endpoint, timeoutMillis = 500)
            val startedAt = System.nanoTime()
            val failure = assertFailsWith<UpdateException> { runBlocking { source.latest() } }
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue("没响应" in failure.message.orEmpty(), failure.message.orEmpty())
            assertTrue(elapsedMillis < 5_000, "elapsed=$elapsedMillis")
        }
    }

    @Test
    fun `the release api is asked with the headers it requires`() {
        Server(delayMillis = 0, status = 200, body = """{"tag_name":"v0.0.9","assets":[]}""").use { server ->
            val release = runBlocking {
                GithubReleases(createSharedHttpClient(), server.endpoint, timeoutMillis = 5_000).latest()
            }

            assertEquals("0.0.9", release.version)
            assertEquals("ClipSync", server.userAgent, "缺 User-Agent 会被 GitHub 直接 403")
            assertTrue(
                server.accept.orEmpty().startsWith("application/vnd.github+json"),
                "Accept=${server.accept}",
            )
        }
    }

    @Test
    fun `an error status is reported as the server answer`() {
        Server(delayMillis = 0, status = 404, body = "There is not a GitHub release here").use { server ->
            val source = GithubReleases(createSharedHttpClient(), server.endpoint, timeoutMillis = 5_000)
            val failure = assertFailsWith<UpdateException> { runBlocking { source.latest() } }

            assertTrue("HTTP 404" in failure.message.orEmpty(), failure.message.orEmpty())
        }
    }

    @Test
    fun `the api message is shown when it explains itself`() {
        Server(
            delayMillis = 0,
            status = 403,
            body = """{"message":"API rate limit exceeded for 1.2.3.4"}""",
        ).use { server ->
            val source = GithubReleases(createSharedHttpClient(), server.endpoint, timeoutMillis = 5_000)
            val failure = assertFailsWith<UpdateException> { runBlocking { source.latest() } }

            assertTrue(
                "API rate limit exceeded" in failure.message.orEmpty(),
                failure.message.orEmpty(),
            )
        }
    }
}
