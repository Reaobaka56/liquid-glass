package dev.glass.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.AppEntry
import dev.glass.launcher.AppRepository
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalReduceMotion
import kotlinx.coroutines.launch

/** App icon size, set in glass settings. */
val LocalIconSize = compositionLocalOf<Dp> { 56.dp }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    recentApps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongClickApp: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var query by remember { mutableStateOf("") }
    var showUsagePrompt by remember { mutableStateOf(false) }

    // Prefix-first then substring search matching
    val filteredApps by remember(apps, query) {
        derivedStateOf {
            if (query.isBlank()) {
                apps
            } else {
                val q = query.trim()
                val prefixMatches = apps.filter { it.label.startsWith(q, ignoreCase = true) }
                val substringMatches = apps.filter {
                    !it.label.startsWith(q, ignoreCase = true) && it.label.contains(q, ignoreCase = true)
                }
                prefixMatches + substringMatches
            }
        }
    }

    // Alphabetical Grouping
    val sections by remember(filteredApps) {
        derivedStateOf {
            filteredApps.groupBy {
                val char = it.label.firstOrNull()?.uppercaseChar() ?: '#'
                if (char.isLetter()) char.toString() else "#"
            }
        }
    }

    val availableLetters = remember(sections) {
        sections.keys.sorted()
    }

    // Map section letters to grid item index
    val letterGridIndices by remember(sections, recentApps, query) {
        derivedStateOf {
            val map = mutableMapOf<String, Int>()
            var currentIndex = 0
            if (query.isBlank() && recentApps.isNotEmpty()) {
                currentIndex += 1 // Recent section header
                currentIndex += recentApps.size // Recent items
            }
            sections.keys.sorted().forEach { letter ->
                map[letter] = currentIndex
                currentIndex += 1 // Letter header
                currentIndex += sections[letter]?.size ?: 0 // App items
            }
            map
        }
    }

    if (showUsagePrompt) {
        AlertDialog(
            onDismissRequest = { showUsagePrompt = false },
            title = { Text("Usage Access Permission", color = Color.White) },
            text = {
                Text(
                    "To show your most frequently and recently used apps, please grant usage access in system settings.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUsagePrompt = false
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Grant Access", color = Color(0xFF64B5F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsagePrompt = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E24),
        )
    }

    GlassPanel(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { keyboard?.hide() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(8.dp),
        cornerRadius = 32.dp,
        material = LocalGlassMaterial.current.forOverlay(),
        interactive = false, // Full-screen: no finger bulge or squish while scrolling.
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                // Search Input Field
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search apps...", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(12.dp))

                if (filteredApps.isEmpty()) {
                    // Empty Search State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No apps match \"$query\"",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    // Main Grid
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(bottom = 24.dp, end = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Recently Used Row (Only when not searching)
                        if (query.isBlank() && recentApps.isNotEmpty()) {
                            item(span = { GridItemSpan(4) }, key = "header_recents") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Recently Used",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (!AppRepository.hasUsageStatsPermission(context)) {
                                        Text(
                                            text = "Grant Access",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { showUsagePrompt = true }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            items(recentApps, key = { "recent_${it.packageName}" }) { app ->
                                AppIcon(
                                    app = app,
                                    showLabel = true,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { onLongClickApp(app) },
                                )
                            }
                        }

                        // Alphabetical Sections
                        availableLetters.forEach { letter ->
                            val sectionApps = sections[letter] ?: emptyList()

                            item(span = { GridItemSpan(4) }, key = "header_$letter") {
                                Text(
                                    text = letter,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }

                            items(sectionApps, key = { it.packageName }) { app ->
                                AppIcon(
                                    app = app,
                                    showLabel = true,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { onLongClickApp(app) },
                                )
                            }
                        }
                    }
                }
            }

            // Fast-Scroll Letter Index Bar on Edge
            if (query.isBlank() && availableLetters.isNotEmpty()) {
                val alphabetList = remember { ('A'..'Z').map { it.toString() } }
                var activeIndexLetter by remember { mutableStateOf<String?>(null) }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .pointerInput(availableLetters) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val itemHeight = size.height / alphabetList.size
                                    val idx = (offset.y / itemHeight)
                                        .toInt()
                                        .coerceIn(0, alphabetList.size - 1)
                                    val targetLetter = alphabetList[idx]
                                    if (targetLetter != activeIndexLetter) {
                                        activeIndexLetter = targetLetter
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        letterGridIndices[targetLetter]?.let { gridIdx ->
                                            scope.launch { gridState.scrollToItem(gridIdx) }
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    val itemHeight = size.height / alphabetList.size
                                    val idx = (change.position.y / itemHeight)
                                        .toInt()
                                        .coerceIn(0, alphabetList.size - 1)
                                    val targetLetter = alphabetList[idx]
                                    if (targetLetter != activeIndexLetter) {
                                        activeIndexLetter = targetLetter
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        letterGridIndices[targetLetter]?.let { gridIdx ->
                                            scope.launch { gridState.scrollToItem(gridIdx) }
                                        }
                                    }
                                },
                                onDragEnd = { activeIndexLetter = null },
                                onDragCancel = { activeIndexLetter = null },
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    alphabetList.forEach { letter ->
                        val isAvailable = availableLetters.contains(letter)
                        Text(
                            text = letter,
                            color = if (isAvailable) Color.White else Color.White.copy(alpha = 0.25f),
                            fontSize = 10.sp,
                            fontWeight = if (isAvailable) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 1.dp, horizontal = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Springy press: shrinks fast, then rebounds with a little overshoot on release.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
        label = "iconPress",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(6.dp)
            .semantics {
                role = Role.Button
                contentDescription = app.label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(LocalIconSize.current),
        )
        if (showLabel) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
