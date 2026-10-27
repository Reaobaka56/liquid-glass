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
 * Rounded-rectangle optical lens for Android 13+.
 *
 * Refraction and dispersion are confined to the bevel, with a smooth falloff.
 * A directional specular reflection and restrained cool rim tint add depth without
 * turning the whole panel into a glowing neon card.
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
uniform float rimGlow;

float2 clampPos(float2 p) {
    return clamp(p, float2(0.5), size - float2(0.5));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float2 q = abs(p) - (halfSize - float2(radius));

    // Signed distance: 0 at the edge, negative inside the rounded rectangle.
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float2 signs = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float2 normal;
    if (q.x > 0.0 || q.y > 0.0) {
        normal = normalize(max(q, float2(0.0))) * signs;
    } else if (q.x > q.y) {
        normal = float2(signs.x, 0.0);
    } else {
        normal = float2(0.0, signs.y);
    }

    // Smooth the bevel to avoid a harsh, stepped refraction band.
    float edge = smoothstep(-max(bevel, 1.0), 0.0, sd);
    float bend = edge * edge * (3.0 - 2.0 * edge);
    float2 offset = -normal * bend * strength;

    // Chromatic separation fades with the lens curvature instead of tinting the centre.
    float chroma = dispersion * bend;
    half red = content.eval(clampPos(coord + offset * (1.0 + chroma))).r;
    half green = content.eval(clampPos(coord + offset)).g;
    half blue = content.eval(clampPos(coord + offset * (1.0 - chroma))).b;
    half3 rgb = half3(red, green, blue);

    // Directional highlight on the side facing the light source.
    float facing = max(dot(normal, normalize(lightDir)), 0.0);
    float highlight = pow(facing, 2.4) * edge * specular;

    // A restrained cool reflection is strongest at the rim and falls inward.
    half3 coolRim = half3(0.34, 0.62, 1.0) * (rimGlow * edge * (0.25 + 0.75 * facing));
    rgb = min(rgb + half3(highlight) + coolRim, half3(1.0));

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
    rimGlow: Float,
): androidx.compose.ui.graphics.RenderEffect {
    val shader = RuntimeShader(LENS_AGSL)
    shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
    shader.setFloatUniform("radius", radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f))
    shader.setFloatUniform("bevel", bevelPx.coerceAtLeast(1f))
    shader.setFloatUniform("strength", strengthPx.coerceAtLeast(0f))
    shader.setFloatUniform("dispersion", dispersion.coerceIn(0f, 0.2f))
    shader.setFloatUniform("lightDir", lightDir.x, lightDir.y)
    shader.setFloatUniform("specular", specular.coerceIn(0f, 1f))
    shader.setFloatUniform("rimGlow", rimGlow.coerceIn(0f, 0.35f))

    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
    // Chain order is intentional: blur the backdrop first, then bend its pixels.
    val effect = if (blurPx > 0.5f) {
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
 * A bounded wallpaper-glass surface.
 *
 * Android 13+: backdrop blur, rounded-lens refraction and chromatic edge dispersion.
 * Android 12: backdrop blur without the AGSL lens.
 * Android 11 and below: a tinted translucent fallback.
 *
 * Keep the optical effect subtle: the wallpaper should remain legible through the glass.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blurRadius: Dp = 24.dp,
    tint: Color = Color.White.copy(alpha = 0.12f),
    refraction: Dp = 14.dp,
    bevel: Dp = 26.dp,
    dispersion: Float = 0.055f,
    specular: Float = 0.28f,
    rimGlow: Float = 0.10f,
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
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.24f),
            )
            .clip(shape),
    ) {
        // 1. Process a wallpaper-aligned copy only inside this panel's bounds.
        if (blurSupported && backdrop != null && backdrop.size != IntSize.Zero) {
            val layerModifier =
                if (lensSupported && panelSize != IntSize.Zero) {
                    val effect = remember(
                        panelSize, cornerRadius, blurRadius, refraction, bevel,
                        dispersion, specular, rimGlow, lightDirection, density,
                    ) {
                        with(density) {
                            lensEffect(
                                size = panelSize,
                                radiusPx = cornerRadius.toPx(),
                                bevelPx = bevel.toPx(),
                                strengthPx = refraction.toPx(),
                                dispersion = dispersion,
                                blurPx = blurRadius.toPx() * 0.32f,
                                lightDir = lightDirection,
                                specular = specular,
                                rimGlow = rimGlow,
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

        // 2. Frost layer: a little less white keeps wallpaper colours alive.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    if (blurSupported) tint
                    else Color(0xFF14141E).copy(alpha = 0.52f),
                ),
        )

        // 3. Broad, soft reflection; intentionally low contrast.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.19f),
                            0.34f to Color.White.copy(alpha = 0.055f),
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

        // 5. Outer rim plus a faint inner edge creates a double-reflection cue.
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.58f),
                            Color.White.copy(alpha = 0.12f),
                            Color(0xFFB8D9FF).copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.32f),
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
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape((cornerRadius - 1.dp).coerceAtLeast(0.dp)),
                ),
        )

        content()
    }
}
