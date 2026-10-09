package zhiqiu.app.cs.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.DevicePresence
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.FileLimits

/**
 * 共享 UI 的编排层：连接引擎、剪贴板监视（发送侧）、收到即上屏（接收侧）、
 * 历史搜索与文件操作。桌面/Android/Web 复用同一份逻辑。
 */
class AppModel(
    private val scope: CoroutineScope,
    private val platform: PlatformUi,
) {
    private val engine = ClipSyncEngine(scope)
    private val repository = ClipRepository(platform.store)

    val status: StateFlow<ClipSyncEngine.Status> = engine.status
    val devices: StateFlow<List<DevicePresence>> = engine.devices

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _roomCode = MutableStateFlow(randomRoomCode())
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val transfer: StateFlow<ClipSyncEngine.TransferProgress?> = engine.transfer

    val items: StateFlow<List<ClipItem>> =
        combine(repository.items, _query) { list, q -> repository.search(q, list) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var monitorJob: Job? = null
    private var receiveJob: Job? = null
    private var lastPublished: String? = null
    private var lastOwnWrite: String? = null

    init {
        scope.launch {
            engine.fileErrors.collect { _error.value = it }
        }
        // 加载持久化历史，未连接也能看（本机缓存的文件仍可打开）
        scope.launch { repository.load() }
    }

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun updateRoomCode(value: String) {
        _roomCode.value = value.uppercase().filter { it.isLetterOrDigit() || it in "-_" }.take(MAX_ROOM_CODE)
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun regenerateRoomCode() {
        _roomCode.value = randomRoomCode()
        _password.value = ""
    }

    fun connect() {
        val room = _roomCode.value.trim()
        if (room.isEmpty()) {
            _error.value = "请输入房间码"
            return
        }
        if (status.value is ClipSyncEngine.Status.Online || _connecting.value) return
        _connecting.value = true
        _error.value = null
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
                _error.value = e.message ?: e::class.simpleName ?: "连接失败"
            }
            _connecting.value = false
            if (status.value is ClipSyncEngine.Status.Online) startMonitoring()
        }
    }

    fun disconnect() {
        monitorJob?.cancel()
        monitorJob = null
        scope.launch { engine.stop() }
    }

    /** 工具栏“剪贴板 → 同步”按钮。 */
    fun sendClipboard() {
        val text = platform.clipboard.read()?.takeIf { it.isNotBlank() } ?: run {
            _notice.value = "剪贴板为空"
            return
        }
        publish(text)
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        publish(text)
    }

    private fun publish(text: String) {
        scope.launch {
            runCatching { engine.publishText(text) }
                .onSuccess { lastPublished = text; _notice.value = "已同步 ${text.length} 字" }
                .onFailure { _error.value = it.message ?: "发送失败" }
        }
    }

    /** 打开系统选择器挑文件；大小在读取前预检。 */
    fun pickFiles() {
        platform.picker.pick { sources ->
            if (sources.isEmpty()) return@pick
            scope.launch {
                for (src in sources) {
                    if (src.sizeHint > FileLimits.MAX_FILE_BYTES) {
                        _error.value = "文件超过 ${FileLimits.MAX_FILE_BYTES / 1024 / 1024}MB，暂不支持：${src.name}"
                        continue
                    }
                    val bytes = runCatching { withContext(Dispatchers.Default) { src.open() } }.getOrElse { e ->
                        _error.value = "读取失败：${src.name}（${e.message}）"
                        continue
                    }
                    runCatching { engine.publishFile(src.name, src.mime, bytes) }
                        .onFailure { e -> _error.value = "发送失败：${e.message}" }
                        .onSuccess { item -> if (item != null) _notice.value = "已发送 ${src.name}" }
                }
            }
        }
    }

    /**
     * 写系统剪贴板并标记“这是我自己写的”，监视器不会把同一条再广播出去。
     * 安全码、文本条目复制都走这里。
     */
    fun copyToClipboard(text: String) {
        platform.clipboard.write(text)
        lastOwnWrite = text
        _notice.value = "已复制"
    }

    /** 列表“复制”：仅文本条目可进剪贴板；文件走打开/下载。 */
    fun copy(item: ClipItem) {
        if (item.kind == ClipRepository.KIND_TEXT) copyToClipboard(item.text)
    }

    fun togglePin(item: ClipItem) {
        scope.launch { repository.togglePin(item.id) }
    }

    fun remove(item: ClipItem) {
        scope.launch { repository.remove(item.id) }
    }

    fun clearAll() {
        scope.launch {
            repository.clear()
            _notice.value = "历史已清空"
        }
    }

    /** 打开文件：已缓存直接打开，否则先下载解密。 */
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
                        _notice.value = "已下载 ${file.name}"
                        then?.invoke()
                    }
                }
                .onFailure { e -> _error.value = e.message ?: "下载失败" }
        }
    }

    fun dismissError() {
        _error.value = null
        _notice.value = null
    }

    // --- 剪贴板双向同步 ---

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(CLIPBOARD_POLL_MS)
                val status = status.value
                if (status !is ClipSyncEngine.Status.Online) continue
                val text = platform.clipboard.read() ?: continue
                if (text.isBlank()) continue
                if (text == lastOwnWrite || text == lastPublished) continue
                runCatching { engine.publishText(text) }
                    .onSuccess { lastPublished = text }
                    .onFailure { _error.value = it.message ?: "同步失败" }
            }
        }
        if (receiveJob?.isActive == true) return
        receiveJob = scope.launch {
            engine.clips.collect { event ->
                if (event.file == null) {
                    // 收到文本：写进系统剪贴板，并抑制监视器把同一条再发出去
                    platform.clipboard.write(event.text)
                    lastOwnWrite = event.text
                    _notice.value = "收到 ${event.senderName} 的文本"
                }
            }
        }
    }

    companion object {
        const val MAX_ROOM_CODE = 24
        private const val CLIPBOARD_POLL_MS = 800L
        /** 房间码用 Crockford 风格 base32（去掉易混淆的 I/L/O/U）。 */
        private const val ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

        fun randomRoomCode(len: Int = 6): String {
            val rnd = kotlin.random.Random
            return buildString(len) { repeat(len) { append(ROOM_ALPHABET[rnd.nextInt(ROOM_ALPHABET.length)]) } }
        }
    }
}
