package app.myfinhub.android.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

object MyFinHubMotion {
    const val QuickDurationMillis = 120
    const val StandardDurationMillis = 180
    const val EmphasizedDurationMillis = 240
    const val PressedScale = 0.985f
}

/** Lightweight tactile feedback. Compose animation timing follows the platform duration scale. */
@Composable
fun Modifier.myFinHubPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = MyFinHubMotion.PressedScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = MyFinHubMotion.QuickDurationMillis),
        label = "MyFinHub press feedback",
    )
    return if (!pressed && scale == 1f) {
        this
    } else {
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}
