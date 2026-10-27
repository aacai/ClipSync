package zhiqiu.app.cs.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javafx.animation.KeyFrame
import javafx.animation.PauseTransition
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javax.swing.SwingUtilities
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.cancel
import zhiqiu.app.cs.resources.record_preview_close
import zhiqiu.app.cs.resources.record_preview_delete
import zhiqiu.app.cs.resources.record_preview_delete_body
import zhiqiu.app.cs.resources.record_preview_delete_confirm
import zhiqiu.app.cs.resources.record_preview_delete_title
import zhiqiu.app.cs.resources.record_preview_download
import zhiqiu.app.cs.resources.record_preview_failed
import zhiqiu.app.cs.resources.record_preview_missing
import zhiqiu.app.cs.resources.record_preview_pause
import zhiqiu.app.cs.resources.record_preview_play
import zhiqiu.app.cs.resources.record_preview_share
import zhiqiu.app.cs.resources.record_preview_title
import javafx.util.Duration as FxDuration

private val PreviewBg = Color(0xFF101216)
private val PreviewPanel = Color(0xFF171A20)
private val PreviewLine = Color(0x1FFFFFFF)
private val PreviewMuted = Color(0xFF9AA1AD)
private val PreviewAccent = Color(0xFF4C8DFF)
private val PreviewDanger = Color(0xFFFF453A)

/**
 * 录完自动弹出的预览窗口。
 *
 * 播放器是 JavaFX 的 [MediaView]：Compose Desktop 没有视频控件，而 JavaFX 自带 H.264/MP4
 * 解码，不用让用户另外装 VLC。画面走 [SwingPanel] 嵌进 Compose，控件仍然全部是 Compose。
 */
@Composable
internal actual fun RecordPreviewHost(
        preview: RecordPreview?,
        onClose: () -> Unit,
        onShare: () -> Unit,
        onDownload: () -> Unit,
        onDelete: () -> Unit,
) {
    val current = preview ?: return
    val title = stringResource(Res.string.record_preview_title)
    val state =
            rememberWindowState(
                    position = WindowPosition(Alignment.Center),
                    size = DpSize(920.dp, 640.dp),
            )
    Window(
            onCloseRequest = onClose,
            state = state,
            title = title,
            resizable = true,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(680, 480)
            window.toFront()
        }
        PreviewBody(current, onClose, onShare, onDownload, onDelete)
    }
}

