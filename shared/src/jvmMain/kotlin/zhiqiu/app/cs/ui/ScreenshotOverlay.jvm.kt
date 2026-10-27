package zhiqiu.app.cs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.BasicStroke
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.overlay_record_tag
import zhiqiu.app.cs.resources.record_exit
import zhiqiu.app.cs.resources.record_format
import zhiqiu.app.cs.resources.record_mic_default
import zhiqiu.app.cs.resources.record_mic_off
import zhiqiu.app.cs.resources.record_quality_hd
import zhiqiu.app.cs.resources.record_quality_hd_hint
import zhiqiu.app.cs.resources.record_quality_native
import zhiqiu.app.cs.resources.record_quality_native_hint
import zhiqiu.app.cs.resources.record_quality_smooth
import zhiqiu.app.cs.resources.record_quality_smooth_hint
import zhiqiu.app.cs.resources.record_start
import zhiqiu.app.cs.resources.record_system_off
import zhiqiu.app.cs.resources.record_system_on
import zhiqiu.app.cs.resources.shot_cancel
import zhiqiu.app.cs.resources.shot_confirm
import zhiqiu.app.cs.resources.shot_text_hint
import zhiqiu.app.cs.resources.shot_tool_arrow
import zhiqiu.app.cs.resources.shot_tool_box
import zhiqiu.app.cs.resources.shot_tool_mosaic
import zhiqiu.app.cs.resources.shot_tool_pen
import zhiqiu.app.cs.resources.shot_tool_redo
import zhiqiu.app.cs.resources.shot_tool_select
import zhiqiu.app.cs.resources.shot_tool_text
import zhiqiu.app.cs.resources.shot_tool_undo

/** 除了用户瞄准的那块，其余都压这么黑，和 Rust 端的 128/255 一致。 */
private val Mask = Color(0x80000000L)
private val Ink = Color(0xE61A1A1A)
private val HandleFill = Color(0xFFFFFFFF)
private val Accent = Color(0xFF2B7FFF)
private val Confirm = Color(0xFF3FB950)
private val MarkRed = Color(0xFFFF3B30)
private val Muted = Color(0xFFB8B8B8)

/**
 * 微信那种截屏遮罩层：整屏冻住、压暗，拖一块、拉八个手柄，切下来交给调用方。
 *
 * 窗口整个进程只建一次，用完藏起来而不是关掉——关掉只关 Compose 那一半，原生窗口还挂在系统里，
 * 下一次截屏就会叠在上面。几何全在 [ScreenshotSession] 里，这里只负责把事件喂进去、把结果画出来，
 * 和 Rust 端 `clipsync-desktop/src/overlay.rs` 是同一套流程。
 */
object ScreenshotOverlay : PlatformScreenshotOverlay {

    override val isAvailable: Boolean
        get() = !GraphicsEnvironment.isHeadless()

    private var pending by mutableStateOf<OverlayRequest?>(null)

    /** 跟 pending 拆开：先藏窗口再清请求，避免中间闪一帧空窗。 */
    private var overlayShown by mutableStateOf(false)

    /** 弹遮罩层等用户框选：切好了返回 PNG 字节，按取消返回 null。 */
    override suspend fun crop(): ByteArray? {
        if (pending != null) return null
        val hideOwn = !AppSettings.shared.screenshotKeepWindow.value
        val freeze = withContext(Dispatchers.IO) { ScreenFreeze.grab(hideOwnWindows = hideOwn) }
        val request = OverlayRequest(freeze, record = false)
        pending = request
        overlayShown = true
        try {
            val outcome = request.done.await()
            // 先收遮罩：自家窗口还藏着，不会在压暗画面后突然蹦出主窗口。
            overlayShown = false
            yield()
            val crop = outcome as? Outcome.Crop ?: return null
            return withContext(Dispatchers.IO) {
                encode(freeze.image, freeze.screen, crop.rect, request.session.marks.marks)
            }
        } finally {
            overlayShown = false
            pending = null
            freeze.restore()
        }
    }

