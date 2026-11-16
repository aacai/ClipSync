package zhiqiu.app.cs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.applewidgets.kmp.oink.basic.OinkButton
import zhiqiu.applewidgets.kmp.oink.basic.OinkButtonVariant
import zhiqiu.applewidgets.kmp.oink.basic.OinkIcon
import zhiqiu.applewidgets.kmp.oink.basic.OinkIconButton
import zhiqiu.applewidgets.kmp.oink.basic.OinkSegmented
import zhiqiu.applewidgets.kmp.oink.basic.OinkSpinner
import zhiqiu.applewidgets.kmp.oink.basic.OinkText
import zhiqiu.applewidgets.kmp.oink.form.OinkTextField
import zhiqiu.applewidgets.kmp.oink.theme.OinkTheme
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
private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OinkButton(text = text, onClick = onClick, enabled = enabled, variant = OinkButtonVariant.Accent)
}

@Composable
private fun Ghost(text: String, onClick: () -> Unit) {
    OinkButton(text = text, onClick = onClick, variant = OinkButtonVariant.Ghost)
}

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
    val c = OinkTheme.colors
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(title), style = OinkTheme.textStyles.h4) },
            text = {
                Column {
                    OinkTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = stringResource(placeholder),
                            singleLine = false,
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(min = (minLines * 24).dp)
                                            .focusRequester(focus),
                    )
                    if (supporting != null) {
                        OinkText(
                                stringResource(supporting),
                                style = OinkTheme.textStyles.caption,
                                color = c.text3,
                                modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = { Primary(stringResource(confirm), enabled = allowBlank || text.isNotBlank()) { onSubmit(text) } },
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
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
    val c = OinkTheme.colors
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.transform_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    OinkText(
                            text.lineSequence().first().take(72).ifBlank { stringResource(Res.string.empty_text) },
                            style = OinkTheme.textStyles.caption,
                            color = c.text3,
                            maxLines = 1,
                    )
                    OinkText(
                            stringResource(Res.string.transform_hint),
                            style = OinkTheme.textStyles.caption,
                            color = c.text3,
                            modifier = Modifier.padding(vertical = 8.dp),
                    )
                    TextOp.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { op ->
                                OinkButton(
                                        text = opLabel(op),
                                        onClick = {
                                            onDismiss()
                                            onApply(op)
                                        },
                                        modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
    )
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
internal fun TranslateDialog(
        item: ClipItem,
        model: AppModel,
        onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val c = OinkTheme.colors
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

    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.translate_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OinkSegmented(
                            options = listOf(stringResource(Res.string.translate_to_en), stringResource(Res.string.translate_to_zh)),
                            selectedIndex = if (target == TranslateTarget.English) 0 else 1,
                            onSelectedChange = {
                                target = if (it == 0) TranslateTarget.English else TranslateTarget.Chinese
                            },
                    )
                    SectionLabel(Res.string.translate_source)
                    ReadOnlyTextBox(source)
                    if (tooLong) {
                        OinkText(
                                stringResource(Res.string.fb_translate_too_long, source.length, MAX_TRANSLATE_CHARS),
                                style = OinkTheme.textStyles.caption,
                                color = c.dangerText,
                        )
                    }
                    val failed = failure
                    if (failed != null) {
                        OinkText(
                                translateFailureText(failed.first, failed.second),
                                style = OinkTheme.textStyles.caption,
                                color = c.dangerText,
                        )
                    }
                    val translated = result
                    if (translated != null) {
                        SectionLabel(Res.string.translate_result)
                        ReadOnlyTextBox(translated)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OinkButton(
                                    text = stringResource(Res.string.translate_copy_result),
                                    onClick = { model.copyToClipboard(translated) },
                                    variant = OinkButtonVariant.Ghost,
                            )
                            OinkButton(
                                    text = stringResource(Res.string.translate_save_new),
                                    onClick = {
                                            model.addEntry(translated)
                                            onDismiss()
                                    },
                                    variant = OinkButtonVariant.Ghost,
                            )
                            OinkButton(
                                    text = stringResource(Res.string.translate_replace),
                                    onClick = {
                                            model.editItem(item, translated)
                                            onDismiss()
                                    },
                                    variant = OinkButtonVariant.Ghost,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                OinkButton(
                        text = stringResource(if (running) Res.string.translate_running else Res.string.translate_run),
                        onClick = { run() },
                        enabled = !running && !tooLong,
                        variant = OinkButtonVariant.Accent,
                )
            },
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
    )
}

@Composable
private fun ReadOnlyTextBox(text: String) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(OinkTheme.colors.well, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
    ) {
        OinkText(
                text.ifBlank { stringResource(Res.string.empty_text) },
                style = OinkTheme.textStyles.caption,
        )
    }
}

@Composable
internal fun MoveToPositionDialog(info: MoveInfo, onDismiss: () -> Unit, onSubmit: (Int) -> Unit) {
    val c = OinkTheme.colors
    var input by remember { mutableStateOf(info.position.toString()) }
    val target = input.trim().toIntOrNull()
    val outOfRange = target != null && target !in info.from..info.to
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.move_to_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column {
                    OinkTextField(
                            value = input,
                            onValueChange = { input = it.filter(Char::isDigit).take(6) },
                            placeholder = stringResource(Res.string.move_to_position),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    OinkText(
                            if (outOfRange) stringResource(Res.string.move_to_invalid, info.from, info.to)
                            else stringResource(Res.string.move_to_hint, info.from, info.to, info.position),
                            style = OinkTheme.textStyles.caption,
                            color = if (outOfRange) c.dangerText else c.text3,
                            modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                Primary(
                        stringResource(Res.string.confirm),
                        enabled = target != null && !outOfRange && target != info.position,
                ) { target?.let(onSubmit) }
            },
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
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
    val c = OinkTheme.colors
    val isFile = item.kind == ClipRepository.KIND_FILE
    val file = item.file
    val path = file?.localPath?.takeIf { isImage(file.mime) }
    val thumb = rememberImageBitmap(path, THUMB_MAX_SIDE) { model.cachedBytes(item) }
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.detail_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    if (thumb != null) {
                        Box(modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = onZoom)) {
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
                                            .background(c.well, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                            .padding(10.dp)
                                            .verticalScroll(rememberScrollState()),
                    ) {
                        val body =
                                if (isFile) {
                                    file?.localPath ?: stringResource(Res.string.not_downloaded)
                                } else {
                                    item.text
                                }
                        OinkText(body, style = OinkTheme.textStyles.mono)
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
                    OinkText(
                            meta.joinToString(" · "),
                            style = OinkTheme.textStyles.caption,
                            color = c.text3,
                            modifier = Modifier.padding(top = 10.dp),
                    )
                    if (item.note.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        SectionLabel(Res.string.detail_note)
                        OinkText(item.note, style = OinkTheme.textStyles.caption)
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLabel(Res.string.detail_id)
                    OinkText(item.id, style = OinkTheme.textStyles.mono)
                }
            },
            confirmButton = {
                val copyAsFile = file != null && file.localPath != null && model.supportsClipboardFiles
                val copyAsImage = file != null && isImage(file.mime) && model.supportsClipboardImage
                if (!isFile || copyAsImage || copyAsFile) {
                    Ghost(
                            stringResource(
                                    when {
                                        copyAsImage -> Res.string.cd_copy_image
                                        copyAsFile -> Res.string.cd_copy_file
                                        else -> Res.string.cd_copy_text
                                    }
                            ),
                            onCopy,
                    )
                }
            },
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
    )
}

@Composable
internal fun ImageViewDialog(item: ClipItem, model: AppModel, onDismiss: () -> Unit) {
    val file = item.file
    val path = file?.localPath?.takeIf { isImage(file.mime) }
    val bitmap = rememberImageBitmap(path, ZOOM_MAX_SIDE) { model.cachedBytes(item) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.9f))
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
                        OinkText(stringResource(Res.string.not_downloaded), style = OinkTheme.textStyles.body, color = Color.White)
                else -> OinkSpinner(modifier = Modifier.size(28.dp), color = Color.White)
            }
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                if (model.supportsSaveToDownloads) {
                    OinkIconButton(
                            icon = AppIcons.Download,
                            onClick = { model.saveToDownloads(item) },
                            contentDescription = stringResource(Res.string.act_save_to_downloads),
                    )
                }
                OinkIconButton(icon = AppIcons.Close, onClick = onDismiss, contentDescription = stringResource(Res.string.cd_close_image))
            }
        }
    }
}

