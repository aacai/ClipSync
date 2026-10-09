package zhiqiu.app.cs.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.QoS
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.files.FileCrypto
import zhiqiu.app.cs.files.FileClip
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FilePayload
import zhiqiu.app.cs.files.FileRejectReason
import zhiqiu.app.cs.files.FileTransferClient
import zhiqiu.app.cs.files.createSharedHttpClient
import zhiqiu.app.cs.files.sanitizeFileName
import zhiqiu.app.cs.files.validateOutgoingFile

/**
 * Clipboard room: connects to the broker, keeps the room namespace subscribed, decrypts incoming
 * clips and publishes local ones. All clipboard content is end-to-end encrypted with the room
 * key derived from the room code (+ optional password).
 */
class ClipSyncEngine(private val scope: CoroutineScope) {

    data class Config(
        val roomId: String,
        val password: String = "",
        val deviceId: String,
        val deviceName: String,
        /** 文件/图片剪贴板的历史仓库（注入以便测试与多端复用） */
        val repository: ClipRepository? = null,
        /** 文件传输客户端；null 时用默认的 litterbox */
        val transferClient: FileTransferClient? = null,
    )

    data class ClipEvent(
        val mid: String,
        val ts: Long,
        val text: String,
        val senderId: String,
        val senderName: String,
        /** 文件条目（kind = file 时非空） */
        val file: FileClip? = null,
    )

    sealed interface Status {
        data object Idle : Status
        data object Connecting : Status
        data class Online(val roomHash: String, val safetyNumber: String) : Status
        data class Offline(val reason: String?) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _clips = MutableSharedFlow<ClipEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val clips: SharedFlow<ClipEvent> = _clips.asSharedFlow()

    private val _devices = MutableStateFlow<List<DevicePresence>>(emptyList())
    val devices: StateFlow<List<DevicePresence>> = _devices.asStateFlow()

