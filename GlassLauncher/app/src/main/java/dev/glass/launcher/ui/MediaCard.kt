package dev.glass.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.media.MediaUi
import kotlinx.coroutines.delay

/**
 * Now-playing card: big artwork, title and artist, a scrubbable progress bar and transport
 * controls, on glass. Shows a one-time "Enable" button until notification access is granted,
 * and nothing when nothing is playing.
 */
@Composable
fun MediaCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val media = remember { MediaUi(context) }
    DisposableEffect(media) {
        media.start()
        onDispose { media.stop() }
    }

    if (!media.hasAccess) {
        // Access can be granted in Settings and the user comes back: re-check on every frame
        // of the screen being alive is overkill, so poll gently.
        LaunchedEffect(Unit) {
            while (true) {
                delay(2_000L)
                media.refresh()
                if (media.hasAccess) media.start()
            }
        }
        GlassPanel(modifier.fillMaxWidth(), cornerRadius = 28.dp, interactive = false) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show what's playing here",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                GlassButton("Enable", {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                })
            }
        }
        return
    }
    if (!media.active) return

    var position by remember { mutableLongStateOf(media.positionMs()) }
    LaunchedEffect(media.playing, media.title) {
        while (true) {
            position = media.positionMs()
            delay(500L)
        }
    }

    GlassPanel(modifier.fillMaxWidth(), cornerRadius = 32.dp, interactive = false) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = media.art
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                ) {
                    if (art != null) {
                        Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(76.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        media.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        media.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (media.durationMs > 0) {
                Spacer(Modifier.height(14.dp))
                val frac = (position.toFloat() / media.durationMs).coerceIn(0f, 1f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(media.durationMs) {
                            detectTapGestures { o ->
                                val f = (o.x / size.width).coerceIn(0f, 1f)
                                media.seekTo((f * media.durationMs).toLong())
                                position = (f * media.durationMs).toLong()
                            }
                        },
                ) {
                    val h = 5.dp.toPx()
                    val y = (size.height - h) / 2f
                    drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(Color.White, Offset(0f, y), Size(size.width * frac, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text("-" + formatTime((media.durationMs - position).coerceAtLeast(0L)), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportIcon(Kind.Previous) { media.previous() }
                TransportIcon(if (media.playing) Kind.Pause else Kind.Play) { media.playPause() }
                TransportIcon(Kind.Next) { media.next() }
            }
        }
    }
}

private enum class Kind { Previous, Play, Pause, Next }

@Composable
private fun TransportIcon(kind: Kind, onClick: () -> Unit) {
    Canvas(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        val w = size.width
        val h = size.height
        fun tri(x0: Float, x1: Float, pointRight: Boolean) = Path().apply {
            if (pointRight) {
                moveTo(x0, 0f); lineTo(x1, h / 2f); lineTo(x0, h)
            } else {
                moveTo(x1, 0f); lineTo(x0, h / 2f); lineTo(x1, h)
            }
            close()
        }
        when (kind) {
            Kind.Play -> drawPath(tri(w * 0.15f, w * 0.95f, true), Color.White)
            Kind.Pause -> {
                drawRoundRect(Color.White, Offset(w * 0.1f, 0f), Size(w * 0.30f, h), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
                drawRoundRect(Color.White, Offset(w * 0.6f, 0f), Size(w * 0.30f, h), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            }
            Kind.Next -> {
                drawPath(tri(0f, w * 0.5f, true), Color.White)
                drawPath(tri(w * 0.5f, w, true), Color.White)
            }
            Kind.Previous -> {
                drawPath(tri(0f, w * 0.5f, false), Color.White)
                drawPath(tri(w * 0.5f, w, false), Color.White)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
