package zhiqiu.app.cs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 框选状态机跑的是真实代码，公式对着 Rust 端 `overlay.rs` 的同名用例逐条核。
 *
 * 两端同一个手势必须给出同一个选区，所以这里的期望值和那边测试里的期望值是一样的数。
 */
class ScreenshotOverlayTest {

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
        Rect(left, top, right, bottom)

    private fun session(windows: List<Rect> = emptyList()) =
        ScreenshotSession(FrozenScreen(widthPx = 1000, heightPx = 600, scale = 1f, windows = windows))

    private val screen = rect(0f, 0f, 1000f, 600f)

    @Test
    fun `a drag in any direction becomes a positive region`() {
        assertEquals(rect(100f, 100f, 300f, 200f), between(Offset(100f, 100f), Offset(300f, 200f)))
        assertEquals(rect(100f, 100f, 300f, 200f), between(Offset(300f, 200f), Offset(100f, 100f)))
    }

    @Test
    fun `a click selects the front window and the desktop falls back to the screen`() {
        val windows = listOf(rect(400f, 200f, 900f, 500f), rect(0f, 0f, 500f, 300f))
        // 从前到后：排在前面的窗口赢。
        assertEquals(rect(400f, 200f, 900f, 500f), hitWindow(windows, Offset(450f, 250f), screen))
        assertEquals(rect(0f, 0f, 500f, 300f), hitWindow(windows, Offset(60f, 60f), screen))
        assertEquals(screen, hitWindow(windows, Offset(950f, 550f), screen))
    }

    @Test
    fun `a jitter click is not a region`() {
        assertTrue(!isRegion(rect(100f, 100f, 103f, 300f), screen))
        assertTrue(isRegion(rect(100f, 100f, 200f, 300f), screen))
    }

    @Test
    fun `only the eight handles are grabbable and corners come first`() {
        val selection = rect(100f, 100f, 300f, 200f)
        assertEquals(Handle.TopLeft, handleAt(selection, Offset(100f, 100f), 9f))
        assertEquals(Handle.Top, handleAt(selection, Offset(200f, 100f), 9f))
        assertEquals(Handle.BottomRight, handleAt(selection, Offset(300f, 200f), 9f))
        assertEquals(null, handleAt(selection, Offset(150f, 150f), 9f))
        assertEquals(Offset(300f, 150f), handlePoint(selection, Handle.Right))
    }

    @Test
    fun `dragging a corner moves only that corner`() {
        val selection = rect(100f, 100f, 300f, 200f)
        assertEquals(rect(100f, 100f, 400f, 260f), resize(selection, Handle.BottomRight, Offset(400f, 260f), screen))
        assertEquals(rect(100f, 60f, 300f, 200f), resize(selection, Handle.Top, Offset(200f, 60f), screen))
    }

    @Test
    fun `a handle stays on the screen and never inverts`() {
        val selection = rect(100f, 100f, 300f, 200f)
        assertEquals(rect(100f, 100f, 1000f, 600f), resize(selection, Handle.BottomRight, Offset(5000f, 5000f), screen))
        // 拖过对面的角：锚不动，区域塌成最小而不是翻面。
        assertEquals(
            rect(100f, 100f, 100f + MIN_SIZE, 100f + MIN_SIZE),
            resize(selection, Handle.BottomRight, Offset(20f, 20f), screen),
        )
        assertEquals(
            rect(300f - MIN_SIZE, 200f - MIN_SIZE, 300f, 200f),
            resize(selection, Handle.TopLeft, Offset(500f, 500f), screen),
        )
        val flipped = resize(selection, Handle.Left, Offset(400f, 150f), screen)
        assertEquals(rect(294f, 100f, 300f, 200f), flipped)
        assertTrue(flipped.width >= MIN_SIZE && flipped.height == 100f)
    }

    @Test
    fun `moving the interior slides the region and stops at the edges`() {
        val selection = rect(100f, 100f, 300f, 200f)
        assertEquals(rect(400f, 300f, 600f, 400f), translate(selection, Offset(400f, 300f), screen))
        assertEquals(rect(0f, 0f, 200f, 100f), translate(selection, Offset(-500f, -500f), screen))
        assertEquals(rect(800f, 500f, 1000f, 600f), translate(selection, Offset(2000f, 2000f), screen))
    }

