package zhiqiu.app.cs.ui

import zhiqiu.app.cs.core.FileIssue
import zhiqiu.app.cs.translate.TranslateFailure
import zhiqiu.app.cs.translate.TranslateTarget

enum class Level { Info, Warn, Error }

sealed class Feedback {
    abstract val level: Level

    data object ClipboardEmpty : Feedback() {
        override val level get() = Level.Info
    }

    data class ClipboardReadBlocked(val code: String?, val detail: String?) : Feedback() {
        override val level get() = Level.Error
    }

    data class ClipboardWriteBlocked(val code: String?, val detail: String?) : Feedback() {
        override val level get() = Level.Error
    }

    data class Sent(val chars: Int) : Feedback() {
        override val level get() = Level.Info
    }

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

    data class SavedTo(val name: String) : Feedback() {
        override val level get() = Level.Info
    }

    data class DownloadFailed(val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    data class Deleted(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    /** 过期自动清掉的那批不进取消栈，所以也不给撤销按钮。 */
    data class Pruned(val count: Int) : Feedback() {
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

    data class Moved(val position: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class Exported(val count: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class Translated(val target: TranslateTarget, val chars: Int) : Feedback() {
        override val level get() = Level.Info
    }

    data class TranslateFailed(val failure: TranslateFailure, val detail: String? = null) : Feedback() {
        override val level get() = Level.Error
    }

    data class TranslateTooLong(val chars: Int, val limit: Int) : Feedback() {
        override val level get() = Level.Error
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

    data object AutostartFailed : Feedback() {
        override val level get() = Level.Error
    }

    data class FileProblem(val issue: FileIssue) : Feedback() {
        override val level get() = Level.Error
    }
}

enum class CopyTarget { Text, File, Image }
