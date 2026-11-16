package zhiqiu.app.cs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.applewidgets.kmp.oink.basic.OinkBadge
import zhiqiu.applewidgets.kmp.oink.basic.OinkBadgeVariant
import zhiqiu.applewidgets.kmp.oink.basic.OinkButton
import zhiqiu.applewidgets.kmp.oink.basic.OinkButtonVariant
import zhiqiu.applewidgets.kmp.oink.basic.OinkCheckbox
import zhiqiu.applewidgets.kmp.oink.basic.OinkIcon
import zhiqiu.applewidgets.kmp.oink.basic.OinkIconButton
import zhiqiu.applewidgets.kmp.oink.basic.OinkText
import zhiqiu.applewidgets.kmp.oink.basic.OinkButtonSize
import zhiqiu.applewidgets.kmp.oink.overlay.OinkMenu
import zhiqiu.applewidgets.kmp.oink.overlay.OinkMenuItem
import zhiqiu.applewidgets.kmp.oink.overlay.OinkMenuSeparator
import zhiqiu.applewidgets.kmp.oink.theme.OinkTheme
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.MoveInfo
import zhiqiu.app.cs.files.displayTitle
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.files.isImage
import zhiqiu.app.cs.resources.*

internal val ROW_HORIZONTAL_PADDING = 14.dp

private val NUMBER_WIDTH = 24.dp
private const val MAX_BODY_LINES = 24
private val IMAGE_PREVIEW_HEIGHT = 140.dp
private val IMAGE_PREVIEW_MAX_WIDTH = 220.dp
private const val IMAGE_PREVIEW_MAX_SIDE = 512

