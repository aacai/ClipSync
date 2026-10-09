package zhiqiu.app.cs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.displayTitle
import zhiqiu.app.cs.files.humanSize
import zhiqiu.app.cs.files.isImage
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.rememberPlatformUi

// 中性灰阶 + 高对比配色（参考 GitHub / Linear 的 Web 视觉规范），
// 所有正文/次要文字在对应底色上均满足 WCAG AA 对比度。
private val LightColors =
        lightColorScheme(
                primary = Color(0xFF0969DA),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFDDF4FF),
                onPrimaryContainer = Color(0xFF0A3060),
                onBackground = Color(0xFF1F2328),
                background = Color(0xFFF6F8FA),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF1F2328),
                surfaceVariant = Color(0xFFEEF1F4),
                onSurfaceVariant = Color(0xFF57606A),
                surfaceContainer = Color(0xFFF3F5F7),
                surfaceContainerHigh = Color(0xFFEAEEF2),
                outline = Color(0xFF6E7781),
                outlineVariant = Color(0xFFD8DEE4),
                error = Color(0xFFCF222E),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFFEBE9),
                onErrorContainer = Color(0xFF86181D),
        )

private val DarkColors =
        darkColorScheme(
                primary = Color(0xFF4493F8),
                onPrimary = Color(0xFF0D1117),
                primaryContainer = Color(0xFF143A66),
                onPrimaryContainer = Color(0xFFB6E3FF),
                onBackground = Color(0xFFE6EDF3),
                background = Color(0xFF0D1117),
                surface = Color(0xFF161B22),
                onSurface = Color(0xFFE6EDF3),
                surfaceVariant = Color(0xFF1C2128),
                onSurfaceVariant = Color(0xFF9DA7B3),
                surfaceContainer = Color(0xFF161B22),
                surfaceContainerHigh = Color(0xFF1C2128),
                outline = Color(0xFF6E7681),
                outlineVariant = Color(0xFF30363D),
                error = Color(0xFFF85149),
                onError = Color(0xFF0D1117),
                errorContainer = Color(0xFF490202),
                onErrorContainer = Color(0xFFFFB4AE),
        )

// Web 风：更小的圆角，弱化 M3 圆润感
private val WebShapes =
        Shapes(
                extraSmall = RoundedCornerShape(5.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(12.dp),
                extraLarge = RoundedCornerShape(16.dp),
        )

@Composable
fun App() {
    val platform = rememberPlatformUi()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val model = remember(platform) { AppModel(scope, platform) }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = WebShapes,
    ) {
        // 根节点必须铺满背景色：否则 wasmJs 下根元素透明，深色主题的浅色文字会落在白色 body 上
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val status by model.status.collectAsState()
            var offlineBrowsing by rememberSaveable { mutableStateOf(false) }
            val online = status is ClipSyncEngine.Status.Online

            if (online || offlineBrowsing) {
                MainView(
                        model = model,
                        onLeave = {
                            model.disconnect()
                            offlineBrowsing = false
                        },
                )
            } else {
                JoinView(model, onBrowseOffline = { offlineBrowsing = true })
            }
        }
    }
}

// ---------------------------------------------------------------- join

