package dev.glass.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A frosted-glass surface.
 *
 * It draws a copy of the wallpaper, shifted so it lines up with the real one, blurs
 * that copy inside the panel's own bounds, then layers a tint, a soft highlight and a
 * thin border on top. Only the panel's area is blurred, which keeps it cheap enough
 * for mid-range GPUs. Below Android 12 (no RenderEffect blur) it falls back to a
 * darker translucent tint.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 24.dp,
    tint: Color = Color.White.copy(alpha = 0.14f),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                origin = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f),
            )
            .clip(shape),
    ) {
        // 1. Blurred copy of the wallpaper, aligned to the real one.
        if (blurSupported && backdrop != null && backdrop.size != IntSize.Zero) {
            Box(
                Modifier
                    .matchParentSize()
                    .blur(blurRadius, BlurredEdgeTreatment.Rectangle),
            ) {
                Box(
                    Modifier.layout { measurable, _ ->
                        val placeable = measurable.measure(
                            Constraints.fixed(backdrop.size.width, backdrop.size.height)
                        )
                        // Report zero size so the copy never affects the panel's layout,
                        // and shift it so it lines up with the wallpaper behind the panel.
                        layout(0, 0) {
                            placeable.place(-origin.x, -origin.y)
                        }
                    },
                ) {
                    BackdropArt(backdrop.wallpaper, Modifier.fillMaxSize())
                }
            }
        }

        // 2. Frost tint (darker when real blur isn't available).
        Box(
            Modifier
                .matchParentSize()
                .background(if (blurSupported) tint else Color(0xFF14141E).copy(alpha = 0.55f)),
        )

        // 3. Soft light from the top-left corner.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(420f, 420f),
                    )
                ),
        )

        // 4. Thin edge highlight.
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.25f),
                        ),
                    ),
                    shape = shape,
                ),
        )

        content()
    }
}
