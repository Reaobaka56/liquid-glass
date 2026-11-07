package dev.glass.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.glass.launcher.AppEntry
import dev.glass.launcher.AppIconTheme
import dev.glass.launcher.IconCollection
import dev.glass.launcher.IconPackCandidate
import dev.glass.launcher.OriginalIconLibrary

@Composable
fun IconEditorSheet(
    apps: List<AppEntry>,
    collections: List<IconCollection>,
    iconPacks: List<IconPackCandidate>,
    selectedTheme: AppIconTheme,
    onSelectTheme: (AppIconTheme) -> Unit,
    onApplyIcon: (String, String) -> Unit,
    onApplyTheme: (AppIconTheme) -> Unit,
    onPickCustomIcon: (String) -> Unit,
    onApplyIconPack: (String, (Boolean, String?) -> Unit) -> Unit,
    onSaveCollection: (String, (Boolean, String?) -> Unit) -> Unit,
    onDuplicateCollection: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onRenameCollection: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onDeleteCollection: (String, (Boolean, String?) -> Unit) -> Unit,
    onApplyCollection: (String, (Boolean, String?) -> Unit) -> Unit,
    onExportCollection: (String) -> Unit,
    onImportCollection: () -> Unit,
    onResetIcon: (String) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selectedPackage by remember(apps) { mutableStateOf(apps.firstOrNull()?.packageName) }
    var selectedGlyph by remember(selectedPackage) {
        val selected = apps.firstOrNull { it.packageName == selectedPackage }
        mutableStateOf(
            selected?.iconAssignment?.substringBefore('|')?.takeUnless { it == "custom" || it == "pack" }
                ?: selected?.let { OriginalIconLibrary.suggestedGlyph(it.packageName, it.label) }
                ?: "generic",
        )
    }
    var showResetAll by remember { mutableStateOf(false) }
    var showIconPacks by remember { mutableStateOf(false) }
    var packError by remember { mutableStateOf<String?>(null) }
    var packApplying by remember { mutableStateOf(false) }
    var collectionName by remember { mutableStateOf("") }
    var collectionError by remember { mutableStateOf<String?>(null) }
    var collectionMessage by remember { mutableStateOf<String?>(null) }
    var collectionAction by remember { mutableStateOf<String?>(null) }
    var collectionActionSource by remember { mutableStateOf<String?>(null) }
    var collectionActionName by remember { mutableStateOf("") }
    var collectionActionError by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCollection by remember { mutableStateOf<String?>(null) }
    val selectedApp = apps.firstOrNull { it.packageName == selectedPackage }
    val filteredApps = remember(apps, search) {
        apps.filter { it.label.contains(search, ignoreCase = true) ||
            it.packageName.contains(search, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassPanel(
            Modifier.fillMaxSize().padding(10.dp),
            cornerRadius = 30.dp,
            interactive = false,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Icon studio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Changes affect this launcher only. Apps still open normally.",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Done", color = Color.White) }
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIconTheme.entries.forEach { theme ->
                        Text(
                            theme.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (theme == selectedTheme) Color.White.copy(alpha = 0.24f)
                                    else Color.White.copy(alpha = 0.08f),
                                )
                                .clickable { onSelectTheme(theme) }
                                .padding(horizontal = 3.dp, vertical = 10.dp),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onApplyTheme(selectedTheme) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Apply to all apps")
                    }
                    TextButton(onClick = { showResetAll = true }) {
                        Text("Reset all", color = Color.White)
                    }
                }
                TextButton(
                    onClick = {
                        packError = null
                        showIconPacks = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = iconPacks.isNotEmpty(),
                ) {
                    Text(
                        if (iconPacks.isEmpty()) "No compatible installed icon packs"
                        else "Import installed icon pack (${iconPacks.size})",
                        color = Color.White,
                    )
                }
                Text(
                    "Icon collections",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        decorationBox = { inner ->
                            Box {
                                if (collectionName.isEmpty()) Text("Collection name", color = Color.White.copy(alpha = 0.5f))
                                inner()
                            }
                        },
                    )
                    TextButton(onClick = {
                        collectionError = null
                        collectionMessage = null
                        onSaveCollection(collectionName) { success, message ->
                            if (success) {
                                collectionName = ""
                                collectionMessage = "Collection saved."
                            } else collectionError = message
                        }
                    }) { Text("Save current", color = Color.White) }
                    TextButton(onClick = onImportCollection) { Text("Restore", color = Color.White) }
                }
                collectionError?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
                collectionMessage?.let { Text(it, color = Color(0xFFB7F0C1), fontSize = 12.sp) }
                LazyColumn(Modifier.fillMaxWidth().height(104.dp)) {
                    items(collections.size, key = { collections[it].name }) { index ->
                        val collection = collections[index]
                        Column(
                            Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${collection.name} · ${collection.assignments.size} apps",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = {
                                    collectionMessage = null
                                    collectionError = null
                                    onApplyCollection(collection.name) { success, message ->
                                        if (success) collectionMessage = "Applied ${collection.name}."
                                        else collectionError = message
                                    }
                                }) { Text("Apply", color = Color.White) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    collectionAction = "Duplicate"
                                    collectionActionSource = collection.name
                                    collectionActionName = "${collection.name} copy"
                                    collectionActionError = null
                                }) { Text("Copy", color = Color.White) }
                                TextButton(onClick = {
                                    collectionAction = "Rename"
                                    collectionActionSource = collection.name
                                    collectionActionName = collection.name
                                    collectionActionError = null
                                }) { Text("Rename", color = Color.White) }
                                TextButton(onClick = { onExportCollection(collection.name) }) {
                                    Text("Export", color = Color.White)
                                }
                                TextButton(onClick = { pendingDeleteCollection = collection.name }) {
                                    Text("Delete", color = Color.White)
                                }
                            }
                        }
                    }
                }

                Text(
                    "Installed apps (${apps.size})",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (search.isEmpty()) Text("Search installed apps", color = Color.White.copy(alpha = 0.55f))
                            inner()
                        }
                    },
                )

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.weight(0.9f)) {
                        items(filteredApps.size, key = { filteredApps[it].packageName }) { index ->
                            val app = filteredApps[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (app.packageName == selectedPackage) Color.White.copy(alpha = 0.17f)
                                        else Color.Transparent,
                                    )
                                    .clickable {
                                        selectedPackage = app.packageName
                                        selectedGlyph = app.iconAssignment
                                            ?.substringBefore('|')
                                            ?.takeUnless { it == "custom" || it == "pack" }
                                            ?: OriginalIconLibrary.suggestedGlyph(app.packageName, app.label)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIconArtwork(
                                    original = app.icon,
                                    label = app.label,
                                    assignment = app.iconAssignment,
                                    customIcon = app.customIcon,
                                    size = 38.dp,
                                )
                                Text(
                                    app.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    Column(
                        Modifier.weight(1.1f).padding(start = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Before  →  After", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            selectedApp?.let { app ->
                                AppIconArtwork(
                                    app.icon,
                                    app.label,
                                    app.iconAssignment,
                                    app.customIcon,
                                    size = 52.dp,
                                )
                                AppIconArtwork(
                                    app.icon,
                                    app.label,
                                    "$selectedGlyph|${selectedTheme.name}",
                                    customIcon = app.customIcon,
                                    size = 52.dp,
                                )
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(OriginalIconLibrary.glyphs, key = { it.first }) { (glyph, name) ->
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (glyph == selectedGlyph) Color.White.copy(alpha = 0.18f)
                                            else Color.Transparent,
                                        )
                                        .clickable { selectedGlyph = glyph }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AppIconArtwork(
                                        original = selectedApp?.icon ?: apps.firstOrNull()?.icon ?: return@items,
                                        label = name,
                                        assignment = "$glyph|${selectedTheme.name}",
                                        size = 44.dp,
                                    )
                                    Text(
                                        name,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                selectedApp?.let { onApplyIcon(it.packageName, selectedGlyph) }
                            },
                            enabled = selectedApp != null,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Apply to selected app")
                        }
                        TextButton(
                            onClick = { selectedApp?.let { onPickCustomIcon(it.packageName) } },
                            enabled = selectedApp != null,
                        ) {
                            Text("Upload custom image", color = Color.White)
                        }
                        TextButton(
                            onClick = { selectedApp?.let { onResetIcon(it.packageName) } },
                            enabled = selectedApp?.iconAssignment != null,
                        ) {
                            Text("Restore original", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showResetAll) {
        AlertDialog(
            onDismissRequest = { showResetAll = false },
            title = { Text("Restore all original icons?") },
            text = { Text("All custom icon assignments in this launcher will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetAll()
                    showResetAll = false
                }) { Text("Reset all") }
            },
            dismissButton = {
                TextButton(onClick = { showResetAll = false }) { Text("Cancel") }
            },
        )
    }
    if (showIconPacks) {
        AlertDialog(
            onDismissRequest = { if (!packApplying) showIconPacks = false },
            title = { Text("Apply an installed icon pack") },
            text = {
                Column {
                    Text("Only apps with a compatible appfilter.xml are listed.")
                    if (packError != null) {
                        Text(packError.orEmpty(), color = Color(0xFFFFB4AB))
                    }
                    LazyColumn {
                        items(iconPacks.size, key = { iconPacks[it].packageName }) { index ->
                            val pack = iconPacks[index]
                            TextButton(
                                enabled = !packApplying,
                                onClick = {
                                    packApplying = true
                                    packError = null
                                    onApplyIconPack(pack.packageName) { success, message ->
                                        packApplying = false
                                        if (success) showIconPacks = false else packError = message
                                    }
                                },
                            ) {
                                Text("${pack.label}  ·  ${pack.packageName}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconPacks = false }, enabled = !packApplying) {
                    Text("Close")
                }
            },
        )
    }
    if (collectionAction != null && collectionActionSource != null) {
        AlertDialog(
            onDismissRequest = {
                collectionAction = null
                collectionActionSource = null
            },
            title = { Text("${collectionAction} icon collection") },
            text = {
                Column {
                    BasicTextField(
                        value = collectionActionName,
                        onValueChange = { collectionActionName = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    if (collectionActionError != null) Text(collectionActionError.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val source = collectionActionSource ?: return@TextButton
                    val action = collectionAction
                    val done: (Boolean, String?) -> Unit = { success, message ->
                        if (!success) collectionActionError = message
                        else {
                            collectionMessage = "Collection updated."
                            collectionAction = null
                            collectionActionSource = null
                        }
                    }
                    when (action) {
                        "Duplicate" -> onDuplicateCollection(source, collectionActionName, done)
                        "Rename" -> onRenameCollection(source, collectionActionName, done)
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    collectionAction = null
                    collectionActionSource = null
                }) { Text("Cancel") }
            },
        )
    }
    pendingDeleteCollection?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCollection = null },
            title = { Text("Delete “$name”?") },
            text = { Text("This removes the saved collection. It does not change the icons currently applied.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCollection(name) { success, message ->
                        if (!success) collectionError = message
                        else collectionMessage = "Collection deleted."
                        pendingDeleteCollection = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCollection = null }) { Text("Cancel") }
            },
        )
    }
}
