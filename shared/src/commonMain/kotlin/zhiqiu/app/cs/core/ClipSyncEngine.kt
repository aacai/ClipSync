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
import org.meshtastic.mqtt.ConnectionState
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.files.FileCrypto
import zhiqiu.app.cs.files.FileClip
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FilePayload
import zhiqiu.app.cs.files.FileTransferClient
import zhiqiu.app.cs.files.createSharedHttpClient
import zhiqiu.app.cs.files.sanitizeFileName
import zhiqiu.app.cs.files.validateOutgoingFile

internal class ClipSyncEngine(
    private val scope: CoroutineScope,
    private val links: MqttLinkFactory = DefaultMqttLinks,
) {

    data class Config(
        val roomId: String,
        val password: String = "",
        /** 非空 = 用上次保存的派生密钥恢复会话，跳过 KDF，此时 [password] 不起作用 */
        val roomKey: ByteArray? = null,
        val deviceId: String,
        val deviceName: String,
        val deviceOs: String,
        val repository: ClipRepository? = null,
    )

    data class ClipEvent(
        val text: String,
        val senderName: String,
        val file: FileClip? = null,
    )

    sealed interface Status {
        data object Idle : Status
        data object Connecting : Status
        data class Online(val safetyNumber: String) : Status
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

    /** 同屋里密钥指纹对不上的设备名：多半是某端房间密码打错，两边解不开彼此的密文。 */
    private val _keyMismatch = MutableStateFlow<List<String>>(emptyList())
    val keyMismatch: StateFlow<List<String>> = _keyMismatch.asStateFlow()

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

    private val _fileIssues = MutableSharedFlow<FileIssue>(extraBufferCapacity = 16)
    val fileIssues: SharedFlow<FileIssue> = _fileIssues.asSharedFlow()

    private var repository: ClipRepository? = null
    private var transferClient: FileTransferClient? = null
    private var fileCrypto: FileCrypto? = null
    private var link: MqttLink? = null
    private var crypto: RoomCrypto? = null

    /** 用户想待在的房间：连接断了不改它，重连成功后照原样续上，退出房间才清空。 */
    private var desired: Config? = null
    private var roomHash: String = ""
    private var safetyPrefix: String = ""
    private var safety: String = ""
    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null
    private var collectorJob: Job? = null
    private var recoveryJob: Job? = null
    private var backoffMs = RECOVERY_BASE_MS

    /** 已派生密钥的副本，供上层持久化；用完请自行清零。 */
    fun roomKeyMaterial(): ByteArray? = crypto?.keyMaterial()

    suspend fun start(config: Config) {
        stop()
        desired = config
        _devices.value = emptyList()
        _status.value = Status.Connecting
        try {
            val hash = ClipProtocol.roomHash(config.roomId.trim())
            val roomCrypto = config.roomKey?.let { RoomCrypto.restore(it) }
                ?: RoomCrypto.open(config.roomId, config.password)
            roomHash = hash
            crypto = roomCrypto
            this.repository = config.repository
            transferClient = FileTransferClient(createSharedHttpClient())
            fileCrypto = FileCrypto(roomCrypto)
            config.repository?.load()
            safety = roomCrypto.safetyNumber()
            safetyPrefix = ClipProtocol.safetyPrefix(safety)
        } catch (e: Exception) {
            // 房间本身没打开（密钥坏了之类），重连也救不回来，直接报错
            stop()
            _status.value = Status.Offline(e.message ?: e::class.simpleName)
            throw e
        }
        try {
            openLink()
        } catch (e: Exception) {
            closeLink()
            _status.value = Status.Offline(e.message ?: e::class.simpleName)
            armRecovery()
            throw e
        }
    }

    /** 开一条新会话并把它接上：连上、订阅、报 presence，之后由 watchConnection 盯状态。 */
    private suspend fun openLink() {
        val cfg = desired ?: return
        val mqtt = links.create(cfg, roomHash, safetyPrefix)
        link = mqtt
        mqtt.connect()

        collectorJob = scope.launch {
            mqtt.messages.collect { message -> onMessage(message.topic, message.payload) }
        }
        watchConnection(mqtt)
        mqtt.subscribe(ClipProtocol.roomFilter(roomHash))
        publishPresence(online = true)

        _status.value = Status.Online(safety)
        backoffMs = RECOVERY_BASE_MS
        startHeartbeat()
    }

    /** 改昵称：只换署名，连接与密钥都不动，立刻重播 presence，同伴不必等下一次心跳。 */
    suspend fun renameDevice(name: String) {
        val cfg = desired ?: return
        if (name.isBlank() || name == cfg.deviceName) return
        desired = cfg.copy(deviceName = name)
        publishPresence(online = true)
    }

    suspend fun publishText(text: String): ClipItem {
        val cfg = desired ?: error("engine not started")
        val roomCrypto = crypto ?: error("engine not started")
        val payload = ClipProtocol.encode(ClipPayload(text = text, len = text.length))
        val envelope = ClipEnvelope(
            mid = sha256Hex(payload.encodeToByteArray() + cfg.deviceId.encodeToByteArray()).take(16),
            ts = now(),
            dev = cfg.deviceId,
            name = cfg.deviceName,
            kind = ClipRepository.KIND_TEXT,
            ct = roomCrypto.encrypt(payload.encodeToByteArray(), ClipProtocol.clipTopic(roomHash).encodeToByteArray()),
        )
        val item = ClipItem(
            id = envelope.mid,
            kind = ClipRepository.KIND_TEXT,
            ts = envelope.ts,
            senderId = envelope.dev,
            senderName = envelope.name,
            text = text,
        )
        // 先进历史再上线：连接正在死时 publish 会抛，那条复制不能因为发不出去就当没发生过。
        addToHistory(item)
        link?.publish(topic = ClipProtocol.clipTopic(roomHash), payload = ClipProtocol.encode(envelope))
        return item
    }

    /**
     * 发布一个文件/图片：本地校验 → 加密 → 上传 → 广播元信息。
     * 返回 null 表示被本地规则拒绝（超限/空文件）。
     */
    suspend fun publishFile(name: String, mime: String, bytes: ByteArray): ClipItem? {
        val cfg = desired ?: error("engine not started")
        val validator = validateOutgoingFile(name, bytes.size.toLong(), bytes.size)
        if (validator != null) {
            _fileIssues.emit(FileIssue.Rejected(validator, name))
            return null
        }
        val safeName = sanitizeFileName(name)
        val crypto = fileCrypto ?: error("engine not started")
        val transfer = transferClient ?: error("engine not started")

        val encrypted = runCatching {
            crypto.encrypt(safeName, mime, bytes)
        }.getOrElse { e ->
            _fileIssues.emit(FileIssue.EncryptFailed(safeName, e.message))
            return null
        }

        val uploadName = FileCrypto.uploadFileName()
        val link = runCatching {
            transfer.upload(uploadName, encrypted.cipher) { sent, total ->
                _transfer.value = TransferProgress(TransferProgress.Direction.Upload, sent, total, safeName)
            }
        }.getOrElse { e ->
            _transfer.value = null
            _fileIssues.emit(FileIssue.UploadFailed(safeName, e.message))
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
        publishFileEnvelope(file)
        return item
    }

    suspend fun fetchFile(file: FileClip): ByteArray? {
        val crypto = fileCrypto ?: return null
        val transfer = transferClient ?: return null
        if (file.size > FileLimits.MAX_FILE_BYTES) {
            _fileIssues.emit(FileIssue.TooLargeIncoming(file.name))
            return null
        }
        val cipher = runCatching {
            transfer.download(file.link) { received, total ->
                _transfer.value = TransferProgress(TransferProgress.Direction.Download, received, total, file.name)
            }
        }.getOrElse { e ->
            _transfer.value = null
            _fileIssues.emit(FileIssue.DownloadFailed(file.name, e.message))
            return null
        }
        _transfer.value = null

        val decrypted = runCatching { crypto.decrypt(file, cipher) }.getOrElse { e ->
            _fileIssues.emit(FileIssue.DecryptFailed(file.name, e.message))
            return null
        }
        val localPath = runCatching { repository?.writeCache(file.id, file.name, decrypted.bytes) }.getOrNull()
        if (localPath != null) repository?.markCached(file.id, localPath)
        _clips.emit(ClipEvent(text = file.name, senderName = file.senderName, file = file))
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
            kind = ClipRepository.KIND_FILE,
            ct = FileCrypto.encodeCipher(wire),
        )
        link?.publish(topic = ClipProtocol.clipTopic(roomHash), payload = ClipProtocol.encode(envelope))
    }

    private suspend fun addToHistory(item: ClipItem) {
        repository?.add(item)
    }

    suspend fun stop() {
        desired = null
        recoveryJob?.cancel()
        recoveryJob = null
        backoffMs = RECOVERY_BASE_MS
        if (_status.value is Status.Online) {
            runCatching { publishPresence(online = false) }
        }
        closeLink()
        crypto?.wipe()
        crypto = null
        safety = ""
        safetyPrefix = ""
        _keyMismatch.value = emptyList()
        _status.value = Status.Idle
    }

    private suspend fun closeLink() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        connectionJob?.cancel()
        connectionJob = null
        collectorJob?.cancel()
        collectorJob = null
        val current = link
        link = null
        runCatching { current?.close() }
    }

    /**
     * 库自己的重连只管同一条连接；首次就没连上、或被 broker 明确拒掉时，这里接手按退避再开一条。
     * 房间密钥还在手上，所以续上的是原来那个房间，不用重新输密码。
     */
    private fun armRecovery() {
        if (recoveryJob?.isActive == true) return
        recoveryJob = scope.launch {
            while (isActive && desired != null && _status.value !is Status.Online) {
                delay(backoffMs)
                // 等的这段时间里库自己连上了，就别去掐断一条好连接
                if (desired == null || _status.value is Status.Online) continue
                backoffMs = (backoffMs * 2).coerceAtMost(RECOVERY_MAX_MS)
                closeLink()
                runCatching { openLink() }.onFailure {
                    _status.value = Status.Offline(it.message ?: it::class.simpleName)
                }
            }
        }
    }

    private suspend fun onMessage(topic: String, payload: String) {
        when (ClipProtocol.parseTopic(topic)?.channel) {
            ClipProtocol.CH_CLIP -> onClip(topic, payload)
            ClipProtocol.CH_PRESENCE -> onPresence(topic, payload)
            ClipProtocol.CH_SYNC -> Unit
            else -> Unit
        }
    }

    private suspend fun onClip(topic: String, raw: String) {
        val roomCrypto = crypto ?: return
        val envelope = runCatching { ClipProtocol.decode<ClipEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.v != ClipProtocol.PROTOCOL_VERSION) return
        val cfg = desired ?: return
        if (envelope.dev == cfg.deviceId) return

        if (envelope.kind == ClipRepository.KIND_FILE) {
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
        _clips.emit(ClipEvent(text = item.text, senderName = envelope.name))
    }

    /** 收到文件元信息：入历史，并可后台下载解密缓存（失败只在 fileErrors 报，不打断界面）。 */
    private suspend fun onFileClip(envelope: ClipEnvelope, roomCrypto: RoomCrypto) {
        val wire = runCatching { FileCrypto.decodeCipher(envelope.ct) }.getOrNull() ?: return
        val json = runCatching {
            roomCrypto.decryptBytes(wire, ClipProtocol.clipTopic(roomHash).encodeToByteArray()).decodeToString()
        }.getOrElse { return }
        val payload = runCatching { ClipProtocol.decode<FilePayload>(json) }.getOrNull() ?: return
        if (payload.size > FileLimits.MAX_FILE_BYTES) {
            _fileIssues.emit(FileIssue.TooLargeIncoming(payload.name))
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

    private suspend fun onPresence(topic: String, raw: String) {
        val cfg = desired ?: return
        val parsed = ClipProtocol.parseTopic(topic) ?: return
        if (parsed.deviceId == cfg.deviceId) return
        val envelope = runCatching { ClipProtocol.decode<PresenceEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.dev == cfg.deviceId) return
        val existing = _devices.value.associateBy { it.id }.toMutableMap()
        val fresh = envelope.dev !in existing
        if (envelope.online) {
            existing[envelope.dev] =
                    DevicePresence(id = envelope.dev, name = envelope.name, safety = envelope.sp, os = envelope.os)
        } else {
            existing.remove(envelope.dev)
        }
        _devices.value = existing.values.sortedBy { it.name }
        _keyMismatch.value = _devices.value
            .filter { it.safety.isNotEmpty() && it.safety != safetyPrefix }
            .map { it.name }
        // 头一回见到就自报一次：新来的这才刚订阅上，错过我们上次的心跳就看不见彼此。
        // 有了这条，心跳只当兜底，不用刷得很勤。
        if (fresh && envelope.online) publishPresence(online = true)
    }

    private suspend fun publishPresence(online: Boolean) {
        val cfg = desired ?: return
        val mqtt = link ?: return
        val envelope = PresenceEnvelope(
            dev = cfg.deviceId,
            name = cfg.deviceName,
            os = cfg.deviceOs,
            online = online,
            ts = now(),
            sp = safetyPrefix,
        )
        runCatching {
            mqtt.publish(
                topic = ClipProtocol.presenceTopic(roomHash, cfg.deviceId),
                payload = ClipProtocol.encode(envelope),
            )
        }
    }

    /**
     * 底层连接状态才是真相：断线重连期间界面要说“连接中”，别继续挂着“已连接”。
     * 重连成功后补一条 presence，否则同伴要等遗嘱过期才发现我们还在。
     */
    private fun watchConnection(mqtt: MqttLink) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            mqtt.connectionState.collect { state ->
                when {
                    state is ConnectionState.Connected && _status.value !is Status.Online -> {
                        _status.value = Status.Online(safety)
                        backoffMs = RECOVERY_BASE_MS
                        publishPresence(online = true)
                    }

                    (state is ConnectionState.Connecting || state is ConnectionState.Reconnecting) &&
                            _status.value is Status.Online -> _status.value = Status.Connecting

                    state is ConnectionState.Disconnected && state.reason != null &&
                            desired != null -> {
                        _status.value = Status.Offline(state.reason?.message)
                        // 库到这里已经不试了，接着试归恢复循环管
                        armRecovery()
                    }
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(PRESENCE_INTERVAL_MS)
                if (!isActive) break
                if (link?.connectionState?.value is ConnectionState.Connected) {
                    publishPresence(online = true)
                }
            }
        }
    }

    private fun now(): Long = currentTimeMillis()

    companion object {
        // 心跳只是兜底（丢了一条 presence 时的补救），发现新同伴时会立刻自报一次。
        // 120s 正好是 keepalive 60s 的整数倍：两次唤醒叠在一起，无线电不用额外醒。
        private const val PRESENCE_INTERVAL_MS = 120_000L
        private const val RECOVERY_BASE_MS = 5_000L
        private const val RECOVERY_MAX_MS = 60_000L
        private val EXPIRY_HOURS = mapOf(
            "1h" to 1L,
            "12h" to 12L,
            "24h" to 24L,
            "72h" to 72L,
        )

        internal fun expiryFrom(expires: String, nowMs: Long = currentTimeMillis()): Long =
            nowMs +
                    (EXPIRY_HOURS[expires.lowercase()] ?: EXPIRY_HOURS.getValue(FileLimits.DEFAULT_EXPIRES)) *
                            3_600_000L
    }
}
@OptIn(ExperimentalTime::class)
internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
