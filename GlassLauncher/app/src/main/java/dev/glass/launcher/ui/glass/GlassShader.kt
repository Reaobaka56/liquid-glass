package dev.glass.launcher.ui.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

/**
 * AGSL lens for a rounded-rectangle glass surface (Android 13+).
 *
 * The surface is treated as a thick, rounded slab. Within `bevel` pixels of the edge the
 * backdrop is pulled inward along the edge normal, with the pull growing quadratically toward
 * the rim, so the wallpaper looks bent around the glass edge and is untouched in the middle.
 * Red and blue are sampled at slightly different distances for colour fringing that fades
 * with the curvature. On top of that:
 *
 * - a specular highlight on the rim facing `lightDir` (thin sharp core plus a soft lobe),
 * - a faint bounce highlight on the opposite rim,
 * - inner bevel shading: a band inside the rim, darker away from the light, lighter toward it,
 * - a soft environment reflection blended (not added) toward the rim, so it can't blow out,
 * - ±0.5/255 noise to break up banding in smooth gradients.
 *
 * `size` and `radius` are in pixels in the panel's local coordinates, which is also the
 * coordinate space of `content` (the panel's own layer), so no extra offsets are needed.
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float dispersion;
uniform float2 lightDir;
uniform float highlight;
uniform float reflection;
uniform float innerShade;
uniform float2 touch;
uniform float touchAmount;
uniform float touchRadius;

float2 clampPos(float2 p) {
    return clamp(p, float2(0.5), size - float2(0.5));
}

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float2 q = abs(p) - (halfSize - float2(radius));

    // Signed distance to the rounded rect (negative inside) and its outward normal.
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

    // depth: 0 at the rim, 1 at `bevel` pixels inside. edge is its complement.
    float depth = clamp(-sd / max(bevel, 1.0), 0.0, 1.0);
    float edge = 1.0 - depth;
    float bend = edge * edge;

    float2 offset = -normal * bend * strength;

    // Finger: magnify the backdrop around the touch point, like a water drop under the glass.
    // touchAmount is spring-driven from Kotlin, so it overshoots and settles on release.
    float2 toTouch = touch - coord;
    float touchDist = length(toTouch);
    float finger = touchAmount * exp(-(touchDist * touchDist) / (touchRadius * touchRadius));
    offset += (toTouch / max(touchDist, 1.0)) * finger * strength * 0.5;

    float chroma = dispersion * bend;
    half red = content.eval(clampPos(coord + offset * (1.0 + chroma))).r;
    half green = content.eval(clampPos(coord + offset)).g;
    half blue = content.eval(clampPos(coord + offset * (1.0 - chroma))).b;
    half3 rgb = half3(red, green, blue);

    float facing = dot(normal, normalize(lightDir));
    float lit = max(facing, 0.0);
    float away = max(-facing, 0.0);
    float rim = edge * edge * edge;

    // Inner bevel: a band peaking halfway into the bevel.
    float band = smoothstep(0.0, 0.5, depth) * (1.0 - smoothstep(0.5, 1.0, depth));
    rgb *= half(1.0 - innerShade * band * away);
    rgb += half3(innerShade * 0.5 * band * lit);

    // Environment reflection, then specular and bounce highlights.
    half3 env = half3(0.80, 0.88, 1.0);
    rgb = mix(rgb, env, half(reflection * rim * (0.3 + 0.7 * lit)));
    float spec = (pow(lit, 3.0) * rim + 0.35 * lit * edge * edge) * highlight;
    float bounce = away * away * rim * highlight * 0.35;
    rgb += half3(spec + bounce);

    // Soft specular bloom under the finger.
    rgb += half3(finger * (0.10 + 0.25 * highlight));

    rgb += half3((hash(coord) - 0.5) / 255.0);
    return half4(clamp(rgb, half3(0.0), half3(1.0)), 1.0);
}
"""

/**
 * Owns one compiled [RuntimeShader]. Compiling AGSL is the expensive part, so each panel
 * creates this once and only rebuilds the cheap [RenderEffect] when a parameter changes.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LensShader {
    private val shader = RuntimeShader(LENS_AGSL)

    fun effect(
        size: IntSize,
        radiusPx: Float,
        bevelPx: Float,
        strengthPx: Float,
        dispersion: Float,
        blurPx: Float,
        lightDir: Offset,
        highlight: Float,
        reflection: Float,
        innerShade: Float,
        touch: Offset = Offset.Zero,
        touchAmount: Float = 0f,
        touchRadiusPx: Float = 1f,
    ): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("radius", radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f))
        shader.setFloatUniform("bevel", bevelPx.coerceAtLeast(1f))
        shader.setFloatUniform("strength", strengthPx.coerceAtLeast(0f))
        shader.setFloatUniform("dispersion", dispersion.coerceIn(0f, 0.2f))
        shader.setFloatUniform("lightDir", lightDir.x, lightDir.y)
        shader.setFloatUniform("highlight", highlight.coerceIn(0f, 1f))
        shader.setFloatUniform("reflection", reflection.coerceIn(0f, 0.35f))
        shader.setFloatUniform("innerShade", innerShade.coerceIn(0f, 0.4f))
        shader.setFloatUniform("touch", touch.x, touch.y)
        shader.setFloatUniform("touchAmount", touchAmount.coerceIn(0f, 1f))
        shader.setFloatUniform("touchRadius", touchRadiusPx.coerceAtLeast(1f))

        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val effect = if (blurPx > 0.5f) {
            // Chain order is intentional: blur the backdrop first (inner), then bend it (outer).
            RenderEffect.createChainEffect(
                lens,
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
            )
        } else {
            lens
        }
        return effect.asComposeRenderEffect()
    }
}

object GlassCapabilities {
    private const val TAG = "GlassLens"

    /** True when the AGSL lens exists on this device and compiled once successfully. */
    val lensAvailable: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && probeLens()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun probeLens(): Boolean = try {
        LensShader()
        true
    } catch (e: Exception) {
        // A bad shader must never take the launcher down; panels fall back to plain blur.
        Log.w(TAG, "AGSL lens unavailable, falling back to blur", e)
        false
    }
}

/** How much work glass surfaces may do. */
enum class GlassQuality {
    /** Blur plus the AGSL lens. */
    Full,

    /** Blur only (Android 12, a failed shader, or battery saver). */
    Reduced,

    /** Tint only, no real blur (Android 11 and below). */
    Minimal,
}

val LocalGlassQuality = compositionLocalOf { GlassQuality.Full }

/** Picks a quality from the OS version, shader health and battery saver. Provide it once, high up. */
@Composable
fun rememberGlassQuality(): GlassQuality {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        powerSave = powerManager.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }

    return when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> GlassQuality.Minimal
        powerSave || !GlassCapabilities.lensAvailable -> GlassQuality.Reduced
        else -> GlassQuality.Full
    }
}
