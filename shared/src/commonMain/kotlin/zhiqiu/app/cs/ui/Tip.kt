package zhiqiu.app.cs.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun Tip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above, 8.dp),
            tooltip = {
                PlainTooltip { Text(text, style = MaterialTheme.typography.labelSmall) }
            },
            state = rememberTooltipState(),
            modifier = modifier,
    ) { content() }
}