    @Test
    fun `the mask dims everything but the hole exactly once`() {
        val hole = rect(100f, 100f, 300f, 200f)
        val bands = maskRects(screen, hole).filter { it.width > 0f && it.height > 0f }
        assertEquals(4, bands.size)
        val area = bands.sumOf { (it.width * it.height).toDouble() }
        assertEquals((screen.width * screen.height - hole.width * hole.height).toDouble(), area, 0.5)
        for (band in bands) {
            // 每一条压暗带都不许啃进洞里。
            val overlap = between(Offset(band.left, band.top), Offset(band.right, band.bottom))
            val left = maxOf(overlap.left, hole.left)
            val right = minOf(overlap.right, hole.right)
            val top = maxOf(overlap.top, hole.top)
            val bottom = minOf(overlap.bottom, hole.bottom)
            assertTrue(right - left <= 0f || bottom - top <= 0f, "$band eats into the hole")
        }
        // 没有洞就是整屏压暗，而且只画一层。
        val all = maskRects(screen, null)
        assertEquals(listOf(screen), all)
    }

    @Test
    fun `a selection becomes whole pixels at the frame scale`() {
        // 2× 画面：点翻倍成像素。
        assertEquals(
            PixelRect(201, 100, 400, 400),
            cropPx(rect(100.5f, 50f, 300.5f, 250f), Size(2000f, 1200f), 2f),
        )
        assertEquals(PixelRect(0, 0, 1000, 600), cropPx(screen, Size(1000f, 600f), 1f))
        // 出界的选择是裁掉而不是绕回去。
        assertEquals(
            PixelRect(990, 590, 10, 10),
            cropPx(rect(990f, 590f, 1400f, 900f), Size(1000f, 600f), 1f),
        )
        // 一个点宽的缝隙也至少留一个像素，切出来的图不会是空的。
        assertEquals(
            PixelRect(10, 10, 1, 1),
            cropPx(rect(10f, 10f, 10.4f, 10.4f), Size(1000f, 600f), 1f),
        )
    }

    @Test
    fun `handle squares are centred on the edge`() {
        val selection = rect(100f, 100f, 300f, 200f)
        assertEquals(rect(96f, 146f, 104f, 154f), squareAt(handlePoint(selection, Handle.Left), 8f))
    }

    @Test
    fun `the pill sits under the region and flips above it at the bottom edge`() {
        val pill = pillAt(rect(100f, 100f, 300f, 200f), screen)
        assertEquals(300f, pill.right)
        assertEquals(200f + TOOLBAR_GAP, pill.top)

        val flipped = pillAt(rect(100f, 100f, 300f, 590f), screen)
        assertEquals(100f - TOOLBAR_GAP, flipped.bottom)

        assertEquals(screen.right, pillAt(rect(900f, 100f, 1000f, 200f), screen).right)
    }

    @Test
    fun `drawing a region then adjusting it keeps working through the real events`() {
        val shot = session()
        shot.onDown(Offset(700f, 400f), 0)
        shot.onDrag(Offset(1100f, 700f))
        shot.onUp(Offset(1100f, 700f), 20)
        assertEquals(rect(700f, 400f, 1000f, 600f), shot.selection)
        assertEquals(null, shot.outcome)

        // 按住内部整块搬。
        shot.onDown(Offset(800f, 500f), 100)
        assertIs<Drag.Move>(shot.drag)
        shot.onDrag(Offset(600f, 300f))
        assertEquals(rect(500f, 200f, 800f, 400f), shot.selection)
        shot.onUp(Offset(600f, 300f), 160)

        // 拖右下角。
        shot.onDown(Offset(800f, 400f), 200)
        assertIs<Drag.Resize>(shot.drag)
        shot.onDrag(Offset(900f, 500f))
        assertEquals(rect(500f, 200f, 900f, 500f), shot.selection)
        shot.onUp(Offset(900f, 500f), 260)

        shot.onEnter()
        assertIs<Outcome.Crop>(shot.outcome)
        assertEquals(rect(500f, 200f, 900f, 500f), (shot.outcome as Outcome.Crop).rect)
    }

