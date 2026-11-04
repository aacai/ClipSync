package zhiqiu.app.cs.ui

import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import org.jcodec.api.awt.AWTSequenceEncoder

/**
 * 没有 ScreenCaptureKit 的桌面平台上的录屏：AWT 的 Robot 按时抓帧，JCodec 把帧编成 H.264 写进 MP4。
 *
 * 纯 Java 就能跑，Windows / Linux 不用额外装编码器；代价是没有声音，麦克风和系统声只在 macOS 上混。
 */
internal object RobotScreenRecord {

    private const val MAX_STOP_WAIT_MS = 30_000L
    private const val ENCODE_PIXELS_PER_SECOND = 16_000_000L

    private val state = Any()
    private var running = false
    private var stopRequested = false
    private var thread: Thread? = null
    private var failure: String? = null

    fun active(): Boolean = synchronized(state) { running }

    fun start(region: RecordRegion?, fps: Int, longEdge: Int, path: String): String? {
        synchronized(state) {
            if (running) return "已经在录了"
            running = true
            stopRequested = false
            failure = null
        }
        val worker =
                Thread(
                        {
                            try {
                                capture(region, fps.coerceIn(5, 60), longEdge, File(path))
                            } catch (error: Throwable) {
                                synchronized(state) { failure = error.message.orEmpty().ifBlank { error.toString() } }
                            } finally {
                                synchronized(state) { running = false }
                            }
                        },
                        "clipsync-robot-record",
                )
        worker.isDaemon = true
        synchronized(state) { thread = worker }
        worker.start()
        return null
    }

    fun stop(): String? {
        val worker =
                synchronized(state) {
                    stopRequested = true
                    thread.also { thread = null }
                } ?: return null
        worker.join(MAX_STOP_WAIT_MS)
        if (worker.isAlive) return "录屏收尾超时"
        return synchronized(state) { failure }
    }

    private fun capture(region: RecordRegion?, fps: Int, longEdge: Int, file: File) {
        val screen = displayBounds()
        val area = sourceArea(screen, region)
        val robot = Robot()
        // 先抓一帧量出实际像素：HiDPI 下抓回来的尺寸比点数大，下面按它算缩放。
        val first = robot.createScreenCapture(area)
        val target = targetSize(first.width, first.height, longEdge)
        val canvas = BufferedImage(target.first, target.second, BufferedImage.TYPE_3BYTE_BGR)
        file.parentFile?.mkdirs()
        // JCodec 是纯 Java 编码，1080p 实测只有 16 MPix/s 左右。按这个吞吐把帧率压到追得上的值：
        // 时间戳仍按真实帧率走，硬撑 30 fps 只会让成片看起来像快进。
        val effectiveFps =
                minOf(fps, maxOf(3, (ENCODE_PIXELS_PER_SECOND / (target.first.toLong() * target.second)).toInt()))
        val encoder = AWTSequenceEncoder.createSequenceEncoder(file, effectiveFps)
        try {
            val frameNanos = 1_000_000_000L / effectiveFps
            var deadline = System.nanoTime()
            encode(robot, area, canvas, encoder)
            while (!synchronized(state) { stopRequested }) {
                deadline += frameNanos
                val wait = deadline - System.nanoTime()
                if (wait > 0) {
                    Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    encode(robot, area, canvas, encoder)
                } else {
                    // 编码跟不上就当场再抓一帧，别攒下一串过期的等待时间。
                    deadline = System.nanoTime()
                    encode(robot, area, canvas, encoder)
                }
            }
        } finally {
            runCatching { encoder.finish() }
        }
    }

    private fun encode(
            robot: Robot,
            area: Rectangle,
            canvas: BufferedImage,
            encoder: AWTSequenceEncoder,
    ) {
        val raw = robot.createScreenCapture(area)
        val graphics = canvas.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.drawImage(raw, 0, 0, canvas.width, canvas.height, null)
        } finally {
            graphics.dispose()
        }
        encoder.encodeImage(canvas)
    }

    private fun sourceArea(screen: Rectangle, region: RecordRegion?): Rectangle {
        val area =
                if (region == null) {
                    screen
                } else {
                    Rectangle(
                        (screen.x + region.x).toInt(),
                        (screen.y + region.y).toInt(),
                        region.width.toInt(),
                        region.height.toInt(),
                    )
                }
        val clipped = area.intersection(screen)
        if (clipped.width < 2 || clipped.height < 2) throw IllegalArgumentException("框选区域太小")
        return clipped
    }

    private fun targetSize(width: Int, height: Int, longEdge: Int): Pair<Int, Int> {
        val scaled =
                if (longEdge <= 0 || maxOf(width, height) <= longEdge) {
                    width to height
                } else if (width >= height) {
                    longEdge to (height.toDouble() * longEdge / width).toInt()
                } else {
                    (width.toDouble() * longEdge / height).toInt() to longEdge
                }
        // JCodec 的 H.264 编码器按 16×16 宏块整块处理，宽高不是 16 的倍数会直接抛异常。
        return align16(scaled.first) to align16(scaled.second)
    }

    private fun align16(value: Int): Int = maxOf(16, value / 16 * 16)
}
