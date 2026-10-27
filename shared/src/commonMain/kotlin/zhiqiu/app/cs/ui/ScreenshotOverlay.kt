package zhiqiu.app.cs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt

/**
 * 截屏遮罩层的框选状态机。
 *
 * 这里只有几何，没有像素也没有窗口：一帧冻结画面怎么压暗、区域怎么拖、手柄怎么缩放、按钮条摆在
 * 哪，全在这一个文件里算完，平台侧（桌面窗口）只负责把事件喂进来、把结果画出去。公式与 Rust 端
 * `clipsync-desktop/src/overlay.rs` 一一对应，两端同一个手势必须给出同一个选区。
 */

/** 手柄从这里外就能抓住，不必正好点在点上。 */
const val HANDLE_SLOP: Float = 9f

/** 画出来（也用来命中）的手柄边长。 */
const val HANDLE_SIZE: Float = 8f

/** 窄过这个数就是点击，不是一块区域。 */
const val MIN_SIZE: Float = 6f

/** 按钮条离选区多远。 */
const val TOOLBAR_GAP: Float = 8f

/** 按钮条的高度，以及里面两个按钮的排布。 */
const val TOOLBAR_HEIGHT: Float = 45f
const val PILL_INSET: Float = 6f
const val PILL_BUTTON: Float = 33f
const val PILL_SPACING: Float = 4f
const val TOOLBAR_WIDTH: Float = PILL_INSET * 2f + PILL_BUTTON * 2f + PILL_SPACING

/** 连点两下算确认，和 Rust 端的双击一致。 */
const val DOUBLE_TAP_MS: Long = 400L
const val DOUBLE_TAP_SLOP: Float = 24f

/** 遮罩层的四个角与四条边。 */
enum class Handle {
    TopLeft,
    Top,
    TopRight,
    Right,
    BottomRight,
    Bottom,
    BottomLeft,
    Left,
}

val HANDLES: List<Handle> = Handle.entries

/** 录屏画质预设：工具条上选一次，同时决定长边和帧率。 */
enum class RecordQuality(val longEdge: Int, val fps: Int) {
    Native(0, 60),
    HD(1920, 30),
    Smooth(1280, 15);

    companion object {
        fun of(longEdge: Int): RecordQuality =
            when (longEdge) {
                1280 -> Smooth
                1920 -> HD
                else -> Native
            }
    }
}

/** 录屏工具条这一次选的东西；开始录制时原样交给 AppModel。 */
class RecordOptions(
    var quality: RecordQuality = RecordQuality.Native,
    var systemAudio: Boolean = true,
    var mic: Boolean = true,
    var micDevice: String = "",
)

/** 一次框选的结局：切这块区域、按这组选项开录，或者用户不要了。 */
sealed interface Outcome {
    data class Crop(val rect: Rect) : Outcome

    data class StartRecording(val rect: Rect, val options: RecordOptions) : Outcome

    data object Cancel : Outcome
}

/** 按钮条上的两个按钮。 */
enum class PillButton { Cancel, Confirm }

/** 截图工具栏上的工具。Select 只负责搬动/缩放选区。 */
enum class ShotTool { Select, Box, Arrow, Pen, Text, Mosaic }

/** 画在截图上的一个标注。坐标都是遮罩点坐标。 */
sealed interface Mark {
    data class Box(val rect: Rect) : Mark

    data class Arrow(val from: Offset, val to: Offset) : Mark

    data class Pen(val points: List<Offset>) : Mark

    data class Text(val at: Offset, val text: String) : Mark

    data class Mosaic(val rect: Rect) : Mark
}

/** 标注列表加上撤销栈；撤销过的还能重做，新画一笔就把重做栈清空。 */
class MarkLayer {
    private val items = mutableListOf<Mark>()
    private val undone = mutableListOf<Mark>()

    val marks: List<Mark>
        get() = items

    val canUndo: Boolean
        get() = items.isNotEmpty()

    val canRedo: Boolean
        get() = undone.isNotEmpty()

    fun add(mark: Mark) {
        items += mark
        undone.clear()
    }

    fun undo(): Boolean {
        val last = items.removeLastOrNull() ?: return false
        undone += last
        return true
    }

