package dev.glass.launcher.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.asComposeRenderEffect
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
import kotlin.math.roundToInt

/**
 * AGSL lens for the glass rim (Android 13+).
 *
 * The panel is treated as a rounded-rect lens. Inside [bevel] pixels of the edge the
 * backdrop is pulled inward along the edge normal, so the wallpaper appears bent around
 * the rim, like thick glass. Red and blue are sampled at slightly different strengths
 * to get colour fringing, and a specular highlight is added along the edge facing
 * [lightDir].
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float dispersion;
uniform float2 lightDir;
uniform float specular;

float2 clampPos(float2 p) {
    return clamp(p, float2(0.5), size - float2(0.5));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float2 q = abs(p) - (halfSize - float2(radius));

    // Signed distance to the rounded rect (negative inside) and its outward normal.
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float2 n;
    if (q.x > 0.0 || q.y > 0.0) {
        n = normalize(max(q, float2(0.0))) * s;
    } else if (q.x > q.y) {
        n = float2(s.x, 0.0);
    } else {
        n = float2(0.0, s.y);
    }

    // 1 at the very edge, falling to 0 at [bevel] pixels inside.
    float t = clamp(1.0 + sd / bevel, 0.0, 1.0);
    float bend = t * t * t;
    float2 disp = -n * bend * strength;

    half r = content.eval(clampPos(coord + disp * (1.0 + dispersion))).r;
    half g = content.eval(clampPos(coord + disp)).g;
    half b = content.eval(clampPos(coord + disp * (1.0 - dispersion))).b;
    half3 rgb = half3(r, g, b);

    // Specular rim light.
    float facing = max(dot(n, normalize(lightDir)), 0.0);
    float spec = facing * facing * t * t * specular;
    rgb += half3(spec);

    return half4(rgb, 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(
    size: IntSize,
    radiusPx: Float,
    bevelPx: Float,
    strengthPx: Float,
    dispersion: Float,
    blurPx: Float,
    lightDir: Offset,
    specular: Float,
): androidx.compose.ui.graphics.RenderEffect {
    val shader = RuntimeShader(LENS_AGSL)
    shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
    shader.setFloatUniform("radius", radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f))
    shader.setFloatUniform("bevel", bevelPx)
    shader.setFloatUniform("strength", strengthPx)
    shader.setFloatUniform("dispersion", dispersion)
    shader.setFloatUniform("lightDir", lightDir.x, lightDir.y)
    shader.setFloatUniform("specular", specular)

    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val effect = if (blurPx > 0.5f) {
        // Blur first (inner), then refract the blurred result (outer).
        RenderEffect.createChainEffect(
            lens,
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
        )
    } else {
        lens
    }
    return effect.asComposeRenderEffect()
}

/**
 * A liquid-glass surface.
 *
 * It draws a copy of the wallpaper, shifted so it lines up with the real one, and
 * processes that copy inside the panel's own bounds, then layers a tint, a soft
 * highlight and a thin border on top.
 *
 * - Android 13+: light blur plus the refraction lens ([LENS_AGSL]).
 * - Android 12: blur only.
 * - Below Android 12: a darker translucent tint.
 *
 * Only the panel's area is processed, which keeps it cheap enough for mid-range GPUs.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blurRadius: Dp = 24.dp,
    tint: Color = Color.White.copy(alpha = 0.14f),
    refraction: Dp = 16.dp,
    bevel: Dp = 24.dp,
    dispersion: Float = 0.08f,
    specular: Float = 0.35f,
    lightDirection: Offset = Offset(-0.6f, -0.8f),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalBackdrop.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val lensSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f),
            )
            .clip(shape),
    ) {
        // 1. Processed copy of the wallpaper, aligned to the real one.
        if (blurSupported && backdrop != null && backdrop.size != IntSize.Zero) {
            val layerModifier =
                if (lensSupported && panelSize != IntSize.Zero) {
                    val effect = remember(
                        panelSize, cornerRadius, blurRadius, refraction, bevel,
                        dispersion, specular, lightDirection, density,
                    ) {
                        with(density) {
                            lensEffect(
                                size = panelSize,
                                radiusPx = cornerRadius.toPx(),
                                bevelPx = bevel.toPx(),
                                strengthPx = refraction.toPx(),
                                dispersion = dispersion,
                                blurPx = blurRadius.toPx() * 0.35f,
                                lightDir = lightDirection,
                                specular = specular,
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
