package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.FileIssue
import zhiqiu.app.cs.core.looksLikeTypeName
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.files.FileRejectReason
import zhiqiu.app.cs.translate.TranslateFailure
import zhiqiu.app.cs.translate.TranslateTarget
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
                when {
                    feedback.code != null || feedback.detail != null ->
                        stringResource(
                                Res.string.fb_received_blocked,
                                feedback.sender,
                                clipboardReason(feedback.code, feedback.detail),
                        )
                    !feedback.copied -> stringResource(Res.string.fb_received_saved, feedback.sender)
                    else -> stringResource(Res.string.fb_received, feedback.sender)
                }
            is Feedback.Copied ->
                when (feedback.what) {
                    CopyTarget.Text -> stringResource(Res.string.fb_copied_text)
                    CopyTarget.File -> stringResource(Res.string.fb_copied_file)
                    CopyTarget.Image -> stringResource(Res.string.fb_copied_image)
                }
            is Feedback.FileSent -> stringResource(Res.string.fb_file_sent, feedback.name)
            is Feedback.Downloaded -> stringResource(Res.string.fb_downloaded, feedback.name)
            is Feedback.SavedTo -> stringResource(Res.string.fb_saved, feedback.name)
            is Feedback.DownloadFailed ->
                if (feedback.reason.isBlank() || looksLikeTypeName(feedback.reason)) {
                    stringResource(Res.string.fb_download_failed_generic)
                } else {
                    stringResource(Res.string.fb_download_failed, feedback.reason)
                }
            is Feedback.BrowserMissing -> stringResource(Res.string.fb_browser_missing)
            is Feedback.Deleted -> pluralStringResource(Res.plurals.fb_deleted, feedback.count, feedback.count)
            is Feedback.Pruned -> pluralStringResource(Res.plurals.fb_pruned, feedback.count, feedback.count)
            is Feedback.Undeleted -> pluralStringResource(Res.plurals.fb_undeleted, feedback.count, feedback.count)
            is Feedback.Edited -> pluralStringResource(Res.plurals.fb_edit, feedback.count, feedback.count)
            is Feedback.Duplicated ->
                pluralStringResource(Res.plurals.fb_duplicated, feedback.count, feedback.count)
            is Feedback.Moved -> stringResource(Res.string.fb_moved, feedback.position)
            is Feedback.Exported -> pluralStringResource(Res.plurals.fb_exported, feedback.count, feedback.count)
            is Feedback.Translated ->
                stringResource(
                        if (feedback.target == TranslateTarget.English) Res.string.fb_translated_en
                        else Res.string.fb_translated_zh,
                        feedback.chars,
                )
            is Feedback.TranslateFailed -> translateFailureText(feedback.failure, feedback.detail)
            is Feedback.TranslateTooLong ->
                stringResource(Res.string.fb_translate_too_long, feedback.chars, feedback.limit)
            is Feedback.PinChanged ->
                stringResource(
                        if (feedback.pinned) Res.string.fb_pinned else Res.string.fb_unpinned,
                        feedback.count,
                )
            is Feedback.SendFailed ->
                if (feedback.reason.isBlank() || looksLikeTypeName(feedback.reason)) {
                    stringResource(Res.string.fb_send_failed_generic)
                } else {
                    stringResource(Res.string.fb_send_failed, feedback.reason)
                }
            Feedback.RoomCodeRequired -> stringResource(Res.string.fb_room_required)
            is Feedback.ConnectFailed ->
                if (feedback.reason.isBlank() || looksLikeTypeName(feedback.reason)) {
                    stringResource(Res.string.fb_connect_failed_generic)
                } else {
                    stringResource(Res.string.fb_connect_failed, feedback.reason)
                }
            is Feedback.LocalFileReadFailed ->
                stringResource(
                        Res.string.fb_local_read_failed,
                        feedback.name,
                        feedback.reason.takeUnless { it.isBlank() || looksLikeTypeName(it) }
                                ?: stringResource(Res.string.fb_generic_failed),
                )
            Feedback.DropNeedsRoom -> stringResource(Res.string.fb_drop_needs_room)
            is Feedback.DropSkippedDirectory ->
                    stringResource(Res.string.fb_drop_skipped_directory, feedback.name)
            Feedback.AutostartFailed -> stringResource(Res.string.fb_autostart_failed)
            Feedback.ScreenshotTaken -> stringResource(Res.string.fb_screenshot_taken)
            Feedback.ScreenshotPermission -> stringResource(Res.string.fb_screenshot_permission)
            is Feedback.ScreenshotTooLarge ->
                stringResource(Res.string.fb_screenshot_too_large, feedback.mb)
            is Feedback.ScreenshotFailed ->
                if (feedback.reason.isBlank() || looksLikeTypeName(feedback.reason)) {
                    stringResource(Res.string.fb_screenshot_failed_generic)
                } else {
                    stringResource(Res.string.fb_screenshot_failed, feedback.reason)
                }
            Feedback.ScreenshotEmpty -> stringResource(Res.string.fb_screenshot_empty)
            is Feedback.HotkeySaved -> stringResource(Res.string.fb_hotkey_saved, feedback.spec)
            is Feedback.RecordHotkeySaved -> stringResource(Res.string.fb_record_hotkey_saved, feedback.spec)
            is Feedback.RecordStarted -> stringResource(Res.string.fb_record_started, feedback.hotkey)
            Feedback.RecordingBusy -> stringResource(Res.string.fb_record_busy)
            Feedback.RecordingAutoStopped -> stringResource(Res.string.fb_record_auto_stopped)
            Feedback.RecordingTruncated -> stringResource(Res.string.fb_record_truncated)
            Feedback.RecordingSaved -> stringResource(Res.string.fb_record_saved)
            Feedback.RecordingLocal -> stringResource(Res.string.fb_record_local)
            is Feedback.RecordingLocalOnly -> stringResource(Res.string.fb_record_local_only, feedback.mb)
            is Feedback.RecordingFailed -> stringResource(Res.string.fb_record_failed, feedback.reason)
            Feedback.RecordingEmpty -> stringResource(Res.string.fb_record_empty)
            Feedback.RecordingCopied -> stringResource(Res.string.fb_record_copied)
            Feedback.RecordingCopyFailed -> stringResource(Res.string.fb_record_copy_failed)
            is Feedback.RecordingDownloaded -> stringResource(Res.string.fb_record_downloaded, feedback.name)
            Feedback.RecordingDownloadFailed -> stringResource(Res.string.fb_record_download_failed)
            is Feedback.HotkeyFailed -> stringResource(Res.string.fb_hotkey_failed, feedback.spec)
            is Feedback.FileProblem -> fileIssueText(feedback.issue)
        }

