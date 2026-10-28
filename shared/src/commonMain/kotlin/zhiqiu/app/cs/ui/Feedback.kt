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

    data class Received(
        val sender: String,
        val code: String? = null,
        val detail: String? = null,
        val copied: Boolean = true,
    ) : Feedback() {
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

    /** 这台机器上没有能接住链接的浏览器：说了总比点了没反应强。 */
    data object BrowserMissing : Feedback() {
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

    /** 拖文件进来但还没进房间：和工具栏「发送文件」禁用语义一致，只是给得出文案。 */
    data object DropNeedsRoom : Feedback() {
        override val level get() = Level.Error
    }

    /** 拖进来的是文件夹：本轮不递归，跳过并点名。 */
    data class DropSkippedDirectory(val name: String) : Feedback() {
        override val level get() = Level.Warn
    }

    data object AutostartFailed : Feedback() {
        override val level get() = Level.Error
    }

    data class FileProblem(val issue: FileIssue) : Feedback() {
        override val level get() = Level.Error
    }

    data object ScreenshotTaken : Feedback() {
        override val level get() = Level.Info
    }

    /** 没有屏幕录制权限时 macOS 不报错，只把壁纸给你；不点名说清楚就像功能坏了。 */
    data object ScreenshotPermission : Feedback() {
        override val level get() = Level.Error
    }

    data class ScreenshotTooLarge(val mb: Int) : Feedback() {
        override val level get() = Level.Error
    }

    data class ScreenshotFailed(val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    /** 抓到了屏但一张图都没编出来：没有可说的原因，只能说这次没成。 */
    data object ScreenshotEmpty : Feedback() {
        override val level get() = Level.Error
    }

    data class HotkeySaved(val spec: String) : Feedback() {
        override val level get() = Level.Info
    }

    data class RecordHotkeySaved(val spec: String) : Feedback() {
        override val level get() = Level.Info
    }

    /** 开录成功要说一声，顺带把怎么停止写清楚：药丸小，看不见的人只会以为没开始。 */
    data class RecordStarted(val hotkey: String) : Feedback() {
        override val level get() = Level.Info
    }

    data object RecordingBusy : Feedback() {
        override val level get() = Level.Info
    }

    data object RecordingAutoStopped : Feedback() {
        override val level get() = Level.Info
    }

    data object RecordingTruncated : Feedback() {
        override val level get() = Level.Error
    }

    data object RecordingSaved : Feedback() {
        override val level get() = Level.Info
    }

    data object RecordingLocal : Feedback() {
        override val level get() = Level.Info
    }

    data class RecordingLocalOnly(val mb: Int) : Feedback() {
        override val level get() = Level.Warn
    }

    data class RecordingFailed(val reason: String) : Feedback() {
        override val level get() = Level.Error
    }

    data object RecordingCopied : Feedback() {
        override val level get() = Level.Info
    }

    data object RecordingCopyFailed : Feedback() {
        override val level get() = Level.Error
    }

    data class RecordingDownloaded(val name: String) : Feedback() {
        override val level get() = Level.Info
    }

    data object RecordingDownloadFailed : Feedback() {
        override val level get() = Level.Error
    }

    data object RecordingEmpty : Feedback() {
        override val level get() = Level.Error
    }

    data class HotkeyFailed(val spec: String) : Feedback() {
        override val level get() = Level.Error
    }
}

enum class CopyTarget { Text, File, Image }
