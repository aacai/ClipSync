package zhiqiu.app.cs.ui

import java.io.File

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.record_saving
import zhiqiu.app.cs.resources.record_stop

internal actual fun recordingFile(id: String): String {
    val dir = File(System.getProperty("java.io.tmpdir"), "clipsync-recordings").apply { mkdirs() }
    return File(dir, "recording-$id.mp4").absolutePath
}

internal actual fun readAllBytes(path: String): ByteArray? =
        File(path).takeIf { it.isFile }?.readBytes()

internal actual fun deleteRecording(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

internal actual fun recordingInputs(): List<RecordInput> = ScreenRecord.inputs()

internal actual val recordingAvailable: Boolean
    get() = ScreenRecord.isAvailable

internal actual fun recordingActive(): Boolean = ScreenRecord.active()

internal actual fun recordingStart(
        region: RecordRegion?,
        mic: Boolean,
        systemAudio: Boolean,
        fps: Int,
        longEdge: Int,
        micDevice: String,
        path: String,
): String? = ScreenRecord.start(region, mic, systemAudio, fps, longEdge, micDevice, path)

internal actual fun recordingStop(): String? = ScreenRecord.stop()

/** 录制条上显示的时长：分钟不封顶，120 分钟就是 120:00。 */
internal fun recordClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

@Composable
internal actual fun RecordStopHost(
        recording: Boolean,
        saving: Boolean,
        elapsedSec: Int,
        limitSec: Int,
        onStop: () -> Unit,
) {
    if (!recording && !saving) return
    val bounds = remember { displayBounds() }
    val barWidth = 420.dp
    val barHeight = 56.dp
    // 和录屏工具条同一个位置：顶部居中、菜单栏下面。
    val state =
            rememberWindowState(
                    position =
                            WindowPosition.Absolute(
                                    (bounds.x + (bounds.width - 420) / 2).dp,
                                    bounds.y.dp + MacChromeInset.top + 8.dp,
                            ),
                    size = DpSize(barWidth, barHeight),
            )
    Window(
            onCloseRequest = onStop,
            state = state,
            title = "ClipSync 录制",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
    ) {
        LaunchedEffect(Unit) {
            window.toFront()
            // 这条停止栏是开录之后才建出来的窗口，开录那一份排除名单里没有它；
            // 等它真的挂在屏幕上了再重刷一次过滤器，两次之间留出露脸的时间。
            withContext(Dispatchers.IO) { ScreenRecord.excludeSelf() }
            delay(600)
            withContext(Dispatchers.IO) { ScreenRecord.excludeSelf() }
        }
        Row(
                modifier =
                        Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xF01A1A1A))
                                .border(1.dp, Color(0xAAFF453A), RoundedCornerShape(28.dp))
                                .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(RecordRed, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                    text =
                            if (saving) stringResource(Res.string.record_saving)
                            else "${recordClock(elapsedSec)} / ${recordClock(limitSec)}",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            if (!saving) {
                Button(
                        onClick = onStop,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = RecordRed,
                                        contentColor = Color.White,
                                ),
                ) {
                    Text(stringResource(Res.string.record_stop), fontSize = 13.sp)
                }
            }
        }
    }
}

/** 和截屏的蓝色区分开：录制中的一切都是这枚红。 */
internal val RecordRed = Color(0xFFFF453AL)
