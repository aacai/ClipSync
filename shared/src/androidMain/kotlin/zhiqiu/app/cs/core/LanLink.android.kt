package zhiqiu.app.cs.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import kotlin.concurrent.thread

private const val MAX_HEAD = 16 * 1024
private const val MAX_BODY = 64 * 1024 * 1024 + 4096
private const val BEACON_INTERVAL_MS = 1500L
private const val BEACON_MAX_AGE_MS = 6000L

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

internal actual fun readLanBlob(name: String): ByteArray? {
    if (!validLanBlobName(name)) return null
    val file = File(lanBlobDir(), name)
    return if (file.isFile) runCatching { file.readBytes() }.getOrNull() else null
}

internal actual fun cleanupTempFiles(maxAgeMs: Long): Int {
    val cutoff = System.currentTimeMillis() - maxAgeMs
    val tmp = File(System.getProperty("java.io.tmpdir"))
    return listOf("clipsync-lan-blobs", "clipsync-drag-out")
        .sumOf { name -> deleteOldFiles(File(tmp, name), cutoff) }
}

private fun deleteOldFiles(dir: File, cutoff: Long): Int {
    val files = dir.listFiles() ?: return 0
    return files.count { file -> file.isFile && file.lastModified() < cutoff && file.delete() }
}

internal actual fun createLanServer(
    port: Int,
    identity: LanIdentity,
    roomHash: String,
    blobDir: String,
    fsToken: String,
    fsRoots: List<SharedRoot>,
    onClip: (String) -> Unit,
    onPeer: (LanBeacon) -> Unit,
): LanServer? = runCatching {
    val socket = ServerSocket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(port))
    val dir = File(blobDir).also { it.mkdirs() }
    val info = LanInfo(socket.localPort, localLanAddresses(), fsToken)
    val discovery = runCatching { startDiscovery(info, identity, roomHash, onPeer) }
        .getOrElse { AutoCloseable { } }
    val server = object : LanServer {
        override val info = info
        override fun close() {
            discovery.close()
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
            thread(isDaemon = true) { serveLan(client, roomHash, dir, fsToken, fsRoots, onClip) }
        }
    }
    server
}.getOrNull()

private fun startDiscovery(
    info: LanInfo,
    identity: LanIdentity,
    roomHash: String,
    onPeer: (LanBeacon) -> Unit,
): AutoCloseable {
    val socket = DatagramSocket(null)
    socket.reuseAddress = true
    socket.broadcast = true
    socket.bind(InetSocketAddress(ClipProtocol.LAN_DISCOVERY_PORT))
    val beacon =
            ClipProtocol.encode(
                    LanBeacon(
                            id = identity.id,
                            name = identity.name,
                            os = identity.os,
                            port = info.port,
                            addrs = info.addrs,
                            roomHash = roomHash,
                            nonce = info.nonce,
                    ),
            )
    val seen = HashMap<String, Long>()
    thread(name = "clipsync-lan-discovery-send", isDaemon = true) {
        while (!socket.isClosed) {
            val payload = beacon.toByteArray()
            for (target in broadcastAddresses()) {
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, target, ClipProtocol.LAN_DISCOVERY_PORT))
                }
            }
            Thread.sleep(BEACON_INTERVAL_MS)
        }
    }
    thread(name = "clipsync-lan-discovery-recv", isDaemon = true) {
        val buffer = ByteArray(8 * 1024)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
            if (!received) break
            val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val found = runCatching { ClipProtocol.decode<LanBeacon>(raw) }.getOrNull() ?: continue
            if (found.roomHash != roomHash || found.id == identity.id) continue
            val now = System.currentTimeMillis()
            val host = packet.address.hostAddress
            val addrs = (listOf(host) + found.addrs).distinct()
            seen[found.id] = now
            seen.entries.removeIf { now - it.value > BEACON_MAX_AGE_MS }
            onPeer(found.copy(addrs = addrs, port = found.port))
        }
    }
    return AutoCloseable { socket.close() }
}

