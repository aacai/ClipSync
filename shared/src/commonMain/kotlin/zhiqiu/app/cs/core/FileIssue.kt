package zhiqiu.app.cs.core

import zhiqiu.app.cs.files.FileRejectReason

sealed class FileIssue {
    abstract val name: String

    data class Rejected(val reason: FileRejectReason, override val name: String) : FileIssue()

    data class TooLargeIncoming(override val name: String) : FileIssue()

    data class EncryptFailed(override val name: String, val detail: String?) : FileIssue()

    data class UploadFailed(override val name: String, val detail: String?) : FileIssue()

    data class DownloadFailed(override val name: String, val detail: String?) : FileIssue()

    data class DecryptFailed(override val name: String, val detail: String?) : FileIssue()
}