@Composable
internal fun DeleteHistoryDialog(
        counts: List<Int>,
        onDismiss: () -> Unit,
        onPick: (Long?) -> Unit,
) {
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.delete_history_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column {
                    OinkText(stringResource(Res.string.delete_history_body), style = OinkTheme.textStyles.caption)
                    Spacer(Modifier.height(10.dp))
                    val rows =
                            listOf(
                                    Res.string.tools_delete_hour to 3_600_000L,
                                    Res.string.tools_delete_day to 86_400_000L,
                                    Res.string.tools_delete_week to 604_800_000L,
                            )
                    for ((index, row) in rows.withIndex()) {
                        DeleteRangeRow(
                                label = stringResource(row.first),
                                count = counts.getOrElse(index) { 0 },
                                onClick = { onPick(row.second) },
                        )
                    }
                    DeleteRangeRow(
                            label = stringResource(Res.string.tools_delete_all),
                            count = counts.getOrElse(3) { 0 },
                            onClick = { onPick(null) },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
    )
}

@Composable
private fun DeleteRangeRow(label: String, count: Int, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OinkText(label, style = OinkTheme.textStyles.body)
        OinkText(stringResource(Res.string.delete_count, count), style = OinkTheme.textStyles.caption, color = OinkTheme.colors.text3)
    }
}

