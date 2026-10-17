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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.MoveInfo
import zhiqiu.app.cs.files.displayTitle
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.files.isImage
import zhiqiu.app.cs.resources.*

internal val ROW_HORIZONTAL_PADDING = 14.dp

/** 左侧编号栏：右对齐，三位数也放得下。 */
private val NUMBER_WIDTH = 24.dp

/** 正文整段显示，行高跟着内容走；只给超长内容留个兜底上限。 */
private const val MAX_BODY_LINES = 24

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
    // 桌面/网页：快捷操作默认收起，指针掠过（或键盘聚焦）才展开；勾选/选中不再触发显隐。触摸设备常驻。
    val rowInteraction = remember { MutableInteractionSource() }
    val hovered by rowInteraction.collectIsHoveredAsState()
    val revealQuick = !model.hoverRevealsActions || hovered || cursor
    val badge by
            animateColorAsState(
                    if (item.pinned) {
                        if (isAppDarkTheme()) PinAmberDark else PinAmber
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    tween(M3.MEDIUM_1, easing = M3.STANDARD),
                    label = "rowBadge",
            )

    Surface(
            color = background,
            border = BorderStroke(1.dp, edge),
            shape = MaterialTheme.shapes.small,
            modifier = modifier.fillMaxWidth().padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 3.dp),
    ) {
        Row(
                modifier =
                        Modifier.clickable(
                                        interactionSource = rowInteraction,
                                        indication = LocalIndication.current,
                                        onClick = onActivate,
                                )
                                .padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.Top,
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
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PickBox(picked = picked, onToggle = onTogglePick)
                    Spacer(Modifier.width(10.dp))
                }
            }

            val thumbPath = file?.localPath?.takeIf { isImage(file.mime) }
            val thumb = rememberImageBitmap(thumbPath, THUMB_MAX_SIDE) { model.cachedBytes(item) }
            Box(
                    modifier = Modifier.width(NUMBER_WIDTH).padding(top = 7.dp),
                    contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (cursor) FontWeight.SemiBold else FontWeight.Normal,
                        color = badge,
                )
            }
            if (thumb != null) {
                Spacer(Modifier.width(10.dp))
                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .clickable(onClick = onZoom),
                ) {
                    Image(
                            bitmap = thumb,
                            contentDescription = stringResource(Res.string.cd_zoom_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 5.dp)) {
                Text(
                        text =
                                when {
                                    isFile && file != null -> displayTitle(file)
                                    else -> item.text.ifBlank { stringResource(Res.string.empty_text) }
                                },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (cursor) FontWeight.Medium else FontWeight.Normal,
                        maxLines = MAX_BODY_LINES,
                        overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
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
                Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                )
            }

            // 右侧操作区始终按完整尺寸占位，hover 只淡入淡出透明度——不动画宽度，
            // 左侧 weight(1f) 的文本列宽度才恒定，长文本不会因为重排闪烁。
            // 触摸设备（没有 hover）不进淡出逻辑，行为同旧版。
            val fadeSupported = model.hoverRevealsActions
            val actionsAlpha by animateFloatAsState(
                    if (!fadeSupported || revealQuick) 1f else 0f,
                    tween(M3.SHORT_4, easing = M3.STANDARD),
                    label = "actionsAlpha",
            )
            val actionsEnabled = !fadeSupported || revealQuick
            Row(
                    modifier = Modifier.alpha(actionsAlpha),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isFile) {
                    RowAction(AppIcons.Send, stringResource(Res.string.cd_send), enabled = actionsEnabled && online) {
                        model.sendText(item.text)
                    }
                }
                if (!isFile || copyAsImage || copyAsFile) {
                    RowAction(AppIcons.Copy, copyLabel, enabled = actionsEnabled) { model.copy(item) }
                }
                if (isFile && cached && model.supportsLocalFiles) {
                    RowAction(AppIcons.OpenInNew, stringResource(Res.string.cd_open), enabled = actionsEnabled) { model.open(item) }
                } else if (isFile && !cached && model.supportsLocalFiles) {
                    RowAction(AppIcons.Download, stringResource(Res.string.cd_download), enabled = actionsEnabled) { model.download(item) }
                }
                RowAction(
                        AppIcons.Pin,
                        if (item.pinned) stringResource(Res.string.cd_unpin) else stringResource(Res.string.cd_pin),
                        active = item.pinned,
                        enabled = actionsEnabled,
                ) { model.togglePin(item) }
            }
            Box {
                    RowAction(AppIcons.More, stringResource(Res.string.cd_more), onClick = { menu = true })
                    DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = 3.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.width(216.dp),
                    ) {
                        MenuAction(AppIcons.Edit, Res.string.act_edit, enabled = !isFile) {
                            menu = false
                            onEdit()
                        }
                        MenuAction(AppIcons.Note, Res.string.act_note) {
                            menu = false
                            onNote()
                        }
                        MenuAction(AppIcons.Transform, Res.string.act_transform, enabled = !isFile) {
                            menu = false
                            onTransform()
                        }
                        MenuAction(AppIcons.Translate, Res.string.act_translate, enabled = !isFile) {
                            menu = false
                            onTranslate()
                        }
                        MenuAction(AppIcons.Duplicate, Res.string.act_duplicate) {
                            menu = false
                            model.duplicate(item)
                        }
                        if (model.supportsSaveToDownloads) {
                            MenuAction(AppIcons.Download, Res.string.act_save_to_downloads) {
                                menu = false
                                model.saveToDownloads(item)
                            }
                        }
                        if (model.canResend(item)) {
                            MenuAction(AppIcons.Send, Res.string.act_resend, enabled = online) {
                                menu = false
                                model.resend(item)
                            }
                        }
                        MenuAction(AppIcons.Info, Res.string.act_detail) {
                            menu = false
                            onDetail()
                        }
                        MenuDivider()
                        MenuAction(AppIcons.Up, Res.string.act_move_up, enabled = canUp) {
                            menu = false
                            model.move(item, -1)
                        }
                        MenuAction(AppIcons.Down, Res.string.act_move_down, enabled = canDown) {
                            menu = false
                            model.move(item, 1)
                        }
                        MenuAction(AppIcons.Top, Res.string.act_move_top, enabled = canUp) {
                            menu = false
                            onMoveTop()
                        }
                        MenuAction(AppIcons.Bottom, Res.string.act_move_bottom, enabled = canDown) {
                            menu = false
                            onMoveBottom()
                        }
                        MenuAction(AppIcons.Position, Res.string.act_move_to, enabled = canUp || canDown) {
                            menu = false
                            onMoveTo()
                        }
                        MenuDivider()
                        MenuAction(AppIcons.Delete, Res.string.act_delete, tinted = true) {
                            menu = false
                            model.remove(item)
                        }
                    }
                }
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
            thickness = 0.7.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun MenuAction(
        icon: ImageVector,
        label: StringResource,
        enabled: Boolean = true,
        tinted: Boolean = false,
        onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fade = if (enabled) 1f else 0.38f
    val ink = (if (tinted) scheme.error else scheme.onSurface).copy(alpha = fade)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val highlight by
            animateColorAsState(
                    if (enabled && (pressed || hovered)) scheme.onSurface.copy(alpha = 0.07f) else Color.Transparent,
                    tween(M3.SHORT_4, easing = M3.STANDARD),
                    label = "menuHighlight",
            )
    Row(
            modifier =
                    Modifier.padding(horizontal = 5.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(highlight)
                            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                            .height(32.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = (if (tinted) scheme.error else scheme.onSurfaceVariant).copy(alpha = fade),
        )
        Spacer(Modifier.width(11.dp))
        Text(stringResource(label), fontSize = 12.5.sp, maxLines = 1, color = ink)
    }
}

@Composable
private fun PickBox(picked: Boolean, onToggle: () -> Unit) {
    val fill by
            animateColorAsState(
                    if (picked) MaterialTheme.colorScheme.primary else Color.Transparent,
                    tween(M3.SHORT_4, easing = M3.STANDARD),
                    label = "pickFill",
            )
    Box(
            modifier =
                    Modifier.size(20.dp)
                            .background(fill, RoundedCornerShape(6.dp))
                            .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Icon(
                    AppIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp),
            )
        } else {
            Box(
                    modifier =
                            Modifier.size(16.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun rowBackground(cursor: Boolean, picked: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        picked -> scheme.primary.copy(alpha = 0.16f)
        cursor -> scheme.primary.copy(alpha = 0.08f)
        // 浅色用纯白面而不是半透明白：叠在渐变底上会发脏灰，纯白卡片才干净
        else -> if (isAppDarkTheme()) scheme.surfaceVariant.copy(alpha = 0.5f) else scheme.surface
    }
}

@Composable
private fun rowBorder(cursor: Boolean, picked: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        picked -> scheme.primary.copy(alpha = 0.55f)
        cursor -> scheme.primary.copy(alpha = 0.32f)
        else -> scheme.outlineVariant.copy(alpha = 0.45f)
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
    val ink by
            animateColorAsState(
                    targetValue =
                            when {
                                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                tinted != null -> tinted
                                active -> if (isAppDarkTheme()) PinAmberDark else PinAmber
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    animationSpec = tween(M3.SHORT_4, easing = M3.STANDARD),
                    label = "actionInk",
            )
    Tip(description) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(33.dp)) {
            Icon(
                    icon,
                    contentDescription = description,
                    tint = ink,
                    modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun FieldIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
    val scheme = MaterialTheme.colorScheme
    val container = if (isError) scheme.errorContainer.copy(alpha = 0.9f) else scheme.surface.copy(alpha = 0.8f)
    val content = if (isError) scheme.onErrorContainer else scheme.onSurface
    Row(
            modifier =
                    modifier
                            .fillMaxWidth()
                            .background(container, MaterialTheme.shapes.small)
                            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                            .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null && actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            Button(
                    onClick = action,
                    colors = ButtonDefaults.textButtonColors(contentColor = content),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.extraSmall,
            ) {
                Icon(actionIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionLabel, fontSize = 12.sp)
            }
        } else if (action != null) {
            IconButton(onClick = action, modifier = Modifier.size(26.dp)) {
                Icon(AppIcons.Close, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun SectionLabel(resource: StringResource) {
    Text(
            stringResource(resource),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp),
    )
}
