package zhiqiu.app.cs.ui

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zhiqiu.app.cs.core.APP_VERSION
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.DefaultMqttLinks
import zhiqiu.app.cs.core.DefaultReleaseSource
import zhiqiu.app.cs.core.DevicePresence
import zhiqiu.app.cs.core.FileIssue
import zhiqiu.app.cs.core.MqttLinkFactory
import zhiqiu.app.cs.core.ReleaseSource
import zhiqiu.app.cs.core.UpdateState
import zhiqiu.app.cs.core.assetFor
import zhiqiu.app.cs.core.isBehind
import zhiqiu.app.cs.core.platformArch
import zhiqiu.app.cs.core.platformSupportsResidentSync
import zhiqiu.app.cs.core.platformSupportsSelfUpdate
import zhiqiu.app.cs.core.sha256Hex
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.FileClip
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FileRejectReason
import zhiqiu.app.cs.files.MoveInfo
import zhiqiu.app.cs.files.TextOp
import zhiqiu.app.cs.files.applyTextOp
import zhiqiu.app.cs.files.extForMime
import zhiqiu.app.cs.files.isImage
import zhiqiu.app.cs.files.sanitizeFileName
import zhiqiu.app.cs.translate.MAX_TRANSLATE_CHARS
import zhiqiu.app.cs.translate.TranslateClient
import zhiqiu.app.cs.translate.TranslateFailure
import zhiqiu.app.cs.translate.TranslateTarget
import zhiqiu.app.cs.translate.TranslationException
import zhiqiu.app.cs.translate.createTranslateClient
import kotlin.time.Clock

