package dev.glass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.AppEntry
import dev.glass.launcher.ui.glass.LocalReduceMotion

/**
 * An open folder: a glass card that springs out of the screen centre. Tap an app to launch it,
 * long-press to take it out of the folder, tap the title to rename.
 */
@Composable
fun FolderOverlay(
    name: String,
    apps: List<AppEntry>,
    onRename: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
    }
    var title by remember(name) { mutableStateOf(name) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .pointerInput(Unit) { detectTapGestures { onRename(title); onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.6f + 0.4f * enter.value
                    scaleX = s
                    scaleY = s
                }
                // Touches inside the card must not reach the dismiss layer behind it.
                .pointerInput(Unit) { detectTapGestures { } },
            cornerRadius = 36.dp,
            interactive = false,
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppIcon(
                            app = app,
                            showLabel = true,
                            onClick = { onLaunch(app) },
                            onLongClick = { onRemove(app) },
                        )
                    }
                }
                Text(
                    "Long-press an app to take it out",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
