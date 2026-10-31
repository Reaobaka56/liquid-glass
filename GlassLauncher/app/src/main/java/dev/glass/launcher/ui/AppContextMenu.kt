package dev.glass.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.AppEntry
import dev.glass.launcher.AppRepository
import dev.glass.launcher.AppShortcut
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalReduceMotion

@Composable
fun AppContextMenu(
    app: AppEntry,
    shortcuts: List<AppShortcut>,
    isInHome: Boolean,
    isInDock: Boolean,
    onToggleHome: () -> Unit,
    onToggleDock: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onEnterEditMode: () -> Unit,
    onDismiss: () -> Unit,
    onLaunchShortcut: (AppShortcut) -> Unit,
) {
    val context = LocalContext.current
    val isSystem = AppRepository.isSystemApp(context, app.packageName)

    // Pop-in: the icon and menu spring up from slightly smaller while the scrim fades in.
    val reduceMotion = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) pop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.45f * pop.value.coerceIn(0f, 1f)))
            }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .graphicsLayer {
                    val s = 0.85f + 0.15f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = pop.value.coerceIn(0f, 1f)
                },
        ) {
            // Lifted Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(1.15f)
                    .padding(bottom = 16.dp),
            ) {
                AppIconArtwork(
                    original = app.icon,
                    label = app.label,
                    assignment = app.iconAssignment,
                    customIcon = app.customIcon,
                    size = 64.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Glass Menu Box
            GlassPanel(
                modifier = Modifier
                    .width(280.dp)
                    .pointerInput(Unit) { detectTapGestures { /* consume */ } },
                cornerRadius = 28.dp,
                material = LocalGlassMaterial.current.forOverlay(),
                interactive = false,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    // Shortcuts Section
                    if (shortcuts.isNotEmpty()) {
                        shortcuts.forEach { shortcut ->
                            ContextMenuItem(
                                label = shortcut.shortLabel,
                                subLabel = shortcut.longLabel,
                                onClick = {
                                    onDismiss()
                                    onLaunchShortcut(shortcut)
                                },
                            )
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }

                    // Toggle Home
                    ContextMenuItem(
                        label = if (isInHome) "Remove from Home" else "Add to Home",
                        onClick = {
                            onToggleHome()
                            onDismiss()
                        },
                    )

                    // Toggle Dock
                    ContextMenuItem(
                        label = if (isInDock) "Remove from Dock" else "Add to Dock",
                        onClick = {
                            onToggleDock()
                            onDismiss()
                        },
                    )

                    // Edit Home Screen
                    ContextMenuItem(
                        label = "Edit Home Screen",
                        onClick = {
                            onDismiss()
                            onEnterEditMode()
                        },
                    )

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )

                    // App Info
                    ContextMenuItem(
                        label = "App info",
                        onClick = {
                            onDismiss()
                            onOpenAppInfo()
                        },
                    )

                    // Uninstall (Only if non-system)
                    if (!isSystem) {
                        ContextMenuItem(
                            label = "Uninstall",
                            textColor = Color(0xFFFF6B6B),
                            onClick = {
                                onDismiss()
                                onUninstall()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    label: String,
    subLabel: String? = null,
    textColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp) // >= 48dp touch target
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
