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

/** [tag]：交给平台的 BCP47 标签，System 为 null 表示跟随系统。 */
enum class AppLanguage(val tag: String?) {
    System(null),
    Chinese("zh"),
    English("en"),
}

internal expect fun platformSettings(): Settings

class AppSettings {

    private val store = platformSettings()

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
        if (!enabled) clearRoomSession()
    }

    /**
     * 上次房间：存 PBKDF2 派生密钥而不是明文密码。密钥是按房间码加盐的，
     * 存储被翻出来最多丢这一间房，不会牵出用户在别处复用的密码。
     */
    fun saveRoomSession(roomCode: String, roomKey: ByteArray) {
        if (!_rememberRoom.value || roomKey.size != RoomCrypto.KEY_SIZE) return
        store.putString(KEY_ROOM_CODE, roomCode)
        store.putString(KEY_ROOM_KEY, Base64.encode(roomKey))
    }

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

    private companion object {
        const val KEY_THEME = "ui.themeMode"
        const val KEY_LANGUAGE = "ui.language"
        const val KEY_REMEMBER_ROOM = "room.remember"
        const val KEY_ROOM_CODE = "room.code"
        const val KEY_ROOM_KEY = "room.key"

        fun themeOf(name: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System

        fun languageOf(name: String?): AppLanguage =
            AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.System
    }
}