@Composable
private fun PreviewBody(
        preview: RecordPreview,
        onClose: () -> Unit,
        onShare: () -> Unit,
        onDownload: () -> Unit,
        onDelete: () -> Unit,
) {
    val exists = remember(preview.path) { File(preview.path).isFile }
    val player = remember(preview.path) { if (exists) PreviewPlayer(preview.path) else null }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        player?.start()
        onDispose { player?.dispose() }
    }

    Column(Modifier.fillMaxSize().background(PreviewBg)) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                        text = preview.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                )
                Text(
                        text = if (exists) "MP4" else stringResource(Res.string.record_preview_missing),
                        color = PreviewMuted,
                        fontSize = 11.sp,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                        AppIcons.Close,
                        contentDescription = stringResource(Res.string.record_preview_close),
                        tint = PreviewMuted,
                        modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
                modifier =
                        Modifier.weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                contentAlignment = Alignment.Center,
        ) {
            when {
                !exists ->
                        Text(
                                stringResource(Res.string.record_preview_missing),
                                color = PreviewMuted,
                                fontSize = 13.sp,
                        )
                player == null ->
                        Text(
                                stringResource(Res.string.record_preview_missing),
                                color = PreviewMuted,
                                fontSize = 13.sp,
                        )
                player.failed ->
                        Text(
                                stringResource(Res.string.record_preview_failed),
                                color = PreviewMuted,
                                fontSize = 13.sp,
                        )
                else ->
                        SwingPanel(
                                factory = { player.panel },
                                modifier = Modifier.fillMaxSize(),
                        )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayPauseButton(
                    playing = player?.playing == true,
                    enabled = player != null && player.ready && !player.failed,
                    onClick = { player?.toggle() },
            )
            Spacer(Modifier.width(14.dp))
            Text(
                    text = timeLabel(player),
                    color = PreviewMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
            )
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) {
                val max = (player?.duration ?: 0.0).coerceAtLeast(0.1)
                val value =
                        (if (player?.scrubbing == true) player.scrub else player?.position ?: 0.0)
                                .coerceIn(0.0, max)
                Slider(
                        value = value.toFloat(),
                        onValueChange = { player?.scrubTo(it) },
                        onValueChangeFinished = { player?.endScrub() },
                        valueRange = 0f..max.toFloat(),
                        enabled = player != null && player.ready && !player.failed,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = PreviewAccent,
                                        inactiveTrackColor = Color(0x33FFFFFF),
                                        disabledThumbColor = PreviewMuted,
                                        disabledActiveTrackColor = Color(0x22FFFFFF),
                                        disabledInactiveTrackColor = Color(0x14FFFFFF),
                                ),
                )
            }
            Spacer(Modifier.width(12.dp))
            PreviewAction(
                    icon = AppIcons.Export,
                    label = stringResource(Res.string.record_preview_share),
                    tint = Color.White,
                    onClick = onShare,
            )
            PreviewAction(
                    icon = AppIcons.Download,
                    label = stringResource(Res.string.record_preview_download),
                    tint = Color.White,
                    onClick = onDownload,
            )
            PreviewAction(
                    icon = AppIcons.Delete,
                    label = stringResource(Res.string.record_preview_delete),
                    tint = PreviewDanger,
                    onClick = { confirmDelete = true },
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    if (confirmDelete) {
        GlassAlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = {
                    Text(
                            stringResource(Res.string.record_preview_delete_title),
                            fontSize = 17.sp,
                    )
                },
                text = {
                    Text(
                            stringResource(Res.string.record_preview_delete_body),
                            style = MaterialTheme.typography.bodySmall,
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                confirmDelete = false
                                onDelete()
                            },
                    ) {
                        Text(
                                stringResource(Res.string.record_preview_delete_confirm),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
        )
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.size(42.dp)
                            .clip(CircleShape)
                            .background(if (enabled) PreviewAccent else PreviewPanel)
                            .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(17.dp)) {
            if (playing) {
                val bar = size.width * 0.22f
                drawRect(Color.White, topLeft = Offset(size.width * 0.24f, 0f), size = androidx.compose.ui.geometry.Size(bar, size.height))
                drawRect(Color.White, topLeft = Offset(size.width * 0.54f, 0f), size = androidx.compose.ui.geometry.Size(bar, size.height))
            } else {
                val path =
                        Path().apply {
                            moveTo(size.width * 0.28f, 0f)
                            lineTo(size.width * 0.88f, size.height * 0.5f)
                            lineTo(size.width * 0.28f, size.height)
                            close()
                        }
                drawPath(path, Color.White)
            }
        }
    }
}

