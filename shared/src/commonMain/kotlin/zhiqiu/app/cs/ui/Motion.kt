package zhiqiu.app.cs.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Material 3 motion token（md.sys.motion.duration.* / md.sys.motion.easing.*）。 */
internal object M3 {
    const val SHORT_3 = 150
    const val SHORT_4 = 200
    const val MEDIUM_1 = 250
    const val MEDIUM_2 = 300
    const val MEDIUM_3 = 350

    val STANDARD: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val EMPHASIZED_IN: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EMPHASIZED_OUT: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val EMPHASIZED: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