    fun redo(): Boolean {
        val mark = undone.removeLastOrNull() ?: return false
        items += mark
        return true
    }

    fun clear() {
        items.clear()
        undone.clear()
    }
}

/** 正在输入的一段文字；还没落到 [MarkLayer] 里。 */
class TextDraft(val at: Offset, var text: String = "")

/**
 * 指针下面该是什么形状。
 *
 * 只有「拖哪条边」需要分开说，所以八个方向直接带 [Handle]，平台侧自己映射到自家光标。
 */
sealed interface CursorShape {
    /** 还没定下来的时候：画新区域。 */
    data object Crosshair : CursorShape

    /** 按住内部整块搬。 */
    data object Move : CursorShape

    /** 按钮条上面：那是普通点击，不是又一块区域。 */
    data object Arrow : CursorShape

    /** 抓住了一条边或一个角。 */
    data class Resize(val handle: Handle) : CursorShape
}

/** 按住之后在做什么。 */
sealed interface Drag {
    /** 从零开始画一块新区域。 */
    data class Fresh(val anchor: Offset) : Drag

    /** 按住内部整块搬动。 */
    data class Move(val grab: Offset) : Drag

    /** 拖某一条边或一个角。 */
    data class Resize(val handle: Handle) : Drag

    /** 在选区内画一笔标注。 */
    data class Paint(val tool: ShotTool, val start: Offset) : Drag
}

/**
 * 冻结的那一帧屏幕。像素留在平台侧，这里只带框选要的几何。
 *
 * [windows] 是抓取那一刻屏上的窗口，遮罩本地坐标、从前到后，用来做「点一下就选中那个窗口」。
 */
class FrozenScreen(
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float,
    val windows: List<Rect> = emptyList(),
) {
    val size: Size
        get() = Size(widthPx / scale, heightPx / scale)
}

