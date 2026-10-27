package dev.glass.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.glass.launcher.ui.glass.GlassQuality
import dev.glass.launcher.ui.glass.LensShader
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalGlassQuality
import dev.glass.launcher.ui.glass.GlassMaterial
import kotlin.math.roundToInt

/**
 * A liquid-glass surface.
 *
 * It draws a copy of the wallpaper, shifted so it lines up with the real one, processes that
 * copy inside the panel's own bounds, then layers tint, reflections and an edge on top. The
 * look comes from [material]; use the same material across surfaces so they match.
 *
 * What runs depends on [LocalGlassQuality]:
 * - Full (Android 13+): light blur, then the AGSL lens (see `GlassShader.kt`).
 * - Reduced (Android 12, battery saver, or a shader that failed to compile): blur only.
 * - Minimal (Android 11 and below): a darker translucent tint, no blur.
 *
 * Only the panel's own area is processed, and the shader is compiled once per panel; later
 * parameter or size changes only rebuild a cheap render effect.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    material: GlassMaterial = LocalGlassMaterial.current,
    lightDirection: Offset = DefaultLightDirection,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalBackdrop.current
    val quality = LocalGlassQuality.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    val blurs = quality != GlassQuality.Minimal
    val lens = remember(quality) {
        if (quality == GlassQuality.Full && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                LensShader()
            } catch (e: Exception) {
                null // Falls back to plain blur below.
            }
        } else {
            null
        }
    }

    // Strength of the edge highlights relative to the Clear preset, so the border follows
    // the material instead of being a fixed white line.
    val edge = (material.highlight / 0.28f).coerceIn(0f, 1.6f)

    Box(
        modifier
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                origin = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                panelSize = coords.size
            }
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.24f),
            )
            .clip(shape),
    ) {
        // 1. Process a wallpaper-aligned copy only inside this panel's bounds.
        if (blurs && backdrop != null && backdrop.size != IntSize.Zero) {
            val blurRadius = if (quality == GlassQuality.Reduced) material.blur * 0.7f else material.blur

            val layerModifier =
                if (lens != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    panelSize != IntSize.Zero
                ) {
                    val effect = remember(panelSize, cornerRadius, material, lightDirection, density) {
                        with(density) {
                            lens.effect(
                                size = panelSize,
                                radiusPx = cornerRadius.toPx(),
                                bevelPx = material.thickness.toPx(),
                                strengthPx = material.refraction.toPx(),
                                dispersion = material.dispersion,
                                blurPx = blurRadius.toPx() * 0.32f,
                                lightDir = lightDirection,
                                highlight = material.highlight,
                                reflection = material.reflection,
                                innerShade = material.innerShade,
                            )
                        }
                    }
                    Modifier.graphicsLayer {
                        renderEffect = effect
                        clip = true
                    }
                } else {
                    Modifier.blur(blurRadius, BlurredEdgeTreatment.Rectangle)
                }

            Box(Modifier.matchParentSize().then(layerModifier)) {
                Box(
                    Modifier.layout { measurable, _ ->
                        val placeable = measurable.measure(
                            Constraints.fixed(backdrop.size.width, backdrop.size.height)
                        )
                        // A zero-sized layout node lets the wallpaper copy be offset without
                        // participating in the panel's measured size.
                        layout(0, 0) {
                            placeable.place(-origin.x, -origin.y)
                        }
                    },
                ) {
                    BackdropArt(backdrop.wallpaper, Modifier.fillMaxSize(), backdrop.tilt)
                }
            }
        }

        // 2. Tint. Without real blur, sit it on a darker base so text stays readable.
        if (!blurs) {
            Box(Modifier.matchParentSize().background(Color(0xFF14141E).copy(alpha = 0.5f)))
        }
        Box(Modifier.matchParentSize().background(material.tint))

        // 3. Broad, soft reflection; intentionally low contrast.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.19f * edge),
                            0.34f to Color.White.copy(alpha = 0.055f * edge),
                            1.0f to Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(520f, 620f),
                    ),
                ),
        )

        // 4. Gentle lower shade gives the surface thickness without a muddy overlay.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.075f),
                        ),
                    ),
                ),
        )

        // 5. Outer rim, plus a faint line just inside it for a double-reflection cue.
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.58f * edge),
                            Color.White.copy(alpha = 0.12f * edge),
                            Color(0xFFB8D9FF).copy(alpha = 0.24f * edge),
                            Color.White.copy(alpha = 0.32f * edge),
                        ),
                        start = Offset.Zero,
                        end = Offset(480f, 640f),
                    ),
                    shape = shape,
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f * edge),
                    shape = RoundedCornerShape((cornerRadius - 1.dp).coerceAtLeast(0.dp)),
                ),
        )

        content()
    }
}

/** Light from the top-left, matching the highlight position drawn by the shader. */
private val DefaultLightDirection = Offset(-0.6f, -0.8f)
