package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable

/**
 * 录完自动弹出的预览窗口。桌面端才有真正的播放器；别的平台没有本地录像，直接不显示。
 */
@Composable
internal expect fun RecordPreviewHost(
        preview: RecordPreview?,
        onClose: () -> Unit,
        onShare: () -> Unit,
        onDownload: () -> Unit,
        onDelete: () -> Unit,
)