/** 一帧冻结画面加上用户正在调的那块区域。 */
class ScreenshotSession(
    val screen: FrozenScreen,
    /** 这次是框录屏区域（顶部工具条）还是截一张图（底部工具栏）。 */
    val record: Boolean = false,
    /** 录屏工具条这一次的选项；截屏模式下不用。 */
    val options: RecordOptions = RecordOptions(),
) {

    /** 遮罩自己的画面范围，遮罩本地坐标。 */
    val frame: Rect = Rect(Offset.Zero, screen.size)

    var selection: Rect? = null
        private set

    /** 现在点下去会拿到的那块区域，还没定下来时先淡淡地框出来。 */
    var hover: Rect? = null
        private set

    var drag: Drag? = null
        private set

    var outcome: Outcome? = null
        private set

    /** 截图标注：已经画下的、正在画的那一笔、以及正在输入的文字。 */
    val marks = MarkLayer()

    var tool: ShotTool = ShotTool.Select
        private set

    var draft: TextDraft? = null
        private set

    var pending: Mark? = null
        private set

    /**
     * 平台层量好的工具条范围（遮罩点坐标）。
     *
     * 工具条是平台控件，不归状态机画；把范围告诉它只是为了让「点工具条」不会被当成又开一块选区。
     */
    var bar: Rect? = null

    private var lastUp: Offset? = null
    private var lastUpAt: Long = 0L
    private var pillPress: Pair<PillButton, Rect>? = null

    /** 旧的两按钮条。新界面用完整工具条，这里只留给状态机单测和历史几何。 */
    val pill: Rect?
        get() = if (record) null else selection?.let { if (drag == null) pillAt(it, frame) else null }

    /** 该亮起来的那块：定下来的选区，没有就还是指针下面预览的窗口。 */
    val hole: Rect?
        get() = selection ?: hover

    /** 尺寸读数，单位是切出来那块的像素；只要框还在就一直显示。 */
    val readout: String?
        get() = hole?.let { sizeLabel(it, screen.scale) }

    /** 读数顶到屏幕下沿时翻到选区上面去。 */
    fun readoutFlips(rect: Rect): Boolean =
        rect.bottom + TOOLBAR_GAP + TOOLBAR_HEIGHT > frame.height

    /** 指针下面该换什么光标：正在拖就跟着拖的东西走，没拖就跟着「这一下按下去会拿到什么」走。 */
    fun cursor(at: Offset): CursorShape {
        if (bar?.contains(at) == true) return CursorShape.Arrow
        when (val current = drag) {
            is Drag.Move -> return CursorShape.Move
            is Drag.Resize -> return CursorShape.Resize(current.handle)
            is Drag.Fresh -> return CursorShape.Crosshair
            is Drag.Paint -> return CursorShape.Crosshair
            null -> Unit
        }
        if (pill?.contains(at) == true) return CursorShape.Arrow
        val selected = selection ?: return CursorShape.Crosshair
        val handle = handleAt(selected, at, HANDLE_SLOP)
        return when {
            handle != null -> CursorShape.Resize(handle)
            !record && tool != ShotTool.Select && selected.contains(at) -> CursorShape.Crosshair
            selected.contains(at) -> CursorShape.Move
            else -> CursorShape.Crosshair
        }
    }

    fun chooseTool(tool: ShotTool) {
        this.tool = tool
        if (tool != ShotTool.Text) draft = null
    }

    fun undo(): Boolean = marks.undo()

    fun redo(): Boolean = marks.redo()

    fun commitText() {
        val current = draft ?: return
        val text = current.text.trim()
        if (text.isNotEmpty()) marks.add(Mark.Text(current.at, text))
        draft = null
    }

    fun cancelText() {
        draft = null
    }

    fun onRightClick() {
        if (draft != null) {
            cancelText()
            return
        }
        outcome = Outcome.Cancel
    }

    fun onEscape() {
        if (draft != null) {
            cancelText()
            return
        }
        outcome = Outcome.Cancel
    }

    /** 回车：正在输入就先收下这段字；否则有选区就切/开录。 */
    fun onEnter() {
        if (outcome != null) return
        if (draft != null) {
            commitText()
            return
        }
        // 录屏不强迫先拖选区：没选就录整块屏幕，按钮从打开遮罩起就是可点的。
        val selected = selection ?: if (record) frame else return
        outcome =
            if (record) Outcome.StartRecording(selected, options)
            else Outcome.Crop(selected)
    }

    /** 指针移动（没按住）：预览点下去会拿到的窗口。 */
    fun onHover(at: Offset) {
        if (outcome != null || selection != null || drag != null) return
        hover = hitWindow(screen.windows, at, frame)
    }

    fun onDown(at: Offset, nowMs: Long) {
        if (outcome != null) return
        // 工具条上的按下由平台控件处理，别在这里又开一块选区。
        if (bar?.contains(at) == true) return
        if (draft != null) commitText()
        val selected = selection
        val previous = lastUp
        if (selected != null &&
            tool == ShotTool.Select &&
            previous != null &&
            nowMs - lastUpAt <= DOUBLE_TAP_MS &&
            (at - previous).getDistance() <= DOUBLE_TAP_SLOP
        ) {
            // 第二下也是「按下」，得先在这里收尾，否则下面 beginDrag 会把选区擦掉重画。
            outcome =
                if (record) Outcome.StartRecording(selected, options)
                else Outcome.Crop(selected)
            return
        }
        beginDrag(at, pill)
    }

    fun onDrag(at: Offset) {
        if (outcome != null) return
        val current = drag ?: return
        applyDrag(current, at, frame)
    }

    fun onUp(at: Offset, nowMs: Long) {
        if (outcome != null) return
        val pressed = pillPress
        pillPress = null
        if (pressed != null) {
            // 这一抬是给按钮的：按下和抬起都在同一个按钮上才算点它，别的一律什么都不做。
            if (pressed.second.contains(at)) {
                when (pressed.first) {
                    PillButton.Cancel -> outcome = Outcome.Cancel
                    PillButton.Confirm ->
                        selection?.let {
                            outcome =
                                if (record) Outcome.StartRecording(it, options)
                                else Outcome.Crop(it)
                        }
                }
            }
            return
        }
        lastUp = at
        lastUpAt = nowMs
        if (drag is Drag.Paint) {
            val mark = pending
            drag = null
            pending = null
            if (mark != null && isMarkWorthKeeping(mark)) marks.add(mark)
            return
        }
        finishDrag(at, frame)
    }

    private fun beginDrag(at: Offset, pill: Rect?) {
        val selected = selection
        if (selected != null) {
            handleAt(selected, at, HANDLE_SLOP)?.let {
                drag = Drag.Resize(it)
                return
            }
            if (!record && tool != ShotTool.Select && selected.contains(at)) {
                if (tool == ShotTool.Text) {
                    draft = TextDraft(at)
                    return
                }
                drag = Drag.Paint(tool, at)
                pending = markOf(tool, at, at)
                return
            }
            if (selected.contains(at)) {
                drag = Drag.Move(at - selected.topLeft)
                return
            }
            // 按钮条就在选区下沿外面，那一下是给按钮的，不是给一块新区域的：
            // 在这里顺手开一块新区域会把按钮依附的那块选区擦掉。
            if (pill != null && pill.contains(at)) {
                pillPress = pillButtons(pill).firstOrNull { it.second.contains(at) }
                return
            }
        }
        drag = Drag.Fresh(at)
        selection = null
        hover = null
        // 重新框一块就是一张新截图，旧标注不能跟着跑到新区域里。
        if (!record) marks.clear()
    }

    private fun applyDrag(drag: Drag, at: Offset, bounds: Rect) {
        when (drag) {
            is Drag.Fresh -> selection = clampRect(at, drag.anchor, bounds)
            is Drag.Move -> selection = selection?.let { translate(it, at - drag.grab, bounds) }
            is Drag.Resize -> selection = selection?.let { resize(it, drag.handle, at, bounds) }
            is Drag.Paint -> {
                pending =
                    when (drag.tool) {
                        ShotTool.Pen -> {
                            val points = (pending as? Mark.Pen)?.points.orEmpty() + at
                            Mark.Pen(points)
                        }
                        ShotTool.Box -> Mark.Box(between(drag.start, at))
                        ShotTool.Mosaic -> Mark.Mosaic(between(drag.start, at))
                        ShotTool.Arrow -> Mark.Arrow(drag.start, at)
                        else -> pending
                    }
            }
        }
    }

    private fun finishDrag(at: Offset, bounds: Rect) {
        val current = drag
        drag = null
        if (current !is Drag.Fresh) return
        // 拖出屏幕之外也要把区域留在屏幕里：工具条和手柄都按这块摆，出界就点不到了。
        val dragged = clampTo(between(at, current.anchor), bounds)
        selection = if (isRegion(dragged, bounds)) dragged else hitWindow(screen.windows, at, bounds)
        hover = null
    }

    private fun markOf(tool: ShotTool, from: Offset, to: Offset): Mark? =
        when (tool) {
            ShotTool.Box -> Mark.Box(between(from, to))
            ShotTool.Mosaic -> Mark.Mosaic(between(from, to))
            ShotTool.Arrow -> Mark.Arrow(from, to)
            ShotTool.Pen -> Mark.Pen(listOf(from, to))
            else -> null
        }

    private fun isMarkWorthKeeping(mark: Mark): Boolean =
        when (mark) {
            is Mark.Box -> mark.rect.width >= MIN_SIZE && mark.rect.height >= MIN_SIZE
            is Mark.Mosaic -> mark.rect.width >= MIN_SIZE && mark.rect.height >= MIN_SIZE
            is Mark.Arrow -> (mark.to - mark.from).getDistance() >= MIN_SIZE
            is Mark.Pen -> mark.points.size >= 2
            is Mark.Text -> mark.text.isNotBlank()
        }
}

