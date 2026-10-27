package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 桌面：从条目拖出文件到访达/资源管理器。
 * [paths] 在拖动手势开始时求值，返回本机绝对路径；空列表则不启动拖出。
 * 其它平台 no-op。
 */
@Composable
internal expect fun Modifier.fileDragOutSource(paths: () -> List<String>): Modifier

/**
 * 把字节写成系统临时目录里的文件，供拖出；失败返回 null。
 * 非桌面平台恒为 null。
 */
internal expect fun writeDragOutTemp(fileName: String, bytes: ByteArray): String?
