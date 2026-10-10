package zhiqiu.app.cs.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.resources.*

@Composable
internal fun MainView(model: AppModel, settings: AppSettings, roomCode: String?, onLeave: () -> Unit) {
    val status by model.status.collectAsState()
    val devices by model.devices.collectAsState()
    val items by model.items.collectAsState()
    val query by model.query.collectAsState()
    val regex by model.regex.collectAsState()
    val caseSensitive by model.caseSensitive.collectAsState()
    val invalidQuery by model.invalidQuery.collectAsState()
    val failure by model.failure.collectAsState()
    val info by model.info.collectAsState()
    val transfer by model.transfer.collectAsState()
    val connecting by model.connecting.collectAsState()
    val undoDepth by model.undoDepth.collectAsState()
    val online = status is ClipSyncEngine.Status.Online

    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    var barHeight by remember { mutableStateOf(0.dp) }
    var selected by rememberSaveable { mutableStateOf(-1) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var now by remember { mutableStateOf(nowMs()) }

    var composingNew by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClipItem?>(null) }
    var noting by remember { mutableStateOf<ClipItem?>(null) }
    var transforming by remember { mutableStateOf<ClipItem?>(null) }
    var inspecting by remember { mutableStateOf<ClipItem?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val cursorItem = items.getOrNull(selected)
    val targets = if (picking && picked.isNotEmpty()) items.filter { it.id in picked } else listOfNotNull(cursorItem)
    val barLift by animateDpAsState(if (picking) 78.dp else 0.dp, tween(M3.MEDIUM_2, easing = M3.STANDARD), label = "barLift")

    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(items.size) { if (selected >= items.size) selected = items.size - 1 }
    LaunchedEffect(selected) { if (selected >= 0) listState.animateScrollToItem(selected) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = nowMs()
        }
    }

    fun endPicking() {
        picking = false
        picked = emptySet()
    }

    fun togglePick(item: ClipItem) {
        picked = if (item.id in picked) picked - item.id else picked + item.id
        if (picked.isEmpty()) picking = false
    }

    fun selectAllVisible() {
        if (picking && picked.isNotEmpty()) endPicking() else {
            picked = items.map { it.id }.toSet()
            picking = picked.isNotEmpty()
        }
    }

    fun moveCursor(delta: Int, extend: Boolean): Boolean {
        if (items.isEmpty()) return false
        val from = selected
        selected =
                if (from < 0) {
                    if (delta > 0) 0 else items.lastIndex
                } else {
                    (from + delta).coerceIn(0, items.lastIndex)
                }
        if (extend && picking && from in items.indices) {
            val lo = minOf(from, selected)
            val hi = maxOf(from, selected)
            picked = picked + items.subList(lo, hi + 1).map { it.id }
        }
        return true
    }

    fun moveCursorItem(delta: Int): Boolean {
        val item = cursorItem ?: return false
        val to = selected + delta
        if (to < 0 || to >= items.size) return false
        model.move(item, delta)
        selected = to
        return true
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val modifier = event.isCtrlPressed || event.isMetaPressed
        val shifting = event.isShiftPressed
        return when (event.key) {
            Key.F -> modifier && !searchFocused && run {
                searchFocus.requestFocus()
                true
            }
            Key.Escape ->
                    when {
                        picking -> {
                            endPicking()
                            true
                        }
                        query.isNotEmpty() -> {
                            model.updateQuery("")
                            true
                        }
                        failure != null || info != null -> {
                            model.dismissFailure()
                            model.dismissInfo()
                            true
                        }
                        else -> {
                            selected = -1
                            true
                        }
                    }
            Key.DirectionDown ->
                    !searchFocused &&
                            if (modifier && shifting) moveCursorItem(1) else moveCursor(1, extend = shifting)
            Key.DirectionUp ->
                    !searchFocused &&
                            if (modifier && shifting) moveCursorItem(-1) else moveCursor(-1, extend = shifting)
            Key.Enter ->
                    if (searchFocused && selected < 0) {
                        false
                    } else if (cursorItem != null) {
                            if (picking) togglePick(cursorItem) else model.activate(cursorItem)
                            true
                        } else {
                            false
                        }
            Key.Delete ->
                    if (!searchFocused && targets.isNotEmpty()) {
                        model.remove(targets)
                        endPicking()
                        true
                    } else {
                        false
                    }
            Key.F2 -> {
                val item = cursorItem
                if (!searchFocused && item != null && item.kind != ClipRepository.KIND_FILE) {
                    editing = item
                    true
                } else {
                    false
                }
            }
            Key.C -> if (!searchFocused && modifier && cursorItem != null) {
                model.copy(cursorItem)
                true
            } else {
                false
            }
            // 输入框里的 Cmd+Z 是撤销打字，必须留给它；只有列表焦点在我们手上时才接管。
            Key.Z -> if (modifier && !searchFocused && !composingNew && undoDepth > 0) {
                model.undoDelete()
                true
            } else {
                false
            }
            Key.D -> if (modifier && !searchFocused && cursorItem != null) {
                model.duplicate(cursorItem)
                true
            } else {
                false
            }
            Key.N -> if (modifier && !searchFocused) {
                composingNew = true
                true
            } else {
                false
            }
            Key.A -> if (modifier && !searchFocused) {
                selectAllVisible()
                true
            } else {
                false
            }
            Key.MoveHome -> if (!searchFocused && items.isNotEmpty()) {
                selected = 0
                true
            } else {
                false
            }
            Key.MoveEnd -> if (!searchFocused && items.isNotEmpty()) {
                selected = items.lastIndex
                true
            } else {
                false
            }
            else -> false
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .focusRequester(rootFocus)
                                .focusable()
                                .onPreviewKeyEvent { handleKey(it) },
                contentAlignment = Alignment.TopCenter,
        ) {
            BackdropGlow()

            Box(modifier = Modifier.widthIn(max = 940.dp).fillMaxSize().padding(horizontal = 6.dp)) {
                Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                    AnimatedContent(
                            targetState = items.isEmpty(),
                            transitionSpec = {
                                fadeIn(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)) togetherWith
                                        fadeOut(tween(M3.SHORT_4, easing = M3.EMPHASIZED_OUT)) +
                                        scaleOut(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_OUT), targetScale = 0.97f)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "body",
                    ) { empty ->
                        if (empty) {
                            EmptyState(query, barHeight) {
                                Banners(model, status, roomCode, transfer, connecting, online, invalidQuery)
                            }
                        } else {
                            LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding =
                                            PaddingValues(
                                                    top = barHeight + 8.dp,
                                                    bottom = 28.dp + barLift,
                                            ),
                            ) {
                                item(key = "banners") {
                                    Banners(model, status, roomCode, transfer, connecting, online, invalidQuery)
                                }
                                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                    ClipRow(
                                            model = model,
                                            item = item,
                                            now = now,
                                            cursor = index == selected,
                                            picked = item.id in picked,
                                            selectionActive = picking,
                                            canMoveUp = index > 0,
                                            canMoveDown = index < items.lastIndex,
                                            onActivate = {
                                                selected = index
                                                if (picking) togglePick(item) else model.activate(item)
                                            },
                                            onTogglePick = { togglePick(item) },
                                            onEdit = { editing = item },
                                            onNote = { noting = item },
                                            onDetail = { inspecting = item },
                                            onTransform = { transforming = item },
                                            modifier =
                                                    Modifier.animateItem(
                                                            placementSpec = tween(M3.MEDIUM_3, easing = M3.EMPHASIZED),
                                                            fadeInSpec = tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN),
                                                            fadeOutSpec = tween(M3.SHORT_4, easing = M3.EMPHASIZED_OUT),
                                                    ),
                                    )
                                }
                            }
                        }
                    }
                }

                GlassPanel(
                        modifier =
                                Modifier.align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                        shape = MaterialTheme.shapes.medium,
                ) {
                    Header(model, status, roomCode, devices.map { it.name }, onLeave, onSettings = { settingsOpen = true })
                    Toolbar(
                            model = model,
                            query = query,
                            regex = regex,
                            caseSensitive = caseSensitive,
                            online = online,
                            canClear = items.isNotEmpty(),
                            undoDepth = undoDepth,
                            picking = picking,
                            searchFocus = searchFocus,
                            onSearchFocused = { searchFocused = it },
                            onToggleSelect = {
                                if (picking) endPicking() else picking = true
                            },
                            onNew = { composingNew = true },
                            onClear = { confirmClear = true },
                    )
                }

                AnimatedVisibility(
                        visible = picking,
                        enter =
                                fadeIn(tween(M3.SHORT_4, easing = M3.EMPHASIZED_IN)) +
                                        slideInVertically(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)) { it },
                        exit =
                                fadeOut(tween(M3.SHORT_3, easing = M3.EMPHASIZED_OUT)) +
                                        slideOutVertically(tween(M3.SHORT_3, easing = M3.EMPHASIZED_OUT)) { it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                        label = "selectionBar",
                ) {
                    val allPinned = targets.isNotEmpty() && targets.all { it.pinned }
                    SelectionBar(
                            count = picked.size,
                            allPinned = allPinned,
                            modifier = Modifier,
                            onCopy = { model.copySelection(targets) },
                            onTogglePin = { model.setPinned(targets, !allPinned) },
                            onExport = { model.exportJson(targets) },
                            onDelete = {
                                model.remove(targets)
                                endPicking()
                            },
                            onClose = { endPicking() },
                    )
                }

                ToastHost(model, bottom = 22.dp + barLift, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    if (composingNew) {
        NewEntryDialog(
                onDismiss = { composingNew = false },
                onSubmit = { text ->
                    composingNew = false
                    model.sendText(text)
                },
        )
    }
    if (confirmClear) {
        ClearConfirmDialog(onDismiss = { confirmClear = false }, onConfirm = model::clearAll)
    }
    editing?.let { item ->
        EditDialog(
                item = item,
                onDismiss = { editing = null },
                onSave = {
                    model.editItem(item, it)
                    editing = null
                },
        )
    }
    noting?.let { item ->
        NoteDialog(
                item = item,
                onDismiss = { noting = null },
                onSave = {
                    model.setNote(item, it)
                    noting = null
                },
        )
    }
    transforming?.let { item ->
        TransformDialog(
                text = item.text,
                onDismiss = { transforming = null },
                onApply = { op -> model.transform(item, op) },
        )
    }
    inspecting?.let { item ->
        DetailDialog(
                item = item,
                now = now,
                onDismiss = { inspecting = null },
                onCopy = { model.copy(item) },
        )
    }
    if (settingsOpen) {
        SettingsDialog(settings, onDismiss = { settingsOpen = false })
    }
}