/** 两个指针位置之间的矩形，不管往哪个方向拖。 */
fun between(a: Offset, b: Offset): Rect =
    Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

/** 用户自己画出来的区域，至少要大到值得留几个像素。 */
fun isRegion(rect: Rect, frame: Rect): Boolean {
    val clamped = clampTo(rect, frame)
    return clamped.width >= MIN_SIZE && clamped.height >= MIN_SIZE
}

/** 点下去命中的最前层窗口；桌面（没有窗口罩住这一点）就是整屏。 */
fun hitWindow(windows: List<Rect>, at: Offset, frame: Rect): Rect =
    windows.firstOrNull { it.contains(at) } ?: frame

/** 读数写的就是切出来那块的像素，和 Rust 端 `paint_size` 同一个算法。 */
fun sizeLabel(rect: Rect, scale: Float): String =
    "${(rect.width * scale).roundToInt().coerceAtLeast(1)} * ${(rect.height * scale).roundToInt().coerceAtLeast(1)}"

/** 按钮条的位置：贴着选区右下角下面，顶到屏幕下沿时翻到选区上面去。 */
fun pillAt(selection: Rect, frame: Rect): Rect {
    val x = (selection.right - TOOLBAR_WIDTH).coerceIn(0f, (frame.width - TOOLBAR_WIDTH).coerceAtLeast(0f))
    val y = if (selection.bottom + TOOLBAR_GAP + TOOLBAR_HEIGHT > frame.height) {
        (selection.top - TOOLBAR_GAP - TOOLBAR_HEIGHT).coerceAtLeast(0f)
    } else {
        selection.bottom + TOOLBAR_GAP
    }
    return Rect(x, y, x + TOOLBAR_WIDTH, y + TOOLBAR_HEIGHT)
}

