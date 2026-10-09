package zhiqiu.app.cs.core

enum class AppLanguageSupport {
    Immediate,
    AfterRestart,
    Unsupported,
}

internal expect val appLanguageSupport: AppLanguageSupport

internal expect fun applyAppLanguage(language: AppLanguage)