@Composable
private fun PreviewAction(
        icon: ImageVector,
        label: String,
        tint: Color,
        onClick: () -> Unit,
) {
    Row(
            modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClick)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun timeLabel(player: PreviewPlayer?): String {
    if (player == null) return "00:00 / 00:00"
    val at = if (player.scrubbing) player.scrub else player.position
    return "${clock(at)} / ${clock(player.duration)}"
}

private fun clock(seconds: Double): String {
    val total = seconds.takeIf { it.isFinite() && it > 0 }?.toInt() ?: 0
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%02d:%02d".format(minutes, secs)
}

/**
 * 一个录像的播放器状态。
 *
 * JavaFX 的所有操作都必须跑在它自己的 FX 线程上，Compose 状态只在 AWT 线程上改；
 * 两边用 [onEdt] 交接，别让 MediaPlayer 的属性跨线程读。
 */
private class PreviewPlayer(val path: String) {

    val panel =
            JFXPanel().apply {
                preferredSize = Dimension(720, 405)
                background = java.awt.Color.BLACK
                isOpaque = true
            }

    private val player = AtomicReference<MediaPlayer?>(null)
    private val ticker = AtomicReference<Timeline?>(null)

    var ready by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var duration by mutableStateOf(0.0)
        private set
    var position by mutableStateOf(0.0)
        private set
    var scrubbing by mutableStateOf(false)
        private set
    var scrub by mutableStateOf(0.0)
        private set

    fun start() {
        Platform.runLater {
            Platform.setImplicitExit(false)
            runCatching {
                val media = Media(File(path).toURI().toString())
                val mp = MediaPlayer(media)
                val view =
                        MediaView(mp).apply {
                            isPreserveRatio = true
                            isSmooth = true
                        }
                val root =
                        StackPane(view).apply {
                            style = "-fx-background-color: black;"
                        }
                view.fitWidthProperty().bind(root.widthProperty())
                view.fitHeightProperty().bind(root.heightProperty())
                panel.scene = Scene(root, javafx.scene.paint.Color.BLACK)
                player.set(mp)
                media.errorProperty().addListener { _, _, error -> if (error != null) markFailed() }
                mp.setOnError { markFailed() }
                mp.setOnReady {
                    val total = mp.totalDuration.toSeconds()
                    onEdt {
                        if (total.isFinite() && total > 0) duration = total
                        ready = true
                    }
                    // 先静音滚一小段再回到开头：暂停状态也能看到第一帧，而不是一块黑。
                    mp.volume = 0.0
                    mp.play()
                    val settle = PauseTransition(FxDuration.millis(160.0))
                    settle.setOnFinished {
                        runCatching {
                            mp.pause()
                            mp.seek(FxDuration.ZERO)
                            mp.volume = 1.0
                        }
                        onEdt {
                            position = 0.0
                            playing = false
                        }
                    }
                    settle.play()
                }
                mp.setOnPlaying { onEdt { playing = true } }
                mp.setOnPaused { onEdt { playing = false } }
                mp.setOnStopped { onEdt { playing = false } }
                mp.setOnEndOfMedia {
                    mp.seek(FxDuration.ZERO)
                    mp.pause()
                }
                val timer =
                        Timeline(
                                KeyFrame(
                                        FxDuration.millis(120.0),
                                        EventHandler<ActionEvent> {
                                            val at = mp.currentTime.toSeconds()
                                            val total = mp.totalDuration.toSeconds()
                                            onEdt {
                                                if (!scrubbing) position = at
                                                if (total.isFinite() && total > 0) duration = total
                                            }
                                        },
                                )
                        )
                timer.cycleCount = Timeline.INDEFINITE
                timer.play()
                ticker.set(timer)
            }
                    .onFailure { markFailed() }
        }
    }

    fun toggle() {
        Platform.runLater {
            val mp = player.get() ?: return@runLater
            when (mp.status) {
                MediaPlayer.Status.PLAYING -> mp.pause()
                MediaPlayer.Status.PAUSED,
                MediaPlayer.Status.READY,
                MediaPlayer.Status.STOPPED -> {
                    val total = mp.totalDuration.toSeconds()
                    if (total.isFinite() && total > 0 && mp.currentTime.toSeconds() >= total - 0.05) {
                        mp.seek(FxDuration.ZERO)
                    }
                    mp.play()
                }
                else -> Unit
            }
        }
    }

    fun scrubTo(value: Float) {
        scrubbing = true
        scrub = value.toDouble()
    }

    fun endScrub() {
        val target = scrub
        seekTo(target)
        scrubbing = false
    }

    private fun seekTo(seconds: Double) {
        Platform.runLater {
            val mp = player.get() ?: return@runLater
            val total = mp.totalDuration.toSeconds()
            val max = if (total.isFinite() && total > 0) total else seconds.coerceAtLeast(0.0)
            val at = seconds.coerceIn(0.0, max)
            mp.seek(FxDuration.seconds(at))
            onEdt { position = at }
        }
    }

    fun dispose() {
        runCatching {
            Platform.runLater {
                ticker.getAndSet(null)?.stop()
                player.getAndSet(null)?.let { mp ->
                    runCatching { mp.stop() }
                    runCatching { mp.dispose() }
                }
                runCatching { panel.scene = null }
            }
        }
    }

    private fun markFailed() {
        onEdt {
            failed = true
            ready = false
            playing = false
        }
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
