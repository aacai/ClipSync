package zhiqiu.app.cs.core

/**
 * 截屏全局快捷键的组合键写法。
 *
 * 字符串的形状和 Rust 端 `screenshot::spec()` 一模一样：修饰键按 Command、Control、Alt、Shift
 * 排前面，键名排在最后。两端各存各的设置，但同一条「Alt+A」在两边必须是同一个键。
 */
enum class HotkeyModifier(val label: String, val aliases: Set<String>) {
    Command("Command", setOf("CMD", "SUPER", "META")),
    Control("Control", setOf("CTRL")),
    Alt("Alt", setOf("OPTION")),
    Shift("Shift", emptySet()),
}

data class ScreenshotHotkey(val modifiers: Set<HotkeyModifier>, val key: String) {

    override fun toString(): String =
        (HotkeyModifier.entries.filter { it in modifiers }.map { it.label } + key)
            .joinToString(separator = "+")

    companion object {
        const val DEFAULT_SPEC: String = "Alt+A"
        const val RECORD_DEFAULT_SPEC: String = "Alt+Shift+A"

        /** 平台侧注册表（macOS 虚拟键码）能覆盖的键；不在这里的一律当不能做快捷键。 */
        val keys: Set<String> = buildSet {
            ('A'..'Z').forEach { add(it.toString()) }
            (0..9).forEach { add(it.toString()) }
            (1..12).forEach { add("F$it") }
            addAll(
                listOf(
                    "Space",
                    "Enter",
                    "Tab",
                    "Backspace",
                    "Delete",
                    "Home",
                    "End",
                    "PageUp",
                    "PageDown",
                    "ArrowUp",
                    "ArrowDown",
                    "ArrowLeft",
                    "ArrowRight",
                    "Minus",
                    "Equal",
                    "BracketLeft",
                    "BracketRight",
                    "Backslash",
                    "Semicolon",
                    "Quote",
                    "Comma",
                    "Period",
                    "Slash",
                    "Backquote",
                ),
            )
        }

        /** 只认自己写得出来的形状：`A+Shift` 这种键在前的直接拒，和 Rust 的 `parse` 保持一致。 */
        fun parse(spec: String): ScreenshotHotkey? {
            val parts = spec.split('+').map { it.trim() }
            val key = parts.lastOrNull()?.let { canonicalKey(it) } ?: return null
            if (parts.size < 2) return null
            val modifiers = mutableSetOf<HotkeyModifier>()
            for (part in parts.dropLast(1)) {
                val modifier = modifierOf(part) ?: return null
                if (!modifiers.add(modifier)) return null
            }
            return ScreenshotHotkey(modifiers, key)
        }

        /** Rust 的键名不分大小写，手写的「ctrl+shift+a」要能存成规范串。 */
        private fun canonicalKey(token: String): String? =
            keys.firstOrNull { it.equals(token, ignoreCase = true) }

        /** Rust 那边 Ctrl / Cmd / Option 也算，手写的配置两端得认同一份。 */
        private fun modifierOf(token: String): HotkeyModifier? {
            val upper = token.uppercase()
            return HotkeyModifier.entries.firstOrNull {
                it.label.uppercase() == upper || upper in it.aliases
            }
        }

        /** 录键用：没有任何修饰键的裸键不能当全局快捷键。 */
        fun of(
            key: String,
            command: Boolean = false,
            control: Boolean = false,
            alt: Boolean = false,
            shift: Boolean = false,
        ): ScreenshotHotkey? {
            if (key !in keys) return null
            val modifiers = buildSet {
                if (command) add(HotkeyModifier.Command)
                if (control) add(HotkeyModifier.Control)
                if (alt) add(HotkeyModifier.Alt)
                if (shift) add(HotkeyModifier.Shift)
            }
            if (modifiers.isEmpty()) return null
            return ScreenshotHotkey(modifiers, key)
        }
    }
}