@Composable
internal fun ClipRow(
        model: AppModel,
        item: ClipItem,
        now: Long,
        position: Int,
        cursor: Boolean,
        picked: Boolean,
        selectionActive: Boolean,
        moveInfo: MoveInfo?,
        online: Boolean,
        onActivate: () -> Unit,
        onTogglePick: () -> Unit,
        onEdit: () -> Unit,
        onNote: () -> Unit,
        onDetail: () -> Unit,
        onZoom: () -> Unit,
        onTransform: () -> Unit,
        onTranslate: () -> Unit,
        onMoveTop: () -> Unit,
        onMoveBottom: () -> Unit,
        onMoveTo: () -> Unit,
        modifier: Modifier = Modifier,
) {
    val c = OinkTheme.colors
    val isFile = item.kind == ClipRepository.KIND_FILE
    val file = item.file
    val cached = isFile && file?.localPath != null
    val copyAsFile = file != null && cached && model.supportsClipboardFiles
    val copyAsImage = file != null && isImage(file.mime) && model.supportsClipboardImage
    var menu by remember { mutableStateOf(false) }
    val canUp = moveInfo != null && moveInfo.position > moveInfo.from
    val canDown = moveInfo != null && moveInfo.position < moveInfo.to

    val copyLabel =
            when {
                copyAsImage -> stringResource(Res.string.cd_copy_image)
                copyAsFile -> stringResource(Res.string.cd_copy_file)
                else -> stringResource(Res.string.cd_copy_text)
            }

    val colorSpec = tween<Color>(M3.SHORT_4, easing = M3.STANDARD)
    val background by animateColorAsState(rowBackground(cursor, picked), colorSpec, label = "rowBg")
    val edge by animateColorAsState(rowBorder(cursor, picked), colorSpec, label = "rowEdge")
    val compact = LocalWindowSize.current.isCompact
    val rowInteraction = remember { MutableInteractionSource() }
    val hovered by rowInteraction.collectIsHoveredAsState()
    val revealQuick = !model.hoverRevealsActions || hovered || cursor
    val badgeColor by
            animateColorAsState(
                    if (item.pinned) c.warning else c.text3,
                    tween(M3.MEDIUM_1, easing = M3.STANDARD),
                    label = "rowBadge",
            )

    val shape = RoundedCornerShape(20.dp)
    Box(
            modifier =
                    modifier
                            .fillMaxWidth()
                            .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 4.dp)
                            .fileDragOutSource { model.prepareDragOutPaths(listOf(item)) }
                            .clip(shape)
                            .background(background, shape)
                            .border(1.dp, edge, shape)
    ) {
        Box(
                Modifier.fillMaxWidth()
                        .height(1.dp)
                        .background(c.hi)
        )
        Row(
                modifier =
                        Modifier.clickable(
                                        interactionSource = rowInteraction,
                                        indication = LocalIndication.current,
                                        onClick = onActivate,
                                )
                                .padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                    visible = selectionActive,
                    enter =
                            fadeIn(tween(M3.SHORT_4, easing = M3.EMPHASIZED_IN)) +
                                    expandHorizontally(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)),
                    exit =
                            fadeOut(tween(M3.SHORT_3, easing = M3.EMPHASIZED_OUT)) +
                                    shrinkHorizontally(tween(M3.SHORT_3, easing = M3.EMPHASIZED_OUT)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OinkCheckbox(checked = picked, onCheckedChange = { onTogglePick() })
                    Spacer(Modifier.width(10.dp))
                }
            }

            val thumbPath = file?.localPath?.takeIf { isImage(file.mime) }
            val thumb = rememberImageBitmap(thumbPath, IMAGE_PREVIEW_MAX_SIDE) { model.cachedBytes(item) }
            Box(
                    modifier = Modifier.width(if (compact) 20.dp else NUMBER_WIDTH),
                    contentAlignment = if (compact) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                OinkText(
                        text = position.toString(),
                        style = OinkTheme.textStyles.mono,
                        color = badgeColor,
                )
            }
            val meta =
                    buildList {
                        add(relTime(item.ts, now))
                        add(stringResource(Res.string.from_sender, item.senderName))
                        if (isFile && file != null) {
                            add(humanSize(file.size))
                            add(
                                    if (cached) {
                                        stringResource(Res.string.cached)
                                    } else {
                                        stringResource(Res.string.not_downloaded)
                                    },
                            )
                            val left = file.expiresAt - now
                            if (left > 0) add(stringResource(Res.string.expires_in, humanLeft(left)))
                        }
                        if (item.pinned) add(stringResource(Res.string.pinned))
                        if (item.note.isNotBlank()) add(stringResource(Res.string.note_flag))
                        if (item.order != 0L && item.order != item.ts) add(stringResource(Res.string.moved))
                    }
                            .joinToString(" · ")
            if (isFile && file != null && isImage(file.mime)) {
                val previewMaxWidth = if (compact) 160.dp else IMAGE_PREVIEW_MAX_WIDTH
                val previewSize =
                        if (thumb == null) {
                            DpSize(previewMaxWidth, IMAGE_PREVIEW_HEIGHT)
                        } else {
                            val scale =
                                    (previewMaxWidth / thumb.width.dp)
                                            .coerceAtMost(IMAGE_PREVIEW_HEIGHT / thumb.height.dp)
                            DpSize(thumb.width.dp * scale, thumb.height.dp * scale)
                        }
                Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                            modifier =
                                    Modifier.size(previewSize)
                                            .clip(RoundedCornerShape(10.dp))
                                            .then(if (thumb != null) Modifier.clickable(onClick = onZoom) else Modifier),
                            contentAlignment = Alignment.Center,
                    ) {
                        if (thumb != null) {
                            Image(
                                    bitmap = thumb,
                                    contentDescription = stringResource(Res.string.cd_zoom_image),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                    modifier =
                                            Modifier.fillMaxSize()
                                                    .background(c.well.copy(alpha = 0.5f))
                            )
                            OinkIcon(AppIcons.Attach, contentDescription = null, tint = c.text3, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    OinkText(
                            meta,
                            style = OinkTheme.textStyles.caption,
                            color = c.text3,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    OinkText(
                            text =
                                    when {
                                        isFile && file != null -> displayTitle(file)
                                        else -> item.text.ifBlank { stringResource(Res.string.empty_text) }
                                    },
                            style =
                                    if (cursor) OinkTheme.textStyles.body.copy(fontWeight = FontWeight.Medium)
                                    else OinkTheme.textStyles.body,
                            color = c.text,
                            maxLines = MAX_BODY_LINES,
                    )
                    OinkText(
                            meta,
                            style = OinkTheme.textStyles.caption,
                            color = c.text3,
                            maxLines = 1,
                    )
                }
            }

            val fadeSupported = model.hoverRevealsActions
            val actionsAlpha by animateFloatAsState(
                    if (!fadeSupported || revealQuick) 1f else 0f,
                    tween(M3.SHORT_4, easing = M3.STANDARD),
                    label = "actionsAlpha",
            )
            val actionsEnabled = !fadeSupported || revealQuick
            if (!compact) {
                Row(modifier = Modifier.alpha(actionsAlpha), verticalAlignment = Alignment.CenterVertically) {
                    if (!isFile) {
                        RowAction(AppIcons.Send, stringResource(Res.string.cd_send), enabled = actionsEnabled && online) {
                            model.sendText(item.text)
                        }
                    }
                    if (!isFile || copyAsImage || copyAsFile) {
                        RowAction(AppIcons.Copy, copyLabel, enabled = actionsEnabled) { model.copy(item) }
                    }
                    if (isFile && cached && model.supportsLocalFiles) {
                        RowAction(AppIcons.OpenInNew, stringResource(Res.string.cd_open), enabled = actionsEnabled) {
                            model.open(item)
                        }
                    } else if (isFile && !cached && model.supportsLocalFiles) {
                        RowAction(AppIcons.Download, stringResource(Res.string.cd_download), enabled = actionsEnabled) {
                            model.download(item)
                        }
                    }
                    RowAction(
                            AppIcons.Pin,
                            if (item.pinned) stringResource(Res.string.cd_unpin) else stringResource(Res.string.cd_pin),
                            active = item.pinned,
                            enabled = actionsEnabled,
                    ) { model.togglePin(item) }
                }
            }
            Box {
                RowAction(AppIcons.More, stringResource(Res.string.cd_more), onClick = { menu = true })
                OinkMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (compact) {
                        if (!isFile) {
                            OMenu(stringResource(Res.string.cd_send), enabled = online, leadingIcon = AppIcons.Send) {
                                menu = false
                                model.sendText(item.text)
                            }
                        }
                        if (!isFile || copyAsImage || copyAsFile) {
                            OMenu(copyLabel, leadingIcon = AppIcons.Copy) {
                                menu = false
                                model.copy(item)
                            }
                        }
                        OMenu(
                                stringResource(if (item.pinned) Res.string.cd_unpin else Res.string.cd_pin),
                                leadingIcon = AppIcons.Pin,
                        ) {
                            menu = false
                            model.togglePin(item)
                        }
                        if (isFile && cached && model.supportsLocalFiles) {
                            OMenu(stringResource(Res.string.cd_open), leadingIcon = AppIcons.OpenInNew) {
                                menu = false
                                model.open(item)
                            }
                        } else if (isFile && !cached && model.supportsLocalFiles) {
                            OMenu(stringResource(Res.string.cd_download), leadingIcon = AppIcons.Download) {
                                menu = false
                                model.download(item)
                            }
                        }
                        OinkMenuSeparator()
                    }
                    if (item.isText) {
                        OMenu(stringResource(Res.string.act_edit), leadingIcon = AppIcons.Edit) {
                            menu = false
                            onEdit()
                        }
                    }
                    OMenu(stringResource(Res.string.act_note), leadingIcon = AppIcons.Note) {
                        menu = false
                        onNote()
                    }
                    if (item.isText) {
                        OMenu(stringResource(Res.string.act_transform), leadingIcon = AppIcons.Transform) {
                            menu = false
                            onTransform()
                        }
                        OMenu(stringResource(Res.string.act_translate), leadingIcon = AppIcons.Translate) {
                            menu = false
                            onTranslate()
                        }
                    }
                    OMenu(stringResource(Res.string.act_duplicate), leadingIcon = AppIcons.Duplicate) {
                        menu = false
                        model.duplicate(item)
                    }
                    if (model.supportsSaveToDownloads) {
                        OMenu(stringResource(Res.string.act_save_to_downloads), leadingIcon = AppIcons.Download) {
                            menu = false
                            model.saveToDownloads(item)
                        }
                    }
                    if (model.canResend(item)) {
                        OMenu(stringResource(Res.string.act_resend), enabled = online, leadingIcon = AppIcons.Send) {
                            menu = false
                            model.resend(item)
                        }
                    }
                    OMenu(stringResource(Res.string.act_detail), leadingIcon = AppIcons.Info) {
                        menu = false
                        onDetail()
                    }
                    OinkMenuSeparator()
                    OMenu(stringResource(Res.string.act_move_up), enabled = canUp, leadingIcon = AppIcons.Up) {
                        menu = false
                        model.move(item, -1)
                    }
                    OMenu(stringResource(Res.string.act_move_down), enabled = canDown, leadingIcon = AppIcons.Down) {
                        menu = false
                        model.move(item, 1)
                    }
                    OMenu(stringResource(Res.string.act_move_top), enabled = canUp, leadingIcon = AppIcons.Top) {
                        menu = false
                        onMoveTop()
                    }
                    OMenu(stringResource(Res.string.act_move_bottom), enabled = canDown, leadingIcon = AppIcons.Bottom) {
                        menu = false
                        onMoveBottom()
                    }
                    OMenu(stringResource(Res.string.act_move_to), enabled = canUp || canDown, leadingIcon = AppIcons.Position) {
                        menu = false
                        onMoveTo()
                    }
                    OinkMenuSeparator()
                    OMenu(stringResource(Res.string.act_delete), leadingIcon = AppIcons.Delete, destructive = true) {
                        menu = false
                        model.remove(item)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RowAction(
        icon: ImageVector,
        description: String,
        active: Boolean = false,
        enabled: Boolean = true,
        tinted: Color? = null,
        onClick: () -> Unit,
) {
    // 统一走 oink 按钮变体：普通=幽灵、选中/进行中=强调、危险操作=红，禁用态交给组件自身处理。
    val variant =
            when {
                tinted != null -> OinkButtonVariant.Danger
                active -> OinkButtonVariant.Accent
                else -> OinkButtonVariant.Ghost
            }
    Tip(description) {
        OinkIconButton(
                icon = icon,
                onClick = onClick,
                enabled = enabled,
                contentDescription = description,
                variant = variant,
                size = OinkButtonSize.Sm,
                modifier = Modifier.size(33.dp),
        )
    }
}

@Composable
internal fun FieldIcon(icon: ImageVector) {
    OinkIcon(icon, contentDescription = null, tint = OinkTheme.colors.text2, modifier = Modifier.size(18.dp))
}

@Composable
internal fun InlineMessage(
        icon: ImageVector,
        text: String,
        isError: Boolean,
        modifier: Modifier = Modifier,
        action: (() -> Unit)? = null,
        actionLabel: String? = null,
        actionIcon: ImageVector = AppIcons.Refresh,
) {
    val c = OinkTheme.colors
    Row(
            modifier =
                    modifier
                            .fillMaxWidth()
                            .background(if (isError) c.dangerSoft else c.well, RoundedCornerShape(14.dp))
                            .border(1.dp, c.line, RoundedCornerShape(14.dp))
                            .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        OinkIcon(icon, contentDescription = null, tint = if (isError) c.dangerText else c.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        OinkText(text, style = OinkTheme.textStyles.caption, color = if (isError) c.dangerText else c.text, modifier = Modifier.weight(1f))
        if (action != null && actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            OinkButton(text = actionLabel, onClick = action, variant = OinkButtonVariant.Ghost, size = OinkButtonSize.Sm)
        } else if (action != null) {
            OinkIconButton(icon = AppIcons.Close, onClick = action, size = OinkButtonSize.Sm, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun SectionLabel(resource: StringResource) {
    OinkText(
            stringResource(resource),
            style = OinkTheme.textStyles.caption,
            color = OinkTheme.colors.text3,
            modifier = Modifier.padding(bottom = 5.dp),
    )
}

@Composable
private fun OMenu(
        text: String,
        enabled: Boolean = true,
        leadingIcon: ImageVector? = null,
        destructive: Boolean = false,
        onClick: () -> Unit,
) {
    OinkMenuItem(
            text = text,
            onClick = onClick,
            enabled = enabled,
            leadingIcon = leadingIcon,
            destructive = destructive,
    )
}

@Composable
private fun rowBackground(cursor: Boolean, picked: Boolean): Color {
    val c = OinkTheme.colors
    return when {
        picked -> c.accentSoft
        cursor -> c.accentTint
        else -> c.card
    }
}

@Composable
private fun rowBorder(cursor: Boolean, picked: Boolean): Color {
    val c = OinkTheme.colors
    return when {
        picked -> c.accent
        cursor -> c.accent.copy(alpha = 0.5f)
        else -> c.line
    }
}
