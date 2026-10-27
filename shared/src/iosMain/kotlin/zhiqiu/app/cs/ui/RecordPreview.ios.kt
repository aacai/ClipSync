package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun RecordPreviewHost(
        preview: RecordPreview?,
        onClose: () -> Unit,
        onShare: () -> Unit,
        onDownload: () -> Unit,
        onDelete: () -> Unit,
) = Unit
