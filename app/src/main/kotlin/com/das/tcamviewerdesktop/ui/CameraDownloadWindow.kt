package com.das.tcamviewerdesktop.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.das.tcamviewerdesktop.cameraService
import com.das.tcamviewerdesktop.util.DeviceFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

private val log = Logger.getLogger("CameraDownloadWindow")

/**
 * Full-screen browser for the files on a full tCam's micro-SD card — ported from tcamViewer2's
 * Android CameraDownloadWindow. Shows the folder list first, then the files in the chosen folder.
 * Downloads go to the Library's Pictures folder, under a folder with the camera's own folder name.
 */
@Composable
fun CameraDownloadWindow(onDismiss: () -> Unit, onSaved: () -> Unit, onShowMessage: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val root = remember { DeviceFiles.picturesRoot() }

    var folders by remember { mutableStateOf<List<String>?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<String>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun closeFolder() {
        openFolder = null
        files = null
        selected = emptySet()
    }

    fun loadFolder(name: String) {
        openFolder = name
        files = null
        selected = emptySet()
        scope.launch {
            files = cameraService.listDirectory(name)?.filter(DeviceFiles::isSafeName)?.sorted() ?: emptyList()
        }
    }

    /** Downloads [names] from [folder]. Already-imported files are skipped, not re-downloaded.
     *  Progress and cancel are driven by the modal dialog below, not by whichever list screen
     *  happens to be on top — a folder's own "download all" icon starts this without opening
     *  that folder at all. */
    fun download(folder: String, names: List<String>) {
        job = scope.launch {
            var saved = 0
            var skipped = 0
            var failed = 0
            // Paused once for the whole batch, not once per file — see pauseStreamingForBatch's
            // doc for why (fewer stream_on/off round trips, less chance of the idle-while-
            // streaming watchdog misfiring on a lost ack partway through a long download).
            val wasStreaming = cameraService.pauseStreamingForBatch()
            try {
                for ((index, name) in names.withIndex()) {
                    if (!cameraService.isConnected) {
                        failed += names.size - index
                        log.fine("download $folder: disconnected at $index/${names.size}, stopping")
                        break
                    }
                    progress = index to names.size
                    try {
                        val json = cameraService.fetchFile(folder, name)
                        if (json == null || !json.has("radiometric")) {
                            failed++
                            log.fine("download $folder/$name: no data (got=${json != null})")
                        } else if (DeviceFiles.saveImage(root, folder, name, json.toString()) ==
                            DeviceFiles.SaveResult.SAVED
                        ) {
                            saved++
                        } else {
                            skipped++
                        }
                    } catch (e: IOException) {
                        failed++
                        log.log(Level.FINE, "download $folder/$name: IOException", e)
                    }
                }
            } finally {
                progress = null
                job = null
                if (wasStreaming) cameraService.resumeStreamingAfterBatch()
                onShowMessage("Saved $saved, already imported $skipped, failed $failed")
                selected = emptySet()
                if (saved > 0) onSaved()
            }
        }
    }

    fun downloadFolder(name: String) {
        scope.launch {
            val names = cameraService.listDirectory(name)
                ?.filter(DeviceFiles::isDownloadableImage)
                ?.sorted()
                .orEmpty()
            if (names.isEmpty()) {
                onShowMessage("No images in $name")
            } else {
                download(name, names)
            }
        }
    }

    LaunchedEffect(Unit) {
        folders = cameraService.listDirectory("/")?.filter(DeviceFiles::isSafeName)?.sortedDescending()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.97f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Steps out of a folder, or closes the window from the folder list. While
                // downloading, this cancels the download instead of leaving partway through.
                IconButton(onClick = {
                    when {
                        job != null -> job?.cancel()
                        openFolder != null -> closeFolder()
                        else -> onDismiss()
                    }
                }) {
                    if (openFolder != null) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    } else {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Text(openFolder ?: "Camera files", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val current = openFolder
                if (current == null) {
                    FolderList(folders = folders, onOpen = { loadFolder(it) }, onDownloadAll = { downloadFolder(it) })
                } else {
                    FileList(
                        files = files,
                        selected = selected,
                        onToggle = { name -> selected = if (name in selected) selected - name else selected + name },
                    )
                }
            }

            // The "Download N" selection button only applies within an open folder, and only
            // while nothing is already downloading. Live progress and cancel are a separate modal
            // (below) so they never compete with either list's own layout.
            if (openFolder != null && job == null) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    Button(enabled = selected.isNotEmpty(), onClick = { download(openFolder!!, selected.sorted()) }) {
                        Text("Download ${selected.size}")
                    }
                }
            }
        }
    }

    // A separate modal, not a bottom row, so it never overlaps or competes with either list's own
    // layout (folder list or file list) and works the same regardless of which is showing.
    if (job != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading") },
            text = {
                Column {
                    progress?.let { (done, total) ->
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Downloading ${done + 1} of $total")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { job?.cancel() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FolderList(folders: List<String>?, onOpen: (String) -> Unit, onDownloadAll: (String) -> Unit) {
    when {
        folders == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        folders.isEmpty() -> Text(
            "No folders found. Check that the camera has a micro-SD card inserted.",
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )

        else -> LazyColumn {
            items(folders) { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(name) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                    Text(name, color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    IconButton(onClick = { onDownloadAll(name) }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Download all images in $name", tint = Color.White)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FileList(files: List<String>?, selected: Set<String>, onToggle: (String) -> Unit) {
    when {
        files == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        files.isEmpty() -> Text("This folder is empty.", color = Color.White, modifier = Modifier.padding(16.dp))

        else -> LazyColumn {
            items(files) { name ->
                val downloadable = DeviceFiles.isDownloadableImage(name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = downloadable) { onToggle(name) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = name in selected, onCheckedChange = null, enabled = downloadable)
                    Text(
                        if (downloadable) name else "$name (video, not supported yet)",
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
