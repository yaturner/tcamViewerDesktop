package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.ImageDto
import com.das.tcamviewerdesktop.settingsManager
import com.das.tcamviewerdesktop.util.CameraUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Library tab: browses saved `.tjsn` single-frame captures under `~/tCamViewer/Pictures`,
 *  grouped by date folder, with multi-select delete and a full-size browse view. Recording
 *  (`.mtjsn`) / time-lapse (`.tltjsn`) playback isn't ported yet — see the README. */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    var fileGroups by remember { mutableStateOf<List<Pair<String, List<File>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var browseFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        fileGroups = withContext(Dispatchers.IO) {
            val picturesDir = File(CameraUtils.dataRoot, "Pictures")
            val folderMap = mutableMapOf<String, MutableList<File>>()
            picturesDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dateDir ->
                    val files = dateDir.listFiles { f -> f.extension == "tjsn" } ?: return@forEach
                    if (files.isNotEmpty()) folderMap.getOrPut(dateDir.name) { mutableListOf() }.addAll(files)
                }
            folderMap.entries
                .sortedByDescending { it.key }
                .map { (folder, files) -> folder to files.sortedByDescending { it.name } }
        }
        isLoading = false
    }

    val allPaths = remember(fileGroups) { fileGroups.flatMap { it.second }.map { it.absolutePath }.toSet() }

    fun removeFromGroups(paths: Set<String>) {
        fileGroups = fileGroups.mapNotNull { (folder, files) ->
            val remaining = files.filter { it.absolutePath !in paths }
            if (remaining.isNotEmpty()) folder to remaining else null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (selectedPaths.isEmpty()) "Library" else "${selectedPaths.size} selected",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (selectedPaths.isNotEmpty()) {
                    IconButton(onClick = {
                        browseFiles = fileGroups.flatMap { it.second }.filter { it.absolutePath in selectedPaths }
                    }) { Icon(Icons.Default.Visibility, contentDescription = "Browse selected") }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                    TextButton(onClick = { selectedPaths = emptySet() }) { Text("Clear") }
                } else if (fileGroups.isNotEmpty()) {
                    TextButton(onClick = { selectedPaths = allPaths }) { Text("Select all") }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                fileGroups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No saved files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), modifier = Modifier.fillMaxSize()) {
                    fileGroups.forEach { (folderName, files) ->
                        item(key = "header_$folderName", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                formatDateFolder(folderName),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(files, key = { it.absolutePath }) { file ->
                            val isSelected = file.absolutePath in selectedPaths
                            ThumbnailGridCell(
                                file = file,
                                isSelected = isSelected,
                                onClick = {
                                    selectedPaths = if (isSelected) selectedPaths - file.absolutePath else selectedPaths + file.absolutePath
                                },
                            )
                        }
                    }
                }
            }
        }

        if (browseFiles.isNotEmpty()) {
            BrowseOverlay(
                files = browseFiles,
                onDismiss = { browseFiles = emptyList() },
                onDelete = { deleted ->
                    deleted.delete()
                    removeFromGroups(setOf(deleted.absolutePath))
                    browseFiles = browseFiles.filter { it.absolutePath != deleted.absolutePath }
                    selectedPaths = selectedPaths - deleted.absolutePath
                },
            )
        }

        if (showDeleteConfirm) {
            val n = selectedPaths.size
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete $n file${if (n == 1) "" else "s"}?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedPaths.forEach { File(it).delete() }
                        removeFromGroups(selectedPaths)
                        selectedPaths = emptySet()
                        showDeleteConfirm = false
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ThumbnailGridCell(file: File, isSelected: Boolean, onClick: () -> Unit) {
    var thumbnail by remember(file) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file) {
        thumbnail = withContext(Dispatchers.Default) {
            runCatching { ImageDto.create(file.absolutePath, null).bitmap?.toComposeImageBitmap() }.getOrNull()
        }
    }

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxWidth().background(bgColor).clickable(onClick = onClick).padding(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                )
            }
        }
        Text(
            formatFilename(file.name),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun BrowseOverlay(files: List<File>, onDismiss: () -> Unit, onDelete: (File) -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(files.size) {
        if (files.isEmpty()) onDismiss() else currentIndex = currentIndex.coerceAtMost(files.size - 1)
    }
    val file = files.getOrNull(currentIndex) ?: return

    var dto by remember { mutableStateOf<ImageDto?>(null) }
    val tempUnit by settingsManager.temperatureUnitFlow.collectAsState()
    val isCelsius = tempUnit == "Celsius"

    LaunchedEffect(file) {
        dto = null
        dto = withContext(Dispatchers.Default) { runCatching { ImageDto.create(file.absolutePath, null) }.getOrNull() }
    }

    val imageBitmap = remember(dto?.bitmap) { dto?.bitmap?.toComposeImageBitmap() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.97f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                Text(formatFilename(file.name), color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    dto == null -> CircularProgressIndicator()
                    imageBitmap != null -> {
                        val currentDto = dto!!
                        val hasThermal = currentDto.tLinearEnabled != 0
                        val scale = if (currentDto.tLinearResolution == 0) 10f else 100f
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (hasThermal) {
                                Text(
                                    formatTemp(currentDto.spotmeterMean, scale, isCelsius),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier.size((Constants.IMAGE_WIDTH * 4).dp, (Constants.IMAGE_HEIGHT * 4).dp),
                                contentScale = ContentScale.FillBounds,
                            )
                            if (hasThermal) {
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Text("Max: ${formatTemp(currentDto.maxTemperature, scale, isCelsius)}", color = Color.White)
                                    Text("Min: ${formatTemp(currentDto.minTemperature, scale, isCelsius)}", color = Color.White)
                                }
                            }
                        }
                    }
                    else -> Text("Could not load image", color = Color.White)
                }
            }
            if (files.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { currentIndex-- }, enabled = currentIndex > 0) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = "Previous", tint = Color.White)
                    }
                    Text("${currentIndex + 1} / ${files.size}", color = Color.White)
                    IconButton(onClick = { currentIndex++ }, enabled = currentIndex < files.size - 1) {
                        Icon(Icons.Default.NavigateNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this file?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete(file)
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            )
        }
    }
}

private fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): String {
    val tempC = rawValue / scale - 273.15f
    return if (isCelsius) "%.1f°C".format(tempC) else "%.1f°F".format(tempC * 9f / 5f + 32f)
}

/** MM_dd_yyyy → "June 25, 2026" */
private fun formatDateFolder(name: String): String {
    val parts = name.split("_")
    if (parts.size != 3) return name
    val monthNames = arrayOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val month = parts[0].toIntOrNull()?.let { monthNames.getOrNull(it) } ?: return name
    return "$month ${parts[1]}, ${parts[2]}"
}

/** img_HH_mm_ss.tjsn → "HH:mm:ss" */
private fun formatFilename(name: String): String {
    val base = name.removeSuffix(".tjsn").removePrefix("img_")
    val parts = base.split("_")
    return if (parts.size == 3) "${parts[0]}:${parts[1]}:${parts[2]}" else name
}