    /** 文件收发进度：上传中 / 下载中 / 失败原因 / null = 空闲 */
    data class TransferProgress(
        val direction: Direction,
        val done: Long,
        val total: Long,
        val name: String,
    ) {
        enum class Direction { Upload, Download }
        val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private val _transfer = MutableStateFlow<TransferProgress?>(null)
    val transfer: StateFlow<TransferProgress?> = _transfer.asStateFlow()

    private val _fileErrors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val fileErrors: SharedFlow<String> = _fileErrors.asSharedFlow()

    private var repository: ClipRepository? = null
    private var transferClient: FileTransferClient? = null
    private var fileCrypto: FileCrypto? = null
    private var client: MqttClient? = null
    private var crypto: RoomCrypto? = null
    private var config: Config? = null
    private var roomHash: String = ""
    private var heartbeatJob: Job? = null

    suspend fun start(config: Config) {
        stop()
        this.config = config
        _devices.value = emptyList()
        _status.value = Status.Connecting
        try {
            val hash = ClipProtocol.roomHash(config.roomId.trim())
            val roomCrypto = RoomCrypto.open(config.roomId, config.password)
            roomHash = hash
            crypto = roomCrypto
            this.repository = config.repository
            transferClient = config.transferClient ?: FileTransferClient(createSharedHttpClient())
            fileCrypto = FileCrypto(roomCrypto)
            config.repository?.load()

            val mqtt = MqttClient(clientId(config, hash)) {
                username = MqttSecrets.USERNAME
                password(MqttSecrets.PASSWORD)
                transportFactory = mqttTransportFactory()
                keepAliveSeconds = 45
                cleanStart = true
                will {
                    topic = ClipProtocol.presenceTopic(hash, config.deviceId)
                    payload(ClipProtocol.encode(PresenceEnvelope(
                        dev = config.deviceId,
                        name = config.deviceName,
                        online = false,
                        ts = now(),
                    )))
                    qos = QoS.AT_LEAST_ONCE
                }
            }
            client = mqtt
            mqtt.connect(mqttEndpoint())

            scope.launch {
                mqtt.messages.collect { message -> onMessage(message.topic, message.payloadAsString()) }
            }
            // noLocal: the broker does not echo our own publishes back to us (MQTT 5 feature).
            mqtt.subscribe(ClipProtocol.roomFilter(hash), QoS.AT_LEAST_ONCE, noLocal = true)
            publishPresence(online = true)

            val safety = roomCrypto.safetyNumber()
            _status.value = Status.Online(hash, safety)
            startHeartbeat(config, hash)
        } catch (e: Exception) {
            _status.value = Status.Offline(e.message ?: e::class.simpleName)
            stop()
            throw e
        }
    }

    suspend fun publishText(text: String): ClipItem {
        val cfg = config ?: error("engine not started")
        val roomCrypto = crypto ?: error("engine not started")
        val payload = ClipProtocol.encode(ClipPayload(text = text, len = text.length))
        val envelope = ClipEnvelope(
            mid = sha256Hex(payload.encodeToByteArray() + cfg.deviceId.encodeToByteArray()).take(16),
            ts = now(),
            dev = cfg.deviceId,
            name = cfg.deviceName,
            kind = ClipProtocol.KIND_TEXT,
            ct = roomCrypto.encrypt(payload.encodeToByteArray(), ClipProtocol.clipTopic(roomHash).encodeToByteArray()),
        )
        client?.publish(
            topic = ClipProtocol.clipTopic(roomHash),
            payload = ClipProtocol.encode(envelope),
            qos = QoS.AT_LEAST_ONCE,
        )
        val item = ClipItem(
            id = envelope.mid,
            kind = ClipRepository.KIND_TEXT,
            ts = envelope.ts,
            senderId = envelope.dev,
            senderName = envelope.name,
            text = text,
        )
        addToHistory(item)
        return item
    }

    /**
     * 发布一个文件/图片：本地校验 → 加密 → 上传 → 广播元信息。
     * 返回 null 表示被本地规则拒绝（超限/空文件）。
     */
    suspend fun publishFile(name: String, mime: String, bytes: ByteArray): ClipItem? {
        val cfg = config ?: error("engine not started")
        val validator = validateOutgoingFile(name, bytes.size.toLong(), bytes.size)
        if (validator != null) {
            _fileErrors.emit(rejectMessage(validator))
            return null
        }
        val safeName = sanitizeFileName(name)
        val crypto = fileCrypto ?: error("engine not started")
        val transfer = transferClient ?: error("engine not started")

        val encrypted = runCatching {
            crypto.encrypt(safeName, mime, bytes)
        }.getOrElse { e ->
            _fileErrors.emit("加密失败：${e.message}")
            return null
        }

        val uploadName = FileCrypto.uploadFileName()
        val link = runCatching {
            transfer.upload(uploadName, encrypted.cipher) { sent, total ->
                _transfer.value = TransferProgress(TransferProgress.Direction.Upload, sent, total, safeName)
            }
        }.getOrElse { e ->
            _transfer.value = null
            _fileErrors.emit("上传失败：${e.message}")
            return null
        }
        _transfer.value = null

        val mid = sha256Hex(safeName.encodeToByteArray() + bytes.size.toString().encodeToByteArray() + cfg.deviceId.encodeToByteArray()).take(16)
        val file = FileClip(
            id = mid,
            name = safeName,
            mime = mime,
            size = bytes.size.toLong(),
            sha256 = encrypted.sha256,
            link = link,
            expiresAt = expiryFrom(FileLimits.DEFAULT_EXPIRES),
            ts = now(),
            senderId = cfg.deviceId,
            senderName = cfg.deviceName,
        )
        publishFileEnvelope(file)
        val item = ClipItem(
            id = mid,
            kind = ClipRepository.KIND_FILE,
            ts = file.ts,
            senderId = file.senderId,
            senderName = file.senderName,
            text = file.name,
            file = file,
        )
        addToHistory(item)
        return item
    }

    /** 下载、校验、解密并缓存一个文件条目（自己上传的条目也会走这里以填充 localPath）。 */
    suspend fun fetchFile(file: FileClip): ByteArray? {
        val crypto = fileCrypto ?: return null
        val transfer = transferClient ?: return null
        if (file.size > FileLimits.MAX_FILE_BYTES) {
            _fileErrors.emit("文件超过 50MB，暂不支持：${file.name}")
            return null
        }
        val cipher = runCatching {
            transfer.download(file.link) { received, total ->
                _transfer.value = TransferProgress(TransferProgress.Direction.Download, received, total, file.name)
            }
        }.getOrElse { e ->
            _transfer.value = null
            _fileErrors.emit("下载失败：${e.message}")
            return null
        }
        _transfer.value = null

        val decrypted = runCatching { crypto.decrypt(file, cipher) }.getOrElse { e ->
            _fileErrors.emit("文件校验/解密失败：${e.message}")
            return null
        }
        val localPath = runCatching { repository?.writeCache(file.id, file.name, decrypted.bytes) }.getOrNull()
        if (localPath != null) repository?.markCached(file.id, localPath)
        _clips.emit(
            ClipEvent(
                mid = file.id,
                ts = file.ts,
                text = file.name,
                senderId = file.senderId,
                senderName = file.senderName,
                file = file,
            ),
        )
        return decrypted.bytes
    }

    private suspend fun publishFileEnvelope(file: FileClip) {
        val payload = ClipProtocol.encode(
            FilePayload(
                name = file.name,
                mime = file.mime,
                size = file.size,
                sha256 = file.sha256,
                link = file.link,
                expiresAt = file.expiresAt,
            ),
        )
        val wire = crypto?.encryptBytes(payload.encodeToByteArray(), ClipProtocol.clipTopic(roomHash).encodeToByteArray())
            ?: return
        val envelope = ClipEnvelope(
            mid = file.id,
            ts = file.ts,
            dev = file.senderId,
            name = file.senderName,
            kind = ClipProtocol.KIND_FILE,
            ct = FileCrypto.encodeCipher(wire),
        )
        client?.publish(
            topic = ClipProtocol.clipTopic(roomHash),
            payload = ClipProtocol.encode(envelope),
            qos = QoS.AT_LEAST_ONCE,
        )
    }

    private suspend fun addToHistory(item: ClipItem) {
        repository?.add(item)
    }

    suspend fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        val current = config
        if (current != null && _status.value is Status.Online) {
            runCatching { publishPresence(current, roomHash, online = false) }
        }
        runCatching { client?.close() }
        client = null
        crypto?.wipe()
        crypto = null
        config = null
        if (_status.value !is Status.Offline) _status.value = Status.Idle
    }