/** 按钮条里的两个按钮：左边取消，右边完成。 */
fun pillButtons(pill: Rect): List<Pair<PillButton, Rect>> {
    val top = pill.top + (TOOLBAR_HEIGHT - PILL_BUTTON) / 2f
    val cancel = Rect(pill.left + PILL_INSET, top, pill.left + PILL_INSET + PILL_BUTTON, top + PILL_BUTTON)
    val confirm = Rect(cancel.right + PILL_SPACING, top, cancel.right + PILL_SPACING + PILL_BUTTON, top + PILL_BUTTON)
    return listOf(PillButton.Cancel to cancel, PillButton.Confirm to confirm)
}

fun handlePoint(rect: Rect, handle: Handle): Offset {
    val x = when (handle) {
        Handle.TopLeft, Handle.BottomLeft, Handle.Left -> rect.left
        Handle.TopRight, Handle.BottomRight, Handle.Right -> rect.right
        Handle.Top, Handle.Bottom -> (rect.left + rect.right) / 2f
    }
    val y = when (handle) {
        Handle.TopLeft, Handle.Top, Handle.TopRight -> rect.top
        Handle.BottomLeft, Handle.Bottom, Handle.BottomRight -> rect.bottom
        Handle.Left, Handle.Right -> (rect.top + rect.bottom) / 2f
    }
    return Offset(x, y)
}

fun squareAt(center: Offset, size: Float): Rect = Rect(center - Offset(size / 2f, size / 2f), Size(size, size))

/** 指针下面能抓的手柄；角先被检查，所以角优先于边。 */
fun handleAt(rect: Rect, at: Offset, slop: Float): Handle? =
    HANDLES.firstOrNull { squareAt(handlePoint(rect, it), slop * 2f).contains(at) }

/** 整块滑动，左上角落到 [target]，不许滑出屏幕。 */
fun translate(rect: Rect, target: Offset, bounds: Rect): Rect {
    val width = rect.width
    val height = rect.height
    val left = target.x.coerceIn(bounds.left, maxOf(bounds.right - width, bounds.left))
    val top = target.y.coerceIn(bounds.top, maxOf(bounds.bottom - height, bounds.top))
    return Rect(left, top, left + width, top + height)
}

/** 某个轴上跟指针走的是哪条边；另一条是锚。 */
private enum class Mover { Min, Max, Pinned }

