package zhiqiu.app.cs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.TextOp
import zhiqiu.app.cs.files.displayTitle
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.resources.*

@Composable
internal fun TextEntryDialog(
        title: StringResource,
        initial: String,
        placeholder: StringResource,
        confirm: StringResource,
        supporting: StringResource? = null,
        allowBlank: Boolean = false,
        minLines: Int = 3,
        onDismiss: () -> Unit,
        onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(title), fontSize = 17.sp) },
            text = {
                Column {
                    OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(stringResource(placeholder)) },
                            minLines = minLines,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (supporting != null) {
                        Text(
                                stringResource(supporting),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                        onClick = { onSubmit(text) },
                        enabled = allowBlank || text.isNotBlank(),
                ) {
                    Text(stringResource(confirm), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun NewEntryDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    TextEntryDialog(
            title = Res.string.new_entry_title,
            initial = "",
            placeholder = Res.string.new_entry_placeholder,
            confirm = Res.string.send,
            onDismiss = onDismiss,
            onSubmit = onSubmit,
    )
}

@Composable
internal fun EditDialog(item: ClipItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    TextEntryDialog(
            title = Res.string.edit_title,
            initial = item.text,
            placeholder = Res.string.new_entry_placeholder,
            confirm = Res.string.save,
            onDismiss = onDismiss,
            onSubmit = onSave,
    )
}

@Composable
internal fun NoteDialog(item: ClipItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    TextEntryDialog(
            title = Res.string.note_title,
            initial = item.note,
            placeholder = Res.string.note_placeholder,
            confirm = Res.string.save,
            supporting = Res.string.note_hint,
            allowBlank = true,
            minLines = 2,
            onDismiss = onDismiss,
            onSubmit = onSave,
    )
}

@Composable
internal fun TransformDialog(text: String, onDismiss: () -> Unit, onApply: (TextOp) -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.transform_title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                            text.lineSequence().first().take(72).ifBlank { stringResource(Res.string.empty_text) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                    )
                    Text(
                            stringResource(Res.string.transform_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                    )
                    TextOp.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { op ->
                                TransformButton(op, Modifier.weight(1f)) {
                                    onDismiss()
                                    onApply(op)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun TransformButton(op: TextOp, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            shape = MaterialTheme.shapes.small,
    ) {
        Text(opLabel(op), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun opLabel(op: TextOp): String =
        stringResource(
                when (op) {
                    TextOp.Trim -> Res.string.op_trim
                    TextOp.StripBlankLines -> Res.string.op_strip_blank
                    TextOp.SortLines -> Res.string.op_sort
                    TextOp.SortLinesDesc -> Res.string.op_sort_desc
                    TextOp.UniqueLines -> Res.string.op_unique
                    TextOp.Upper -> Res.string.op_upper
                    TextOp.Lower -> Res.string.op_lower
                    TextOp.JoinLines -> Res.string.op_join
                    TextOp.UrlEncode -> Res.string.op_url_encode
                    TextOp.UrlDecode -> Res.string.op_url_decode
                    TextOp.Base64Encode -> Res.string.op_b64_encode
                    TextOp.Base64Decode -> Res.string.op_b64_decode
                }
        )

@Composable
internal fun DetailDialog(item: ClipItem, now: Long, onDismiss: () -> Unit, onCopy: () -> Unit) {
    val isFile = item.kind == ClipRepository.KIND_FILE
    val file = item.file
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.detail_title), fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    MaterialTheme.shapes.small,
                                            )
                                            .padding(10.dp)
                                            .verticalScroll(rememberScrollState()),
                    ) {
                        val body =
                                if (isFile) {
                                    file?.localPath ?: stringResource(Res.string.not_downloaded)
                                } else {
                                    item.text
                                }
                        Text(
                                body,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                        )
                    }

                    val meta = buildList {
                        add(relTime(item.ts, now))
                        add(stringResource(Res.string.from_sender, item.senderName))
                        if (isFile && file != null) {
                            add(displayTitle(file))
                            add(file.mime)
                            add(humanSize(file.size))
                            val left = file.expiresAt - now
                            if (left > 0) add(stringResource(Res.string.expires_in, humanLeft(left)))
                        }
                        if (item.pinned) add(stringResource(Res.string.pinned))
                    }
                    Text(
                            meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                    )

                    if (item.note.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        SectionLabel(Res.string.detail_note)
                        Text(item.note, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(10.dp))
                    SectionLabel(Res.string.detail_id)
                    Text(item.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = onCopy) { Text(stringResource(Res.string.cd_copy_text)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun ClearConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.clear_all_title), fontSize = 17.sp) },
            text = { Text(stringResource(Res.string.clear_all_body), style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                        onClick = {
                            onDismiss()
                            onConfirm()
                        },
                ) {
                    Text(
                            stringResource(Res.string.clear),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}
