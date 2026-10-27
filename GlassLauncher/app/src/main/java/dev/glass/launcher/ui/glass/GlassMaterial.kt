package dev.glass.launcher.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One description of how glass looks, shared by every surface (clock, dock, drawer, menus),
 * so the whole launcher reads as a single material.
 *
 * @property blur backdrop blur radius.
 * @property tint colour laid over the glass; its alpha is the glass opacity.
 * @property refraction how far the wallpaper is bent at the rim.
 * @property thickness width of the curved rim, measured inward from the edge. Thicker glass
 *   bends a wider band of the wallpaper.
 * @property dispersion colour fringing at the rim (fraction of the refraction distance).
 * @property highlight strength of the directional specular highlight.
 * @property reflection strength of the soft environment reflection along the rim.
 * @property innerShade strength of the shading just inside the rim that gives the edge volume.
 */
@Immutable
data class GlassMaterial(
    val blur: Dp,
    val tint: Color,
    val refraction: Dp,
    val thickness: Dp,
    val dispersion: Float,
    val highlight: Float,
    val reflection: Float,
    val innerShade: Float,
) {
    /**
     * Scales the optical effects (refraction, dispersion, highlights, reflection, shading) by
     * [intensity]. 1 is the material as designed, 0 is plain tinted blur. Blur and tint are
     * left alone so legibility doesn't change.
     */
    fun scaled(intensity: Float): GlassMaterial = copy(
        refraction = refraction * intensity,
        dispersion = dispersion * intensity,
        highlight = highlight * intensity,
        reflection = reflection * intensity,
        innerShade = innerShade * intensity,
    )

    /** For large overlays such as the app drawer: a little more blur, a little less tint. */
    fun forOverlay(): GlassMaterial = copy(
        blur = blur * 1.35f,
        tint = tint.copy(alpha = tint.alpha * 0.85f),
    )
}

/** The built-in looks. Values were tuned by eye against bright, dark and busy wallpapers. */
enum class GlassPreset(val label: String, val material: GlassMaterial) {
    Clear(
        "Clear",
        GlassMaterial(
            blur = 24.dp, tint = Color.White.copy(alpha = 0.12f),
            refraction = 14.dp, thickness = 26.dp, dispersion = 0.055f,
            highlight = 0.28f, reflection = 0.10f, innerShade = 0.10f,
        ),
    ),
    Frost(
        "Frost",
        GlassMaterial(
            blur = 34.dp, tint = Color.White.copy(alpha = 0.22f),
            refraction = 9.dp, thickness = 22.dp, dispersion = 0.03f,
            highlight = 0.22f, reflection = 0.07f, innerShade = 0.08f,
        ),
    ),
    Smoke(
        "Smoke",
        GlassMaterial(
            blur = 28.dp, tint = Color(0xFF1C1C22).copy(alpha = 0.38f),
            refraction = 12.dp, thickness = 24.dp, dispersion = 0.04f,
            highlight = 0.20f, reflection = 0.06f, innerShade = 0.12f,
        ),
    ),
    Midnight(
        "Midnight",
        GlassMaterial(
            blur = 26.dp, tint = Color(0xFF0B1230).copy(alpha = 0.46f),
            refraction = 14.dp, thickness = 26.dp, dispersion = 0.05f,
            highlight = 0.26f, reflection = 0.10f, innerShade = 0.12f,
        ),
    ),
    Aurora(
        "Aurora",
        GlassMaterial(
            blur = 22.dp, tint = Color(0xFF7C6CFF).copy(alpha = 0.16f),
            refraction = 16.dp, thickness = 26.dp, dispersion = 0.06f,
            highlight = 0.30f, reflection = 0.11f, innerShade = 0.09f,
        ),
    );

    companion object {
        fun fromName(name: String?): GlassPreset = entries.firstOrNull { it.name == name } ?: Clear
    }
}

/** The material glass surfaces use unless one is passed explicitly. */
val LocalGlassMaterial = compositionLocalOf { GlassPreset.Clear.material }