    // --- internals ---

    private fun clientId(config: Config, hash: String) = "cs-$hash-${config.deviceId}"

    private suspend fun onMessage(topic: String, payload: String) {
        when (ClipProtocol.parseTopic(topic)?.channel) {
            ClipProtocol.CH_CLIP -> onClip(topic, payload)
            ClipProtocol.CH_PRESENCE -> onPresence(topic, payload)
            ClipProtocol.CH_SYNC -> Unit // history backfill lands in M2
            else -> Unit
        }
    }

    private suspend fun onClip(topic: String, raw: String) {
        val roomCrypto = crypto ?: return
        val envelope = runCatching { ClipProtocol.decode<ClipEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.v != ClipProtocol.PROTOCOL_VERSION) return
        val cfg = config ?: return
        if (envelope.dev == cfg.deviceId) return

        if (envelope.kind == ClipProtocol.KIND_FILE) {
            onFileClip(envelope, roomCrypto)
            return
        }

        val decrypted = runCatching {
            roomCrypto.decrypt(envelope.ct, topic.encodeToByteArray()).decodeToString()
        }.getOrElse { return }
        val item = runCatching { ClipProtocol.decode<ClipPayload>(decrypted) }.getOrNull() ?: return
        addToHistory(
            ClipItem(
                id = envelope.mid,
                kind = ClipRepository.KIND_TEXT,
                ts = envelope.ts,
                senderId = envelope.dev,
                senderName = envelope.name,
                text = item.text,
            ),
        )
        _clips.emit(ClipEvent(
            mid = envelope.mid,
            ts = envelope.ts,
            text = item.text,
            senderId = envelope.dev,
            senderName = envelope.name,
        ))
    }