@Composable
internal fun LeaveRoomDialog(room: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = OinkTheme.colors
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.leave_room_title), style = OinkTheme.textStyles.h4) },
            text = {
                OinkText(stringResource(Res.string.leave_room_body, room), style = OinkTheme.textStyles.caption)
            },
            confirmButton = {
                OinkButton(
                        text = stringResource(Res.string.action_leave_room),
                        onClick = onConfirm,
                        variant = OinkButtonVariant.Danger,
                )
            },
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
    )
}

@Composable
internal fun DevicesDialog(
        devices: List<String>,
        onDismiss: () -> Unit,
) {
    val c = OinkTheme.colors
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.devices_title), style = OinkTheme.textStyles.h4) },
            text = {
                if (devices.isEmpty()) {
                    OinkText(
                            stringResource(Res.string.devices_empty),
                            style = OinkTheme.textStyles.caption,
                            color = c.text3,
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
                                                    .background(c.well, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OinkIcon(AppIcons.Devices, contentDescription = null, tint = c.text2, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                OinkText(
                                        name,
                                        style = OinkTheme.textStyles.body,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                )
                                if (index == 0) {
                                    OinkText(stringResource(Res.string.devices_this), style = OinkTheme.textStyles.caption, color = c.accent)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Ghost(stringResource(Res.string.safety_close), onDismiss) },
    )
}

@Composable
internal fun SafetyDialog(
        safety: String,
        peers: List<DevicePresence>,
        mismatch: List<String>,
        onDismiss: () -> Unit,
        onCopy: () -> Unit,
) {
    val c = OinkTheme.colors
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.safety_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column(
                        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(c.well, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                    ) {
                        OinkText(
                                safety,
                                style =
                                        OinkTheme.textStyles.h2.copy(
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.Center,
                                        ),
                                color = c.text,
                        )
                    }
                    SafetyPoint(1, Res.string.safety_point_what)
                    SafetyPoint(2, Res.string.safety_point_verify)
                    SafetyPoint(3, Res.string.safety_point_mismatch)
                    if (peers.isNotEmpty() || mismatch.isNotEmpty()) {
                        SectionLabel(Res.string.safety_peers)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            peers.forEach { peer ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OinkIcon(AppIcons.Check, contentDescription = stringResource(Res.string.safety_match), tint = c.success, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    OinkText(peer.name, style = OinkTheme.textStyles.caption, maxLines = 1, modifier = Modifier.weight(1f))
                                    OinkText(stringResource(Res.string.safety_match), style = OinkTheme.textStyles.caption, color = c.success)
                                }
                            }
                            mismatch.forEach { label ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OinkIcon(AppIcons.Warning, contentDescription = stringResource(Res.string.safety_diff), tint = c.danger, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    OinkText(label, style = OinkTheme.textStyles.caption, maxLines = 1, modifier = Modifier.weight(1f))
                                    OinkText(stringResource(Res.string.safety_diff), style = OinkTheme.textStyles.caption, color = c.dangerText)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Ghost(stringResource(Res.string.cd_copy_safety), onCopy) },
            dismissButton = { Ghost(stringResource(Res.string.safety_close), onDismiss) },
    )
}

@Composable
private fun SafetyPoint(index: Int, resource: StringResource) {
    Row(verticalAlignment = Alignment.Top) {
        OinkText(
                "$index",
                style = OinkTheme.textStyles.mono,
                color = OinkTheme.colors.accent,
                modifier = Modifier.padding(top = 3.dp, end = 8.dp),
        )
        OinkText(
                stringResource(resource),
                style = OinkTheme.textStyles.caption,
                color = OinkTheme.colors.text2,
                modifier = Modifier.weight(1f),
        )
    }
}

private const val REPO_URL = "https://github.com/aacai/ClipSync"

@Composable
internal fun AboutDialog(model: AppModel, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val c = OinkTheme.colors
    GlassAlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {},
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                            painter = painterResource(Res.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(13.dp))
                    OinkText("ClipSync", style = OinkTheme.textStyles.h2.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(3.dp))
                    OinkText(
                            "${stringResource(Res.string.setting_version)} ${model.appVersion}",
                            style = OinkTheme.textStyles.body,
                            color = c.text2,
                    )
                    Spacer(Modifier.height(18.dp))
                    OinkText(stringResource(Res.string.about_copyright), style = OinkTheme.textStyles.body, color = c.text2)
                    Spacer(Modifier.height(10.dp))
                    OinkText(
                            REPO_URL.removePrefix("https://"),
                            style = OinkTheme.textStyles.label,
                            color = c.accent,
                            modifier = Modifier.clickable { uriHandler.openUri(REPO_URL) },
                    )
                }
            },
    )
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

    GlassAlertDialog(
            onDismissRequest = onDismiss,
            title = { OinkText(stringResource(Res.string.share_title), style = OinkTheme.textStyles.h4) },
            text = {
                Column {
                    SectionLabel(Res.string.room_code)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OinkTextField(
                                value = roomCode,
                                onValueChange = model::updateRoomCode,
                                placeholder = stringResource(Res.string.room_code),
                                singleLine = true,
                                enabled = !connecting,
                                leadingIcon = AppIcons.Key,
                                keyboardOptions =
                                        KeyboardOptions(
                                                keyboardType = KeyboardType.Ascii,
                                                capitalization = KeyboardCapitalization.Characters,
                                        ),
                                modifier = Modifier.weight(1f).focusRequester(codeFocus),
                        )
                        Tip(stringResource(Res.string.cd_random_room)) {
                            OinkIconButton(
                                    icon = AppIcons.Refresh,
                                    onClick = model::regenerateRoomCode,
                                    enabled = !connecting,
                                    contentDescription = stringResource(Res.string.cd_random_room),
                            )
                        }
                    }
                    OinkText(
                            stringResource(Res.string.room_code_hint),
                            style = OinkTheme.textStyles.caption,
                            color = OinkTheme.colors.text3,
                            modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(Res.string.room_password)
                    OinkTextField(
                            value = password,
                            onValueChange = model::updatePassword,
                            placeholder = stringResource(Res.string.room_password_placeholder),
                            singleLine = true,
                            enabled = !connecting,
                            leadingIcon = AppIcons.Lock,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                OinkButton(
                        text = stringResource(if (connecting) Res.string.connecting else Res.string.enter_room),
                        onClick = model::connect,
                        enabled = !connecting && roomCode.isNotBlank(),
                        variant = OinkButtonVariant.Accent,
                )
            },
            dismissButton = { Ghost(stringResource(Res.string.cancel), onDismiss) },
    )
}
