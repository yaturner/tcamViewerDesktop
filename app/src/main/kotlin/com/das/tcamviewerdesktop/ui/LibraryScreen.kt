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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.das.tcamviewerdesktop.util.buildCompositeImage
import com.das.tcamviewerdesktop.util.saveExportedPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/** Library tab: browses saved `.tjsn` frames, `.tmjsn` recordings, and `.tltjsn` time lapses
 *  under `~/tCamViewer/{Pictures,Movies}`, grouped by date folder, with multi-select delete, a
 *  date-range filter, a full-size browse view (Export to composite PNG), and video playback for
 *  recordings/time lapses. Ported from tcamViewer2's Android LibraryScreen — no MP4 export/share
 *  (no portable desktop video encoder wired up). */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier, onShowMessage: (String) -> Unit = {}) {
    var fileGroups by remember { mutableStateOf<List<Pair<String, List<File>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var sortAscending by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var browseFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var playFile by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterFromMillis by remember { mutableStateOf<Long?>(null) }
    var filterToMillis by remember { mutableStateOf<Long?>(null) }
    val dateFilterActive = filterFromMillis != null || filterToMillis != null

    LaunchedEffect(Unit) {
        isLoading = true
        fileGroups = withContext(Dispatchers.IO) {
            val picturesDir = File(CameraUtils.dataRoot, "Pictures")
            val moviesDir = File(CameraUtils.dataRoot, "Movies")
            val folderMap = mutableMapOf<String, MutableList<File>>()
            for (rootDir in listOf(picturesDir, moviesDir)) {
                rootDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.forEach { dateDir ->
                        val files = dateDir.listFiles { f ->
                            // "mtjsn" is the legacy extension (renamed to "tmjsn" for new
                            // recordings); still recognized so existing files stay visible.
                            f.extension == "tjsn" || f.extension == "tmjsn" || f.extension == "mtjsn" ||
                                f.extension == "tltjsn"
                        } ?: return@forEach
                        if (files.isNotEmpty()) folderMap.getOrPut(dateDir.name) { mutableListOf() }.addAll(files)
                    }
            }
            folderMap.entries
                .sortedByDescending { it.key }
                .map { (folder, files) -> folder to files.sortedByDescending { it.name } }
        }
        isLoading = false
    }

    val displayGroups = remember(fileGroups, sortAscending, filterFromMillis, filterToMillis) {
        val filtered = fileGroups.filter { (folder, _) ->
            val folderMillis = parseFolderDateMillis(folder) ?: return@filter true
            (filterFromMillis == null || folderMillis >= filterFromMillis!!) &&
                (filterToMillis == null || folderMillis <= filterToMillis!!)
        }
        val sortedFolders = if (sortAscending) filtered.sortedBy { it.first } else filtered.sortedByDescending { it.first }
        sortedFolders.map { (folder, files) ->
            folder to if (sortAscending) files.sortedBy { it.name } else files.sortedByDescending { it.name }
        }
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (selectedPaths.isEmpty()) "Library" else "${selectedPaths.size} selected",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (selectedPaths.isNotEmpty()) {
                    IconButton(onClick = {
                        browseFiles = displayGroups.flatMap { it.second }.filter { it.absolutePath in selectedPaths }
                    }) { Icon(Icons.Default.Visibility, contentDescription = "Browse selected") }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                    }
                    TextButton(onClick = { selectedPaths = emptySet() }) { Text("Clear") }
                } else if (fileGroups.isNotEmpty()) {
                    TextButton(onClick = { selectedPaths = allPaths }) { Text("Select all") }
                }
                IconButton(onClick = { showFilterDialog = true }, enabled = fileGroups.isNotEmpty()) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = if (dateFilterActive) "Date filter (active)" else "Filter by date",
                        tint = if (dateFilterActive && fileGroups.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = fileGroups.isNotEmpty()) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Sort ascending") }, onClick = { sortAscending = true; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Sort descending") }, onClick = { sortAscending = false; menuExpanded = false })
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                displayGroups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No saved files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), modifier = Modifier.fillMaxSize()) {
                    displayGroups.forEach { (folderName, files) ->
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
                                onPlay = { playFile = file },
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
                onShowMessage = onShowMessage,
            )
        }

        playFile?.let { file ->
            VideoPlayerWindow(
                file = file,
                onDismiss = { playFile = null },
                onDelete = {
                    file.delete()
                    removeFromGroups(setOf(file.absolutePath))
                    selectedPaths = selectedPaths - file.absolutePath
                    playFile = null
                },
                onShowMessage = onShowMessage,
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

        if (showFilterDialog) {
            DateFilterDialog(
                fromMillis = filterFromMillis,
                toMillis = filterToMillis,
                onApply = { from, to ->
                    filterFromMillis = from
                    filterToMillis = to
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false },
            )
        }
    }
}