    /** 同一套框选，但确认后返回区域和工具条上选的录屏选项。 */
    override suspend fun selectRegion(): RecordSelection? {
        if (pending != null) return null
        // 录屏必须把自家窗口收起来：录进去的每一帧都不该出现 ClipSync。
        MainWindowVisibility.hideForRecording()
        val freeze = withContext(Dispatchers.IO) { ScreenFreeze.grab(hideOwnWindows = true) }
        val request = OverlayRequest(freeze, record = true)
        pending = request
        overlayShown = true
        var keepHidden = false
        try {
            val outcome = request.done.await()
            overlayShown = false
            yield()
            val start = outcome as? Outcome.StartRecording ?: return null
            val options = start.options
            // 工具条选过的值回写设置，下次打开还是这一套。
            AppSettings.shared.setRecordLongEdge(options.quality.longEdge)
            AppSettings.shared.setRecordFps(options.quality.fps)
            AppSettings.shared.setRecordSystemAudio(options.systemAudio)
            AppSettings.shared.setRecordMic(options.mic)
            AppSettings.shared.setRecordMicDevice(options.micDevice)
            keepHidden = true
            val rect = start.rect
            return RecordSelection(
                RecordRegion(rect.left, rect.top, rect.width, rect.height),
                options.quality,
                options.systemAudio,
                options.mic,
                options.micDevice,
            )
        } finally {
            overlayShown = false
            pending = null
            if (keepHidden) {
                // 录屏要开始了：窗口一直藏着，等录完由 AppModel 按录前状态还原。
                // 这里不能调 freeze.restore()，否则主窗口会在框选结束的一瞬间弹出来。
            } else {
                freeze.restore()
                MainWindowVisibility.restoreAfterRecording()
            }
        }
    }

    /** 遮罩层窗口本身：没有请求时它是藏着的，不是不存在。 */
    @Composable
    fun OverlayWindow() {
        val bounds = remember { displayBounds() }
        val state =
            rememberWindowState(
                position = WindowPosition.Absolute(bounds.x.dp, bounds.y.dp),
                size = DpSize(bounds.width.dp, bounds.height.dp),
            )
        Window(
            onCloseRequest = { pending?.settle(Outcome.Cancel) },
            state = state,
            // Esc 之外没有焦点所有者也照样收得到：窗口级的按键过滤在焦点系统之前。
            onPreviewKeyEvent = ::onKeyEvent,
            visible = overlayShown,
            undecorated = true,
            resizable = false,
            title = OVERLAY_TITLE,
        ) {
            val request = pending
            if (request != null) {
                // 置顶只到浮动级，压不过菜单栏，所以再把原生窗口的层级抬上去。
                LaunchedEffect(request) {
                    window.isAlwaysOnTop = true
                    MacWindowLevel.raiseAboveMenuBar()
                    window.toFront()
                    window.requestFocus()
                }
                OverlayFrame(request)
            }
        }
    }

    /** 回车切这块，Esc 放弃；Cmd+Z / Cmd+Shift+Z 撤销重做；别的键留给系统。 */
    private fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val request = pending ?: return false
        val session = request.session
        // 文字输入框自己处理 Enter/Esc，别在这里把整个遮罩关掉。
        if (session.draft != null) return false
        if (event.isMetaPressed && event.key == Key.Z) {
            if (event.isShiftPressed) session.redo() else session.undo()
            request.revision.intValue++
            return true
        }
        when (event.key) {
            Key.Escape -> session.onEscape()
            Key.Enter -> session.onEnter()
            else -> return false
        }
        request.revision.intValue++
        request.settle(session.outcome)
        return true
    }
}

/** 遮罩层和主窗口靠标题认亲，所以这个标题只有这两处用得上。 */
internal const val OVERLAY_TITLE: String = "ClipSync 截图"

/** 一次框选：冻好的画面、它的状态机、等结果的坑位，外加一笔重画用的计数。 */
private class OverlayRequest(val freeze: Freeze, val record: Boolean) {

    val session =
        ScreenshotSession(
            freeze.screen,
            record,
            RecordOptions(
                quality = RecordQuality.of(AppSettings.shared.recordLongEdge.value),
                systemAudio = AppSettings.shared.recordSystemAudio.value,
                mic = AppSettings.shared.recordMic.value,
                micDevice = AppSettings.shared.recordMicDevice.value,
            ),
        )
    val done = CompletableDeferred<Outcome?>()