@Composable
private fun Banners(
        model: AppModel,
        status: ClipSyncEngine.Status,
        roomCode: String?,
        transfer: ClipSyncEngine.TransferProgress?,
        connecting: Boolean,
        online: Boolean,
        invalidQuery: Boolean,
) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING),
            verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val progress = transfer
        if (progress != null) {
            val kind =
                    if (progress.direction == ClipSyncEngine.TransferProgress.Direction.Upload) {
                        stringResource(Res.string.progress_upload)
                    } else {
                        stringResource(Res.string.progress_download)
                    }
            GlassPanel(shape = MaterialTheme.shapes.small, alpha = 0.78f) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(
                            stringResource(
                                    Res.string.progress_line,
                                    kind,
                                    progress.name,
                                    "${(progress.fraction * 100).toInt()}%",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                    )
                    LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    )
                }
            }
        }

        if (!online && !connecting) {
            InlineMessage(
                    AppIcons.Warning,
                    if (roomCode == null) {
                        stringResource(Res.string.banner_offline)
                    } else {
                        stringResource(Res.string.banner_disconnected, roomCode)
                    },
                    isError = roomCode != null,
                    action = if (roomCode == null) null else {
                        { model.connect() }
                    },
                    actionLabel = if (roomCode == null) null else stringResource(Res.string.reconnect),
            )
        }

        if (invalidQuery) {
            InlineMessage(AppIcons.Warning, stringResource(Res.string.fb_invalid_regex), isError = true)
        }
    }
}