/** 翻译失败的对外文案：弹窗内联展示和吐司共用同一份。 */
@Composable
internal fun translateFailureText(failure: TranslateFailure, detail: String? = null): String =
        when (failure) {
            TranslateFailure.Malformed -> stringResource(Res.string.fb_translate_failed_malformed)
            TranslateFailure.Empty -> stringResource(Res.string.fb_translate_failed_empty)
            TranslateFailure.Rejected ->
                    stringResource(Res.string.fb_translate_failed_rejected, detail.orEmpty())
            TranslateFailure.Request ->
                    if (detail == null) stringResource(Res.string.fb_translate_failed)
                    else stringResource(Res.string.fb_translate_failed_request, detail)
        }

@Composable
internal fun clipboardReason(code: String?, detail: String?): String =
        when (code) {
            "denied" -> stringResource(Res.string.reason_denied)
            "insecure" -> stringResource(Res.string.reason_insecure)
            "unavailable" -> detail ?: stringResource(Res.string.reason_unavailable)
            else -> detail ?: code?.takeUnless { looksLikeTypeName(it) }
                            ?: stringResource(Res.string.reason_unavailable)
        }

@Composable
internal fun fileIssueText(issue: FileIssue): String {
    val maxMb = FileLimits.maxMb
    return when (issue) {
        is FileIssue.Rejected ->
            when (issue.reason) {
                FileRejectReason.TooLarge -> stringResource(Res.string.issue_too_large, maxMb, issue.name)
                FileRejectReason.Empty -> stringResource(Res.string.issue_empty, issue.name)
                FileRejectReason.InvalidName -> stringResource(Res.string.issue_bad_name, issue.name)
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
