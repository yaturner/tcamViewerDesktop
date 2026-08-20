package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.das.tcamviewerdesktop.model.TempSample
import com.das.tcamviewerdesktop.util.CameraUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

private data class SavedChart(
    val savedTime: String,
    val isCelsius: Boolean,
    val primaryLabel: String,
    val samples: List<TempSample>,
)

private fun loadTempChart(file: File): SavedChart? = runCatching {
    val json = JSONObject(file.readText(StandardCharsets.US_ASCII))
    val isCelsius = json.optString("unit", "Celsius") == "Celsius"
    val savedTime = json.optString("saved_time", "")
    val primaryLabel = json.optString("primary_label", "Spot")
    val arr = json.getJSONArray("samples")
    val samples = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        TempSample(
            timestampMs = o.getLong("t"),
            spot = o.getDouble("spot").toFloat(),
            max = o.getDouble("max").toFloat(),
            min = o.getDouble("min").toFloat(),
        )
    }
    SavedChart(savedTime, isCelsius, primaryLabel, samples)
}.getOrNull()

/** Charts tab: browses saved `.tchart` temperature-history files under `~/tCamViewer/Charts`,
 *  grouped by date folder — ported from tcamViewer2's Android ChartsScreen.kt. */
@Composable
fun ChartsScreen(modifier: Modifier = Modifier) {
    var fileGroups by remember { mutableStateOf<List<Pair<String, List<File>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var sortAscending by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var browseFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterFromMillis by remember { mutableStateOf<Long?>(null) }
    var filterToMillis by remember { mutableStateOf<Long?>(null) }
    val dateFilterActive = filterFromMillis != null || filterToMillis != null

    LaunchedEffect(Unit) {
        isLoading = true
        fileGroups = withContext(Dispatchers.IO) {
            val chartsDir = File(CameraUtils.dataRoot, "Charts")
            val folderMap = mutableMapOf<String, MutableList<File>>()
            chartsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dateDir ->
                    val files = dateDir.listFiles { f -> f.extension == "tchart" } ?: return@forEach
                    if (files.isNotEmpty()) folderMap.getOrPut(dateDir.name) { mutableListOf() }.addAll(files)
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (selectedPaths.isEmpty()) "Charts" else "${selectedPaths.size} selected",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (selectedPaths.isNotEmpty()) {
                    IconButton(onClick = {
                        browseFiles = displayGroups.flatMap { it.second }.filter { it.absolutePath in selectedPaths }
                    }) { Icon(Icons.Default.Visibility, contentDescription = "View") }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
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
                    Text("No saved charts", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            ChartThumbnailGridCell(
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
            ChartBrowseWindow(
                files = browseFiles,
                onDismiss = { browseFiles = emptyList() },
                onDelete = { deletedFile ->
                    deletedFile.delete()
                    fileGroups = fileGroups.mapNotNull { (folder, files) ->
                        val remaining = files.filter { it.absolutePath != deletedFile.absolutePath }
                        if (remaining.isNotEmpty()) folder to remaining else null
                    }
                    browseFiles = browseFiles.filter { it.absolutePath != deletedFile.absolutePath }
                    selectedPaths = selectedPaths - deletedFile.absolutePath
                },
            )
        }

        if (showDeleteConfirm) {
            val n = selectedPaths.size
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete $n chart${if (n == 1) "" else "s"}?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedPaths.forEach { File(it).delete() }
                        fileGroups = fileGroups.mapNotNull { (folder, files) ->
                            val remaining = files.filter { it.absolutePath !in selectedPaths }
                            if (remaining.isNotEmpty()) folder to remaining else null
                        }
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

@Composable
private fun ChartThumbnailGridCell(file: File, isSelected: Boolean, onClick: () -> Unit) {
    var chart by remember(file) { mutableStateOf<SavedChart?>(null) }

    LaunchedEffect(file) {
        chart = withContext(Dispatchers.Default) { loadTempChart(file) }
    }

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxWidth().background(bgColor).clickable(onClick = onClick).padding(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black)) {
            val samples = chart?.samples
            if (samples != null) {
                MiniTempChart(samples = samples, modifier = Modifier.fillMaxSize().padding(4.dp))
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

/** Compact, label-free rendering of the three temperature series for grid thumbnails, where
 *  [TemperatureHistoryChart]'s legend/axis text would be too small to read. */
@Composable
private fun MiniTempChart(samples: List<TempSample>, modifier: Modifier = Modifier) {
    if (samples.size < 2) return
    val yMin = samples.minOf { minOf(it.spot, it.max, it.min) }
    val yMax = samples.maxOf { maxOf(it.spot, it.max, it.min) }
    val yRange = (yMax - yMin).takeIf { it > 0.01f } ?: 1f
    val tStart = samples.first().timestampMs
    val tEnd = samples.last().timestampMs
    val tRange = (tEnd - tStart).takeIf { it > 0L } ?: 1L

    Canvas(modifier = modifier) {
        fun xOf(t: Long) = (t - tStart).toFloat() / tRange * size.width
        fun yOf(v: Float) = size.height - ((v - yMin) / yRange * size.height)

        fun drawSeries(pick: (TempSample) -> Float, color: Color, strokeWidth: Float) {
            val path = Path()
            samples.forEachIndexed { i, s ->
                val px = xOf(s.timestampMs)
                val py = yOf(pick(s))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color = color, style = Stroke(width = strokeWidth))
        }
        drawSeries({ it.max }, Color(0xFFE53935), 2f)
        drawSeries({ it.spot }, Color(0xFF43A047), 3f)
        drawSeries({ it.min }, Color(0xFF1E88E5), 2f)
    }
}

@Composable
private fun ChartBrowseWindow(files: List<File>, onDismiss: () -> Unit, onDelete: (File) -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(files.size) {
        if (files.isEmpty()) onDismiss() else currentIndex = currentIndex.coerceAtMost(files.size - 1)
    }
    val file = files.getOrNull(currentIndex) ?: return
    var chart by remember(file) { mutableStateOf<SavedChart?>(null) }

    LaunchedEffect(file) {
        chart = null
        chart = withContext(Dispatchers.Default) { loadTempChart(file) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatFilename(file.name), style = MaterialTheme.typography.titleMedium)
                    Text(file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
            val currentChart = chart
            when {
                currentChart == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> TemperatureHistoryChart(
                    samples = currentChart.samples,
                    isCelsius = currentChart.isCelsius,
                    primaryLabel = currentChart.primaryLabel,
                )
            }
            if (files.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { currentIndex-- }, enabled = currentIndex > 0) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = "Previous")
                    }
                    Text("${currentIndex + 1} / ${files.size}", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { currentIndex++ }, enabled = currentIndex < files.size - 1) {
                        Icon(Icons.Default.NavigateNext, contentDescription = "Next")
                    }
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this chart?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = { showDeleteConfirm = false; onDelete(file) }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            )
        }
    }
}