    /** 状态机自己不带可观察状态，每接一个事件加一笔，画面才知道要重画。 */
    val revision = mutableIntStateOf(0)

    /** 指针下面该是什么光标，跟着每一笔移动走。 */
    val cursor = mutableStateOf<CursorShape>(CursorShape.Crosshair)

    /** 收尾：只交结果，切 PNG 放到遮罩收起之后，别卡在黑屏上晃眼。 */
    fun settle(outcome: Outcome?) {
        if (outcome == null || done.isCompleted) return
        done.complete(outcome)
    }
}

/** 选区切出来那块的 PNG，顺便把标注按 Retina 比例烘焙进去。 */
private fun encode(image: BufferedImage, screen: FrozenScreen, rect: Rect, marks: List<Mark>): ByteArray? {
    val px = cropPx(rect, Size(screen.widthPx.toFloat(), screen.heightPx.toFloat()), screen.scale)
    if (px.x + px.width > image.width || px.y + px.height > image.height) return null
    val cropped = image.getSubimage(px.x, px.y, px.width, px.height)
    if (marks.isEmpty()) {
        val out = ByteArrayOutputStream()
        ImageIO.write(cropped, "png", out)
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }
    val out = BufferedImage(cropped.width, cropped.height, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.drawImage(cropped, 0, 0, null)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = java.awt.Color(0xFF, 0x3B, 0x30)
        g.stroke =
            BasicStroke(
                3f * screen.scale,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
            )
        val font = java.awt.Font("PingFang SC", java.awt.Font.PLAIN, max(12, (18 * screen.scale).roundToInt()))
        g.font = font
        for (mark in marks) {
            when (mark) {
                is Mark.Box -> {
                    val r = mark.rect.toCropPx(px, screen.scale)
                    g.drawRect(r.x, r.y, max(1, r.width), max(1, r.height))
                }
                is Mark.Arrow -> drawArrow(g, mark.from.toCropPx(px, screen.scale), mark.to.toCropPx(px, screen.scale), screen.scale)
                is Mark.Pen -> {
                    if (mark.points.size >= 2) {
                        val path = java.awt.geom.GeneralPath()
                        val first = mark.points.first().toCropPx(px, screen.scale)
                        path.moveTo(first.x.toDouble(), first.y.toDouble())
                        for (point in mark.points.drop(1)) {
                            val p = point.toCropPx(px, screen.scale)
                            path.lineTo(p.x.toDouble(), p.y.toDouble())
                        }
                        g.draw(path)
                    }
                }
                is Mark.Text -> {
                    val at = mark.at.toCropPx(px, screen.scale)
                    val metrics = g.fontMetrics
                    g.drawString(mark.text, at.x, at.y + metrics.ascent)
                }
                is Mark.Mosaic -> {
                    val r = mark.rect.toCropPx(px, screen.scale)
                    mosaic(g, cropped, r, max(1, (12 * screen.scale).roundToInt()))
                }
            }
        }
    } finally {
        g.dispose()
    }
    val bytes = ByteArrayOutputStream()
    ImageIO.write(out, "png", bytes)
    return bytes.toByteArray().takeIf { it.isNotEmpty() }
}

private fun Rect.toCropPx(crop: PixelRect, scale: Float): java.awt.Rectangle =
    java.awt.Rectangle(
        (left * scale - crop.x).roundToInt(),
        (top * scale - crop.y).roundToInt(),
        max(1, (width * scale).roundToInt()),
        max(1, (height * scale).roundToInt()),
    )

private fun Offset.toCropPx(crop: PixelRect, scale: Float): Offset =
    Offset(x * scale - crop.x, y * scale - crop.y)

private fun drawArrow(g: java.awt.Graphics2D, from: Offset, to: Offset, scale: Float) {
    g.drawLine(from.x.roundToInt(), from.y.roundToInt(), to.x.roundToInt(), to.y.roundToInt())
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val head = (14 * scale).toDouble()
    val left = Offset(to.x - (head * cos(angle - PI / 6)).toFloat(), to.y - (head * sin(angle - PI / 6)).toFloat())
    val right = Offset(to.x - (head * cos(angle + PI / 6)).toFloat(), to.y - (head * sin(angle + PI / 6)).toFloat())
    val polygon =
        java.awt.Polygon(
            intArrayOf(to.x.roundToInt(), left.x.roundToInt(), right.x.roundToInt()),
            intArrayOf(to.y.roundToInt(), left.y.roundToInt(), right.y.roundToInt()),
            3,
        )
    g.fill(polygon)
}