    @Test
    fun `a click on the pill leaves the region alone`() {
        val shot = session()
        shot.onDown(Offset(100f, 100f), 0)
        shot.onDrag(Offset(300f, 200f))
        shot.onUp(Offset(300f, 200f), 20)
        val selection = shot.selection
        val pill = shot.pill!!

        // 取消和完成都在按钮条上，那一下不能拿去开一块新区域。
        shot.onDown(pill.center, 60)
        shot.onUp(pill.center, 90)
        assertEquals(null, shot.drag)
        assertEquals(selection, shot.selection)

        // 手柄仍然优先于按钮条，贴边的那条边照样能拉。
        shot.onDown(Offset(200f, 204f), 120)
        assertIs<Drag.Resize>(shot.drag)
        assertEquals(Handle.Bottom, (shot.drag as Drag.Resize).handle)
        shot.onUp(Offset(200f, 204f), 150)

        // 别的地方才真的重开一块。
        shot.onDown(Offset(600f, 400f), 200)
        assertIs<Drag.Fresh>(shot.drag)
        assertEquals(null, shot.selection)
    }

    @Test
    fun `two quick clicks on the region commit it`() {
        val shot = session(listOf(rect(100f, 100f, 400f, 400f)))
        shot.onDown(Offset(200f, 200f), 0)
        shot.onUp(Offset(200f, 200f), 20)
        assertEquals(rect(100f, 100f, 400f, 400f), shot.selection)

        shot.onDown(Offset(205f, 205f), 100)
        assertIs<Outcome.Crop>(shot.outcome)
        // 收尾之后第二下的抬起不能再改动选区。
        shot.onUp(Offset(205f, 205f), 120)
        assertEquals(rect(100f, 100f, 400f, 400f), (shot.outcome as Outcome.Crop).rect)
    }

    @Test
    fun `a slow second click is a new region, not a commit`() {
        val shot = session()
        shot.onDown(Offset(100f, 100f), 0)
        shot.onDrag(Offset(300f, 200f))
        shot.onUp(Offset(300f, 200f), 20)

        shot.onDown(Offset(600f, 400f), 2_000)
        assertEquals(null, shot.outcome)
        assertIs<Drag.Fresh>(shot.drag)
    }

    @Test
    fun `the pill buttons cancel and confirm`() {
        val shot = session()
        shot.onDown(Offset(100f, 100f), 0)
        shot.onDrag(Offset(300f, 200f))
        shot.onUp(Offset(300f, 200f), 20)
        val buttons = pillButtons(shot.pill!!).toMap()
        assertEquals(shot.pill!!.left + PILL_INSET, buttons.getValue(PillButton.Cancel).left)
        assertEquals(shot.pill!!.right - PILL_INSET, buttons.getValue(PillButton.Confirm).right)

        shot.onDown(buttons.getValue(PillButton.Confirm).center, 60)
        assertEquals(null, shot.outcome) // 抬起之前还不算点了它
        shot.onUp(buttons.getValue(PillButton.Confirm).center, 90)
        assertIs<Outcome.Crop>(shot.outcome)
        assertEquals(rect(100f, 100f, 300f, 200f), (shot.outcome as Outcome.Crop).rect)

        val cancel = session()
        cancel.onDown(Offset(100f, 100f), 0)
        cancel.onDrag(Offset(300f, 200f))
        cancel.onUp(Offset(300f, 200f), 20)
        val cross = pillButtons(cancel.pill!!).toMap().getValue(PillButton.Cancel)
        cancel.onDown(cross.center, 60)
        cancel.onUp(cross.center, 90)
        assertIs<Outcome.Cancel>(cancel.outcome)
    }

    @Test
    fun `escape and right click both cancel`() {
        val escape = session()
        escape.onEscape()
        assertIs<Outcome.Cancel>(escape.outcome)

        val right = session()
        right.onRightClick()
        assertIs<Outcome.Cancel>(right.outcome)
    }