@Composable
private fun Header(
        model: AppModel,
        status: ClipSyncEngine.Status,
        roomCode: String?,
        deviceNames: List<String>,
        onLeave: () -> Unit,
        onSettings: () -> Unit,
) {
    val dark = isAppDarkTheme()
    val online = status is ClipSyncEngine.Status.Online
    val dotColor by
            animateColorAsState(
                    targetValue =
                            when {
                                online -> if (dark) SuccessGreenDark else SuccessGreen
                                status is ClipSyncEngine.Status.Connecting -> if (dark) PinAmberDark else PinAmber
                                else -> MaterialTheme.colorScheme.error
                            },
                    animationSpec = tween(M3.MEDIUM_2, easing = M3.STANDARD),
                    label = "statusDot",
            )

    Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, RoundedCornerShape(percent = 50)))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                        roomCode ?: stringResource(Res.string.offline_history),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                        when {
                            online -> stringResource(Res.string.status_connected)
                            status is ClipSyncEngine.Status.Connecting -> stringResource(Res.string.connecting)
                            else -> stringResource(Res.string.status_disconnected)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val safety = (status as? ClipSyncEngine.Status.Online)?.safetyNumber
            val detail = listOfNotNull(
                safety?.let { stringResource(Res.string.safety_number, it) },
                deviceNames.takeIf { it.isNotEmpty() }?.let {
                    stringResource(Res.string.peers_in_room, it.size, it.joinToString(" · ").take(48))
                },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = safety != null) { safety?.let(model::copyToClipboard) },
                ) {
                    if (safety != null) {
                        Icon(
                                AppIcons.Shield,
                                contentDescription = stringResource(Res.string.cd_copy_safety),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                    )
                }
            }
        }
        if (online) {
            Tip(stringResource(Res.string.cd_devices)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            AppIcons.Devices,
                            contentDescription = stringResource(Res.string.cd_devices),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${deviceNames.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        RowAction(AppIcons.Settings, stringResource(Res.string.cd_settings), onClick = onSettings)
        RowAction(AppIcons.Logout, stringResource(Res.string.cd_leave), onClick = onLeave)
    }
}

@Composable
private fun Toolbar(
        model: AppModel,
        query: String,
        regex: Boolean,
        caseSensitive: Boolean,
        online: Boolean,
        canClear: Boolean,
        undoDepth: Int,
        picking: Boolean,
        searchFocus: FocusRequester,
        onSearchFocused: (Boolean) -> Unit,
        onToggleSelect: () -> Unit,
        onNew: () -> Unit,
        onClear: () -> Unit,
) {
    OutlinedTextField(
            value = query,
            onValueChange = model::updateQuery,
            placeholder = { Text(stringResource(Res.string.search_hint)) },
            singleLine = true,
            leadingIcon = { FieldIcon(AppIcons.Search) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToggleChip(Res.string.cd_case, label = "Aa", active = caseSensitive) {
                        model.toggleCaseSensitive()
                    }
                    Spacer(Modifier.width(2.dp))
                    ToggleChip(Res.string.cd_regex, label = ".*", active = regex) { model.toggleRegex() }
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(2.dp))
                        RowAction(AppIcons.Close, stringResource(Res.string.cd_clear_search)) {
                            model.updateQuery("")
                        }
                    }
                }
            },
            shape = MaterialTheme.shapes.small,
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .focusRequester(searchFocus)
                            .onFocusEvent { onSearchFocused(it.isFocused) },
    )

    Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        RowAction(AppIcons.Send, stringResource(Res.string.send_clipboard), enabled = online) {
            model.sendClipboard()
        }
        RowAction(AppIcons.Add, stringResource(Res.string.new_entry), enabled = online, onClick = onNew)
        RowAction(
                AppIcons.Attach,
                stringResource(Res.string.send_file),
                enabled = online && model.supportsFilePicker,
        ) { model.pickFiles() }
        RowAction(AppIcons.SelectAll, stringResource(Res.string.cd_select), active = picking, onClick = onToggleSelect)
        RowAction(AppIcons.Undo, stringResource(Res.string.cd_undo), enabled = undoDepth > 0) { model.undoDelete() }
        Spacer(Modifier.width(4.dp))
        RowAction(
                AppIcons.Delete,
                stringResource(Res.string.cd_clear_history),
                enabled = canClear,
                tinted = MaterialTheme.colorScheme.error,
                onClick = onClear,
        )
        Spacer(Modifier.weight(1f))
        Text(
                hintFor(model, online),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 340.dp).heightIn(max = 28.dp).padding(end = 6.dp),
        )
    }
}

