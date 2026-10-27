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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** What glass panels blur: the wallpaper, plus the root size so the copy lines up. */
@Immutable
data class Backdrop(
    val size: IntSize,
    val wallpaper: ImageBitmap?,
)

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Draws the wallpaper full-screen and exposes it to [GlassPanel]s below.
 * Anything drawn inside [content] sits on top of the wallpaper.
 */
@Composable
fun BackdropHost(
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
    ) {
        BackdropArt(wallpaper, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalBackdrop provides Backdrop(size, wallpaper)) {
            content()
        }
    }
}

/** The picked wallpaper, or a built-in aurora gradient when none is set. */
@Composable
fun BackdropArt(wallpaper: ImageBitmap?, modifier: Modifier = Modifier) {
    if (wallpaper != null) {
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Canvas(modifier) {
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
