package zhiqiu.app.cs.ui

import zhiqiu.app.cs.core.FileIssue

/** 提示级别：决定条目的配色与图标。 */
enum class Level { Info, Warn, Error }

/** 一次性操作结果：只带类型与参数，文案由 UI 层按语言从资源取。 */
sealed class Feedback {
    abstract val level: Level

    data object ClipboardEmpty : Feedback() {
        override val level get() = Level.Info
    }

    /** [code] 是平台侧错误码（如浏览器的 denied / insecure），null 表示只有原始异常信息。 */
    data class ClipboardReadBlocked(val code: String?, val detail: String?) : Feedback() {
        override val level get() = Level.Error
    }

    data class ClipboardWriteBlocked(val code: String?, val detail: String?) : Feedback() {
        override val level get() = Level.Error
    }

    data class Sent(val chars: Int) : Feedback() {
        override val level get() = Level.Info
    }

    /** 收到文本；[code] 非空表示没能写进系统剪贴板。 */
    data class Received(val sender: String, val code: String? = null, val detail: String? = null) : Feedback() {
        override val level get() = if (code == null && detail == null) Level.Info else Level.Warn
    }

    data class Copied(val what: CopyTarget) : Feedback() {
        override val level get() = Level.Info
    }

    data class FileSent(val name: String) : Feedback() {
        override val level get() = Level.Info
    }

    data class Downloaded(val name: String) : Feedback() {
        override val level get() = Level.Info
    }

    data class DownloadFailed(val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    data class Deleted(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class Undeleted(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class Edited(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class PinChanged(val count: Int, val pinned: Boolean) : Feedback() {
        override val level get() = Level.Info
    }

    data class Duplicated(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    /** 移动到指定位置后的落点（第 [position] 位）。 */
    data class Moved(val position: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class Exported(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class SendFailed(val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    data object RoomCodeRequired : Feedback() {
        override val level get() = Level.Error
    }

    data class ConnectFailed(val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    data class LocalFileReadFailed(val name: String, val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    data class FileProblem(val issue: FileIssue) : Feedback() {
        override val level get() = Level.Error
    }
}

enum class CopyTarget { Text, File, Image }