private fun mosaic(g: java.awt.Graphics2D, source: BufferedImage, rect: java.awt.Rectangle, block: Int) {
    val left = rect.x.coerceIn(0, source.width - 1)
    val top = rect.y.coerceIn(0, source.height - 1)
    val right = (rect.x + rect.width).coerceIn(left + 1, source.width)
    val bottom = (rect.y + rect.height).coerceIn(top + 1, source.height)
    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val sampleX = (x + block / 2).coerceIn(0, source.width - 1)
            val sampleY = (y + block / 2).coerceIn(0, source.height - 1)
            g.color = java.awt.Color(source.getRGB(sampleX, sampleY), true)
            g.fillRect(x, y, minOf(block, right - x), minOf(block, bottom - y))
            x += block
        }
        y += block
    }
}

@Composable
private fun OverlayFrame(request: OverlayRequest) {
    val session = request.session
    val bitmap = remember(request) { request.freeze.image.toComposeImageBitmap() }
    val pixelMap = remember(request) { bitmap.toPixelMap() }
    // 这一笔读在组合里：工具条出现、读数跟着选区走，都靠它让这一层重画。
    request.revision.intValue
    var surface by remember(request) { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val unit = if (surface.width > 0 && session.frame.width > 0f) surface.width / session.frame.width else 1f

    Box(
        Modifier.fillMaxSize()
            .pointerHoverIcon(PointerIcon(Cursor(awtCursor(request.cursor.value))))
            .onSizeChanged { surface = it }
    ) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(request) {
                // 遮罩是按 AWT 的点对齐的，画布像素可能更多（Retina）；换算回点，状态机才只有长度没有比例。
                val scale = if (session.frame.width > 0f) size.width / session.frame.width else 1f
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // 工具条上的按下已经被按钮消费掉了，这里不要再开一块选区。
                        if (event.changes.any { it.isConsumed }) {
                            request.revision.intValue++
                            continue
                        }
                        for (change in event.changes) {
                            val at = Offset(change.position.x / scale, change.position.y / scale)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                session.onRightClick()
                            } else {
                                when (event.type) {
                                    PointerEventType.Press -> session.onDown(at, nowMs())
                                    PointerEventType.Move ->
                                        if (change.pressed) session.onDrag(at) else session.onHover(at)
                                    PointerEventType.Release -> session.onUp(at, nowMs())
                                    else -> Unit
                                }
                            }
                            request.cursor.value = session.cursor(at)
                        }
                        request.revision.intValue++
                        request.settle(session.outcome)
                    }
                }
            }
        ) {
            request.revision.intValue
            drawFreeze(session, bitmap, pixelMap, unit, textMeasurer)
        }

        val hole = session.hole
        if (hole != null && surface.width > 0 && session.frame.width > 0f) {
            SizeReadout(hole = hole, unit = unit, label = session.readout.orEmpty(), record = request.record)
        }

        if (request.record) {
            RecordBar(request, unit)
        } else {
            ShotBar(request, unit, surface)
            TextEditor(request, unit)
        }
    }
}