/** Date-range filter dialog shared in spirit with the Android app's — reused by [ChartsScreen]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DateFilterDialog(
    fromMillis: Long?,
    toMillis: Long?,
    onApply: (from: Long?, to: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingFrom by remember { mutableStateOf(fromMillis) }
    var pendingTo by remember { mutableStateOf(toMillis) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Only show files saved within this range.", style = MaterialTheme.typography.bodySmall)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pendingFrom?.let { dateFmt.format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showFromPicker = true })
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pendingTo?.let { dateFmt.format(Date(it)) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showToPicker = true })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(pendingFrom, pendingTo) }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = { pendingFrom = null; pendingTo = null }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = pendingFrom)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { pendingFrom = state.selectedDateMillis; showFromPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = pendingTo)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { pendingTo = state.selectedDateMillis; showToPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ThumbnailGridCell(file: File, isSelected: Boolean, onClick: () -> Unit, onPlay: () -> Unit) {
    var thumbnail by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    val isVideo = file.extension == "tmjsn" || file.extension == "mtjsn" || file.extension == "tltjsn"

    LaunchedEffect(file) {
        thumbnail = withContext(Dispatchers.Default) {
            runCatching {
                if (isVideo) {
                    val json = readFirstMtjsnFrame(file) ?: return@runCatching null
                    ImageDto.create(json, null).bitmap?.toComposeImageBitmap()
                } else {
                    ImageDto.create(file.absolutePath, null).bitmap?.toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier.fillMaxWidth().background(bgColor)
            .clickable(onClick = { if (isVideo) onPlay() else onClick() })
            .padding(4.dp),
    ) {
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
            if (file.extension == "tmjsn" || file.extension == "mtjsn") {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
                )
            }
            if (file.extension == "tltjsn") {
                Icon(
                    Icons.Default.Timelapse,
                    contentDescription = "Time lapse",
                    tint = Color.Yellow,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
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
private fun BrowseOverlay(
    files: List<File>,
    onDismiss: () -> Unit,
    onDelete: (File) -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var currentIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(files.size) {
        if (files.isEmpty()) onDismiss() else currentIndex = currentIndex.coerceAtMost(files.size - 1)
    }
    val file = files.getOrNull(currentIndex) ?: return

    var dto by remember { mutableStateOf<ImageDto?>(null) }
    val tempUnit by settingsManager.temperatureUnitFlow.collectAsState()
    val isCelsius = tempUnit == "Celsius"
    val spotmeterEnabled by settingsManager.spotmeterFlow.collectAsState()

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
                IconButton(
                    enabled = dto?.bitmap != null && !isExporting,
                    onClick = {
                        val currentDto = dto ?: return@IconButton
                        coroutineScope.launch {
                            isExporting = true
                            val composite = withContext(Dispatchers.Default) {
                                buildCompositeImage(currentDto, isCelsius, spotmeterEnabled)
                            }
                            val name = file.nameWithoutExtension.removePrefix("img_")
                            val saved = withContext(Dispatchers.IO) { saveExportedPng(composite, name) }
                            isExporting = false
                            onShowMessage(if (saved != null) "Exported to ${saved.path}" else "Export failed")
                        }
                    },
                ) { Icon(Icons.Default.SaveAlt, contentDescription = "Export PNG", tint = Color.White) }
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
