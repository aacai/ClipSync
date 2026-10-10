package zhiqiu.app.cs.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import zhiqiu.app.cs.resources.*

/** 一次性提示胶囊：自动消失、点击即走。 */
@Composable
internal fun ToastHost(model: AppModel, bottom: Dp, modifier: Modifier = Modifier) {
    val failure by model.failure.collectAsState()
    val info by model.info.collectAsState()
    val active = failure ?: info
    // 退场期间要留着上一条文案，所以单独记一份最后非空值。
    var shown by remember { mutableStateOf<Feedback?>(null) }
    if (active != null) shown = active

    val text = active?.let { feedbackText(it) }
    LaunchedEffect(active) {
        val current = active ?: return@LaunchedEffect
        delay(toastHoldMillis(current, text?.length ?: 0))
        model.clearFeedback(current)
    }

    AnimatedVisibility(
            visible = active != null,
            enter =
                    fadeIn(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)) +
                            scaleIn(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN), initialScale = 0.92f) +
                            slideInVertically(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)) { it / 2 },
            exit =
                    fadeOut(tween(M3.SHORT_4, easing = M3.EMPHASIZED_OUT)) +
                            slideOutVertically(tween(M3.SHORT_4, easing = M3.EMPHASIZED_OUT)) { it / 3 },
            modifier = modifier.padding(horizontal = 12.dp).padding(bottom = bottom),
            label = "toast",
    ) {
        AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    fadeIn(tween(M3.SHORT_4, easing = M3.STANDARD)) togetherWith
                            fadeOut(tween(M3.SHORT_3, easing = M3.STANDARD))
                },
                label = "toastContent",
        ) { feedback ->
            feedback?.let {
                ToastCard(
                        it,
                        onDismiss = { model.clearFeedback(it) },
                        onUndo = if (it is Feedback.Deleted) model::undoDelete else null,
                )
            }
        }
    }
}

@Composable
private fun ToastCard(feedback: Feedback, onDismiss: () -> Unit, onUndo: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val error = feedback.level == Level.Error

    Row(
            modifier =
                    Modifier.shadow(10.dp, shape, clip = false)
                            .widthIn(max = 480.dp)
                            .background(if (error) scheme.errorContainer else scheme.surfaceContainerHigh, shape)
                            .border(1.dp, glassBorder().copy(alpha = 0.45f), shape)
                            .clickable(onClick = onDismiss)
                            .padding(start = 13.dp, end = if (onUndo == null) 13.dp else 5.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
                when {
                    error || feedback.level == Level.Warn -> AppIcons.Warning
                    else -> AppIcons.Check
                },
                contentDescription = null,
                tint =
                        when {
                            error -> scheme.error
                            feedback.level == Level.Warn ->
                                    if (isAppDarkTheme()) PinAmberDark else PinAmber
                            else -> if (isAppDarkTheme()) SuccessGreenDark else SuccessGreen
                        },
                modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
                text = feedbackText(feedback),
                style = MaterialTheme.typography.labelLarge,
                color = if (error) scheme.onErrorContainer else scheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f, fill = false),
        )
        if (onUndo != null) {
            Spacer(Modifier.width(6.dp))
            RowAction(AppIcons.Undo, stringResource(Res.string.act_undo), onClick = onUndo)
        }
    }
}

private fun toastHoldMillis(feedback: Feedback, chars: Int): Long {
    val base =
            when {
                feedback is Feedback.Deleted -> 9_000L
                feedback.level == Level.Error -> 6_000L
                else -> 4_000L
            }
    return (base + chars * 30L).coerceAtMost(11_000L)
}