/** 尺寸徽标：贴在选区左上角里面，黑底白字。 */
@Composable
private fun BoxScope.SizeReadout(hole: Rect, unit: Float, label: String, record: Boolean) {
    Row(
        Modifier.offset {
                IntOffset(
                    (hole.left * unit + 8).roundToInt(),
                    (hole.top * unit + 8).roundToInt(),
                )
            }
            .background(Ink, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (record) {
            Box(Modifier.size(7.dp).background(RecordRed, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(text = label, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** 录屏工具条：固定在屏幕顶部居中，选择格式、画质、声音来源，再开始/退出。 */
@Composable
private fun BoxScope.RecordBar(request: OverlayRequest, unit: Float) {
    // 同上：画质/声音选择存在普通字段里，靠 revision 通知重画。
    request.revision.intValue
    val session = request.session
    val options = session.options
    var qualityOpen by remember { mutableStateOf(false) }
    var systemOpen by remember { mutableStateOf(false) }
    var micOpen by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(recordingInputs()) }
    val systemLabel =
            stringResource(
                    if (options.systemAudio) Res.string.record_system_on
                    else Res.string.record_system_off
            )
    val micLabel =
            when {
                !options.mic -> stringResource(Res.string.record_mic_off)
                options.micDevice.isBlank() -> stringResource(Res.string.record_mic_default)
                else ->
                        devices.firstOrNull { it.id == options.micDevice }?.name
                                ?: stringResource(Res.string.record_mic_default)
            }

    Row(
        Modifier.align(Alignment.TopCenter)
            .padding(top = MacChromeInset.top + 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF21A1A1A))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                session.bar = Rect(b.left / unit, b.top / unit, b.right / unit, b.bottom / unit)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.record_format), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(Accent)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("MP4", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        BarDivider()

        Box {
            TextButton(onClick = { qualityOpen = true }) {
                Text(qualityLabel(options.quality), color = Color.White, fontSize = 13.sp)
                Text(" ▾", color = Muted, fontSize = 12.sp)
            }
            DropdownMenu(expanded = qualityOpen, onDismissRequest = { qualityOpen = false }) {
                for (quality in RecordQuality.entries) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(qualityLabel(quality), fontSize = 13.sp)
                                Text(qualityHint(quality), color = Muted, fontSize = 11.sp)
                            }
                        },
                        trailingIcon = { if (options.quality == quality) Text("✓", color = Accent) },
                        onClick = {
                            options.quality = quality
                            qualityOpen = false
                            request.revision.intValue++
                        },
                    )
                }
            }
        }
        BarDivider()

        Box {
            TextButton(onClick = { systemOpen = true }) {
                Text(systemLabel, color = Color.White, fontSize = 13.sp)
                Text(" ▾", color = Muted, fontSize = 12.sp)
            }
            DropdownMenu(expanded = systemOpen, onDismissRequest = { systemOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.record_system_off), fontSize = 13.sp) },
                    trailingIcon = { if (!options.systemAudio) Text("✓", color = Accent) },
                    onClick = {
                        options.systemAudio = false
                        systemOpen = false
                        request.revision.intValue++
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.record_system_on), fontSize = 13.sp) },
                    trailingIcon = { if (options.systemAudio) Text("✓", color = Accent) },
                    onClick = {
                        options.systemAudio = true
                        systemOpen = false
                        request.revision.intValue++
                    },
                )
            }
        }
        BarDivider()

        Box {
            TextButton(onClick = {
                devices = recordingInputs()
                micOpen = true
            }) {
                Text(micLabel, color = Color.White, fontSize = 13.sp)
                Text(" ▾", color = Muted, fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = micOpen,
                onDismissRequest = { micOpen = false },
                scrollState = rememberScrollState(),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.record_mic_off), fontSize = 13.sp) },
                    trailingIcon = { if (!options.mic) Text("✓", color = Accent) },
                    onClick = {
                        options.mic = false
                        micOpen = false
                        request.revision.intValue++
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.record_mic_default), fontSize = 13.sp) },
                    trailingIcon = {
                        if (options.mic && options.micDevice.isBlank()) Text("✓", color = Accent)
                    },
                    onClick = {
                        options.mic = true
                        options.micDevice = ""
                        micOpen = false
                        request.revision.intValue++
                    },
                )
                for (device in devices) {
                    DropdownMenuItem(
                        text = { Text(device.name, fontSize = 13.sp) },
                        trailingIcon = { if (options.mic && options.micDevice == device.id) Text("✓", color = Accent) },
                        onClick = {
                            options.mic = true
                            options.micDevice = device.id
                            micOpen = false
                            request.revision.intValue++
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                session.onEnter()
                request.settle(session.outcome)
            },
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
        ) {
            Text(stringResource(Res.string.record_start), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                session.onEscape()
                request.settle(session.outcome)
            },
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF)),
        ) {
            Text(stringResource(Res.string.record_exit), fontSize = 13.sp)
        }
    }
}