@Composable
private fun JoinView(model: AppModel, onBrowseOffline: () -> Unit) {
    val roomCode by model.roomCode.collectAsState()
    val password by model.password.collectAsState()
    val connecting by model.connecting.collectAsState()
    val error by model.error.collectAsState()
    val status by model.status.collectAsState()

    Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
    ) {
        // Web 风卡片：细边框 + 小圆角，限宽居中
        Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border =
                        androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                        ),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        ) {
            Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                        "ClipSync",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        "同一房间码的设备互相同步剪贴板与文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
                )

                OutlinedTextField(
                        value = roomCode,
                        onValueChange = model::updateRoomCode,
                        label = { Text("房间码") },
                        singleLine = true,
                        enabled = !connecting,
                        keyboardOptions =
                                KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        capitalization =
                                                androidx.compose.ui.text.input
                                                        .KeyboardCapitalization.Characters
                                ),
                        modifier = Modifier.fillMaxWidth(),
                )
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                            "房间码可在设备间口述/扫码传递",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = model::regenerateRoomCode, enabled = !connecting) {
                        Text("随机生成")
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                        value = password,
                        onValueChange = model::updatePassword,
                        label = { Text("房间密码（可选）") },
                        singleLine = true,
                        enabled = !connecting,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))
                Button(
                        onClick = model::connect,
                        enabled = !connecting && roomCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("连接中…")
                    } else {
                        Text("进入房间")
                    }
                }

                val failure = error ?: (status as? ClipSyncEngine.Status.Offline)?.reason
                if (failure != null) {
                    Text(
                            failure,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    )
                }

                TextButton(onClick = onBrowseOffline, modifier = Modifier.padding(top = 8.dp)) {
                    Text("离线查看历史")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- main

@Composable
private fun MainView(model: AppModel, onLeave: () -> Unit) {
    val status by model.status.collectAsState()
    val devices by model.devices.collectAsState()
    val items by model.items.collectAsState()
    val query by model.query.collectAsState()
    val error by model.error.collectAsState()
    val notice by model.notice.collectAsState()
    val transfer by model.transfer.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Header(model, status, devices.size, onLeave)
        Toolbar(model, query)

        val progress = transfer
        if (progress != null) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                        "${if (progress.direction == ClipSyncEngine.TransferProgress.Direction.Upload) "上传" else "下载"} " +
                                "${progress.name} · ${(progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                )
                LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

        val banner = error ?: notice
        if (banner != null) {
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                        banner,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                )
                TextButton(onClick = model::dismissError) { Text("知道了", fontSize = 11.sp) }
            }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                        if (query.isBlank()) "还没有剪贴板记录\n在任意设备复制内容后会出现在这里" else "没有匹配「$query」的记录",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ClipRow(model, item)
                    HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
        model: AppModel,
        status: ClipSyncEngine.Status,
        deviceCount: Int,
        onLeave: () -> Unit,
) {
    // Web 风顶栏：纯色 + 底部 1px 分隔线，不用 M3 阴影/色调层
    Surface(
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val online = status is ClipSyncEngine.Status.Online
                        val dark = androidx.compose.foundation.isSystemInDarkTheme()
                        val dotColor =
                                when {
                                    online -> if (dark) Color(0xFF3FB950) else Color(0xFF1A7F37)
                                    status is ClipSyncEngine.Status.Connecting ->
                                            if (dark) Color(0xFFD29922) else Color(0xFF9A6700)
                                    else -> MaterialTheme.colorScheme.error
                                }
                        Box(
                                modifier =
                                        Modifier.size(8.dp)
                                                .background(
                                                        dotColor,
                                                        RoundedCornerShape(percent = 50)
                                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                                when (status) {
                                    is ClipSyncEngine.Status.Online -> "在线 · $deviceCount 台设备"
                                    is ClipSyncEngine.Status.Connecting -> "连接中…"
                                    is ClipSyncEngine.Status.Offline -> "离线"
                                    ClipSyncEngine.Status.Idle -> "未连接"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    val safety = (status as? ClipSyncEngine.Status.Online)?.safetyNumber
                    if (safety != null) {
                        TextButton(
                                onClick = { model.copyToClipboard(safety) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(
                                    "🛡 $safety",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                TextButton(onClick = onLeave) { Text("离开") }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Toolbar(model: AppModel, query: String) {
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3_000)
            confirmClear = false
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                    value = query,
                    onValueChange = model::updateQuery,
                    label = { Text("搜索…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = model::sendClipboard) { Text("发送剪贴板") }
            TextButton(onClick = model::pickFiles) { Text("发送文件") }
            TextButton(
                    onClick = {
                        if (confirmClear) {
                            model.clearAll()
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    },
            ) { Text(if (confirmClear) "确认清空?" else "清空", color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ---------------------------------------------------------------- row

@OptIn(ExperimentalTime::class)
@Composable
private fun ClipRow(model: AppModel, item: ClipItem) {
    var now by remember(item.id) { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(item.id) {
        while (true) {
            delay(30_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }

    val isFile = item.kind == ClipRepository.KIND_FILE
    val file = item.file

    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
    ) {
        Text(
                when {
                    !isFile -> "📋"
                    file != null && isImage(file.mime) -> "🖼"
                    else -> "📄"
                },
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text =
                            when {
                                isFile && file != null -> displayTitle(file)
                                isFile -> item.text
                                else -> item.text.lineSequence().first().ifBlank { "(空文本)" }
                            },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(relTime(item.ts, now))
                append(" · 来自 ")
                append(item.senderName)
                if (isFile && file != null) {
                    append(" · ")
                    append(humanSize(file.size))
                    if (file.cached && file.localPath != null) append(" · 已缓存")
                    else append(" · 未下载")
                    val left = file.expiresAt - now
                    if (left > 0) append(" · ${humanLeft(left)}后过期")
                }
                if (item.pinned) append(" · 📌")
            }
            Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFile && file != null) {
                if (file.cached && file.localPath != null) {
                    Action("打开") { model.open(item) }
                } else {
                    Action("下载") { model.download(item) }
                }
            } else {
                Action("复制") { model.copy(item) }
            }
            Action(if (item.pinned) "取消" else "置顶") { model.togglePin(item) }
            Action("删除") { model.remove(item) }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) { Text(label, fontSize = 12.sp) }
}

// ---------------------------------------------------------------- helpers

@OptIn(ExperimentalTime::class)
private fun relTime(ts: Long, now: Long): String {
    val d = (now - ts).coerceAtLeast(0)
    return when {
        d < 60_000L -> "刚刚"
        d < 3_600_000L -> "${d / 60_000L} 分钟前"
        d < 86_400_000L -> "${d / 3_600_000L} 小时前"
        d < 30L * 86_400_000L -> "${d / 86_400_000L} 天前"
        else -> "${d / (30L * 86_400_000L)} 个月前"
    }
}

private fun humanLeft(ms: Long): String =
        when {
            ms < 3_600_000L -> "${ms / 60_000L} 分钟"
            ms < 86_400_000L -> "${ms / 3_600_000L} 小时"
            else -> "${ms / 86_400_000L} 天"
        }