class AppModel internal constructor(
    private val scope: CoroutineScope,
    private val platform: PlatformUi,
    private val settings: AppSettings,
    private val links: MqttLinkFactory,
    private val releases: ReleaseSource = DefaultReleaseSource,
) {
    constructor(scope: CoroutineScope, platform: PlatformUi, settings: AppSettings) :
            this(scope, platform, settings, DefaultMqttLinks)

    private val engine = ClipSyncEngine(scope, links)
    private val repository = ClipRepository(platform.store)

    internal val status: StateFlow<ClipSyncEngine.Status> = engine.status
    val devices: StateFlow<List<DevicePresence>> = engine.devices
    val keyMismatch: StateFlow<List<String>> = engine.keyMismatch
    internal val transfer: StateFlow<ClipSyncEngine.TransferProgress?> = engine.transfer
    val undoDepth: StateFlow<Int> = repository.undoDepth

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _regex = MutableStateFlow(false)
    val regex: StateFlow<Boolean> = _regex.asStateFlow()

    private val _caseSensitive = MutableStateFlow(false)
    val caseSensitive: StateFlow<Boolean> = _caseSensitive.asStateFlow()

    private val _invalidQuery = MutableStateFlow(false)
    val invalidQuery: StateFlow<Boolean> = _invalidQuery.asStateFlow()

    private val _roomCode = MutableStateFlow(settings.lastRoomCode() ?: randomRoomCode())
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _password = MutableStateFlow(settings.lastRoomPassword())
    val password: StateFlow<String> = _password.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _joinedRoom = MutableStateFlow<String?>(null)
    val joinedRoom: StateFlow<String?> = _joinedRoom.asStateFlow()

    /** 想待的房间：第一次没连上不等于不想要它，引擎在后台续上后照样算进房。 */
    private var wantedRoom: String? = null

    private val _failure = MutableStateFlow<Feedback?>(null)
    val failure: StateFlow<Feedback?> = _failure.asStateFlow()

    private val _info = MutableStateFlow<Feedback?>(null)
    val info: StateFlow<Feedback?> = _info.asStateFlow()

    val supportsClipboardAutoSync: Boolean = platform.clipboard.supportsAutoSync
    val hasSavedRoom: Boolean = settings.hasRoomSession()
    val supportsFilePicker: Boolean get() = platform.picker.isAvailable
    val supportsLocalFiles: Boolean = platform.opener.isAvailable
    val supportsClipboardFiles: Boolean = platform.clipboard.canWriteFiles
    val supportsClipboardImage: Boolean = platform.clipboard.canWriteImage
    val supportsClipboardImageRead: Boolean = platform.clipboard.canReadImages
    val supportsClipboardFilesRead: Boolean = platform.clipboard.canReadFiles
    val supportsSaveToDownloads: Boolean = platform.downloads.isAvailable
    val supportsResidentSync: Boolean = platformSupportsResidentSync
    val supportsLaunchAtLogin: Boolean = platform.autostart.isAvailable

    /** 有指针悬浮的平台上行内快捷操作默认收起（hover 才展开），触摸设备常驻显示。 */
    val hoverRevealsActions: Boolean = platform.hoverRevealsActions
    val deviceName: StateFlow<String> =
        settings.nickname.map { it.ifBlank { platform.deviceName } }
            .stateIn(scope, SharingStarted.Eagerly, platform.deviceName)

    private fun senderName(): String = settings.nickname.value.ifBlank { platform.deviceName }

    val deviceOs: String = platform.deviceOs

    val appVersion: String = APP_VERSION
    val supportsUpdate: Boolean = platformSupportsSelfUpdate && platform.downloads.isAvailable

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun setNickname(name: String) {
        settings.setNickname(name)
        scope.launch { engine.renameDevice(senderName()) }
    }

    val residentSync: StateFlow<Boolean> = settings.residentSync

    val localCount: StateFlow<Int> = repository.items.map { it.size }.stateIn(scope, SharingStarted.Eagerly, 0)

    val items: StateFlow<List<ClipItem>> =
        combine(repository.items, combine(_query, _regex, _caseSensitive) { q, r, c -> Triple(q, r, c) }) { list, (q, r, c) ->
            repository.search(q, r, c, list)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val moveInfos: StateFlow<Map<String, MoveInfo>> =
        repository.items.map { repository.moveInfos() }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private var monitorJob: Job? = null
    private val translator: TranslateClient by lazy { createTranslateClient() }
    private var connectJob: Job? = null
    private var receiveJob: Job? = null
    private var lastPublished: String? = null
    private var lastOwnWrite: String? = null
    private var lastCaptured: String? = null
    private var lastImageHash: String? = null
    private var lastFilesSignature: String? = null
    private var lastOwnImageWrite: String? = null

    init {
        // 包被挪了地方、登录项被人手删掉，下一次启动顺手补回来
        if (supportsLaunchAtLogin && settings.launchAtLogin.value) {
            scope.launch { applyAutostart(true) }
        }
        scope.launch {
            engine.status.collect { if (it is ClipSyncEngine.Status.Online) onRoomLanded() }
        }
        scope.launch {
            engine.fileIssues.collect { _failure.value = Feedback.FileProblem(it) }
        }
        scope.launch {
            repository.load()
            restoreSavedRoom()
            settings.deleteExpired
                    .combine(settings.retentionDays) { enabled, days -> enabled to days }
                    .collect { (enabled, days) -> if (enabled) pruneExpired(days) }
        }
        scope.launch {
            settings.watchClipboard.collect { watching ->
                if (watching) startClipboardWatch() else stopClipboardWatch()
            }
        }
    }

    /** 剪贴板监听跟着 App 走，不跟着房间走：没共享也要留下记录，桌面端打开就是记录器。
     *  启动瞬间的内容只当基线，不记成新条目。有事件源的平台不轮询。 */
    private fun startClipboardWatch() {
        if (!platform.clipboard.supportsAutoSync || monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            lastCaptured = runCatching { platform.clipboard.read() }.getOrNull()
            lastImageHash = runCatching { platform.clipboard.readImage()?.bytes?.let { sha256Hex(it) } }.getOrNull()
            if (platform.clipboard.canReadFiles) {
                lastFilesSignature =
                        runCatching { fileSignature(platform.clipboard.readFiles()) }.getOrNull()
            }
            val events = platform.clipboard.changeEvents
            // 单次采集抛出来的东西必须吃掉：这里没人会重启监听，逃出去只会顺着 UI 线程变成系统级报错弹窗。
            if (events != null) {
                events.collect { runCatching { onClipboardChanged() } }
            } else {
                while (isActive) {
                    delay(platform.clipboard.pollIntervalMs())
                    runCatching { onClipboardChanged() }
                }
            }
        }
    }

    /** 关掉开关就连监听都不留：轮询是常驻进程唯一的定期唤醒，留着它等于没关。 */
    private fun stopClipboardWatch() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun onClipboardChanged() {
        val text = runCatching { platform.clipboard.read() }.getOrNull()
        if (text == null) {
            if (!captureFiles()) captureImage()
            return
        }
        if (text.isBlank()) return
        if (text == lastOwnWrite || text == lastPublished || text == lastCaptured) return
        lastCaptured = text
        if (status.value is ClipSyncEngine.Status.Online) {
            runCatching { engine.publishText(text) }
                .onSuccess { lastPublished = text }
                .onFailure { _failure.value = Feedback.SendFailed(it.message.orEmpty()) }
        } else {
            recordLocal(text)
        }
    }

    /** 复制的文件：一份内容算一批，每个文件留一条。返回 true 表示这批剪贴板归文件管，别再当图片抓。 */
    private suspend fun captureFiles(): Boolean {
        if (!platform.clipboard.canReadFiles || !settings.watchFiles.value) return false
        val sources = runCatching { platform.clipboard.readFiles() }.getOrDefault(emptyList())
        if (sources.isEmpty()) return false
        val signature = fileSignature(sources)
        if (signature == lastFilesSignature) return true
        lastFilesSignature = signature
        val online = status.value is ClipSyncEngine.Status.Online
        sources.forEach { source ->
            if (source.sizeHint <= 0L || source.sizeHint > FileLimits.MAX_FILE_BYTES) return@forEach
            val bytes = runCatching { source.open() }.getOrNull() ?: return@forEach
            if (bytes.isEmpty() || bytes.size.toLong() > FileLimits.MAX_FILE_BYTES) return@forEach
            val name = sanitizeFileName(source.name)
            if (online) {
                val item = runCatching { engine.publishFile(name, source.mime, bytes) }.getOrNull()
                if (item != null) cacheLocally(item.id, name, bytes)
            } else {
                recordLocalFile(name, source.mime, bytes, runCatching { sha256Hex(bytes) }.getOrDefault(""))
            }
        }
        return true
    }

    private fun fileSignature(sources: List<PickedSource>): String =
            sources.joinToString("|") { "${it.name}:${it.sizeHint}" }

    /** 截图、复制的图片：内容换了才算新条目，同一张图被轮询读多少次都只记一条。 */
    private suspend fun captureImage() {
        if (!platform.clipboard.canReadImages || !settings.watchImages.value) return
        val image = runCatching { platform.clipboard.readImage() }.getOrNull() ?: return
        if (image.bytes.isEmpty() || image.bytes.size.toLong() > FileLimits.MAX_FILE_BYTES) return
        val hash = sha256Hex(image.bytes)
        if (hash == lastImageHash || hash == lastOwnImageWrite) return
        lastImageHash = hash
        val name = sanitizeFileName(image.name.ifBlank { "image-${hash.take(8)}.${extForMime(image.mime)}" })
        if (status.value is ClipSyncEngine.Status.Online) {
            val item = runCatching { engine.publishFile(name, image.mime, image.bytes) }.getOrNull()
            if (item != null) cacheLocally(item.id, name, image.bytes)
        } else {
            recordLocalFile(name, image.mime, image.bytes, hash)
        }
    }

    private suspend fun recordLocalFile(name: String, mime: String, bytes: ByteArray, hash: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = newId()
        val path = platform.store.writeCache(id, name, bytes)
        repository.add(
                ClipItem(
                        id = id,
                        kind = ClipRepository.KIND_FILE,
                        ts = now,
                        senderId = platform.installId,
                        senderName = senderName(),
                        text = name,
                        file =
                                FileClip(
                                        id = id,
                                        name = name,
                                        mime = mime,
                                        size = bytes.size.toLong(),
                                        sha256 = hash,
                                        link = "",
                                        expiresAt = 0L,
                                        ts = now,
                                        senderId = platform.installId,
                                        senderName = senderName(),
                                        cached = path != null,
                                        localPath = path,
                                ),
                ),
        )
    }

    /** 发出去的那张图本机也留一份字节，回头点「作为图片复制」不用再从链接下载。 */
    private suspend fun cacheLocally(id: String, name: String, bytes: ByteArray) {
        val path = platform.store.writeCache(id, name, bytes) ?: return
        repository.markCached(id, path)
    }

    /** 过期清理：置顶的条目是用户点名要留的，天数到了也不动。 */
    private suspend fun pruneExpired(days: Int) {
        val cutoff = Clock.System.now().toEpochMilliseconds() - days * MILLIS_PER_DAY
        val count = repository.removeExpired(cutoff)
        if (count > 0) _info.value = Feedback.Pruned(count)
    }

    private suspend fun recordLocal(text: String) {
        repository.add(
                ClipItem(
                        id = newId(),
                        kind = ClipRepository.KIND_TEXT,
                        ts = Clock.System.now().toEpochMilliseconds(),
                        senderId = platform.installId,
                        senderName = senderName(),
                        text = text,
                ),
        )
    }

    /** 重开就回到上次的房间：房间码 + 派生密钥都在，就不该让用户重新拼一遍。 */
    private fun restoreSavedRoom() {
        if (!settings.rememberRoom.value) return
        if (_joinedRoom.value != null || status.value is ClipSyncEngine.Status.Online) return
        val (code, key) = settings.roomSession() ?: return
        _roomCode.value = code
        connectWith(code, key)
    }

    fun updateQuery(value: String) {
        _query.value = value
        validateQuery()
    }

    fun updateRoomCode(value: String) {
        _roomCode.value = value.uppercase().filter { it.isLetterOrDigit() || it in "-_" }.take(MAX_ROOM_CODE)
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun regenerateRoomCode() {
        _roomCode.value = randomRoomCode()
    }

    fun connect() {
        val room = _roomCode.value.trim()
        if (room.isEmpty()) {
            _failure.value = Feedback.RoomCodeRequired
            return
        }
        if (status.value is ClipSyncEngine.Status.Online || _connecting.value) return
        connectWith(room, savedKeyFor(room))
    }

    /** 各端「刚回到前台」的入口：房间还想待着，就别让退避把这段时间耗完。 */
    fun retryNow() {
        engine.retryNow()
    }

    /** 密码留空但存过这间房的密钥就直接用回去：不然会静默换一把钥匙、进错房间还以为连上了。 */
    private fun savedKeyFor(room: String): ByteArray? =
        if (_password.value.isNotEmpty()) null
        else settings.roomSession()?.takeIf { it.first == room }?.second

    private fun connectWith(room: String, savedKey: ByteArray?) {
        connectJob?.cancel()
        wantedRoom = room
        connectJob =
                scope.launch {
                    _connecting.value = true
                    _failure.value = null
                    val error = tryConnect(room, savedKey)
                    _connecting.value = false
                    // 连上了由状态收集器记账：引擎后台续上同一条路径，界面不该分两种说法
                    if (error != null) {
                        // 这一次没连上不代表完：引擎在后台按退避继续试，界面只如实报离线
                        _failure.value = Feedback.ConnectFailed(error)
                    }
                }
    }

    /** 人在房间里了——不管是自己连上的还是退避后引擎续上的，都记一次房、把收来的内容接回剪贴板。 */
    private fun onRoomLanded() {
        val room = wantedRoom ?: return
        if (_joinedRoom.value == room) return
        _joinedRoom.value = room
        if (_failure.value is Feedback.ConnectFailed) _failure.value = null
        persistRoomSession()
        startSync()
    }

    private suspend fun tryConnect(room: String, savedKey: ByteArray?): String? =
            runCatching {
                            engine.start(
                                    ClipSyncEngine.Config(
                                            roomId = room,
                                            password = _password.value,
                                            roomKey = savedKey,
                                            deviceId = platform.installId,
                                            deviceName = senderName(),
                                            deviceOs = platform.deviceOs,
                                            repository = repository,
                                    ),
                            )
                        }
                        .exceptionOrNull()
                        ?.let { it.message ?: it::class.simpleName.orEmpty() }

    private fun persistRoomSession() {
        val room = _joinedRoom.value ?: return
        settings.rememberLastRoomCode(room)
        val key = engine.roomKeyMaterial() ?: return
        settings.saveRoomSession(room, key, _password.value)
        key.fill(0)
    }

    private val _leavePrompt = MutableStateFlow(false)
    val leavePrompt: StateFlow<Boolean> = _leavePrompt.asStateFlow()

    /** 退房会清掉保存的会话，一次误点就回不去原来那间房了，所以先问一句。 */
    fun requestLeaveRoom() {
        if (_joinedRoom.value == null && status.value !is ClipSyncEngine.Status.Online) return
        _leavePrompt.value = true
    }

    fun cancelLeaveRoom() {
        _leavePrompt.value = false
    }

    fun confirmLeaveRoom() {
        _leavePrompt.value = false
        disconnect()
    }

    private fun disconnect() {
        connectJob?.cancel()
        wantedRoom = null
        _joinedRoom.value = null
        settings.forgetRoomKey()
        if (settings.residentSync.value) settings.setResidentSync(false)
        scope.launch { engine.stop() }
    }

    fun setResidentSync(enabled: Boolean) {
        if (!supportsResidentSync) return
        if (enabled && _joinedRoom.value == null) return
        settings.setResidentSync(enabled)
    }

    val launchAtLogin: StateFlow<Boolean> = settings.launchAtLogin

    /** 登录项归系统管，写失败就把开关退回原位，不让它说假话。 */
    fun setLaunchAtLogin(enabled: Boolean) {
        if (!supportsLaunchAtLogin) return
        settings.setLaunchAtLogin(enabled)
        scope.launch {
            applyAutostart(enabled).onFailure {
                settings.setLaunchAtLogin(!enabled)
                _failure.value = Feedback.AutostartFailed
            }
        }
    }

    private suspend fun applyAutostart(enabled: Boolean): Result<Unit> = runCatching {
        withContext(Dispatchers.Default) { platform.autostart.setEnabled(enabled) }
    }

    fun sendClipboard() {
        scope.launch {
            val text = runCatching { platform.clipboard.read() }.getOrElse { e ->
                _failure.value = readBlocked(e)
                return@launch
            }
            if (text.isNullOrBlank()) {
                _info.value = Feedback.ClipboardEmpty
                return@launch
            }
            publish(text)
        }
    }

    fun sendText(text: String) {
        if (text.isNotBlank()) publish(text)
    }

    fun pickFiles() {
        platform.picker.pick { sources ->
            if (sources.isEmpty()) return@pick
            scope.launch {
                for (src in sources) {
                    if (src.sizeHint > FileLimits.MAX_FILE_BYTES) {
                        _failure.value = Feedback.FileProblem(
                            FileIssue.Rejected(FileRejectReason.TooLarge, src.name),
                        )
                        continue
                    }
                    val bytes = runCatching { withContext(Dispatchers.Default) { src.open() } }.getOrElse { e ->
                        _failure.value = Feedback.LocalFileReadFailed(src.name, e.message.orEmpty())
                        continue
                    }
                    runCatching { engine.publishFile(src.name, src.mime, bytes) }
                        .onSuccess { item -> if (item != null) _info.value = Feedback.FileSent(src.name) }
                        .onFailure { e -> _failure.value = Feedback.SendFailed(e.message.orEmpty()) }
                }
            }
        }
    }

    fun copyToClipboard(text: String) = writeSelection(text, Feedback.Copied(CopyTarget.Text))

    /** 条目“复制”：文本进剪贴板；图片按位图进；文件按 file URL / CF_HDROP 进，否则退回下载。 */
    fun copy(item: ClipItem) {
        val file = item.file
        when {
            item.kind == ClipRepository.KIND_TEXT -> copyToClipboard(item.text)
            file != null && file.localPath != null && platform.clipboard.canWriteFiles ->
                copyFiles(listOf(file.localPath))
            file != null && platform.clipboard.canWriteImage -> copyImage(file)
            else -> Unit
        }
    }

    fun activate(item: ClipItem) {
        val file = item.file
        when {
            item.kind == ClipRepository.KIND_TEXT -> copy(item)
            file != null && isImage(file.mime) && platform.clipboard.canWriteImage -> copy(item)
            file?.localPath != null && platform.opener.isAvailable -> open(item)
            else -> download(item)
        }
    }

    private fun copyFiles(paths: List<String>) {
        scope.launch {
            runCatching { platform.clipboard.writeFiles(paths) }
                .onSuccess { _info.value = Feedback.Copied(CopyTarget.File) }
                .onFailure { _failure.value = writeBlocked(it) }
        }
    }

    private fun copyImage(file: FileClip) {
        scope.launch {
            val bytes = runCatching { engine.fetchFile(file) }.getOrElse { e ->
                _failure.value = Feedback.DownloadFailed(e.message.orEmpty())
                return@launch
            }
            if (bytes == null) {
                _failure.value = Feedback.DownloadFailed("")
                return@launch
            }
            runCatching { platform.clipboard.writeImage(bytes, file.mime) }
                .onSuccess {
                    lastOwnImageWrite = runCatching { sha256Hex(bytes) }.getOrNull()
                    _info.value = Feedback.Copied(CopyTarget.Image)
                }
                .onFailure { _failure.value = writeBlocked(it) }
        }
    }

    fun togglePin(item: ClipItem) {
        scope.launch { repository.togglePin(item.id) }
    }

    fun setPinned(items: List<ClipItem>, pinned: Boolean) {
        scope.launch {
            repository.setPinned(items.map { it.id }, pinned)
            _info.value = Feedback.PinChanged(items.size, pinned)
        }
    }

    fun editItem(item: ClipItem, text: String) {
        scope.launch {
            repository.updateText(item.id, text)
            _info.value = Feedback.Edited(1)
        }
    }

    fun setNote(item: ClipItem, note: String) {
        scope.launch {
            repository.updateNote(item.id, note)
            _info.value = Feedback.Edited(1)
        }
    }

    fun transform(item: ClipItem, op: TextOp) {
        scope.launch {
            repository.updateText(item.id, applyTextOp(item.text, op))
            _info.value = Feedback.Edited(1)
        }
    }

    fun move(item: ClipItem, delta: Int) {
        scope.launch { repository.move(item.id, delta) }
    }

    fun moveTo(item: ClipItem, target: Int) {
        scope.launch {
            val to = repository.moveTo(item.id, target) ?: return@launch
            _info.value = Feedback.Moved(to)
        }
    }

    fun moveToTop(item: ClipItem) = moveTo(item, 1)

    fun moveToBottom(item: ClipItem) = moveTo(item, Int.MAX_VALUE)

    fun duplicate(item: ClipItem) {
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            if (repository.duplicate(item.id, newId(), now)) _info.value = Feedback.Duplicated(1)
        }
    }

    /** 中英互译：只负责发请求并把译文交回弹窗，失败原样抛给调用方展示。 */
    suspend fun translateText(source: String, target: TranslateTarget): String {
        if (source.isBlank()) throw TranslationException(TranslateFailure.Empty)
        if (source.length > MAX_TRANSLATE_CHARS) {
            throw TranslationException(
                    TranslateFailure.Rejected,
                    "source too long: ${source.length} > $MAX_TRANSLATE_CHARS",
            )
        }
        return translator.translate(source, target)
    }

    /** 译文另存为一条本机历史：不广播给同屋，原文保留。 */
    fun addEntry(text: String) {
        if (text.isBlank()) return
        scope.launch {
            repository.add(
                    ClipItem(
                            id = newId(),
                            kind = ClipRepository.KIND_TEXT,
                            ts = Clock.System.now().toEpochMilliseconds(),
                            senderId = platform.installId,
                            senderName = senderName(),
                            text = text,
                    ),
            )
        }
    }

    fun remove(items: List<ClipItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val count = repository.removeMany(items.map { it.id })
            if (count > 0) _info.value = Feedback.Deleted(count)
        }
    }

    fun copySelection(items: List<ClipItem>) {
        if (items.isEmpty()) return
        val payload =
            items.joinToString("\n\n") {
                if (it.kind == ClipRepository.KIND_FILE) it.file?.let { f -> "[${f.name}]" } ?: it.text
                else it.text
            }
        writeSelection(payload, Feedback.Copied(CopyTarget.Text))
    }

    fun exportJson(items: List<ClipItem>) {
        if (items.isEmpty()) return
        writeSelection(ClipRepository.encode(items.sortedWith(ClipRepository.ordering)), Feedback.Exported(items.size))
    }

    private fun writeSelection(payload: String, then: Feedback) {
        scope.launch {
            runCatching { platform.clipboard.write(payload) }
                .onSuccess {
                    lastOwnWrite = payload
                    _info.value = then
                }
                .onFailure { _failure.value = writeBlocked(it) }
        }
    }

    fun toggleRegex() {
        _regex.value = !_regex.value
        validateQuery()
    }

    fun toggleCaseSensitive() {
        _caseSensitive.value = !_caseSensitive.value
        validateQuery()
    }

    private fun validateQuery() {
        val q = _query.value.trim()
        _invalidQuery.value =
            _regex.value && q.isNotEmpty() && runCatching { Regex(q) }.isFailure
    }

    private fun newId(): String = buildString(16) {
        val rnd = CryptographyRandom.Default
        repeat(16) { append(HEX_DIGITS[rnd.nextInt(16)]) }
    }

    fun remove(item: ClipItem) {
        scope.launch { if (repository.remove(item.id)) _info.value = Feedback.Deleted(1) }
    }

    fun clearAll() {
        scope.launch {
            val count = repository.clear()
            if (count > 0) _info.value = Feedback.Deleted(count)
        }
    }

    fun undoDelete() {
        scope.launch {
            val batch = repository.undo() ?: return@launch
            _info.value = Feedback.Undeleted(batch.size)
            if (batch.size == 1) batch.first().let { if (it.file != null) download(it) }
        }
    }

    /** 缩略图取的字节：只有本机留了缓存的图片才有，其余交给界面退回图标。 */
    suspend fun cachedBytes(item: ClipItem): ByteArray? =
            item.file?.localPath?.let { path -> runCatching { repository.readCache(path) }.getOrNull() }

    /** 存到系统下载目录：本机有缓存就用缓存，同伴的条目先取回来；文本现写成随机名字的 .txt。 */
    fun saveToDownloads(item: ClipItem) {
        val file = item.file
        scope.launch {
            val bytes =
                    if (file == null) item.text.encodeToByteArray()
                    else
                            file.localPath?.let { runCatching { repository.readCache(it) }.getOrNull() }
                                    ?: runCatching { engine.fetchFile(file) }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                _failure.value = Feedback.DownloadFailed(file?.name.orEmpty())
                return@launch
            }
            val name = file?.name?.takeIf { it.isNotBlank() } ?: "text-${newId().take(8)}.txt"
            runCatching { platform.downloads.save(name, bytes) }
                    .onSuccess { _info.value = Feedback.SavedTo(it) }
                    .onFailure { _failure.value = Feedback.DownloadFailed(it.message.orEmpty()) }
        }
    }

    fun checkUpdates() {
        if (_update.value is UpdateState.Checking) return
        _update.value = UpdateState.Checking
        scope.launch {
            runCatching { releases.latest() }
                    .onSuccess { release ->
                        _update.value =
                                if (isBehind(APP_VERSION, release.version)) {
                                    UpdateState.Available(release, release.assetFor(platform.deviceOs, platformArch()))
                                } else {
                                    UpdateState.UpToDate(release.version)
                                }
                    }
                    .onFailure { _update.value = UpdateState.Failed(it.message.orEmpty()) }
        }
    }

    /** 安装包落到下载目录：桌面重名会让路，浏览器按它自己的下载行为走。 */
    fun downloadUpdate() {
        val asset = (_update.value as? UpdateState.Available)?.asset ?: return
        _update.value = UpdateState.Downloading(asset, 0)
        scope.launch {
            runCatching {
                val bytes = releases.download(asset) { received, _ ->
                    _update.value = UpdateState.Downloading(asset, received)
                }
                platform.downloads.save(asset.name, bytes)
            }.onSuccess { _update.value = UpdateState.Downloaded(it) }
                    .onFailure { _update.value = UpdateState.Failed(it.message.orEmpty()) }
        }
    }

    /** 只有离线攒下来、还没上过天的文件才值得补传；同伴那条本来就人人都有。 */
    fun canResend(item: ClipItem): Boolean = item.file?.link?.isBlank() == true

    /** 进房后把本机缓存里的那一份传出去：还是原来那条条目，只是终于有了链接。 */
    fun resend(item: ClipItem) {
        val file = item.file?.takeIf { it.link.isBlank() } ?: return
        scope.launch {
            val bytes = file.localPath?.let { runCatching { repository.readCache(it) }.getOrNull() }
            if (bytes == null || bytes.isEmpty()) {
                _failure.value = Feedback.SendFailed(file.name)
                return@launch
            }
            runCatching { engine.resend(file, bytes) }
                    .onSuccess { if (it) _info.value = Feedback.FileSent(file.name) }
                    .onFailure { _failure.value = Feedback.SendFailed(it.message.orEmpty()) }
        }
    }

    fun open(item: ClipItem) {
        val file = item.file ?: return
        if (file.localPath != null) {
            platform.opener.open(file.localPath)
            return
        }
        download(item) {
            val refreshed = repository.items.value.firstOrNull { it.id == item.id }?.file
            if (refreshed?.localPath != null) platform.opener.open(refreshed.localPath)
        }
    }

    fun download(item: ClipItem, then: (() -> Unit)? = null) {
        val file = item.file ?: return
        scope.launch {
            runCatching { engine.fetchFile(file) }
                .onSuccess { bytes ->
                    if (bytes != null) {
                        _info.value = Feedback.Downloaded(file.name)
                        then?.invoke()
                    }
                }
                .onFailure { e -> _failure.value = Feedback.DownloadFailed(e.message.orEmpty()) }
        }
    }

    fun dismissFailure() {
        _failure.value = null
    }

    fun dismissInfo() {
        _info.value = null
    }

    fun clearFeedback(feedback: Feedback) {
        if (_failure.value == feedback) _failure.value = null
        if (_info.value == feedback) _info.value = null
    }

    private fun publish(text: String) {
        scope.launch {
            runCatching { engine.publishText(text) }
                .onSuccess { lastPublished = text; _info.value = Feedback.Sent(text.length) }
                .onFailure { _failure.value = Feedback.SendFailed(it.message.orEmpty()) }
        }
    }

    private fun readBlocked(e: Throwable) =
        Feedback.ClipboardReadBlocked(e.clipboardCode(), e.message)

    private fun writeBlocked(e: Throwable) =
        Feedback.ClipboardWriteBlocked(e.clipboardCode(), e.message)

    private fun startSync() {
        if (receiveJob?.isActive != true) {
            receiveJob = scope.launch {
                engine.clips.collect { event ->
                    if (event.file != null) return@collect
                    val outcome = runCatching { platform.clipboard.write(event.text) }
                    lastOwnWrite = event.text
                    _info.value = if (outcome.isSuccess) {
                        Feedback.Received(event.senderName)
                    } else {
                        val e = outcome.exceptionOrNull()
                        Feedback.Received(event.senderName, e?.clipboardCode(), e?.message)
                    }
                }
            }
        }
    }

    companion object {
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        const val MAX_ROOM_CODE = 24
        private const val HEX_DIGITS = "0123456789abcdef"
        private const val ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

        fun randomRoomCode(): String {
            val rnd = CryptographyRandom.Default
            return buildString(6) { repeat(6) { append(ROOM_ALPHABET[rnd.nextInt(ROOM_ALPHABET.length)]) } }
        }
    }
}

private fun Throwable.clipboardCode(): String? = (this as? ClipboardUnavailable)?.code

/** 设备署名 + 系统标签；名字里已经带系统（Web）就不重复。 */
internal fun deviceLabel(name: String, os: String): String =
    if (os.isBlank() || name.contains(os, ignoreCase = true)) name else "$name · $os"
