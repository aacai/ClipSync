package zhiqiu.app.cs.ui

import java.io.File
import java.net.URI
import zhiqiu.app.cs.files.mimeForName

internal actual fun resolveDroppedPaths(paths: List<String>): DroppedPaths {
    val files = ArrayList<PickedSource>()
    val directories = ArrayList<String>()
    for (raw in paths) {
        val file = pathToFile(raw) ?: continue
        when {
            file.isDirectory -> directories.add(file.name.ifBlank { file.path })
            file.isFile ->
                    files.add(
                            PickedSource(
                                    name = file.name,
                                    mime = mimeForName(file.name),
                                    sizeHint = file.length(),
                                    open = { file.readBytes() },
                            ),
                    )
        }
    }
    return DroppedPaths(files = files, directories = directories)
}

private fun pathToFile(raw: String): File? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
                when {
                    trimmed.startsWith("file:") -> File(URI(trimmed))
                    else -> File(trimmed)
                }
            }
            .getOrNull()
}
