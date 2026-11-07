package dev.glass.launcher.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * iOS-style "squircle" (superellipse, exponent 5): the continuous-curvature icon shape, rounder
 * than a rounded rectangle and without the visible join where the corner meets the edge.
 */
object SquircleShape : Shape {
    private const val N = 5.0
    private const val STEPS = 72

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val path = Path()
        for (i in 0..STEPS) {
            val t = i * 2.0 * Math.PI / STEPS
            val c = cos(t)
            val s = sin(t)
            val x = a + a * (sign(c) * abs(c).pow(2.0 / N)).toFloat()
            val y = b + b * (sign(s) * abs(s).pow(2.0 / N)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
