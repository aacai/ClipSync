package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.fileDragOutSource(paths: () -> List<String>): Modifier = this

internal actual fun writeDragOutTemp(fileName: String, bytes: ByteArray): String? = null
