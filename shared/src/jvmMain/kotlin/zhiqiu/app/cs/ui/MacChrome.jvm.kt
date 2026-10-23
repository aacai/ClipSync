package zhiqiu.app.cs.ui

import java.awt.Window
import javax.swing.RootPaneContainer

/**
 * 把 macOS 系统标题栏藏起来：内容铺到顶，灰条和窗口标题不再画出来。
 * 红绿灯还在，关窗 / 最小化照旧。
 */
object MacChrome {

    fun hideSystemTitleBar(window: Window) {
        if (!Mac.isMacOs) return
        val root = (window as? RootPaneContainer)?.rootPane ?: return
        root.putClientProperty("apple.awt.fullWindowContent", true)
        root.putClientProperty("apple.awt.transparentTitleBar", true)
        root.putClientProperty("apple.awt.windowTitleVisible", false)
    }
}
