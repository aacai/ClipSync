package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun RecordStopHost(
        recording: Boolean,
        saving: Boolean,
        elapsedSec: Int,
        limitSec: Int,
        onStop: () -> Unit,
) = Unit

internal actual fun deleteRecording(path: String): Boolean = false

internal actual fun recordingFile(id: String): String = ""

internal actual fun readAllBytes(path: String): ByteArray? = null

internal actual fun recordingInputs(): List<RecordInput> = emptyList()

internal actual val recordingAvailable: Boolean = false

internal actual fun recordingActive(): Boolean = false

internal actual fun recordingStart(
        region: RecordRegion?,
        mic: Boolean,
        systemAudio: Boolean,
        fps: Int,
        longEdge: Int,
        micDevice: String,
        path: String,
): String? = "这个平台不能录屏"

internal actual fun recordingStop(): String? = null
