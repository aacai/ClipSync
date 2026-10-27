package zhiqiu.app.cs.core

/**
 * 给界面看的失败说明：有人话就留下，类名 / 包名这种对用户没意义的丢掉。
 *
 * 以前失败时常把 `FooException` 塞进吐司，弹一下就没了，看不出到底怎么了。
 * 消息全空时按异常种类补一句，避免启动重连失败只剩「出了点问题」。
 */
internal fun Throwable.userFacingDetail(): String? {
    var current: Throwable? = this
    while (current != null) {
        val raw = current.message?.trim().orEmpty()
        if (raw.isNotEmpty() && !looksLikeTypeName(raw)) return raw
        current = current.cause
    }
    return kindHint()
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

/**
 * 异常没有可读 message 时，用类型名猜一句常见原因。
 * 文案保持中性短句，中英文界面都能塞进「连接失败：…」里看懂。
 */
private fun Throwable.kindHint(): String? {
    var current: Throwable? = this
    while (current != null) {
        val name = current::class.simpleName.orEmpty()
        when {
            name.contains("Timeout", ignoreCase = true) -> return "连接超时"
            name.contains("UnknownHost", ignoreCase = true) -> return "找不到服务器"
            name.contains("Unresolved", ignoreCase = true) -> return "找不到服务器"
            name.contains("ConnectException", ignoreCase = true) -> return "连不上服务器"
            name.contains("ConnectionRefused", ignoreCase = true) -> return "连不上服务器"
            name.contains("SSL", ignoreCase = true) ||
                    name.contains("Certificate", ignoreCase = true) -> return "安全连接失败"
            name.contains("SocketException", ignoreCase = true) ||
                    name.contains("SocketTimeout", ignoreCase = true) -> return "网络中断"
            name.contains("EOF", ignoreCase = true) -> return "连接被断开"
            name.contains("Cancellation", ignoreCase = true) -> return null
        }
        current = current.cause
    }
    return null
}