/** 把一条边或一个角拖到指针那里，不许出屏幕。 */
fun resize(rect: Rect, handle: Handle, at: Offset, bounds: Rect): Rect {
    val point = Offset(at.x.coerceIn(bounds.left, bounds.right), at.y.coerceIn(bounds.top, bounds.bottom))
    val horizontal = when (handle) {
        Handle.TopLeft, Handle.Left, Handle.BottomLeft -> Mover.Min
        Handle.TopRight, Handle.Right, Handle.BottomRight -> Mover.Max
        Handle.Top, Handle.Bottom -> Mover.Pinned
    }
    val vertical = when (handle) {
        Handle.TopLeft, Handle.Top, Handle.TopRight -> Mover.Min
        Handle.BottomLeft, Handle.Bottom, Handle.BottomRight -> Mover.Max
        Handle.Left, Handle.Right -> Mover.Pinned
    }
    val (left, right) = axis(rect.left, rect.right, horizontal, point.x, bounds.left, bounds.right)
    val (top, bottom) = axis(rect.top, rect.bottom, vertical, point.y, bounds.top, bounds.bottom)
    return Rect(left, top, right, bottom)
}

/**
 * 缩放的一条轴：动的边追指针，锚不动，所以手柄拖过对面角时区域会塌成最小，而不是翻面。
 */
private fun axis(min: Float, max: Float, mover: Mover, at: Float, boundMin: Float, boundMax: Float): Pair<Float, Float> {
    var low = min
    var high = max
    when (mover) {
        Mover.Min -> low = at
        Mover.Max -> high = at
        Mover.Pinned -> Unit
    }
    if (high - low < MIN_SIZE) {
        when (mover) {
            Mover.Min -> low = maxOf(high - MIN_SIZE, boundMin)
            Mover.Max -> high = minOf(low + MIN_SIZE, boundMax)
            Mover.Pinned -> Unit
        }
    }
    // 锚已经贴死在对边，那就让它让一点。
    if (high - low < MIN_SIZE) {
        when (mover) {
            Mover.Min -> high = minOf(low + MIN_SIZE, boundMax)
            Mover.Max -> low = maxOf(high - MIN_SIZE, boundMin)
            Mover.Pinned -> Unit
        }
    }
    return low to high
}

/** 洞四周的四条压暗带，洞本身保持原亮度；没有洞就是整屏压暗。 */
fun maskRects(outer: Rect, hole: Rect?): List<Rect> {
    val open = hole?.let { clampTo(it, outer) }?.takeIf { it.width > 0f && it.height > 0f }
        ?: return listOf(outer)
    return listOf(
        Rect(outer.left, outer.top, open.left, outer.bottom),
        Rect(open.right, outer.top, outer.right, outer.bottom),
        Rect(open.left, outer.top, open.right, open.top),
        Rect(open.left, open.bottom, open.right, outer.bottom),
    )
}

/** 冻结画面里的一块像素范围：左上角加宽高。 */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** 选区背后的像素：在冻结画面自己的坐标系里，四舍五入成整数。 */
fun cropPx(rect: Rect, image: Size, scale: Float): PixelRect {
    val corner = Offset(image.width, image.height)
    val min = clampOffset(Offset(rect.left * scale, rect.top * scale), corner)
    val max = clampOffset(Offset(rect.right * scale, rect.bottom * scale), corner)
    return PixelRect(
        min.x.toInt(),
        min.y.toInt(),
        maxOf(max.x - min.x, 1f).toInt(),
        maxOf(max.y - min.y, 1f).toInt(),
    )
}

private fun clampOffset(at: Offset, corner: Offset): Offset =
    Offset(at.x.coerceIn(0f, corner.x), at.y.coerceIn(0f, corner.y))

/** 夹进 [bounds]，并且永远保证右 >= 左、下 >= 上。 */
private fun clampTo(rect: Rect, bounds: Rect): Rect {
    val left = rect.left.coerceIn(bounds.left, bounds.right)
    val top = rect.top.coerceIn(bounds.top, bounds.bottom)
    val right = rect.right.coerceIn(bounds.left, bounds.right)
    val bottom = rect.bottom.coerceIn(bounds.top, bounds.bottom)
    return Rect(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
}

private fun clampRect(a: Offset, b: Offset, bounds: Rect): Rect {
    val left = minOf(a.x, b.x).coerceIn(bounds.left, bounds.right)
    val top = minOf(a.y, b.y).coerceIn(bounds.top, bounds.bottom)
    val right = maxOf(a.x, b.x).coerceIn(bounds.left, bounds.right)
    val bottom = maxOf(a.y, b.y).coerceIn(bounds.top, bounds.bottom)
    return Rect(left, top, right, bottom)
}