    @Test
    fun `a click without a drag takes the window under the pointer`() {
        val shot = session(listOf(rect(400f, 200f, 900f, 500f)))
        shot.onDown(Offset(500f, 300f), 0)
        shot.onUp(Offset(500f, 302f), 20)
        assertEquals(rect(400f, 200f, 900f, 500f), shot.selection)
    }

    @Test
    fun `hovering previews a window only while nothing is settled`() {
        val shot = session(listOf(rect(400f, 200f, 900f, 500f)))
        shot.onHover(Offset(500f, 300f))
        assertEquals(rect(400f, 200f, 900f, 500f), shot.hover)

        shot.onDown(Offset(100f, 100f), 0)
        shot.onDrag(Offset(300f, 200f))
        shot.onUp(Offset(300f, 200f), 20)
        // 定下来的选区比预览重要，这时候再晃鼠标不该把框换掉。
        assertEquals(null, shot.hover)
        shot.onHover(Offset(500f, 300f))
        assertEquals(null, shot.hover)
        assertEquals(rect(100f, 100f, 300f, 200f), shot.selection)
    }

    @Test
    fun `the overlay works in points, not pixels`() {
        // 40×20 像素、2× 比例：遮罩接的是点，不是像素。
        val shot = ScreenshotSession(FrozenScreen(40, 20, 2f))
        assertEquals(20f, shot.frame.width)
        assertEquals(rect(0f, 0f, 20f, 10f), shot.frame)
    }

    @Test
    fun `the readout follows the region only while it is still moving`() {
        val shot = session()
        shot.onDown(Offset(100f, 100f), 0)
        shot.onDrag(Offset(300f, 200f))
        assertEquals("200 × 100", shot.readout)
        assertEquals(rect(100f, 100f, 300f, 200f), shot.hole)
        shot.onUp(Offset(300f, 200f), 20)
        // 定下来之后按钮条要占同一个位置，两个都画就叠一起了。
        assertEquals(null, shot.readout)
        assertEquals(rect(100f, 100f, 300f, 200f), shot.hole)
    }

    @Test
    fun `a 2x frame reads in pixels but drags in points`() {
        val shot = ScreenshotSession(
            FrozenScreen(2_000, 1_200, 2f, listOf(rect(100f, 100f, 400f, 400f))),
        )
        shot.onHover(Offset(200f, 200f))
        assertEquals("600 × 600", shot.readout)
        assertTrue(shot.readoutFlips(rect(0f, 0f, 500f, 590f)))
        assertTrue(!shot.readoutFlips(rect(0f, 0f, 500f, 200f)))
    }

    @Test
    fun `the cursor follows what this click would grab`() {
        val shot = session()
        // 还没定下来：这一下是按下去重开一块。
        assertEquals(CursorShape.Crosshair, shot.cursor(Offset(50f, 50f)))

        shot.onDown(Offset(100f, 100f), 0)
        shot.onDrag(Offset(300f, 200f))
        assertEquals(CursorShape.Crosshair, shot.cursor(Offset(200f, 150f)))
        shot.onUp(Offset(300f, 200f), 20)

        // 定下来之后：里面整块搬，边上拉那条边，外面重开，按钮条上是普通点击。
        assertEquals(CursorShape.Move, shot.cursor(Offset(200f, 150f)))
        assertEquals(CursorShape.Resize(Handle.BottomRight), shot.cursor(Offset(300f, 200f)))
        assertEquals(CursorShape.Crosshair, shot.cursor(Offset(600f, 400f)))
        assertEquals(CursorShape.Arrow, shot.cursor(shot.pill!!.center))

        // 正在拖手柄：光标跟着拖的那条边，不看指针跑到哪。
        shot.onDown(Offset(300f, 200f), 600)
        assertIs<Drag.Resize>(shot.drag)
        shot.onDrag(Offset(320f, 220f))
        assertEquals(CursorShape.Resize(Handle.BottomRight), shot.cursor(Offset(320f, 220f)))
        shot.onUp(Offset(320f, 220f), 690)
    }
}
