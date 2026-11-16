package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import zhiqiu.applewidgets.kmp.oink.overlay.OinkTooltip

@Composable
internal fun Tip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    OinkTooltip(tooltip = text, modifier = modifier, content = content)
}