private fun broadcastAddresses(): List<InetAddress> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.interfaceAddresses.mapNotNull { config -> config.broadcast } }
        .distinct()
        .ifEmpty { listOf(InetAddress.getByName("255.255.255.255")) }
}.getOrDefault(listOf(InetAddress.getByName("255.255.255.255")))

private fun localLanAddresses(): List<String> {
    val all = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isVirtual && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .map { it.hostAddress }
    }.getOrDefault(emptyList())
    val usable = all.filter { isLanAddress(it) }
    val via = runCatching {
        val socket = java.net.DatagramSocket()
        socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 80)
        val host = socket.localAddress.hostAddress
        socket.close()
        host
    }.getOrNull()
    return if (via != null && via in usable) listOf(via) + usable.filter { it != via }
    else usable.sortedByDescending { addressRank(it) }
}

private fun isLanAddress(host: String): Boolean {
    val p = host.split('.').mapNotNull { it.toIntOrNull() }
    if (p.size != 4) return false
    return when {
        p[0] == 10 -> true
        p[0] == 192 && p[1] == 168 -> true
        p[0] == 172 -> p[1] in 16..31 && p[1] != 17
        else -> false
    }
}

private fun addressRank(host: String): Int {
    val p = host.split('.').mapNotNull { it.toIntOrNull() }
    return when {
        p.size != 4 -> -1
        p[0] == 192 && p[1] == 168 -> 2
        p[0] == 10 -> 1
        else -> 0
    }
}

private fun serveLan(
    socket: Socket,
    roomHash: String,
    dir: File,
    fsToken: String,
    fsRoots: List<SharedRoot>,
    onClip: (String) -> Unit,
) {
    socket.soTimeout = 600_000
    socket.use { client ->
        val request = readLanRequest(client.getInputStream()) ?: return@use
        val path = lanPath(request.second)
        val query = lanQuery(request.second)
        val clipPath = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.CH_CLIP}"
        val blobPrefix = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.BLOB_SEGMENT}/"
        when {
            request.first == "POST" && path == clipPath -> {
                onClip(String(request.third, Charsets.UTF_8))
                respondLan(client.getOutputStream(), "200 OK", "ok".toByteArray())
            }

            path.startsWith(blobPrefix) -> {
                val name = path.removePrefix(blobPrefix)
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

            request.first == "GET" && path == ClipProtocol.fsPath() -> {
                if (!fsTokenOk(query, fsToken)) {
                    respondLan(client.getOutputStream(), "403 Forbidden", ByteArray(0))
                } else {
                    respondLan(
                            client.getOutputStream(),
                            "200 OK",
                            LAN_FS_HTML.toByteArray(Charsets.UTF_8),
                            "text/html; charset=utf-8",
                    )
                }
            }

            request.first == "GET" && path == "${ClipProtocol.fsPath()}/roots" -> {
                if (!fsTokenOk(query, fsToken)) {
                    respondLan(client.getOutputStream(), "403 Forbidden", ByteArray(0))
                } else {
                    val entries = fsRoots.mapIndexed { index, root -> FsRootEntry(root.name, index) }
                    respondLan(
                            client.getOutputStream(),
                            "200 OK",
                            ClipProtocol.encode(entries).toByteArray(Charsets.UTF_8),
                            "application/json; charset=utf-8",
                    )
                }
            }

            request.first == "GET" && path == "${ClipProtocol.fsPath()}/list" -> {
                if (!fsTokenOk(query, fsToken)) {
                    respondLan(client.getOutputStream(), "403 Forbidden", ByteArray(0))
                } else {
                    val root = query["root"]?.toIntOrNull()?.let { fsRoots.getOrNull(it) }
                    val body = root?.let { fsListJson(it, query["path"].orEmpty(), query["q"]) }
                    if (body == null) {
                        respondLan(client.getOutputStream(), "403 Forbidden", ByteArray(0))
                    } else {
                        respondLan(
                                client.getOutputStream(),
                                "200 OK",
                                body,
                                "application/json; charset=utf-8",
                        )
                    }
                }
            }

            request.first == "GET" && path == "${ClipProtocol.fsPath()}/file" -> {
                if (!fsTokenOk(query, fsToken)) {
                    respondLan(client.getOutputStream(), "403 Forbidden", ByteArray(0))
                } else {
                    val root = query["root"]?.toIntOrNull()?.let { fsRoots.getOrNull(it) }
                    val file = root?.let { fsResolve(it.path, query["path"].orEmpty()) }
                    if (file == null || !file.isFile) {
                        respondLan(client.getOutputStream(), "403 Forbidden", ByteArray(0))
                    } else {
                        respondLan(
                                client.getOutputStream(),
                                "200 OK",
                                file.readBytes(),
                                "application/octet-stream",
                        )
                    }
                }
            }

            else -> respondLan(client.getOutputStream(), "404 Not Found", ByteArray(0))
        }
    }
}

