package zhiqiu.app.cs.core

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One clipboard item as it travels over the wire (the whole envelope is encrypted before send). */
@Serializable
data class ClipEnvelope(
    val v: Int = ClipProtocol.PROTOCOL_VERSION,
    val mid: String,
    val ts: Long,
    val dev: String,
    val name: String,
    val kind: String,
    /** base64(iv || ciphertext || tag) over the JSON-encoded [ClipPayload] */
    val ct: String,
)

@Serializable
data class ClipPayload(
    val text: String,
    val len: Int,
)

@Serializable
data class PresenceEnvelope(
    val v: Int = ClipProtocol.PROTOCOL_VERSION,
    val dev: String,
    val name: String,
    val online: Boolean,
    val ts: Long,
)

data class DevicePresence(
    val id: String,
    val name: String,
    val online: Boolean,
    val lastSeen: Long,
)

/** Parsed room topic: cs/v1/<roomHash>/<channel>[/<deviceId>]. */
data class RoomTopic(
    val roomHash: String,
    val channel: String,
    val deviceId: String? = null,
)

object ClipProtocol {
    const val PROTOCOL_VERSION = 1
    const val ROOT = "cs/v1"

    /** Channels inside a room. Presence is per device: presence/<deviceId>. */
    const val CH_CLIP = "clip"
    const val CH_PRESENCE = "presence"
    const val CH_SYNC = "sync"

    /** Envelope kinds: a plain text clip, or a file/image whose bytes live on a host. */
    const val KIND_TEXT = "text"
    const val KIND_FILE = "file"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decode(raw: String): T = json.decodeFromString(raw)

    // --- topics ---

    /** 8 hex chars of SHA-256(roomId): the (public) namespace a room lives in. */
    suspend fun roomHash(roomId: String): String = sha256Hex(roomId.encodeToByteArray()).take(8)

    fun clipTopic(roomHash: String): String = "$ROOT/$roomHash/$CH_CLIP"

    fun presenceTopic(roomHash: String, deviceId: String): String = "$ROOT/$roomHash/$CH_PRESENCE/$deviceId"

    fun syncTopic(roomHash: String): String = "$ROOT/$roomHash/$CH_SYNC"

    fun roomFilter(roomHash: String): String = "$ROOT/$roomHash/#"

    /** Parses cs/v1/<roomHash>/<channel>[/<deviceId>]; null when the shape does not match. */
    fun parseTopic(topic: String): RoomTopic? {
        val prefix = ROOT.split('/')
        val parts = topic.split('/')
        if (parts.size <= prefix.size + 1) return null
        if (parts.subList(0, prefix.size) != prefix) return null
        val roomHash = parts[prefix.size]
        return when (val channel = parts[prefix.size + 1]) {
            CH_CLIP, CH_SYNC ->
                if (parts.size == prefix.size + 2) RoomTopic(roomHash, channel) else null

            CH_PRESENCE ->
                if (parts.size == prefix.size + 3) RoomTopic(roomHash, channel, parts[prefix.size + 2]) else null

            else -> null
        }
    }
}

internal suspend fun sha256(bytes: ByteArray): ByteArray =
    CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)

internal suspend fun sha256Hex(bytes: ByteArray): String =
    sha256(bytes).joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

/** Short human-verifiable fingerprint of the shared room key. */
suspend fun safetyNumber(roomKey: ByteArray): String =
    sha256(roomKey).take(6).joinToString(" ") { byte ->
        ((byte.toInt() and 0xFF) % 1000).toString().padStart(3, '0')
    }
