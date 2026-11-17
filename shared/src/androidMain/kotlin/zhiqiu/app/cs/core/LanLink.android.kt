package zhiqiu.app.cs.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.random.Random

private const val MAX_HEAD = 16 * 1024
private const val MAX_BODY = 64 * 1024 * 1024 + 4096

internal actual fun lanBlobDir(): String =
    File(System.getProperty("java.io.tmpdir"), "clipsync-lan-blobs").absolutePath

internal actual fun writeLanBlob(name: String, bytes: ByteArray): Boolean {
    if (!validLanBlobName(name)) return false
    return runCatching {
        val dir = File(lanBlobDir())
        dir.mkdirs()
        File(dir, name).writeBytes(bytes)
    }.isSuccess
}

internal actual fun createLanServer(
    port: Int,
    roomHash: String,
    blobDir: String,
    onClip: (String) -> Unit,
): LanServer? = runCatching {
    val socket = ServerSocket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(port))
    val dir = File(blobDir).also { it.mkdirs() }
    val nonce = ByteArray(8).also { Random.nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    val server = object : LanServer {
        override val info = LanInfo(socket.localPort, localLanAddresses(), nonce)
        override fun close() {
            socket.close()
        }
    }
    thread(name = "clipsync-lan", isDaemon = true) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            thread(isDaemon = true) { serveLan(client, roomHash, dir, onClip) }
        }
    }
    server
}.getOrNull()

private fun localLanAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress }
        .map { it.hostAddress }
}.getOrDefault(emptyList())

private fun serveLan(socket: Socket, roomHash: String, dir: File, onClip: (String) -> Unit) {
    socket.soTimeout = 10_000
    socket.use { client ->
        val request = readLanRequest(client.getInputStream()) ?: return@use
        val clipPath = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.CH_CLIP}"
        val blobPrefix = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.BLOB_SEGMENT}/"
        when {
            request.first == "POST" && request.second == clipPath -> {
                onClip(String(request.third, Charsets.UTF_8))
                respondLan(client.getOutputStream(), "200 OK", "ok".toByteArray())
            }

            request.second.startsWith(blobPrefix) -> {
                val name = request.second.removePrefix(blobPrefix)
                if (!validLanBlobName(name)) {
                    respondLan(client.getOutputStream(), "404 Not Found", ByteArray(0))
                } else {
                    val file = File(dir, name)
                    when (request.first) {
                        "PUT" -> {
                            file.writeBytes(request.third)
                            respondLan(client.getOutputStream(), "200 OK", "ok".toByteArray())
                        }

                        "GET" -> if (file.exists()) {
                            respondLan(client.getOutputStream(), "200 OK", file.readBytes())
                        } else {
                            respondLan(client.getOutputStream(), "404 Not Found", ByteArray(0))
                        }

                        else -> respondLan(client.getOutputStream(), "405 Method Not Allowed", ByteArray(0))
                    }
                }
            }

            else -> respondLan(client.getOutputStream(), "404 Not Found", ByteArray(0))
        }
    }
}

private fun readLanRequest(input: InputStream): Triple<String, String, ByteArray>? {
    val head = ByteArrayOutputStream()
    while (head.size() <= MAX_HEAD) {
        val byte = input.read()
        if (byte < 0) return null
        head.write(byte)
        val bytes = head.toByteArray()
        if (bytes.size >= 4 && bytes.copyOfRange(bytes.size - 4, bytes.size).contentEquals("\r\n\r\n".toByteArray())) {
            break
        }
    }
    val lines = head.toString("ISO-8859-1").split("\r\n")
    val parts = lines.firstOrNull().orEmpty().split(" ")
    val method = parts.getOrNull(0) ?: return null
    val path = parts.getOrNull(1) ?: return null
    val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
    if (length !in 0..MAX_BODY) return null
    val body = ByteArray(length)
    var read = 0
    while (read < length) {
        val count = input.read(body, read, length - read)
        if (count <= 0) break
        read += count
    }
    return Triple(method, path, body.copyOf(read))
}

private fun respondLan(output: OutputStream, status: String, body: ByteArray) {
    output.write("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
    output.write(body)
    output.flush()
}

private fun validLanBlobName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 128 && !name.contains("..") &&
        name.all { it.isLetterOrDigit() || it in ".-_" }
