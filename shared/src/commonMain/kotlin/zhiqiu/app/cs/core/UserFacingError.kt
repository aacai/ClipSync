package zhiqiu.app.cs.core

/**
 * 给界面看的失败说明：有人话就留下，类名 / 包名这种对用户没意义的丢掉。
 *
 * 以前失败时常把 `FooException` 塞进吐司，弹一下就没了，看不出到底怎么了。
 */
internal fun Throwable.userFacingDetail(): String? {
    var current: Throwable? = this
    while (current != null) {
        val raw = current.message?.trim().orEmpty()
        if (raw.isNotEmpty() && !looksLikeTypeName(raw)) return raw
        current = current.cause
    }
    return null
}

/** 沿着 cause 链找有没有这种异常：协程一包，顶层往往不是原来那个类型。 */
internal inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}

/** 纯 ASCII 标识符 / 全限定名：多半是异常类名，不是给人看的句子。 */
internal fun looksLikeTypeName(value: String): Boolean =
        value.matches(Regex("""[A-Za-z][\w.$]*"""))