@Composable
private fun hintFor(model: AppModel, online: Boolean): String =
        when {
            !online -> stringResource(Res.string.hint_offline)
            !model.supportsClipboardAutoSync -> stringResource(Res.string.hint_manual_send)
            !model.supportsFilePicker -> stringResource(Res.string.hint_no_picker)
            else -> stringResource(Res.string.shortcuts_hint)
        }

@Composable
private fun ToggleChip(description: StringResource, label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spec = tween<Color>(M3.SHORT_4, easing = M3.STANDARD)
    val fill by animateColorAsState(
            if (active) scheme.primary.copy(alpha = 0.18f) else Color.Transparent,
            spec,
            label = "chipFill",
    )
    val edge by animateColorAsState(
            if (active) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant.copy(alpha = 0.6f),
            spec,
            label = "chipEdge",
    )
    val ink by animateColorAsState(if (active) scheme.primary else scheme.onSurfaceVariant, spec, label = "chipInk")
    val cd = stringResource(description)
    Tip(cd) {
        Box(
                modifier =
                        Modifier.size(width = 30.dp, height = 26.dp)
                                .background(fill, MaterialTheme.shapes.extraSmall)
                                .border(1.dp, edge, MaterialTheme.shapes.extraSmall)
                                .semantics { contentDescription = cd }
                                .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ink)
        }
    }
}

