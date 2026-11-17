package zhiqiu.app.cs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.cancel
import zhiqiu.app.cs.resources.qr_copy_payload
import zhiqiu.app.cs.resources.qr_empty
import zhiqiu.app.cs.resources.qr_no_link
import zhiqiu.app.cs.resources.qr_title
import zhiqiu.app.cs.resources.qr_too_long

/** 二维码内容过长时难以可靠扫码，约 2.5KB UTF-8 上限。 */
internal const val QR_MAX_BYTES = 2500

/** 文本 → 内容；文件 → 已有下载链接。 */
internal fun qrPayloadFor(item: ClipItem): Result<String> {
    return if (item.kind == ClipRepository.KIND_FILE) {
        val link = item.file?.link?.trim().orEmpty()
        when {
            link.startsWith("http") -> Result.success(link)
            else -> Result.failure(IllegalStateException("no_link"))
        }
    } else {
        val text = item.text
        if (text.isBlank()) Result.failure(IllegalStateException("empty"))
        else Result.success(text)
    }
}

@Composable
internal fun QrCodeDialog(
    payload: String?,
    errorKey: String?,
    onCopy: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val tooLong = !payload.isNullOrEmpty() && payload.encodeToByteArray().size > QR_MAX_BYTES
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.qr_title), fontSize = 17.sp) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    errorKey == "no_link" ->
                        Text(
                            stringResource(Res.string.qr_no_link),
                            color = MaterialTheme.colorScheme.error,
                        )
                    errorKey == "empty" || payload.isNullOrEmpty() ->
                        Text(
                            stringResource(Res.string.qr_empty),
                            color = MaterialTheme.colorScheme.error,
                        )
                    tooLong ->
                        Text(
                            stringResource(Res.string.qr_too_long),
                            color = MaterialTheme.colorScheme.error,
                        )
                    else -> {
                        Image(
                            painter = rememberQrCodePainter(payload),
                            contentDescription = stringResource(Res.string.qr_title),
                            modifier =
                                Modifier
                                    .size(240.dp)
                                    .background(Color.White)
                                    .padding(4.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer {
                            Text(
                                payload,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!tooLong && !payload.isNullOrEmpty() && errorKey == null && onCopy != null) {
                TextButton(onClick = onCopy) {
                    Text(stringResource(Res.string.qr_copy_payload))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
