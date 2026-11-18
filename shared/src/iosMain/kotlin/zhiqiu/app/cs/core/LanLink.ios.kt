package zhiqiu.app.cs.core

internal actual fun lanBlobDir(): String = ""

@Suppress("UNUSED_PARAMETER")
internal actual fun writeLanBlob(name: String, bytes: ByteArray): Boolean = false

@Suppress("UNUSED_PARAMETER")
internal actual fun cleanupTempFiles(maxAgeMs: Long): Int = 0

@Suppress("UNUSED_PARAMETER")
internal actual fun createLanServer(
    port: Int,
    identity: LanIdentity,
    roomHash: String,
    blobDir: String,
    fsToken: String,
    fsRoots: List<SharedRoot>,
    onClip: (String) -> Unit,
    onPeer: (LanBeacon) -> Unit,
): LanServer? = null