/** 截图工具栏：贴着选区下方居中，放标注工具、撤销重做和取消/确认。 */
@Composable
private fun BoxScope.ShotBar(request: OverlayRequest, unit: Float, surface: IntSize) {
    // session 里的字段是普通变量，不是 Compose 状态。这里读一下 revision，选区/工具变化时
    // 才会重新走一遍；否则第一次（还没选区）提前返回后，这个工具条就再也不会出现。
    request.revision.intValue
    val session = request.session
    val selection = session.selection ?: return
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val barWidth = barSize.width.toFloat()
    val barHeight = barSize.height.toFloat()
    val x = (selection.center.x * unit - barWidth / 2f).coerceIn(0f, max(0f, surface.width - barWidth))
    val below = selection.bottom * unit + TOOLBAR_GAP * unit
    val above = selection.top * unit - TOOLBAR_GAP * unit - barHeight
    // 全屏框选时上下都贴边，至少把工具条留在菜单栏下面，别让它钻出屏幕。
    val topInset = 32f * unit
    val y = if (below + barHeight > surface.height) max(topInset, above) else below
    val safeY = y.coerceAtMost(max(topInset, surface.height - barHeight))

    Row(
        Modifier.offset { IntOffset(x.roundToInt(), safeY.roundToInt()) }
            .onSizeChanged { barSize = it }
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                session.bar = Rect(b.left / unit, b.top / unit, b.right / unit, b.bottom / unit)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF21A1A1A))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolButton(session.tool == ShotTool.Select, ShotTool.Select, request) { drawSelectIcon() }
        ToolButton(session.tool == ShotTool.Box, ShotTool.Box, request) { drawRectIcon() }
        ToolButton(session.tool == ShotTool.Arrow, ShotTool.Arrow, request) { drawArrowIcon() }
        ToolButton(session.tool == ShotTool.Pen, ShotTool.Pen, request) { drawPenIcon() }
        ToolButton(session.tool == ShotTool.Text, ShotTool.Text, request) { drawTextIcon() }
        ToolButton(session.tool == ShotTool.Mosaic, ShotTool.Mosaic, request) { drawMosaicIcon() }
        BarDivider()
        SmallButton(
            enabled = session.marks.canUndo,
            onClick = {
                session.undo()
                request.revision.intValue++
            },
            icon = { drawUndoIcon(false) },
        )
        SmallButton(
            enabled = session.marks.canRedo,
            onClick = {
                session.redo()
                request.revision.intValue++
            },
            icon = { drawUndoIcon(true) },
        )
        BarDivider()
        SmallButton(
            enabled = true,
            onClick = {
                session.onEscape()
                request.settle(session.outcome)
            },
            icon = { drawCloseIcon(Color.White) },
        )
        Box(Modifier.width(4.dp))
        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable {
                    session.onEnter()
                    request.settle(session.outcome)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) { drawCheckIcon(Color.White) }
        }
    }
}

@Composable
private fun ToolButton(selected: Boolean, tool: ShotTool, request: OverlayRequest, icon: DrawScope.() -> Unit) {
    Box(
        Modifier.size(32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Accent.copy(alpha = 0.35f) else Color.Transparent)
            .clickable {
                request.session.chooseTool(tool)
                request.revision.intValue++
            }
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { icon() }
    }
}

@Composable
private fun SmallButton(enabled: Boolean, onClick: () -> Unit, icon: DrawScope.() -> Unit) {
    Box(
        Modifier.size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { icon() }
    }
}

@Composable
private fun BarDivider() {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(Color(0x33FFFFFF)))
}

