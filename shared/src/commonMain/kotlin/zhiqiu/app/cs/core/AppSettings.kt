package zhiqiu.app.cs.core

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.encoding.Base64

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

class AppSettings(private val store: Settings = platformSettings()) {

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
            clearRoomSession()
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

    private val _watchImages = MutableStateFlow(store.getBoolean(KEY_WATCH_IMAGES, true))
    val watchImages: StateFlow<Boolean> = _watchImages.asStateFlow()

    fun setWatchImages(enabled: Boolean) {
        _watchImages.value = enabled
        store.putBoolean(KEY_WATCH_IMAGES, enabled)
    }

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

    /**
     * 上次房间：存 Argon2id 派生密钥而不是明文密码。密钥是按房间码加盐的，
     * 存储被翻出来最多丢这一间房，不会牵出用户在别处复用的密码。
     */
    fun saveRoomSession(roomCode: String, roomKey: ByteArray) {
        if (!_rememberRoom.value || roomKey.size != RoomCrypto.KEY_SIZE) return
        store.putString(KEY_ROOM_CODE, roomCode)
        store.putString(KEY_ROOM_KEY, Base64.encode(roomKey))
    }

    fun hasRoomSession(): Boolean = _rememberRoom.value && roomSession() != null

    fun roomSession(): Pair<String, ByteArray>? {
        val code = store.getStringOrNull(KEY_ROOM_CODE) ?: return null
        val key = store.getStringOrNull(KEY_ROOM_KEY)
            ?.let { runCatching { Base64.decode(it) }.getOrNull() }
            ?: return null
        if (code.isBlank() || key.size != RoomCrypto.KEY_SIZE) return null
        return code to key
    }

    fun clearRoomSession() {
        store.remove(KEY_ROOM_CODE)
        store.remove(KEY_ROOM_KEY)
    }

    companion object {
        /** 前台服务与界面必须读同一份开关，所以设置只允许一个实例。 */
        val shared: AppSettings by lazy { AppSettings() }

        private const val KEY_THEME = "ui.themeMode"
        private const val KEY_LANGUAGE = "ui.language"
        private const val KEY_REMEMBER_ROOM = "room.remember"
        private const val KEY_WATCH_CLIPBOARD = "clipboard.watch"
        private const val KEY_WATCH_IMAGES = "clipboard.watchImages"
        private const val KEY_DELETE_EXPIRED = "history.deleteExpired"
        private const val KEY_RETENTION_DAYS = "history.retentionDays"
        val RETENTION_CHOICES = listOf(7, 30, 90, 365)
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val KEY_RESIDENT_SYNC = "sync.resident"
        private const val KEY_LAUNCH_AT_LOGIN = "sync.launchAtLogin"
        private const val KEY_NICKNAME = "device.nickname"
        private const val KEY_ROOM_CODE = "room.code"
        private const val KEY_ROOM_KEY = "room.key"

        private const val MAX_NICKNAME = 24

        /** 昵称会进 presence 和每条剪贴板的署名，所以压成单行、限长。 */
        internal fun normalizeNickname(raw: String): String =
            raw.lineSequence().first().trim().take(MAX_NICKNAME)

        private fun themeOf(name: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System

        private fun languageOf(name: String?): AppLanguage =
            AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.System
    }
}