    /** 收到文件元信息：入历史，并可后台下载解密缓存（失败只在 fileErrors 报，不打断界面）。 */
    private suspend fun onFileClip(envelope: ClipEnvelope, roomCrypto: RoomCrypto) {
        val wire = runCatching { FileCrypto.decodeCipher(envelope.ct) }.getOrNull() ?: return
        val json = runCatching {
            roomCrypto.decryptBytes(wire, ClipProtocol.clipTopic(roomHash).encodeToByteArray()).decodeToString()
        }.getOrElse { return }
        val payload = runCatching { ClipProtocol.decode<FilePayload>(json) }.getOrNull() ?: return
        if (payload.size > FileLimits.MAX_FILE_BYTES) {
            _fileErrors.emit("收到超过 50MB 的文件，已忽略：${payload.name}")
            return
        }
        val file = FileClip(
            id = envelope.mid,
            name = sanitizeFileName(payload.name),
            mime = payload.mime,
            size = payload.size,
            sha256 = payload.sha256,
            link = payload.link,
            expiresAt = payload.expiresAt,
            ts = envelope.ts,
            senderId = envelope.dev,
            senderName = envelope.name,
        )
        val known = repository?.items?.value?.any { it.id == file.id } == true
        addToHistory(
            ClipItem(
                id = file.id,
                kind = ClipRepository.KIND_FILE,
                ts = file.ts,
                senderId = file.senderId,
                senderName = file.senderName,
                text = file.name,
                file = file,
            ),
        )
        if (!known) fetchFile(file)
    }

    private fun onPresence(topic: String, raw: String) {
        val cfg = config ?: return
        val parsed = ClipProtocol.parseTopic(topic) ?: return
        if (parsed.deviceId == cfg.deviceId) return
        val envelope = runCatching { ClipProtocol.decode<PresenceEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.dev == cfg.deviceId) return
        val existing = _devices.value.associateBy { it.id }.toMutableMap()
        existing[envelope.dev] = DevicePresence(
            id = envelope.dev,
            name = envelope.name,
            online = envelope.online,
            lastSeen = envelope.ts,
        )
        _devices.value = existing.values.sortedBy { it.name }
    }

    private suspend fun publishPresence(online: Boolean) {
        val cfg = config ?: return
        publishPresence(cfg, roomHash, online)
    }

    private suspend fun publishPresence(cfg: Config, hash: String, online: Boolean) {
        val mqtt = client ?: return
        val envelope = PresenceEnvelope(dev = cfg.deviceId, name = cfg.deviceName, online = online, ts = now())
        runCatching {
            mqtt.publish(
                topic = ClipProtocol.presenceTopic(hash, cfg.deviceId),
                payload = ClipProtocol.encode(envelope),
                qos = QoS.AT_LEAST_ONCE,
            )
        }
    }

    private fun startHeartbeat(cfg: Config, hash: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(PRESENCE_INTERVAL_MS)
                if (!isActive) break
                if (client?.connectionState?.value?.let { it is org.meshtastic.mqtt.ConnectionState.Connected } == true) {
                    publishPresence(cfg, hash, online = true)
                }
            }
        }
    }

    private fun now(): Long = currentTimeMillis()

    companion object {
        private const val PRESENCE_INTERVAL_MS = 25_000L
        private val EXPIRY_HOURS = mapOf(
            "1h" to 1L,
            "12h" to 12L,
            "24h" to 24L,
            "72h" to 72L,
        )

        internal fun expiryFrom(expires: String, nowMs: Long = currentTimeMillis()): Long =
            nowMs + (EXPIRY_HOURS[expires.lowercase()] ?: 72L) * 3_600_000L

        internal fun rejectMessage(reason: FileRejectReason): String = when (reason) {
            FileRejectReason.TooLarge -> "文件超过 ${FileLimits.MAX_FILE_BYTES / 1024 / 1024}MB，暂不支持"
            FileRejectReason.Empty -> "空文件无法分享"
            is FileRejectReason.InvalidName -> "文件名不合法：${reason.detail}"
        }
    }
}
@OptIn(ExperimentalTime::class)
private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
