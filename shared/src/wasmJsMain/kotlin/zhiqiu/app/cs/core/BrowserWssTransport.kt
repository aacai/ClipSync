package zhiqiu.app.cs.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransport
import org.meshtastic.mqtt.MqttTransportFactory

private fun wssLog(msg: String): Unit =
        js("console.log('[WssTransport] ' + new Date().toISOString().slice(11, 23) + ' ' + msg)")

internal class BrowserWssTransport : MqttTransport {
    private var client: HttpClient? = null
    private var session: DefaultClientWebSocketSession? = null
    private val sendMutex = Mutex()

    override val isConnected: Boolean
        get() = session?.isActive == true

    override suspend fun connect(endpoint: MqttEndpoint) {
        require(endpoint is MqttEndpoint.WebSocket) {
            "BrowserWssTransport requires MqttEndpoint.WebSocket"
        }
        wssLog("connect ${endpoint.url}")
        close()
        val httpClient =
                HttpClient(Js) {
                    install(WebSockets) {
                        // 刻意不设置 maxFrameSize / pingIntervalMillis，Js 引擎均不支持。
                    }
                }
        client = httpClient
        session =
                httpClient.webSocketSession(endpoint.url) {
                    headers.append("Sec-WebSocket-Protocol", endpoint.protocols.joinToString(", "))
                }
        wssLog("connect done, protocols=${endpoint.protocols}")
    }

    override suspend fun send(bytes: ByteArray) {
        sendMutex.withLock {
            val ws = session ?: error("Not connected")
            wssLog("send ${bytes.size}B first=${bytes.firstOrNull()}")
            ws.send(Frame.Binary(true, bytes))
        }
    }

    override suspend fun receive(): ByteArray {
        val ws = session ?: error("Not connected")
        val frame = ws.incoming.receive()
        val out =
                when (frame) {
                    is Frame.Binary -> frame.readBytes()
                    is Frame.Close -> error("WebSocket closed by server")
                    else -> error("Unexpected frame type: ${frame.frameType}")
                }
        wssLog("recv ${out.size}B first=${out.firstOrNull()}")
        return out
    }

    override suspend fun close() {
        val quiesced =
                withTimeoutOrNull(CLOSE_QUIESCE_TIMEOUT_MS) {
                    sendMutex.lock()
                    true
                } != null
        try {
            try {
                session?.close()
            } finally {
                try {
                    client?.close()
                } finally {
                    session = null
                    client = null
                }
            }
        } finally {
            if (quiesced) sendMutex.unlock()
        }
    }

    private companion object {
        const val CLOSE_QUIESCE_TIMEOUT_MS = 2_000L
    }
}

internal class BrowserWssTransportFactory : MqttTransportFactory {
    override fun supports(endpoint: MqttEndpoint): Boolean = endpoint is MqttEndpoint.WebSocket

    override fun create(endpoint: MqttEndpoint): MqttTransport = BrowserWssTransport()
}
