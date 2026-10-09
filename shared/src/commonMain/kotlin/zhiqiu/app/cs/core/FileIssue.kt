package zhiqiu.app.cs.core

import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FileRejectReason

/**
 * 文件通道上的问题。只带类型与参数，不带文案——文案属于 UI 层，要跟着语言走。
 */
sealed class FileIssue {
    abstract val name: String

    data class Rejected(val reason: FileRejectReason, override val name: String) : FileIssue() {
        val maxMb: Int get() = (FileLimits.MAX_FILE_BYTES / 1024 / 1024).toInt()
    }

    /** 对端发来的文件超过本机接受上限。 */
    data class TooLargeIncoming(override val name: String) : FileIssue()

    data class EncryptFailed(override val name: String, val detail: String?) : FileIssue()

    data class UploadFailed(override val name: String, val detail: String?) : FileIssue()

    data class DownloadFailed(override val name: String, val detail: String?) : FileIssue()

    data class DecryptFailed(override val name: String, val detail: String?) : FileIssue()
}