private fun lanPath(raw: String): String = raw.substringBefore('?')

private fun lanQuery(raw: String): Map<String, String> {
    val start = raw.indexOf('?')
    if (start < 0 || start == raw.lastIndex) return emptyMap()
    return raw.substring(start + 1).split('&').mapNotNull { part ->
        val split = part.indexOf('=')
        if (split < 0) {
            null
        } else {
            decodeLanParam(part.substring(0, split)) to decodeLanParam(part.substring(split + 1))
        }
    }.toMap()
}

private fun decodeLanParam(raw: String): String =
    runCatching { URLDecoder.decode(raw.replace("+", " "), "UTF-8") }.getOrDefault(raw)

private fun fsTokenOk(query: Map<String, String>, token: String): Boolean =
    token.isNotBlank() && query["token"] == token

private fun fsResolve(rootPath: String, relative: String): File? {
    if (relative.contains("..") || File(relative).isAbsolute || Regex("^[A-Za-z]:").containsMatchIn(relative)) {
        return null
    }
    return runCatching {
        val root = File(rootPath).canonicalFile
        val target = File(root, relative).canonicalFile
        if (!root.isDirectory || (target != root && !target.path.startsWith(root.path + File.separator))) null else target
    }.getOrNull()
}

private fun fsRelative(root: File, file: File): String =
    file.canonicalFile.toRelativeString(root).replace(File.separatorChar, '/')

private fun fsListJson(root: SharedRoot, relative: String, query: String?): ByteArray? {
    val base = fsResolve(root.path, relative) ?: return null
    if (!base.isDirectory) return null
    val dirs = mutableListOf<FsDirEntry>()
    val files = mutableListOf<FsFileEntry>()
    if (query.isNullOrBlank()) {
        (base.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach { file ->
                val path = fsRelative(base, file)
                if (file.isDirectory) dirs += FsDirEntry(file.name, path)
                else if (file.isFile) files += FsFileEntry(file.name, file.length(), path)
            }
    } else {
        base.walkTopDown()
            .onFail { _, _ -> }
            .forEach { file ->
                if (file == base || !file.name.contains(query, ignoreCase = true)) return@forEach
                val path = fsRelative(base, file)
                if (file.isDirectory) dirs += FsDirEntry(file.name, path)
                else if (file.isFile) files += FsFileEntry(file.name, file.length(), path)
            }
    }
    return ClipProtocol.encode(
            FsListEntry(dirs.sortedBy { it.path }, files.sortedBy { it.path })
    ).toByteArray(Charsets.UTF_8)
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

private fun respondLan(
    output: OutputStream,
    status: String,
    body: ByteArray,
    contentType: String? = null,
) {
    val type = if (contentType == null) "" else "Content-Type: $contentType\r\n"
    output.write(("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\n${type}Connection: close\r\n\r\n").toByteArray())
    output.write(body)
    output.flush()
}

