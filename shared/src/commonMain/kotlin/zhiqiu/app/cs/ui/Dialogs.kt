package zhiqiu.app.cs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.ClipProtocol
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.core.DevicePresence
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.MoveInfo
import zhiqiu.app.cs.files.TextOp
import zhiqiu.app.cs.files.displayTitle
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.files.isImage
import zhiqiu.app.cs.resources.*
import zhiqiu.app.cs.translate.MAX_TRANSLATE_CHARS
import zhiqiu.app.cs.translate.TranslateFailure
import zhiqiu.app.cs.translate.TranslateTarget
import zhiqiu.app.cs.translate.TranslationException
import zhiqiu.app.cs.translate.detectTarget

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

/**
 * 中英互译弹窗：选方向 → 点翻译 → 展示译文，再由用户决定复制 / 另存 / 替换。
 * 不再像旧版那样静默翻完直接塞一条历史、只弹个提示。
 */
@Composable
internal fun TranslateDialog(
        item: ClipItem,
        model: AppModel,
        onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val source = item.text
    var target by remember { mutableStateOf(detectTarget(source)) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<Pair<TranslateFailure, String?>?>(null) }
    val tooLong = source.length > MAX_TRANSLATE_CHARS

    fun run() {
        if (running || tooLong) return
        running = true
        failure = null
        scope.launch {
            runCatching { model.translateText(source, target) }
                    .onSuccess { result = it }
                    .onFailure { e ->
                            failure =
                                    if (e is TranslationException) e.failure to e.detail
                                    else TranslateFailure.Request to e.message
                    }
            running = false
        }
    }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.translate_title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                    ) {
                        DirectionChip(
                                label = stringResource(Res.string.translate_to_en),
                                selected = target == TranslateTarget.English,
                                onClick = { target = TranslateTarget.English },
                        )
                        DirectionChip(
                                label = stringResource(Res.string.translate_to_zh),
                                selected = target == TranslateTarget.Chinese,
                                onClick = { target = TranslateTarget.Chinese },
                        )
                    }
                    SectionLabel(Res.string.translate_source)
                    ReadOnlyTextBox(source)
                    if (tooLong) {
                        Text(
                                stringResource(
                                        Res.string.fb_translate_too_long,
                                        source.length,
                                        MAX_TRANSLATE_CHARS,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val failed = failure
                    if (failed != null) {
                        Text(
                                translateFailureText(failed.first, failed.second),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val translated = result
                    if (translated != null) {
                        SectionLabel(Res.string.translate_result)
                        ReadOnlyTextBox(translated)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { model.copyToClipboard(translated) }) {
                                Text(
                                        stringResource(Res.string.translate_copy_result),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                )
                            }
                            TextButton(
                                    onClick = {
                                            model.addEntry(translated)
                                            onDismiss()
                                    },
                            ) {
                                Text(stringResource(Res.string.translate_save_new), fontSize = 12.sp, maxLines = 1)
                            }
                            TextButton(
                                    onClick = {
                                            model.editItem(item, translated)
                                            onDismiss()
                                    },
                            ) {
                                Text(stringResource(Res.string.translate_replace), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = { run() },
                        enabled = !running && !tooLong,
                        shape = MaterialTheme.shapes.small,
                ) {
                    if (running) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                            stringResource(
                                    if (running) Res.string.translate_running else Res.string.translate_run
                            ),
                    )
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
            modifier =
                    Modifier.clip(MaterialTheme.shapes.extraSmall)
                            .background(if (selected) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceVariant)
                            .border(
                                    1.dp,
                                    if (selected) scheme.primary.copy(alpha = 0.55f) else Color.Transparent,
                                    MaterialTheme.shapes.extraSmall,
                            )
                            .clickable(onClick = onClick)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
                label,
                fontSize = 12.sp,
                color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 只读文本块：原文/译文共用，限高 + 内部滚动，长内容不把弹窗撑爆。 */
@Composable
private fun ReadOnlyTextBox(text: String) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
    ) {
        Text(
                text.ifBlank { stringResource(Res.string.empty_text) },
                style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun MoveToPositionDialog(info: MoveInfo, onDismiss: () -> Unit, onSubmit: (Int) -> Unit) {
    var input by remember { mutableStateOf(info.position.toString()) }
    val target = input.trim().toIntOrNull()
    val outOfRange = target != null && target !in info.from..info.to
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.move_to_title), fontSize = 17.sp) },
            text = {
                Column {
                    OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.filter(Char::isDigit).take(6) },
                            singleLine = true,
                            isError = outOfRange,
                            label = { Text(stringResource(Res.string.move_to_position)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Text(
                            if (outOfRange) stringResource(Res.string.move_to_invalid, info.from, info.to)
                            else stringResource(Res.string.move_to_hint, info.from, info.to, info.position),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (outOfRange) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                        onClick = { target?.let(onSubmit) },
                        enabled = target != null && !outOfRange && target != info.position,
                ) {
                    Text(stringResource(Res.string.confirm), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun DetailDialog(
        item: ClipItem,
        model: AppModel,
        now: Long,
        onDismiss: () -> Unit,
        onCopy: () -> Unit,
        onZoom: () -> Unit,
) {
    val isFile = item.kind == ClipRepository.KIND_FILE
    val file = item.file
    val path = file?.localPath?.takeIf { isImage(file.mime) }
    val thumb = rememberImageBitmap(path, THUMB_MAX_SIDE) { model.cachedBytes(item) }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.detail_title), fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    if (thumb != null) {
                        Box(
                                modifier =
                                        Modifier.padding(bottom = 10.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable(onClick = onZoom),
                        ) {
                            Image(
                                    bitmap = thumb,
                                    contentDescription = stringResource(Res.string.cd_zoom_image),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            )
                        }
                    }
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
internal fun ImageViewDialog(item: ClipItem, model: AppModel, onDismiss: () -> Unit) {
    val file = item.file
    val path = file?.localPath?.takeIf { isImage(file.mime) }
    val bitmap = rememberImageBitmap(path, ZOOM_MAX_SIDE) { model.cachedBytes(item) }
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.88f))
                                .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null ->
                        Image(
                                bitmap = bitmap,
                                contentDescription = file?.let { displayTitle(it) },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(28.dp),
                        )
                path == null ->
                        Text(
                                stringResource(Res.string.not_downloaded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                        )
                else ->
                        CircularProgressIndicator(
                                Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                        )
            }
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                if (model.supportsSaveToDownloads) {
                    IconButton(onClick = { model.saveToDownloads(item) }) {
                        Icon(
                                AppIcons.Download,
                                contentDescription = stringResource(Res.string.act_save_to_downloads),
                                tint = Color.White,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                            AppIcons.Close,
                            contentDescription = stringResource(Res.string.cd_close_image),
                            tint = Color.White,
                    )
                }
            }
        }
    }
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

@Composable
internal fun LeaveRoomDialog(room: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.leave_room_title), fontSize = 17.sp) },
            text = {
                Text(
                        stringResource(Res.string.leave_room_body, room),
                        style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                            stringResource(Res.string.action_leave_room),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun DevicesDialog(
        devices: List<String>,
        onDismiss: () -> Unit,
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.devices_title), fontSize = 17.sp) },
            text = {
                if (devices.isEmpty()) {
                    Text(
                            stringResource(Res.string.devices_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        devices.forEachIndexed { index, name ->
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                            MaterialTheme.shapes.small,
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                        AppIcons.Devices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                        name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                )
                                // 列表第一项是本机（进房后拼在 peers 前面）
                                if (index == 0) {
                                    Text(
                                            stringResource(Res.string.devices_this),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.safety_close)) }
            },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun SafetyDialog(
        safety: String,
        peers: List<DevicePresence>,
        onDismiss: () -> Unit,
        onCopy: () -> Unit,
) {
    val mine = ClipProtocol.safetyPrefix(safety)
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.safety_title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    MaterialTheme.shapes.small,
                                            )
                                            .padding(horizontal = 10.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                    ) {
                        Text(
                                safety,
                                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.4.sp),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center,
                        )
                    }

                    SafetyPoint(1, Res.string.safety_point_what)
                    SafetyPoint(2, Res.string.safety_point_verify)
                    SafetyPoint(3, Res.string.safety_point_mismatch)

                    if (peers.isNotEmpty()) {
                        SectionLabel(Res.string.safety_peers)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            peers.forEach { peer ->
                                val known = peer.safety.isNotEmpty()
                                val match = known && peer.safety == mine
                                val label =
                                        when {
                                            !known -> Res.string.safety_unknown
                                            match -> Res.string.safety_match
                                            else -> Res.string.safety_diff
                                        }
                                val tint =
                                        when {
                                            !known -> MaterialTheme.colorScheme.onSurfaceVariant
                                            match -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                            when {
                                                match -> AppIcons.Check
                                                known -> AppIcons.Warning
                                                else -> AppIcons.Info
                                            },
                                            contentDescription = stringResource(label),
                                            tint = tint,
                                            modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                            peer.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                            peer.safety.ifEmpty { "—" },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = tint,
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                            stringResource(label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onCopy) { Text(stringResource(Res.string.cd_copy_safety)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.safety_close)) } },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun SafetyPoint(index: Int, resource: StringResource) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
                "$index",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp, end = 8.dp),
        )
        Text(
                stringResource(resource),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun HelpDialog(onDismiss: () -> Unit) {
    val steps =
            listOf(
                    Res.string.help_step1,
                    Res.string.help_step2,
                    Res.string.help_step3,
                    Res.string.help_step4,
                    Res.string.help_step5,
                    Res.string.help_step6,
            )
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.help_title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    steps.forEachIndexed { index, resource ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(16.dp),
                            )
                            Text(
                                    stringResource(resource),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(Res.string.safety_close), fontWeight = FontWeight.SemiBold)
                }
            },
            shape = MaterialTheme.shapes.large,
    )
}

private const val REPO_URL = "https://github.com/aacai/ClipSync"

@Composable
internal fun AboutDialog(model: AppModel, onDismiss: () -> Unit) {
    val room by model.joinedRoom.collectAsState()
    val localCount by model.localCount.collectAsState()
    val uriHandler = LocalUriHandler.current
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.about_title), fontSize = 17.sp) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    AboutRow(Res.string.setting_version, model.appVersion)
                    AboutRow(Res.string.setting_os, model.deviceOs)
                    AboutRow(Res.string.about_device_id, model.deviceId, monospace = true)
                    AboutRow(
                            Res.string.local_history,
                            pluralStringResource(Res.plurals.setting_item_count, localCount, localCount),
                    )
                    AboutRow(
                            Res.string.about_room,
                            room ?: stringResource(Res.string.about_room_none),
                            monospace = room != null,
                    )
                    AboutRow(Res.string.about_hosting, stringResource(Res.string.about_hosting_value))
                    Text(
                            stringResource(Res.string.about_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                            REPO_URL.removePrefix("https://"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(REPO_URL) },
                    )
                }
            },
            confirmButton = {
                Button(onClick = onDismiss, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(Res.string.safety_close), fontWeight = FontWeight.SemiBold)
                }
            },
            shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun AboutRow(title: StringResource, value: String, monospace: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

@Composable
internal fun ShareDialog(model: AppModel, onDismiss: () -> Unit) {
    val roomCode by model.roomCode.collectAsState()
    val password by model.password.collectAsState()
    val connecting by model.connecting.collectAsState()
    val failure by model.failure.collectAsState()
    val status by model.status.collectAsState()
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { codeFocus.requestFocus() }

    val failureText = failure?.let { feedbackText(it) }
            ?: (status as? ClipSyncEngine.Status.Offline)?.reason

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.share_title), fontSize = 17.sp) },
            text = {
                Column {
                    SectionLabel(Res.string.room_code)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                                value = roomCode,
                                onValueChange = model::updateRoomCode,
                                singleLine = true,
                                enabled = !connecting,
                                leadingIcon = { FieldIcon(AppIcons.Key) },
                                keyboardOptions =
                                        KeyboardOptions(
                                                keyboardType = KeyboardType.Ascii,
                                                capitalization = KeyboardCapitalization.Characters,
                                        ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f).focusRequester(codeFocus),
                        )
                        Tip(stringResource(Res.string.cd_random_room)) {
                            IconButton(onClick = model::regenerateRoomCode, enabled = !connecting) {
                                Icon(
                                        AppIcons.Refresh,
                                        contentDescription = stringResource(Res.string.cd_random_room),
                                )
                            }
                        }
                    }
                    Text(
                            stringResource(Res.string.room_code_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(14.dp))
                    SectionLabel(Res.string.room_password)
                    OutlinedTextField(
                            value = password,
                            onValueChange = model::updatePassword,
                            singleLine = true,
                            enabled = !connecting,
                            placeholder = { Text(stringResource(Res.string.room_password_placeholder)) },
                            leadingIcon = { FieldIcon(AppIcons.Lock) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                    )

                    if (!failureText.isNullOrBlank()) {
                        InlineMessage(
                                AppIcons.Warning,
                                failureText,
                                isError = true,
                                modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = model::connect,
                        enabled = !connecting && roomCode.isNotBlank(),
                        shape = MaterialTheme.shapes.small,
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(stringResource(Res.string.connecting))
                    } else {
                        Text(stringResource(Res.string.enter_room), fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
            shape = MaterialTheme.shapes.large,
    )
}
