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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import zhiqiu.applewidgets.kmp.oink.overlay.OinkToast
import zhiqiu.applewidgets.kmp.oink.overlay.OinkToastVariant
import zhiqiu.app.cs.resources.*
import zhiqiu.app.cs.ui.Feedback.Deleted

@Composable
internal fun ToastHost(model: AppModel, bottom: Dp, modifier: Modifier = Modifier) {
    val failure by model.failure.collectAsState()
    val info by model.info.collectAsState()
    val active = failure ?: info
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
            modifier = modifier.padding(bottom = bottom),
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
                val variant =
                        when {
                            it.level == Level.Error -> OinkToastVariant.Danger
                            it.level == Level.Warn -> OinkToastVariant.Info
                            else -> OinkToastVariant.Success
                        }
                OinkToast(
                        message = feedbackText(it),
                        variant = variant,
                        actionLabel = if (it is Deleted) stringResource(Res.string.act_undo) else null,
                        onAction = if (it is Deleted) model::undoDelete else null,
                        onDismiss = { model.clearFeedback(it) },
                )
            }
        }
    }
}

private fun toastHoldMillis(feedback: Feedback, chars: Int): Long {
    val base =
            when {
                feedback is Deleted -> 9_000L
                feedback.level == Level.Error -> 6_000L
                else -> 4_000L
            }
    return (base + chars * 30L).coerceAtMost(11_000L)
}
