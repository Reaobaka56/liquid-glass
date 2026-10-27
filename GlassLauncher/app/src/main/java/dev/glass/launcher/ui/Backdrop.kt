package dev.glass.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import dev.glass.launcher.wallpaper.Parallax

/**
 * What glass panels blur: the wallpaper, plus the root size so the copy lines up.
 * [tilt] is read during drawing only, so tilting never recomposes anything.
 */
@Immutable
data class Backdrop(
    val size: IntSize,
    val wallpaper: ImageBitmap?,
    val tilt: () -> Offset,
)

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Draws the wallpaper full-screen and exposes it to [GlassPanel]s below.
 * Anything drawn inside [content] sits on top of the wallpaper.
 */
@Composable
fun BackdropHost(
    wallpaper: ImageBitmap?,
    tilt: () -> Offset = NoTilt,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
    ) {
        BackdropArt(wallpaper, Modifier.fillMaxSize(), tilt)
        CompositionLocalProvider(LocalBackdrop provides Backdrop(size, wallpaper, tilt)) {
            content()
        }
    }
}

val NoTilt: () -> Offset = { Offset.Zero }

/**
 * The picked wallpaper, or a built-in aurora gradient when none is set.
 *
 * It is drawn [Parallax.ZOOM] times larger than the screen and shifted against the phone's
 * [tilt]. [GlassPanel] draws its own copy through this same function, so the glass always
 * refracts exactly what is behind it. The wallpaper service applies the same transform.
 */
@Composable
fun BackdropArt(
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    tilt: () -> Offset = NoTilt,
) {
    val parallax = Modifier.graphicsLayer {
        val t = tilt()
        scaleX = Parallax.ZOOM
        scaleY = Parallax.ZOOM
        translationX = -t.x * size.width * Parallax.SHIFT_X
        translationY = -t.y * size.height * Parallax.SHIFT_Y
    }
    if (wallpaper != null) {
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.then(parallax),
        )
    } else {
        Canvas(modifier.then(parallax)) {
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0B1026), Color(0xFF1B1146), Color(0xFF3A1C5C)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )

            val unit = size.minDimension

            fun blob(cx: Float, cy: Float, r: Float, color: Color) {
                val center = Offset(size.width * cx, size.height * cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.85f), Color.Transparent),
                        center = center,
                        radius = unit * r,
                    ),
                    radius = unit * r,
                    center = center,
                )
            }

            blob(0.20f, 0.15f, 0.70f, Color(0xFF4F7CFF))
            blob(0.90f, 0.40f, 0.60f, Color(0xFFFF5FA2))
            blob(0.30f, 0.85f, 0.75f, Color(0xFF29D3C2))
        }
    }
}