@Composable
private fun TextEditor(request: OverlayRequest, unit: Float) {
    // 点「文字」之前 draft 是空的，不订阅 revision 就会一直提前返回，输入框永远不出现。
    request.revision.intValue
    val session = request.session
    val draft = session.draft ?: return
    val focus = remember(draft) { FocusRequester() }
    var text by remember(draft) { mutableStateOf(draft.text) }
    LaunchedEffect(draft) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            draft.text = it
        },
        modifier = Modifier.offset {
                IntOffset((draft.at.x * unit).roundToInt(), (draft.at.y * unit).roundToInt())
            }
            .width(200.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xD9000000))
            .border(1.dp, Accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter -> {
                        session.commitText()
                        request.revision.intValue++
                        true
                    }
                    Key.Escape -> {
                        session.cancelText()
                        request.revision.intValue++
                        true
                    }
                    else -> false
                }
            },
        textStyle = TextStyle(color = MarkRed, fontSize = 15.sp),
        cursorBrush = SolidColor(Accent),
        singleLine = true,
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) {
                    Text(stringResource(Res.string.shot_text_hint), color = Muted, fontSize = 13.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun qualityLabel(quality: RecordQuality): String =
    when (quality) {
        RecordQuality.Native -> stringResource(Res.string.record_quality_native)
        RecordQuality.HD -> stringResource(Res.string.record_quality_hd)
        RecordQuality.Smooth -> stringResource(Res.string.record_quality_smooth)
    }

@Composable
private fun qualityHint(quality: RecordQuality): String =
    when (quality) {
        RecordQuality.Native -> stringResource(Res.string.record_quality_native_hint)
        RecordQuality.HD -> stringResource(Res.string.record_quality_hd_hint)
        RecordQuality.Smooth -> stringResource(Res.string.record_quality_smooth_hint)
    }

/** 冻住的整屏、压暗的四条带、瞄准的框、八个圆形手柄、标注——全在点坐标系里画。 */
private fun DrawScope.drawFreeze(
    session: ScreenshotSession,
    bitmap: ImageBitmap,
    pixelMap: PixelMap,
    unit: Float,
    textMeasurer: TextMeasurer,
) {
    drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    val frame = session.frame
    scale(unit) {
        for (band in maskRects(frame, session.hole)) {
            if (band.width > 0f && band.height > 0f) {
                drawRect(Mask, band.topLeft, band.size)
            }
        }
        val hole = session.hole
        if (hole != null) {
            drawMarks(session, pixelMap)
            drawRect(Accent, hole.topLeft, hole.size, style = Stroke(1.5f))
            if (session.selection != null && session.drag == null) {
                for (handle in HANDLES) {
                    val center = handlePoint(hole, handle)
                    drawCircle(HandleFill, radius = 5f, center = center)
                    drawCircle(Accent, radius = 5f, center = center, style = Stroke(1.5f))
                }
            }
        }
    }
    // 文字标注用 Compose 的文本测量器画，不能在 scale 里按点字号算，单独画在点坐标上。
    for (mark in session.marks.marks + listOfNotNull(session.pending)) {
        if (mark is Mark.Text) {
            val result =
                textMeasurer.measure(
                    mark.text,
                    style = TextStyle(color = MarkRed, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                )
            drawText(result, topLeft = Offset(mark.at.x * unit, mark.at.y * unit))
        }
    }
}

/** 在 scale(unit) 里画标注：矩形、箭头、画笔、马赛克。文字在外面单独画。 */
private fun DrawScope.drawMarks(session: ScreenshotSession, pixelMap: PixelMap) {
    for (mark in session.marks.marks + listOfNotNull(session.pending)) {
        when (mark) {
            is Mark.Box -> drawRect(MarkRed, mark.rect.topLeft, mark.rect.size, style = Stroke(3f))
            is Mark.Arrow -> drawArrowMark(mark.from, mark.to)
            is Mark.Pen -> {
                if (mark.points.size >= 2) {
                    val path = Path()
                    path.moveTo(mark.points.first().x, mark.points.first().y)
                    for (point in mark.points.drop(1)) path.lineTo(point.x, point.y)
                    drawPath(path, MarkRed, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            is Mark.Mosaic -> drawMosaicMark(mark.rect, pixelMap, session.screen.scale)
            is Mark.Text -> Unit
        }
    }
}

private fun DrawScope.drawArrowMark(from: Offset, to: Offset) {
    drawLine(MarkRed, from, to, strokeWidth = 3f, cap = StrokeCap.Round)
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val head = 14.0
    val left = Offset(to.x - (head * cos(angle - PI / 6)).toFloat(), to.y - (head * sin(angle - PI / 6)).toFloat())
    val right = Offset(to.x - (head * cos(angle + PI / 6)).toFloat(), to.y - (head * sin(angle + PI / 6)).toFloat())
    val path =
        Path().apply {
            moveTo(to.x, to.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        }
    drawPath(path, MarkRed)
}

/** 预览里的马赛克：按 12 点的块从冻屏像素里取色，画成一格一格。 */
private fun DrawScope.drawMosaicMark(rect: Rect, pixelMap: PixelMap, imageScale: Float) {
    val block = 12f
    var y = rect.top
    while (y < rect.bottom) {
        var x = rect.left
        while (x < rect.right) {
            val px = (x * imageScale).roundToInt().coerceIn(0, pixelMap.width - 1)
            val py = (y * imageScale).roundToInt().coerceIn(0, pixelMap.height - 1)
            drawRect(
                pixelMap[px, py],
                topLeft = Offset(x, y),
                size = Size(minOf(block, rect.right - x), minOf(block, rect.bottom - y)),
            )
            x += block
        }
        y += block
    }
}

/** 图标：都是 18×18 点的小线条，和工具条一起缩放。 */
private fun DrawScope.drawSelectIcon() {
    val path =
        Path().apply {
            moveTo(4f, 3f)
            lineTo(4f, 14f)
            lineTo(7.5f, 11f)
            lineTo(10f, 16f)
            lineTo(12f, 15f)
            lineTo(9.5f, 10f)
            lineTo(14f, 9.5f)
            close()
        }
    drawPath(path, Color.White)
}

private fun DrawScope.drawRectIcon() {
    drawRect(Color.White, topLeft = Offset(2f, 3f), size = Size(14f, 12f), style = Stroke(1.6f))
}

private fun DrawScope.drawArrowIcon() {
    drawLine(Color.White, Offset(3f, 15f), Offset(15f, 3f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(15f, 3f), Offset(9f, 4f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(15f, 3f), Offset(14f, 9f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawPenIcon() {
    drawLine(Color.White, Offset(3f, 15f), Offset(13f, 5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(13f, 5f), Offset(15f, 3f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(3f, 15f), Offset(6f, 14f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawTextIcon() {
    drawLine(Color.White, Offset(3f, 4f), Offset(15f, 4f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(9f, 4f), Offset(9f, 15f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(6f, 15f), Offset(12f, 15f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawMosaicIcon() {
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            drawRect(
                if ((row + col) % 2 == 0) Color.White else Color(0x66FFFFFF),
                topLeft = Offset(3f + col * 4.4f, 3f + row * 4.4f),
                size = Size(3.4f, 3.4f),
            )
        }
    }
}

private fun DrawScope.drawUndoIcon(redo: Boolean) {
    drawArc(
        Color.White,
        startAngle = if (redo) 210f else -30f,
        sweepAngle = if (redo) 240f else -240f,
        useCenter = false,
        topLeft = Offset(3f, 4f),
        size = Size(12f, 10f),
        style = Stroke(1.6f, cap = StrokeCap.Round),
    )
    val tip = if (redo) Offset(15f, 4f) else Offset(3f, 4f)
    drawLine(Color.White, tip, tip + Offset(if (redo) -3f else 3f, 0f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, tip, tip + Offset(0f, if (redo) 3f else 3f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCloseIcon(color: Color) {
    drawLine(color, Offset(4f, 4f), Offset(14f, 14f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawLine(color, Offset(14f, 4f), Offset(4f, 14f), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCheckIcon(color: Color) {
    drawLine(color, Offset(3f, 9f), Offset(7f, 13f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(color, Offset(7f, 13f), Offset(15f, 4f), strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000

/** 框选状态机要的光标，换成 AWT 的那一个。 */
private fun awtCursor(shape: CursorShape): Int =
    when (shape) {
        CursorShape.Crosshair -> Cursor.CROSSHAIR_CURSOR
        CursorShape.Move -> Cursor.MOVE_CURSOR
        CursorShape.Arrow -> Cursor.DEFAULT_CURSOR
        is CursorShape.Resize ->
            when (shape.handle) {
                Handle.Top -> Cursor.N_RESIZE_CURSOR
                Handle.Bottom -> Cursor.S_RESIZE_CURSOR
                Handle.Left -> Cursor.W_RESIZE_CURSOR
                Handle.Right -> Cursor.E_RESIZE_CURSOR
                Handle.TopLeft -> Cursor.NW_RESIZE_CURSOR
                Handle.TopRight -> Cursor.NE_RESIZE_CURSOR
                Handle.BottomLeft -> Cursor.SW_RESIZE_CURSOR
                Handle.BottomRight -> Cursor.SE_RESIZE_CURSOR
            }
    }
