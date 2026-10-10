package zhiqiu.app.cs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.displayTitle
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.files.isImage
import zhiqiu.app.cs.resources.*

internal val ROW_HORIZONTAL_PADDING = 14.dp

@Composable
internal fun ClipRow(
        model: AppModel,
        item: ClipItem,
        now: Long,
        cursor: Boolean,
        picked: Boolean,
        selectionActive: Boolean,
        canMoveUp: Boolean,
        canMoveDown: Boolean,
        onActivate: () -> Unit,
        onTogglePick: () -> Unit,
        onEdit: () -> Unit,
        onNote: () -> Unit,
        onDetail: () -> Unit,
        onTransform: () -> Unit,
        modifier: Modifier = Modifier,
) {
    val isFile = item.kind == ClipRepository.KIND_FILE
    val file = item.file
    val cached = isFile && file?.localPath != null
    val copyAsFile = file != null && cached && model.supportsClipboardFiles
    val copyAsImage = file != null && isImage(file.mime) && model.supportsClipboardImage
    var menu by remember { mutableStateOf(false) }

    val copyLabel =
            when {
                copyAsImage -> stringResource(Res.string.cd_copy_image)
                copyAsFile -> stringResource(Res.string.cd_copy_file)
                else -> stringResource(Res.string.cd_copy_text)
            }

    val colorSpec = tween<Color>(M3.SHORT_4, easing = M3.STANDARD)
    val background by animateColorAsState(rowBackground(cursor, picked), colorSpec, label = "rowBg")
    val edge by animateColorAsState(rowBorder(cursor, picked), colorSpec, label = "rowEdge")
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
                modifier = Modifier.clickable(onClick = onActivate).padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
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
                    PickBox(picked = picked, onToggle = onTogglePick)
                    Spacer(Modifier.width(10.dp))
                }
            }

            IconBadge(
                    icon =
                            when {
                                !isFile -> AppIcons.Clipboard
                                file != null && isImage(file.mime) -> AppIcons.Image
                                else -> AppIcons.Description
                            },
                    tint = badge,
            )

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                        text =
                                when {
                                    isFile && file != null -> displayTitle(file)
                                    else -> item.text.lineSequence().first().ifBlank {
                                        stringResource(Res.string.empty_text)
                                    }
                                },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (cursor) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isFile) {
                    RowAction(AppIcons.Edit, stringResource(Res.string.cd_edit), onClick = onEdit)
                }
                if (!isFile || copyAsImage || copyAsFile) {
                    RowAction(AppIcons.Copy, copyLabel) { model.copy(item) }
                }
                if (isFile && cached && model.supportsLocalFiles) {
                    RowAction(AppIcons.OpenInNew, stringResource(Res.string.cd_open)) { model.open(item) }
                } else if (isFile && !cached && model.supportsLocalFiles) {
                    RowAction(AppIcons.Download, stringResource(Res.string.cd_download)) { model.download(item) }
                }
                RowAction(
                        AppIcons.Pin,
                        if (item.pinned) stringResource(Res.string.cd_unpin) else stringResource(Res.string.cd_pin),
                        active = item.pinned,
                ) { model.togglePin(item) }
                Box {
                    RowAction(AppIcons.More, stringResource(Res.string.cd_more), onClick = { menu = true })
                    DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.width(232.dp),
                    ) {
                        MenuAction(AppIcons.Note, Res.string.act_note) {
                            menu = false
                            onNote()
                        }
                        MenuAction(AppIcons.Transform, Res.string.act_transform, enabled = !isFile) {
                            menu = false
                            onTransform()
                        }
                        MenuAction(AppIcons.Info, Res.string.act_detail) {
                            menu = false
                            onDetail()
                        }
                        MenuAction(AppIcons.Duplicate, Res.string.act_duplicate) {
                            menu = false
                            model.duplicate(item)
                        }
                        MenuAction(AppIcons.Up, Res.string.act_move_up, enabled = canMoveUp) {
                            menu = false
                            model.move(item, -1)
                        }
                        MenuAction(AppIcons.Down, Res.string.act_move_down, enabled = canMoveDown) {
                            menu = false
                            model.move(item, 1)
                        }
                        MenuAction(AppIcons.Delete, Res.string.act_delete, tinted = true) {
                            menu = false
                            model.remove(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAction(
        icon: ImageVector,
        label: StringResource,
        enabled: Boolean = true,
        tinted: Boolean = false,
        onClick: () -> Unit,
) {
    val color = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
            text = { Text(stringResource(label), fontSize = 13.sp, color = color) },
            leadingIcon = {
                Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            enabled = enabled,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    )
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
        picked -> scheme.primary.copy(alpha = 0.17f)
        cursor -> scheme.surfaceContainerHigh
        else -> if (isAppDarkTheme()) Color.White.copy(alpha = 0.035f) else Color.White.copy(alpha = 0.55f)
    }
}

@Composable
private fun rowBorder(cursor: Boolean, picked: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        picked -> scheme.primary.copy(alpha = 0.55f)
        cursor -> scheme.outlineVariant
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
internal fun IconBadge(
        icon: ImageVector,
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 32.dp,
        tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
            modifier =
                    modifier
                            .size(size)
                            .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    MaterialTheme.shapes.extraSmall,
                            ),
            contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
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
