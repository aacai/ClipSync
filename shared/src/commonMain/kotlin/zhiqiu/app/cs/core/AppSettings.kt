package zhiqiu.app.cs.core

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class AppLanguage(val tag: String?) {
    System(null),
    Chinese("zh"),
    English("en"),
}

internal expect fun platformSettings(): Settings

class AppSettings(
    private val store: Settings = platformSettings(),
    seed: String = installId(),
) {

    private val vault = RoomVault(store, seed)

    private val _themeMode = MutableStateFlow(themeOf(store.getStringOrNull(KEY_THEME)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(languageOf(store.getStringOrNull(KEY_LANGUAGE)))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        if (_language.value != AppLanguage.System) applyAppLanguage(_language.value)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        store.putString(KEY_THEME, mode.name)
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        store.putString(KEY_LANGUAGE, language.name)
        applyAppLanguage(language)
    }

    private val _rememberRoom = MutableStateFlow(store.getBoolean(KEY_REMEMBER_ROOM, true))
    val rememberRoom: StateFlow<Boolean> = _rememberRoom.asStateFlow()

    fun setRememberRoom(enabled: Boolean) {
        _rememberRoom.value = enabled
        store.putBoolean(KEY_REMEMBER_ROOM, enabled)
        if (!enabled) {
            forgetRoomKey()
            setResidentSync(false)
        }
    }

    private val _nickname = MutableStateFlow(normalizeNickname(store.getString(KEY_NICKNAME, "")))
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    fun setNickname(name: String) {
        val value = normalizeNickname(name)
        _nickname.value = value
        store.putString(KEY_NICKNAME, value)
    }

    private val _watchClipboard = MutableStateFlow(store.getBoolean(KEY_WATCH_CLIPBOARD, true))
    val watchClipboard: StateFlow<Boolean> = _watchClipboard.asStateFlow()

    fun setWatchClipboard(enabled: Boolean) {
        _watchClipboard.value = enabled
        store.putBoolean(KEY_WATCH_CLIPBOARD, enabled)
    }

    private val _launchAtLogin = MutableStateFlow(store.getBoolean(KEY_LAUNCH_AT_LOGIN, false))
    val launchAtLogin: StateFlow<Boolean> = _launchAtLogin.asStateFlow()

    fun setLaunchAtLogin(enabled: Boolean) {
        _launchAtLogin.value = enabled
        store.putBoolean(KEY_LAUNCH_AT_LOGIN, enabled)
    }

    private val _autoCopyIncoming = MutableStateFlow(store.getBoolean(KEY_AUTO_COPY, true))
    val autoCopyIncoming: StateFlow<Boolean> = _autoCopyIncoming.asStateFlow()

    fun setAutoCopyIncoming(enabled: Boolean) {
        _autoCopyIncoming.value = enabled
        store.putBoolean(KEY_AUTO_COPY, enabled)
    }

    private val _watchImages = MutableStateFlow(store.getBoolean(KEY_WATCH_IMAGES, true))
    val watchImages: StateFlow<Boolean> = _watchImages.asStateFlow()

    fun setWatchImages(enabled: Boolean) {
        _watchImages.value = enabled
        store.putBoolean(KEY_WATCH_IMAGES, enabled)
    }

    private val _watchFiles = MutableStateFlow(store.getBoolean(KEY_WATCH_FILES, true))
    val watchFiles: StateFlow<Boolean> = _watchFiles.asStateFlow()

    fun setWatchFiles(enabled: Boolean) {
        _watchFiles.value = enabled
        store.putBoolean(KEY_WATCH_FILES, enabled)
    }

    /** 全局截屏快捷键，写法与 Rust 端一致（例如 Alt+A）；坏值按默认值处理，不能让用户卡在一个按不动的键上。 */
    private val _screenshotHotkey =
        MutableStateFlow(
            normalizeHotkey(store.getString(KEY_SCREENSHOT_HOTKEY, ScreenshotHotkey.DEFAULT_SPEC))
        )
    val screenshotHotkey: StateFlow<String> = _screenshotHotkey.asStateFlow()

    fun setScreenshotHotkey(spec: String) {
        val value = normalizeHotkey(spec)
        _screenshotHotkey.value = value
        store.putString(KEY_SCREENSHOT_HOTKEY, value)
    }

    /**
     * 截图时是否保留本机窗口。
     * 默认开：冻屏里还能看见 ClipSync；关掉则抓屏前先藏起自家窗口（旧行为）。
     */
    private val _screenshotKeepWindow = MutableStateFlow(store.getBoolean(KEY_SCREENSHOT_KEEP_WINDOW, true))
    val screenshotKeepWindow: StateFlow<Boolean> = _screenshotKeepWindow.asStateFlow()

    fun setScreenshotKeepWindow(enabled: Boolean) {
        _screenshotKeepWindow.value = enabled
        store.putBoolean(KEY_SCREENSHOT_KEEP_WINDOW, enabled)
    }

    private val _recordHotkey =
        MutableStateFlow(
            normalizeRecordHotkey(store.getString(KEY_RECORD_HOTKEY, ScreenshotHotkey.RECORD_DEFAULT_SPEC))
        )
    val recordHotkey: StateFlow<String> = _recordHotkey.asStateFlow()

    fun setRecordHotkey(spec: String) {
        val value = normalizeRecordHotkey(spec)
        _recordHotkey.value = value
        store.putString(KEY_RECORD_HOTKEY, value)
    }

    /** 区域框选还是整屏。坏值按区域处理。 */
    private val _recordMode =
        MutableStateFlow(recordModeOf(store.getStringOrNull(KEY_RECORD_MODE)))
    val recordMode: StateFlow<RecordMode> = _recordMode.asStateFlow()

    fun setRecordMode(mode: RecordMode) {
        _recordMode.value = mode
        store.putString(KEY_RECORD_MODE, mode.name)
    }

    private val _recordMic = MutableStateFlow(store.getBoolean(KEY_RECORD_MIC, true))
    val recordMic: StateFlow<Boolean> = _recordMic.asStateFlow()

    fun setRecordMic(enabled: Boolean) {
        _recordMic.value = enabled
        store.putBoolean(KEY_RECORD_MIC, enabled)
    }

    /** 录屏用的麦克风设备（AVCaptureDevice.uniqueID）；空串表示系统默认输入。 */
    private val _recordMicDevice = MutableStateFlow(store.getString(KEY_RECORD_MIC_DEVICE, ""))
    val recordMicDevice: StateFlow<String> = _recordMicDevice.asStateFlow()

    fun setRecordMicDevice(id: String) {
        val value = id.trim()
        _recordMicDevice.value = value
        store.putString(KEY_RECORD_MIC_DEVICE, value)
    }

    private val _recordSystemAudio = MutableStateFlow(store.getBoolean(KEY_RECORD_SYSTEM, true))
    val recordSystemAudio: StateFlow<Boolean> = _recordSystemAudio.asStateFlow()

    fun setRecordSystemAudio(enabled: Boolean) {
        _recordSystemAudio.value = enabled
        store.putBoolean(KEY_RECORD_SYSTEM, enabled)
    }

    /** 长边像素。0 是屏幕原始大小，1920 / 1280 会按比例缩小。 */
    private val _recordLongEdge = MutableStateFlow(recordEdgeOf(store.getInt(KEY_RECORD_EDGE, 0)))
    val recordLongEdge: StateFlow<Int> = _recordLongEdge.asStateFlow()

    fun setRecordLongEdge(edge: Int) {
        val value = recordEdgeOf(edge)
        _recordLongEdge.value = value
        store.putInt(KEY_RECORD_EDGE, value)
    }

    private val _recordFps = MutableStateFlow(recordFpsOf(store.getInt(KEY_RECORD_FPS, 30)))
    val recordFps: StateFlow<Int> = _recordFps.asStateFlow()

    fun setRecordFps(fps: Int) {
        val value = recordFpsOf(fps)
        _recordFps.value = value
        store.putInt(KEY_RECORD_FPS, value)
    }

    private fun normalizeHotkey(spec: String): String =
        ScreenshotHotkey.parse(spec)?.toString() ?: ScreenshotHotkey.DEFAULT_SPEC

    private fun normalizeRecordHotkey(spec: String): String =
        ScreenshotHotkey.parse(spec)?.toString() ?: ScreenshotHotkey.RECORD_DEFAULT_SPEC

    private val _residentSync = MutableStateFlow(store.getBoolean(KEY_RESIDENT_SYNC, false))
    val residentSync: StateFlow<Boolean> = _residentSync.asStateFlow()

    /** 过期自动删除默认关：开了就是每 30 天丢一次历史，得用户自己点头。 */
    private val _deleteExpired = MutableStateFlow(store.getBoolean(KEY_DELETE_EXPIRED, false))
    val deleteExpired: StateFlow<Boolean> = _deleteExpired.asStateFlow()

    fun setDeleteExpired(enabled: Boolean) {
        _deleteExpired.value = enabled
        store.putBoolean(KEY_DELETE_EXPIRED, enabled)
    }

    private val _retentionDays = MutableStateFlow(store.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS))
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    fun setRetentionDays(days: Int) {
        _retentionDays.value = days
        store.putInt(KEY_RETENTION_DAYS, days)
    }

    fun setResidentSync(enabled: Boolean) {
        _residentSync.value = enabled
        store.putBoolean(KEY_RESIDENT_SYNC, enabled)
    }

    fun rememberLastRoomCode(roomCode: String) {
        if (roomCode.isBlank()) return
        store.putString(KEY_ROOM_CODE, roomCode)
    }

    fun lastRoomCode(): String? = store.getStringOrNull(KEY_ROOM_CODE)?.takeIf { it.isNotBlank() }

    fun lastRoomPassword(): String = vault.read(KEY_ROOM_PASSWORD) ?: ""

    fun saveRoomSession(roomCode: String, roomKey: ByteArray, password: String) {
        if (roomKey.size != RoomCrypto.KEY_SIZE) return
        rememberLastRoomCode(roomCode)
        if (!_rememberRoom.value) return
        vault.writeBytes(KEY_ROOM_KEY, roomKey)
        vault.write(KEY_ROOM_PASSWORD, password)
    }

    fun hasRoomSession(): Boolean = _rememberRoom.value && roomSession() != null

    fun roomSession(): Pair<String, ByteArray>? {
        val code = store.getStringOrNull(KEY_ROOM_CODE) ?: return null
        val key = vault.readBytes(KEY_ROOM_KEY) ?: return null
        if (code.isBlank() || key.size != RoomCrypto.KEY_SIZE) return null
        return code to key
    }

    fun forgetRoomKey() {
        vault.forget(KEY_ROOM_KEY)
        vault.forget(KEY_ROOM_PASSWORD)
    }

    companion object {
        /** 前台服务与界面必须读同一份开关，所以设置只允许一个实例。 */
        val shared: AppSettings by lazy { AppSettings() }

        private const val KEY_THEME = "ui.themeMode"
        private const val KEY_LANGUAGE = "ui.language"
        private const val KEY_REMEMBER_ROOM = "room.remember"
        private const val KEY_WATCH_CLIPBOARD = "clipboard.watch"
        private const val KEY_AUTO_COPY = "clipboard.autoCopy"
        private const val KEY_WATCH_IMAGES = "clipboard.watchImages"
        private const val KEY_WATCH_FILES = "clipboard.watchFiles"
        private const val KEY_SCREENSHOT_HOTKEY = "screenshot.hotkey"
        private const val KEY_SCREENSHOT_KEEP_WINDOW = "screenshot.keepWindow"
        private const val KEY_RECORD_HOTKEY = "record.hotkey"
        private const val KEY_RECORD_MODE = "record.mode"
        private const val KEY_RECORD_MIC = "record.mic"
        private const val KEY_RECORD_MIC_DEVICE = "record.micDevice"
        private const val KEY_RECORD_SYSTEM = "record.systemAudio"
        private const val KEY_RECORD_EDGE = "record.longEdge"
        private const val KEY_RECORD_FPS = "record.fps"
        private const val KEY_DELETE_EXPIRED = "history.deleteExpired"
        private const val KEY_RETENTION_DAYS = "history.retentionDays"
        val RETENTION_CHOICES = listOf(7, 30, 90, 365)
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val KEY_RESIDENT_SYNC = "sync.resident"
        private const val KEY_LAUNCH_AT_LOGIN = "sync.launchAtLogin"
        private const val KEY_NICKNAME = "device.nickname"
        private const val KEY_ROOM_CODE = "room.code"
        private const val KEY_ROOM_KEY = "room.key"
        private const val KEY_ROOM_PASSWORD = "room.password"

        private const val MAX_NICKNAME = 24

        /** 昵称会进 presence 和每条剪贴板的署名，所以压成单行、限长。 */
        internal fun normalizeNickname(raw: String): String =
            raw.lineSequence().first().trim().take(MAX_NICKNAME)

        private fun themeOf(name: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System

        private fun languageOf(name: String?): AppLanguage =
            AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.System

        private fun recordModeOf(name: String?): RecordMode =
            RecordMode.entries.firstOrNull { it.name == name } ?: RecordMode.Region

        private fun recordEdgeOf(edge: Int): Int = if (edge == 1280 || edge == 1920) edge else 0

        private fun recordFpsOf(fps: Int): Int = if (fps == 15 || fps == 60) fps else 30
    }
}

/** 录屏范围：先框一块，或直接录主屏。 */
enum class RecordMode { Region, Screen }
