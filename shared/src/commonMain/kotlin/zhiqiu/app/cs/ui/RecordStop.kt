package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable

/** 录制中贴在屏幕右下角的计时药丸。非桌面平台不显示。 */
@Composable
internal expect fun RecordStopHost(
        recording: Boolean,
        saving: Boolean,
        elapsedSec: Int,
        limitSec: Int,
        onStop: () -> Unit,
)

/** 删除一段录像文件；预览窗口的「删除」用。非桌面平台没有本地录像。 */
internal expect fun deleteRecording(path: String): Boolean

/**
 * 录像文件是否已经收尾完成。
 *
 * 顶层 box 里必须有 moov，播放器才认这个文件；没有的就是被掐断的半成品。和 Rust 端
 * `clipsync-desktop/src/record.rs` 的 `is_finished` 是同一套判定。
 */
internal fun recordingFinished(bytes: ByteArray): Boolean {
    var offset = 0
    while (offset + 8 <= bytes.size) {
        if (bytes.decodeToString(offset + 4, offset + 8) == "moov") return true
        val declared =
                ((bytes[offset].toInt() and 0xFF) shl 24) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 3].toInt() and 0xFF)
        val step =
                when (declared) {
                    // 0 表示「一直到文件尾」，后面不可能再有 moov。
                    0 -> return false
                    // 64 位长度头：超过 Int 的一定比这个文件大。
                    1 -> {
                        if (offset + 16 > bytes.size) return false
                        var large = 0L
                        for (index in 8 until 16) large = (large shl 8) or (bytes[offset + index].toLong() and 0xFF)
                        if (large > Int.MAX_VALUE) return false
                        large.toInt()
                    }
                    else -> declared
                }
        if (step < 8 || step > bytes.size - offset) return false
        offset += step
    }
    return false
}

/** 新录像的本机绝对路径。非桌面返回空。 */
internal expect fun recordingFile(id: String): String

internal expect fun readAllBytes(path: String): ByteArray?

/** 一个可选的录音输入设备。[id] 是原生层认的设备标识，空串代表系统默认输入。 */
internal class RecordInput(val id: String, val name: String)

/** 本机可用的录音输入设备；没有录屏能力的平台返回空列表。 */
internal expect fun recordingInputs(): List<RecordInput>

internal expect val recordingAvailable: Boolean

internal expect fun recordingActive(): Boolean

internal expect fun recordingStart(
        region: RecordRegion?,
        mic: Boolean,
        systemAudio: Boolean,
        fps: Int,
        longEdge: Int,
        micDevice: String,
        path: String,
): String?

internal expect fun recordingStop(): String?
