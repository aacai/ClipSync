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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val clock: () -> Long = { currentTimeMillis() },
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

    /** 解不开对方东西的那些：显示昵称，认不出人时退回设备号前缀。多半是某端房间密码打错，钥匙不是同一把。 */
    private val _keyMismatch = MutableStateFlow<List<String>>(emptyList())
    val keyMismatch: StateFlow<List<String>> = _keyMismatch.asStateFlow()

    private val roster = mutableMapOf<String, DevicePresence>()

    private val presenceTs = mutableMapOf<String, Long>()

    /** 解不开的那批：署名先从 clip 信封的明文里补，补不到只剩设备号前缀。 */
    private class Mismatch(val seenAt: Long, var label: String)

    private val unknownKey = mutableMapOf<String, Mismatch>()

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
    private var safety: String = ""
    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null
    private var collectorJob: Job? = null
    private var recoveryJob: Job? = null
    private var backoffMs = RECOVERY_BASE_MS

    /** 「情况可能变了」的那一下：回到前台之类，让退避提前结束，多次信号合成一次。 */
    private val retryRequested = MutableStateFlow(false)

    /** 已派生密钥的副本，供上层持久化；用完请自行清零。 */
    fun roomKeyMaterial(): ByteArray? = crypto?.keyMaterial()

    suspend fun start(config: Config) {
        stop()
        desired = config
        clearRoster()
        _status.value = Status.Connecting
        try {
            val hash = ClipProtocol.roomHash(config.roomId.trim())
            val roomCrypto = config.roomKey?.let { RoomCrypto.restore(it) }
                ?: RoomCrypto.open(config.roomId, config.password)
            roomHash = hash
            crypto = roomCrypto
            this.repository = config.repository
            transferClient = FileTransferClient(createSharedHttpClient())
            fileCrypto = FileCrypto(roomCrypto, ClipProtocol.clipTopic(hash))
            config.repository?.load()
            safety = roomCrypto.safetyNumber()
        } catch (e: Exception) {
            // 房间本身没打开（密钥坏了之类），重连也救不回来，直接报错
            stop()
            _status.value = Status.Offline(e.userFacingDetail().orEmpty())
            throw e
        }
        try {
            openLink()
        } catch (e: Exception) {
            closeLink()
            _status.value = Status.Offline(e.userFacingDetail().orEmpty())
            armRecovery()
            throw e
        }
    }

    /**
     * 回到前台、网络刚通这类「情况可能变了」的信号：把退避剩下的时间跳过去，立刻试一次。
     * 只在离线且还想待在房间时管用；连接握手进行中不动它，免得同时开出第二条会话。
     */
    fun retryNow() {
        if (desired == null || _status.value !is Status.Offline) return
        retryRequested.value = true
        armRecovery()
    }

    /** 开一条新会话并把它接上：连上、订阅、报 presence，之后由 watchConnection 盯状态。 */
    private suspend fun openLink() {
        val cfg = desired ?: return
        val mqtt = links.create(cfg, roomHash, willWire(cfg))
        link = mqtt
        mqtt.connect()

        collectorJob = scope.launch {
            mqtt.messages.collect { message -> onMessage(message.topic, message.payload) }
        }
        watchConnection(mqtt)
        mqtt.subscribe(ClipProtocol.roomFilter(roomHash))
        publishPresence(online = true)
        rollCall()

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
            mid = ClipProtocol.randomMid(),
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
        val uploaded = upload(name, mime, bytes) ?: return null
        val mid = ClipProtocol.randomMid()
        val file = FileClip(
            id = mid,
            name = uploaded.name,
            mime = mime,
            size = bytes.size.toLong(),
            sha256 = uploaded.sha256,
            link = uploaded.link,
            expiresAt = uploaded.expiresAt,
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

    /**
     * 把离线攒下的那个文件补传进房间：链接填回原条目，不再多出一条。
     * 返回 false 表示这一步没上传成，原因已经走 [fileIssues] 报出去了。
     */
    suspend fun resend(file: FileClip, bytes: ByteArray): Boolean {
        val uploaded = upload(file.name, file.mime, bytes) ?: return false
        val filled = file.copy(
            name = uploaded.name,
            sha256 = uploaded.sha256,
            link = uploaded.link,
            expiresAt = uploaded.expiresAt,
        )
        publishFileEnvelope(filled)
        repository?.markUploaded(filled.id, filled.link, filled.sha256, filled.expiresAt)
        return true
    }

    /** 加密并上传，只交回「挂在网上」需要的那几样；条目归谁、叫什么 id 由调用方定。 */
    private suspend fun upload(name: String, mime: String, bytes: ByteArray): Uploaded? {
        if (desired == null) error("engine not started")
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
        val uploaded = runCatching {
            transfer.upload(uploadName, encrypted.cipher) { sent, total ->
                _transfer.value = TransferProgress(TransferProgress.Direction.Upload, sent, total, safeName)
            }
        }.getOrElse { e ->
            _transfer.value = null
            _fileIssues.emit(FileIssue.UploadFailed(safeName, e.message))
            return null
        }
        _transfer.value = null
        return Uploaded(safeName, encrypted.sha256, uploaded.url, uploaded.expiresAt(now()))
    }

    /** 上传完成后的元信息：还没决定条目身份。 */
    private data class Uploaded(val name: String, val sha256: String, val link: String, val expiresAt: Long)

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
        // 事件里带上落盘路径：收到即进剪贴板的那条路要用，别让订阅方再去查一遍缓存表
        _clips.emit(
            ClipEvent(
                text = file.name,
                senderName = file.senderName,
                file = file.copy(cached = localPath != null, localPath = localPath),
            )
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
        retryRequested.value = false
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
        _status.value = Status.Idle
    }

    private suspend fun closeLink() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        connectionJob?.cancel()
        connectionJob = null
        collectorJob?.cancel()
        collectorJob = null
        clearRoster()
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
                // 退避等完，或者有人踢一脚，谁先到算谁
                withTimeoutOrNull(backoffMs) { retryRequested.first { it } }
                retryRequested.value = false
                // 等的这段时间里库自己连上了，就别去掐断一条好连接
                if (desired == null || _status.value is Status.Online) continue
                backoffMs = (backoffMs * 2).coerceAtMost(RECOVERY_MAX_MS)
                closeLink()
                runCatching { openLink() }.onFailure {
                    _status.value = Status.Offline(it.userFacingDetail().orEmpty())
                }
            }
        }
    }

    private suspend fun onMessage(topic: String, payload: String) {
        when (ClipProtocol.parseTopic(topic)?.channel) {
            ClipProtocol.CH_CLIP -> onClip(topic, payload)
            ClipProtocol.CH_PRESENCE -> onPresence(topic, payload)
            ClipProtocol.CH_SYNC -> onRollCall(topic, payload)
            else -> Unit
        }
    }

    private suspend fun onClip(topic: String, raw: String) {
        val roomCrypto = crypto ?: return
        val envelope = runCatching { ClipProtocol.decode<ClipEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.v != ClipProtocol.PROTOCOL_VERSION) return
        val cfg = desired ?: return
        if (envelope.dev == cfg.deviceId) return
        if (envelope.name.isNotBlank()) {
            unknownKey[envelope.dev]?.let {
                it.label = envelope.name
                publishRoster()
            }
        }
        markActive(envelope.dev)

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
        val roomCrypto = crypto ?: return
        val parsed = ClipProtocol.parseTopic(topic) ?: return
        val deviceId = parsed.deviceId ?: return
        if (deviceId == cfg.deviceId) return
        val envelope = runCatching { ClipProtocol.decode<PresenceEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.v != ClipProtocol.PROTOCOL_VERSION) return
        val payload = presencePayload(envelope, roomCrypto, topic)
        if (payload == null) {
            // 像一条正经 presence 却解不开：同一间房、不同钥匙，报出来让人去对安全码
            noteUnknownKey(deviceId)
            return
        }
        if (payload.dev == cfg.deviceId) return
        if (unknownKey.remove(deviceId) != null) publishRoster()
        // ts 只排先后：跨设备比时间是错的，过期只看本机收到那一刻
        val last = presenceTs[payload.dev]
        if (last != null && payload.ts < last) return
        presenceTs[payload.dev] = payload.ts
        val fresh = payload.dev !in roster
        if (payload.online) {
            roster[payload.dev] = DevicePresence(
                id = payload.dev,
                name = payload.name,
                os = payload.os,
                seenAt = now(),
            )
        } else {
            roster.remove(payload.dev)
            presenceTs.remove(payload.dev)
            unknownKey.remove(payload.dev)
        }
        pruneRoster()
        publishRoster()
        // 头一回见到就自报一次：新来的这才刚订阅上，错过我们上次的心跳就看不见彼此。
        // 有了这条，心跳只当兜底，不用刷得很勤。
        if (fresh && payload.online) publishPresence(online = true)
    }

    private suspend fun presencePayload(
        envelope: PresenceEnvelope,
        roomCrypto: RoomCrypto,
        topic: String,
    ): PresencePayload? =
        runCatching {
            ClipProtocol.decode<PresencePayload>(
                roomCrypto.decrypt(envelope.ct, topic.encodeToByteArray()).decodeToString()
            )
        }.getOrNull()

    private fun clearRoster() {
        roster.clear()
        presenceTs.clear()
        unknownKey.clear()
        publishRoster()
    }

    private fun pruneRoster() {
        if (roster.isEmpty() && unknownKey.isEmpty()) return
        val cutoff = now() - PRESENCE_TTL_MS
        val gone = roster.filterValues { it.seenAt < cutoff }.keys.toList()
        if (gone.isNotEmpty()) {
            roster.keys.removeAll(gone.toSet())
            presenceTs.keys.removeAll(gone.toSet())
        }
        val stale = unknownKey.filterValues { it.seenAt < cutoff }.keys.toList()
        if (stale.isNotEmpty()) unknownKey.keys.removeAll(stale.toSet())
        if (gone.isNotEmpty() || stale.isNotEmpty()) publishRoster()
    }

    private fun markActive(deviceId: String) {
        val seen = roster[deviceId] ?: return
        roster[deviceId] = seen.copy(seenAt = now())
    }

    /** 往我们频道灌一条像模像样却解不开的 presence，代价是把名单灌到上限为止。 */
    private fun noteUnknownKey(deviceId: String) {
        if (deviceId in unknownKey || unknownKey.size >= MAX_UNKNOWN_KEY_DEVICES) return
        unknownKey[deviceId] = Mismatch(now(), deviceId.take(MISMATCH_ID_CHARS))
        publishRoster()
    }

    private fun publishRoster() {
        _devices.value = roster.values.sortedBy { it.name }
        _keyMismatch.value = unknownKey.values.map { it.label }.sorted().take(MAX_UNKNOWN_KEY_DEVICES)
    }

    /** presence 整条进密文，AAD 用它落进的那条 topic：点名帧在 sync 频道，正常帧在各自的 presence 频道。 */
    private suspend fun presenceWire(topic: String, cfg: Config, online: Boolean, answer: Boolean = false): String? {
        val roomCrypto = crypto ?: return null
        val payload = ClipProtocol.encode(
            PresencePayload(
                dev = cfg.deviceId,
                name = cfg.deviceName,
                os = cfg.deviceOs,
                online = online,
                ts = now(),
                answer = answer,
            ),
        )
        return runCatching {
            ClipProtocol.encode(PresenceEnvelope(ct = roomCrypto.encrypt(payload.encodeToByteArray(), topic.encodeToByteArray())))
        }.getOrNull()
    }

    /** 遗嘱在握手时就定死内容：断线后由 broker 原样发出去，所以这里得先加密好。 */
    private suspend fun willWire(cfg: Config): String {
        val topic = ClipProtocol.presenceTopic(roomHash, cfg.deviceId)
        return presenceWire(topic, cfg, online = false).orEmpty()
    }

    private suspend fun publishPresence(online: Boolean) {
        val cfg = desired ?: return
        val mqtt = link ?: return
        val topic = ClipProtocol.presenceTopic(roomHash, cfg.deviceId)
        val wire = presenceWire(topic, cfg, online) ?: return
        runCatching { mqtt.publish(topic = topic, payload = wire) }
    }

    private suspend fun rollCall() {
        val cfg = desired ?: return
        val mqtt = link ?: return
        val topic = ClipProtocol.syncTopic(roomHash)
        val wire = presenceWire(topic, cfg, online = true, answer = true) ?: return
        runCatching { mqtt.publish(topic = topic, payload = wire) }
    }

    private suspend fun onRollCall(topic: String, raw: String) {
        val cfg = desired ?: return
        val roomCrypto = crypto ?: return
        val envelope = runCatching { ClipProtocol.decode<PresenceEnvelope>(raw) }.getOrNull() ?: return
        if (envelope.v != ClipProtocol.PROTOCOL_VERSION) return
        // sync 频道没有设备号，解不开就只当没听见；那台设备随后会在自己的 presence 频道报上来
        val payload = presencePayload(envelope, roomCrypto, topic) ?: return
        if (payload.dev == cfg.deviceId) return
        publishPresence(online = true)
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
                        rollCall()
                    }

                    (state is ConnectionState.Connecting || state is ConnectionState.Reconnecting) &&
                            _status.value is Status.Online -> {
                        clearRoster()
                        _status.value = Status.Connecting
                    }

                    state is ConnectionState.Disconnected && state.reason != null &&
                            desired != null -> {
                        clearRoster()
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
                pruneRoster()
                if (link?.connectionState?.value is ConnectionState.Connected) {
                    publishPresence(online = true)
                }
            }
        }
    }

    private fun now(): Long = clock()

    companion object {
        // 心跳只是兜底（丢了一条 presence 时的补救），发现新同伴时会立刻自报一次。
        // 120s 正好是 keepalive 60s 的整数倍：两次唤醒叠在一起，无线电不用额外醒。
        private const val PRESENCE_INTERVAL_MS = 120_000L

        // 三条心跳没听到就当他不在了：断线期间的告别和遗嘱会整个丢，只能靠时间收敛
        private const val PRESENCE_TTL_MS = PRESENCE_INTERVAL_MS * 3
        private const val RECOVERY_BASE_MS = 5_000L
        private const val RECOVERY_MAX_MS = 60_000L

        // 密钥不一致的名单是陌生人也能往我们频道灌帧喂出来的，给它一个上限
        private const val MAX_UNKNOWN_KEY_DEVICES = 8
        private const val MISMATCH_ID_CHARS = 8
    }
}
@OptIn(ExperimentalTime::class)
internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
