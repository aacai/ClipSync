package zhiqiu.app.cs.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.Rectangle
import java.awt.Toolkit
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** AWT 的整屏抓取：所有桌面系统都有，macOS 上受屏幕录制权限管。 */
internal object RobotScreenCapture : PlatformScreenCapture {

    override val isAvailable: Boolean
        get() = !GraphicsEnvironment.isHeadless()

    /** 权限没给的时候 macOS 不报错，只把壁纸给你，所以先申请一次再决定要不要按下去。 */
    override suspend fun capture(): ByteArray =
        withContext(Dispatchers.IO) {
            if (Mac.isMacOs && !Mac.requestScreenCapture()) {
                throw ScreenCapturePermissionMissing()
            }
            val bounds = Rectangle(Toolkit.getDefaultToolkit().screenSize)
            val image = Robot().createScreenCapture(bounds)
            val out = ByteArrayOutputStream()
            // 没有 PNG 编码器时 write 只返回 false，空字节交给上层如实报错，这里不编故事。
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
}
