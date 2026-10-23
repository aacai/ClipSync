package zhiqiu.app.cs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext

/** 除了用户瞄准的那块，其余都压这么黑，和 Rust 端的 128/255 一致。 */
private val Mask = Color(0x80000000L)
private val Ink = Color(0xE6000000L)
private val HandleFill = Color(0xE6FFFFFFL)
private val Accent = Color(0xFF2B7FFF)
private val Confirm = Color(0xFF3FB950)

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
        // 遮罩层已经开着的时候再按快捷键就当没听见，别在一层底下又冻一帧、又叠一层。
        if (pending != null) return null
        val freeze = withContext(Dispatchers.IO) { ScreenFreeze.grab() }
        val request = OverlayRequest(freeze)
        pending = request
        overlayShown = true
        return try {
            val outcome = request.done.await()
            // 先收遮罩：自家窗口还藏着，不会在压暗画面后突然蹦出主窗口。
            overlayShown = false
            yield()
            when (outcome) {
                is Outcome.Crop ->
                        withContext(Dispatchers.IO) {
                            encode(freeze.image, freeze.screen, outcome.rect)
                        }
                Outcome.Cancel, null -> null
            }
        } finally {
            overlayShown = false
            pending = null
            freeze.restore()
        }
    }

    /** 遮罩层窗口本身：没有请求时它是藏着的，不是不存在。 */
    @Composable
    fun OverlayWindow() {
        val bounds = remember { displayBounds() }
        val state = rememberWindowState(
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

    /** 回车切这块，Esc 放弃；别的键留给系统。 */
    private fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val request = pending ?: return false
        when (event.key) {
            Key.Escape -> request.session.onEscape()
            Key.Enter -> request.session.onEnter()
            else -> return false
        }
        request.settle(request.session.outcome)
        return true
    }
}

/** 遮罩层和主窗口靠标题认亲，所以这个标题只有这两处用得上。 */
internal const val OVERLAY_TITLE: String = "ClipSync 截图"

/** 一次框选：冻好的画面、它的状态机、等结果的坑位，外加一笔重画用的计数。 */
private class OverlayRequest(val freeze: Freeze) {

    val session = ScreenshotSession(freeze.screen)
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

/** 选区切出来那块的 PNG。切不出像素就当用户取消。 */
private fun encode(image: BufferedImage, screen: FrozenScreen, rect: Rect): ByteArray? {
    val px = cropPx(rect, Size(screen.widthPx.toFloat(), screen.heightPx.toFloat()), screen.scale)
    if (px.x + px.width > image.width || px.y + px.height > image.height) return null
    val out = ByteArrayOutputStream()
    ImageIO.write(image.getSubimage(px.x, px.y, px.width, px.height), "png", out)
    return out.toByteArray().takeIf { it.isNotEmpty() }
}

@Composable
private fun OverlayFrame(request: OverlayRequest) {
    val session = request.session
    val bitmap = remember(request) { request.freeze.image.toComposeImageBitmap() }
    // 这一笔读在组合里：按钮条出现、读数跟着选区走，都靠它让这一层重画。
    request.revision.intValue
    var surface by remember(request) { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerHoverIcon(PointerIcon(Cursor(awtCursor(request.cursor.value))))
            .onSizeChanged { surface = it }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(request) {
                    // 遮罩是按 AWT 的点对齐的，画布像素可能更多（Retina）；换算回点，状态机才只有长度没有比例。
                    val unit = if (session.frame.width > 0f) size.width / session.frame.width else 1f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                val at = Offset(change.position.x / unit, change.position.y / unit)
                                if (event.type == PointerEventType.Press &&
                                    event.buttons.isSecondaryPressed
                                ) {
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
            drawFreeze(session, bitmap)
        }
        val label = session.readout
        val hole = session.hole
        if (label != null && hole != null && surface.width > 0 && session.frame.width > 0f) {
            SizeReadout(
                request = request,
                label = label,
                hole = hole,
                above = session.readoutFlips(hole),
                unit = surface.width / session.frame.width,
                surface = surface,
            )
        }
    }
}

/**
 * 尺寸读数：黑底白字，贴着选区右下角下面，顶到屏幕下沿时翻到选区上面去。
 *
 * 位置和翻转都在这里算，[ScreenshotSession] 只管那块区域本身。传进来的全是算好的值，
 * 不是那个只会往里塞选区的状态机——不然参数每次都比上次「没变」，这一层直接被跳过。
 */
@Composable
private fun SizeReadout(
    request: OverlayRequest,
    label: String,
    hole: Rect,
    above: Boolean,
    unit: Float,
    surface: IntSize,
) {
    var box by remember(request) { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .offset {
                val left = hole.right * unit - box.width
                val top = if (above) hole.top * unit - TOOLBAR_GAP - box.height
                else hole.bottom * unit + TOOLBAR_GAP
                IntOffset(
                    left.coerceIn(0f, maxOf(0f, (surface.width - box.width).toFloat())).roundToInt(),
                    maxOf(0f, top).roundToInt(),
                )
            }
            .onSizeChanged { box = it }
            .background(Ink, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

/** 冻住的整屏、压暗的四条带、瞄准的框、八个手柄，还有按钮条——全在点坐标系里画。 */
private fun DrawScope.drawFreeze(session: ScreenshotSession, bitmap: ImageBitmap) {
    drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    val frame = session.frame
    val unit = if (frame.width > 0f) size.width / frame.width else 1f
    scale(unit) {
        for (band in maskRects(frame, session.hole)) {
            if (band.width > 0f && band.height > 0f) {
                drawRect(Mask, band.topLeft, band.size)
            }
        }
        val hole = session.hole
        if (hole != null) {
            drawRect(Accent, hole.topLeft, hole.size, style = Stroke(1.5f))
            if (session.selection != null && session.drag == null) {
                for (handle in HANDLES) {
                    val square = squareAt(handlePoint(hole, handle), HANDLE_SIZE)
                    drawRoundRect(HandleFill, square.topLeft, square.size, CornerRadius(1f))
                    drawRect(Accent, square.topLeft, square.size, style = Stroke(1f))
                }
            }
        }
        session.pill?.let { drawPill(it) }
    }
}

/** 按钮条：黑底圆角，左边一个叉，右边一个勾。 */
private fun DrawScope.drawPill(pill: Rect) {
    drawRoundRect(Ink, pill.topLeft, pill.size, CornerRadius(10f))
    for ((button, rect) in pillButtons(pill)) {
        val center = rect.center
        val arm = rect.width / 4f
        if (button == PillButton.Cancel) {
            drawLine(Color.White, Offset(center.x - arm, center.y - arm), Offset(center.x + arm, center.y + arm), 2f)
            drawLine(Color.White, Offset(center.x + arm, center.y - arm), Offset(center.x - arm, center.y + arm), 2f)
        } else {
            drawLine(Confirm, Offset(center.x - arm, center.y), Offset(center.x - arm / 3f, center.y + arm * 0.8f), 2f)
            drawLine(Confirm, Offset(center.x - arm / 3f, center.y + arm * 0.8f), Offset(center.x + arm, center.y - arm), 2f)
        }
    }
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000

/** 框选状态机要的光标，换成 AWT 的那一个。 */
private fun awtCursor(shape: CursorShape): Int =
    when (shape) {
        CursorShape.Crosshair -> Cursor.CROSSHAIR_CURSOR
        CursorShape.Move -> Cursor.MOVE_CURSOR
        CursorShape.Arrow -> Cursor.DEFAULT_CURSOR
        is CursorShape.Resize -> when (shape.handle) {
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
