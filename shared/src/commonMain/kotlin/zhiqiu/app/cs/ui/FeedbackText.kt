package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.FileIssue
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FileRejectReason
import zhiqiu.app.cs.resources.*

@Composable
internal fun feedbackText(feedback: Feedback): String =
        when (feedback) {
            Feedback.ClipboardEmpty -> stringResource(Res.string.fb_clipboard_empty)
            is Feedback.ClipboardReadBlocked ->
                stringResource(Res.string.fb_read_blocked, clipboardReason(feedback.code, feedback.detail))
            is Feedback.ClipboardWriteBlocked ->
                stringResource(Res.string.fb_write_blocked, clipboardReason(feedback.code, feedback.detail))
            is Feedback.Sent -> stringResource(Res.string.fb_sent, feedback.chars)
            is Feedback.Received ->
                if (feedback.code == null && feedback.detail == null) {
                    stringResource(Res.string.fb_received, feedback.sender)
                } else {
                    stringResource(
                            Res.string.fb_received_blocked,
                            feedback.sender,
                            clipboardReason(feedback.code, feedback.detail),
                    )
                }
            is Feedback.Copied ->
                when (feedback.what) {
                    CopyTarget.Text -> stringResource(Res.string.fb_copied_text)
                    CopyTarget.File -> stringResource(Res.string.fb_copied_file)
                    CopyTarget.Image -> stringResource(Res.string.fb_copied_image)
                }
            is Feedback.FileSent -> stringResource(Res.string.fb_file_sent, feedback.name)
            is Feedback.Downloaded -> stringResource(Res.string.fb_downloaded, feedback.name)
            is Feedback.DownloadFailed -> stringResource(Res.string.fb_download_failed, feedback.reason)
            is Feedback.Deleted -> pluralStringResource(Res.plurals.fb_deleted, feedback.count, feedback.count)
            is Feedback.Undeleted -> pluralStringResource(Res.plurals.fb_undeleted, feedback.count, feedback.count)
            is Feedback.Edited -> pluralStringResource(Res.plurals.fb_edit, feedback.count, feedback.count)
            is Feedback.Duplicated ->
                pluralStringResource(Res.plurals.fb_duplicated, feedback.count, feedback.count)
            is Feedback.Moved -> stringResource(Res.string.fb_moved, feedback.position)
            is Feedback.Exported -> pluralStringResource(Res.plurals.fb_exported, feedback.count, feedback.count)
            is Feedback.PinChanged ->
                stringResource(
                        if (feedback.pinned) Res.string.fb_pinned else Res.string.fb_unpinned,
                        feedback.count,
                )
            is Feedback.SendFailed -> stringResource(Res.string.fb_send_failed, feedback.reason)
            Feedback.RoomCodeRequired -> stringResource(Res.string.fb_room_required)
            is Feedback.ConnectFailed -> stringResource(Res.string.fb_connect_failed, feedback.reason)
            is Feedback.LocalFileReadFailed ->
                stringResource(Res.string.fb_local_read_failed, feedback.name, feedback.reason)
            is Feedback.FileProblem -> fileIssueText(feedback.issue)
        }

@Composable
internal fun clipboardReason(code: String?, detail: String?): String =
        when (code) {
            "denied" -> stringResource(Res.string.reason_denied)
            "insecure" -> stringResource(Res.string.reason_insecure)
            else -> detail ?: code ?: ""
        }

@Composable
internal fun fileIssueText(issue: FileIssue): String {
    val maxMb = (FileLimits.MAX_FILE_BYTES / 1024 / 1024).toInt()
    return when (issue) {
        is FileIssue.Rejected ->
            when (issue.reason) {
                FileRejectReason.TooLarge -> stringResource(Res.string.issue_too_large, maxMb, issue.name)
                FileRejectReason.Empty -> stringResource(Res.string.issue_empty, issue.name)
                is FileRejectReason.InvalidName -> stringResource(Res.string.issue_bad_name, issue.name)
            }
        is FileIssue.TooLargeIncoming -> stringResource(Res.string.issue_too_large_incoming, maxMb, issue.name)
        is FileIssue.EncryptFailed -> stringResource(Res.string.issue_encrypt, issue.detail.orEmpty())
        is FileIssue.UploadFailed -> stringResource(Res.string.issue_upload, issue.detail.orEmpty())
        is FileIssue.DownloadFailed -> stringResource(Res.string.fb_download_failed, issue.detail.orEmpty())
        is FileIssue.DecryptFailed -> stringResource(Res.string.issue_decrypt, issue.detail.orEmpty())
    }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun relTime(ts: Long, now: Long): String {
    val d = (now - ts).coerceAtLeast(0)
    return when {
        d < 60_000L -> stringResource(Res.string.rel_just_now)
        d < 3_600_000L -> stringResource(Res.string.rel_minutes, (d / 60_000L).toInt())
        d < 86_400_000L -> stringResource(Res.string.rel_hours, (d / 3_600_000L).toInt())
        d < 30L * 86_400_000L -> stringResource(Res.string.rel_days, (d / 86_400_000L).toInt())
        else -> stringResource(Res.string.rel_months, (d / (30L * 86_400_000L)).toInt())
    }
}

@Composable
internal fun humanLeft(ms: Long): String =
        when {
            ms < 3_600_000L -> stringResource(Res.string.dur_minutes, (ms / 60_000L).toInt())
            ms < 86_400_000L -> stringResource(Res.string.dur_hours, (ms / 3_600_000L).toInt())
            else -> stringResource(Res.string.dur_days, (ms / 86_400_000L).toInt())
        }

internal fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
