package zhiqiu.app.cs.ui

/** 拖入路径解析结果：普通文件进上传，文件夹名只用来提示。 */
internal data class DroppedPaths(
        val files: List<PickedSource>,
        val directories: List<String>,
)

/**
 * 把系统拖入的路径（本机绝对路径或 file URI）拆成可读文件与被跳过的目录。
 * 非桌面平台返回空：没有窗口拖放。
 */
internal expect fun resolveDroppedPaths(paths: List<String>): DroppedPaths
