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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.DevicePresence
import zhiqiu.app.cs.core.FileIssue
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.FileClip
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FileRejectReason
import zhiqiu.app.cs.files.TextOp
import zhiqiu.app.cs.files.applyTextOp
import zhiqiu.app.cs.files.isImage
import kotlin.time.Clock

/**
 * 共享 UI 的编排层：连接、剪贴板收发、历史搜索与文件操作。
 * 只产出 [Feedback]（类型 + 参数），不产出文案，文案在 UI 层按语言渲染。
 */
class AppModel(
    private val scope: CoroutineScope,
    private val platform: PlatformUi,
) {
    private val engine = ClipSyncEngine(scope)
    private val repository = ClipRepository(platform.store)

    val status: StateFlow<ClipSyncEngine.Status> = engine.status
    val devices: StateFlow<List<DevicePresence>> = engine.devices
    val transfer: StateFlow<ClipSyncEngine.TransferProgress?> = engine.transfer
    val undoDepth: StateFlow<Int> = repository.undoDepth

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _regex = MutableStateFlow(false)
    val regex: StateFlow<Boolean> = _regex.asStateFlow()

    private val _caseSensitive = MutableStateFlow(false)
    val caseSensitive: StateFlow<Boolean> = _caseSensitive.asStateFlow()

    private val _invalidQuery = MutableStateFlow(false)
    val invalidQuery: StateFlow<Boolean> = _invalidQuery.asStateFlow()

    private val _roomCode = MutableStateFlow(randomRoomCode())
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    /** null = 未进房间。掉线后保留，界面留在历史页并提供重连。 */
    private val _joinedRoom = MutableStateFlow<String?>(null)
    val joinedRoom: StateFlow<String?> = _joinedRoom.asStateFlow()

    private val _failure = MutableStateFlow<Feedback?>(null)
    val failure: StateFlow<Feedback?> = _failure.asStateFlow()

    private val _info = MutableStateFlow<Feedback?>(null)
    val info: StateFlow<Feedback?> = _info.asStateFlow()

    val supportsClipboardAutoSync: Boolean = platform.clipboard.supportsAutoSync
    val supportsFilePicker: Boolean = platform.picker.isAvailable
    val supportsLocalFiles: Boolean = platform.opener.isAvailable
    val supportsClipboardFiles: Boolean = platform.clipboard.canWriteFiles
    val supportsClipboardImage: Boolean = platform.clipboard.canWriteImage

    val items: StateFlow<List<ClipItem>> =
        combine(repository.items, combine(_query, _regex, _caseSensitive) { q, r, c -> Triple(q, r, c) }) { list, (q, r, c) ->
            repository.search(q, r, c, list)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var monitorJob: Job? = null
    private var receiveJob: Job? = null
    private var lastPublished: String? = null
    private var lastOwnWrite: String? = null

    init {
        scope.launch {
            engine.fileIssues.collect { _failure.value = Feedback.FileProblem(it) }
        }
        scope.launch { repository.load() }
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
        _connecting.value = true
        _failure.value = null
        scope.launch {
            runCatching {
                engine.start(
                    ClipSyncEngine.Config(
                        roomId = room,
                        password = _password.value,
                        deviceId = platform.installId,
                        deviceName = platform.deviceName,
                        repository = repository,
                    ),
                )
            }.onFailure { e ->
                _failure.value = Feedback.ConnectFailed(e.message ?: e::class.simpleName.orEmpty())
            }
            _connecting.value = false
            if (status.value is ClipSyncEngine.Status.Online) {
                _joinedRoom.value = room
                startSync()
            }
        }
    }

    fun disconnect() {
        monitorJob?.cancel()
        monitorJob = null
        _joinedRoom.value = null
        scope.launch { engine.stop() }
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

    fun copyToClipboard(text: String) {
        scope.launch {
            runCatching { platform.clipboard.write(text) }
                .onSuccess {
                    lastOwnWrite = text
                    _info.value = Feedback.Copied(CopyTarget.Text)
                }
                .onFailure { _failure.value = writeBlocked(it) }
        }
    }

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

    /** 行的主动作：文本=复制，已缓存文件=打开，图片=复制成位图。 */
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
                .onSuccess { _info.value = Feedback.Copied(CopyTarget.Image) }
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

    fun duplicate(item: ClipItem) {
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            if (repository.duplicate(item.id, newId(), now)) _info.value = Feedback.Duplicated(1)
        }
    }

    fun remove(items: List<ClipItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val count = repository.removeMany(items.map { it.id })
            if (count > 0) _info.value = Feedback.Deleted(count)
        }
    }

    /** 多选“复制”：把所选条目的文本按顺序拼成一整块进剪贴板。 */
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
            // 单条删除的撤销顺手把文件重新拉回来；批量撤销不替用户决定下载多少东西。
            if (batch.size == 1) batch.first().let { if (it.file != null) download(it) }
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
        if (platform.clipboard.supportsAutoSync && monitorJob?.isActive != true) {
            monitorJob = scope.launch {
                while (isActive) {
                    delay(CLIPBOARD_POLL_MS)
                    if (status.value !is ClipSyncEngine.Status.Online) continue
                    val text = runCatching { platform.clipboard.read() }.getOrNull() ?: continue
                    if (text.isBlank()) continue
                    if (text == lastOwnWrite || text == lastPublished) continue
                    runCatching { engine.publishText(text) }
                        .onSuccess { lastPublished = text }
                        .onFailure { _failure.value = Feedback.SendFailed(it.message.orEmpty()) }
                }
            }
        }
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
        const val MAX_ROOM_CODE = 24
        private const val CLIPBOARD_POLL_MS = 800L
        private const val HEX_DIGITS = "0123456789abcdef"
        /** Crockford base32：去掉易混淆的 I/L/O/U。 */
        private const val ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

        fun randomRoomCode(len: Int = 6): String {
            val rnd = CryptographyRandom.Default
            return buildString(len) { repeat(len) { append(ROOM_ALPHABET[rnd.nextInt(ROOM_ALPHABET.length)]) } }
        }
    }
}

/** 浏览器给错误码（denied / insecure），其他平台只有原始信息。 */
private fun Throwable.clipboardCode(): String? = (this as? ClipboardUnavailable)?.code
