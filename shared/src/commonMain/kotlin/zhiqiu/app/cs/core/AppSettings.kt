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

    private companion object {
        const val KEY_THEME = "ui.themeMode"
        const val KEY_LANGUAGE = "ui.language"

        fun themeOf(name: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System

        fun languageOf(name: String?): AppLanguage =
            AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.System
    }
}
