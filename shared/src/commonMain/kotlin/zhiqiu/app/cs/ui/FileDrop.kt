package zhiqiu.app.cs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zhiqiu.applewidgets.kmp.oink.basic.OinkText
import zhiqiu.applewidgets.kmp.oink.theme.OinkTheme
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.drop_overlay_hint

/**
 * 桌面：接系统拖入的文件列表；其它平台 no-op。
 * [onHovering] 进出目标时切换遮罩；[onPaths] 松手时给出 URI/路径字符串。
 */
@Composable
internal expect fun Modifier.fileDropTarget(
        enabled: Boolean,
        onHovering: (Boolean) -> Unit,
        onPaths: (List<String>) -> Unit,
): Modifier

/** 拖文件悬停时盖在主界面上的提示层。 */
@Composable
internal fun FileDropOverlay() {
    val c = OinkTheme.colors
    Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
    ) {
        OinkText(
                stringResource(Res.string.drop_overlay_hint),
                style = OinkTheme.textStyles.h4,
                color = c.text,
                modifier =
                        Modifier.border(2.dp, c.accent.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                                .background(c.pop.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 28.dp, vertical = 18.dp),
        )
    }
}