@Composable
private fun SelectionBar(
        count: Int,
        allPinned: Boolean,
        modifier: Modifier,
        onCopy: () -> Unit,
        onTogglePin: () -> Unit,
        onExport: () -> Unit,
        onDelete: () -> Unit,
        onClose: () -> Unit,
) {
    GlassPanel(modifier = modifier, shape = MaterialTheme.shapes.medium, alpha = 0.82f) {
        Row(
                modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                    stringResource(Res.string.selection_count, count),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp),
            )
            RowAction(AppIcons.Copy, stringResource(Res.string.cd_copy_text), enabled = count > 0, onClick = onCopy)
            RowAction(
                    AppIcons.Pin,
                    stringResource(if (allPinned) Res.string.cd_unpin else Res.string.cd_pin),
                    active = allPinned,
                    enabled = count > 0,
                    onClick = onTogglePin,
            )
            RowAction(AppIcons.Export, stringResource(Res.string.cd_export_json), enabled = count > 0, onClick = onExport)
            RowAction(
                    AppIcons.Delete,
                    stringResource(Res.string.act_delete),
                    enabled = count > 0,
                    tinted = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
            )
            RowAction(AppIcons.Close, stringResource(Res.string.cd_exit_select), onClick = onClose)
        }
    }
}

@Composable
private fun EmptyState(
        query: String,
        contentTop: Dp,
        banners: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = contentTop + 8.dp)) {
        banners()
        Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(24.dp),
                contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                        if (query.isBlank()) AppIcons.Clipboard else AppIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                        if (query.isBlank()) {
                            stringResource(Res.string.empty_title)
                        } else {
                            stringResource(Res.string.empty_no_match, query)
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
