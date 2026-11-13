@file:OptIn(ExperimentalLayoutApi::class)

package com.social_downloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.social_downloader.data.DownloadItem
import com.social_downloader.data.DownloadStatus
import com.social_downloader.download.MediaSaver
import com.social_downloader.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Queue

@Composable
fun QueueScreen(
    items: List<DownloadItem>,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val paused by vm.downloads.queuePaused.collectAsStateWithLifecycle()
    val pending = items.filter { it.status.isPending }.sortedWith(compareBy<DownloadItem>{it.queuePriority}.thenBy{it.createdAt})
    if (pending.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.CloudDownload,
            title = "Nothing in the queue",
            subtitle = "Paste or share a link and your downloads will show up here.",
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Total speed: ${Format.speed(com.social_downloader.download.QueueMetrics.speed(pending)).ifEmpty { "—" }}")
            TextButton(onClick={vm.downloads.pauseQueue(!paused)}) { Text(if(paused) "Resume queue" else "Pause waiting downloads") }
            if(paused) Text("Queue paused. Active downloads continue.",style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pending.size} in progress",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.downloads.cancelAll() }) { Text("Cancel all") }
            }
        }
        items(pending, key = { it.id }) { item ->
            DownloadCard(
                item = item,
                onCancel = { vm.downloads.cancel(item.id) },
                onNext = { vm.downloads.next(item.id) },
            )
        }
    }
}

// ------------------------------------------------------------------ History

@Composable
fun HistoryScreen(
    items: List<DownloadItem>,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var failuresOnly by remember { mutableStateOf(false) }
    val allFinished = items.filter { !it.status.isPending }
    val finished = allFinished.filter { (!failuresOnly || it.status == DownloadStatus.FAILED) &&
        (it.title.contains(search,true) || it.platform.label.contains(search,true)) }.sortedByDescending { maxOf(it.finishedAt,it.createdAt) }
    var toDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    if (allFinished.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.History,
            title = "No history yet",
            subtitle = "Finished, failed and canceled downloads are listed here.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),label={Text("Search history")},singleLine=true)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(failuresOnly,{failuresOnly=!failuresOnly},label={Text("Failed only")})
                TextButton(onClick=vm.downloads::retryFailed){Text("Retry all failed")}
                TextButton(onClick=vm.downloads::clearCanceled){Text("Clear canceled")}
            }
            if(finished.isEmpty()) Text("No matching history.")
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${finished.size} item${if (finished.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear list")
                }
            }
        }
        items(finished, key = { it.id }) { item ->
            DownloadCard(
                item = item,
                onOpen = { vm.openFile(context, item) },
                onShare = { vm.shareFile(context, item) },
                onRetry = { vm.downloads.retry(item.id) },
                onDelete = { toDelete = item },
                onAgain = { vm.useSource(item) },
                onSource = { vm.openSource(context,item) },
            )
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove from history?") },
            text = {
                Text(
                    if (item.status == DownloadStatus.COMPLETED)
                        "You can also delete the video file from your Downloads folder."
                    else "This only removes the entry from the list.",
                )
            },
            confirmButton = {
                if (item.status == DownloadStatus.COMPLETED) {
                    Button(onClick = { vm.downloads.remove(item.id, deleteFile = true); toDelete = null }) {
                        Text("Delete file too")
                    }
                } else {
                    Button(onClick = { vm.downloads.remove(item.id, deleteFile = false); toDelete = null }) {
                        Text("Remove")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { toDelete = null }) { Text("Cancel") }
                    if (item.status == DownloadStatus.COMPLETED) {
                        TextButton(onClick = { vm.downloads.remove(item.id, deleteFile = false); toDelete = null }) {
                            Text("Keep file")
                        }
                    }
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history list?") },
            text = { Text("Your downloaded files stay in the Downloads folder.") },
            confirmButton = {
                Button(onClick = { vm.downloads.clearFinished(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ Shared card

@Composable
fun DownloadCard(
    item: DownloadItem,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onAgain: (() -> Unit)? = null,
    onSource: (() -> Unit)? = null,
) {
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(if(LocalExperience.current.compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if(LocalExperience.current.compact) 4.dp else 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumbnail(
                    url = item.thumbnail,
                    modifier = Modifier.width(112.dp).height(64.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.platform.label} · ${item.qualityLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (item.status) {
                DownloadStatus.QUEUED -> StatusText("Waiting in queue…")
                DownloadStatus.DOWNLOADING -> {
                    if (item.progress < 0f) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        StatusText(item.statusDetail ?: "Starting…")
                    } else {
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                        StatusText(
                            listOfNotNull(
                                "${(item.progress * 100).toInt()}%",
                                Format.speed(item.speedBytes).takeIf { it.isNotEmpty() },
                                Format.eta(item.etaSeconds).takeIf { it.isNotEmpty() },
                            ).joinToString(" · "),
                        )
                    }
                }
                DownloadStatus.PROCESSING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    StatusText(item.statusDetail ?: "Processing…")
                }
                DownloadStatus.COMPLETED -> StatusText(
                    listOf(Format.bytes(item.sizeBytes), Format.date(item.finishedAt)).joinToString(" · "),
                )
                DownloadStatus.FAILED -> Text(
                    item.error ?: "Download failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadStatus.CANCELED -> StatusText("Canceled")
            }

            EntryMenu(item,onAgain,onSource,onNext)
            val hasActions = onCancel != null || onOpen != null || onShare != null || onRetry != null || onDelete != null
            if (hasActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
                    if (item.status == DownloadStatus.COMPLETED) {
                        onOpen?.let {
                            IconButton(onClick = it) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play") }
                        }
                        onShare?.let {
                            IconButton(onClick = it) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
                        }
                    }
                    if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELED) {
                        onRetry?.let {
                            IconButton(onClick = it) { Icon(Icons.Rounded.Refresh, contentDescription = "Retry") }
                        }
                    }
                    onDelete?.let {
                        IconButton(onClick = it) { Icon(Icons.Rounded.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ------------------------------------------------------------------ Library

@Composable
fun LibraryScreen(
    items: List<DownloadItem>,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showAdultSites by vm.settings.showAdultSites.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf<com.social_downloader.engine.Platform?>(null) }
    var sort by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf(0) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var recentOnly by remember { mutableStateOf(false) }
    val favorites by vm.settings.favorites.collectAsStateWithLifecycle()
    val allVideos = items.filter { it.status == DownloadStatus.COMPLETED }
    val filtered = allVideos.filter {
        (platform == null || it.platform == platform) &&
            (!favoritesOnly || it.id in favorites) &&
            (!recentOnly || it.finishedAt >= System.currentTimeMillis()-7L*86400000) &&
            (kind == 0 || (kind == 1 && it.mimeType.orEmpty().startsWith("audio/")) || (kind == 2 && it.mimeType.orEmpty().startsWith("video/"))) &&
            (it.title.contains(search, true) || it.fileName.orEmpty().contains(search, true))
    }
    val videos = when(sort) { 1 -> filtered.sortedBy { it.finishedAt }; 2 -> filtered.sortedByDescending { it.sizeBytes }; 3 -> filtered.sortedBy { it.sizeBytes }; else -> filtered.sortedByDescending { it.finishedAt } }
    var missing by remember { mutableStateOf(emptySet<String>()) }
    var toDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var deletionTargets by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }

    // Re-check which files still exist (the user may have deleted some in a file manager).
    LaunchedEffect(allVideos.map { it.id to it.fileUri }) {
        missing = withContext(Dispatchers.IO) {
            allVideos.filter { !MediaSaver.exists(context, it.fileUri, it.filePath) }.map { it.id }.toSet()
        }
    }

    if (allVideos.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.VideoLibrary,
            title = "Your library is empty",
            subtitle = "Audio and video downloads appear here. Play, share or delete them.",
            modifier = modifier,
        )
        return
    }

    val totalBytes = videos.filter { it.id !in missing }.sumOf { it.sizeBytes }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search your library") }, shape = MaterialTheme.shapes.medium)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = platform == null, onClick = { platform = null }, label = { Text("All sites") }) }
                items(allVideos.map { it.platform }.distinct().filter { !it.adult || showAdultSites }, key = { it.name }) { site ->
                    FilterChip(selected = site == platform, onClick = { platform = site }, label = { Text(site.label) })
                }
            }
        }
        item {
            OptionMenu("Sort",listOf("Newest","Oldest","Largest","Smallest"),sort){sort=it}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("All media","Audio","Video").forEachIndexed { i,label -> FilterChip(kind==i,{kind=i},label={Text(label)}) }
                FilterChip(favoritesOnly,{favoritesOnly=!favoritesOnly},label={Text("Favorites")})
                FilterChip(recentOnly,{recentOnly=!recentOnly},label={Text("Last 7 days")})
            }
            Text("Audio: ${Format.bytes(allVideos.filter { it.id !in missing && it.mimeType.orEmpty().startsWith("audio/") }.sumOf { it.sizeBytes })} · Video: ${Format.bytes(allVideos.filter { it.id !in missing && it.mimeType.orEmpty().startsWith("video/") }.sumOf { it.sizeBytes })}",style=MaterialTheme.typography.bodySmall)
            if (videos.isEmpty()) Text("No downloads match your search.")
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${videos.size} file${if (videos.size == 1) "" else "s"} · ${Format.bytes(totalBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { deletionTargets = videos.toList(); confirmAll = true }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete shown")
                }
            }
        }
        items(videos, key = { it.id }) { item ->
            val isMissing = item.id in missing
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumbnail(url = item.thumbnail, modifier = Modifier.width(112.dp).height(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                item.fileName ?: item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${item.platform.label} · ${item.qualityLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (isMissing) "File no longer exists"
                                else "${Format.bytes(item.sizeBytes)} · ${Format.date(item.finishedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMissing) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick={vm.settings.favorite(item.id)}){Text(if(item.id in favorites) "Unfavorite" else "Favorite")}
                        EntryMenu(item,{vm.useSource(item)},{vm.openSource(context,item)})
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!isMissing) {
                            IconButton(onClick = { vm.openFile(context, item) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                            }
                            IconButton(onClick = { vm.shareFile(context, item) }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share")
                            }
                        }
                        IconButton(onClick = { toDelete = item }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (item.id in missing) "Remove entry?" else "Delete file?") },
            text = {
                Text(
                    if (item.id in missing) "The file is already gone; this removes it from the list."
                    else "This permanently deletes the file from your Downloads folder.",
                )
            },
            confirmButton = {
                Button(onClick = { vm.downloads.remove(item.id, deleteFile = true); toDelete = null }) {
                    Text(if (item.id in missing) "Remove" else "Delete")
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete shown media?") },
            text = { Text("This permanently deletes ${deletionTargets.size} file(s) downloaded by this app.") },
            confirmButton = {
                Button(onClick = {
                    deletionTargets.forEach { vm.downloads.remove(it.id, deleteFile = true) }
                    confirmAll = false
                }) { Text("Delete shown") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EntryMenu(item: DownloadItem, again: (() -> Unit)? = null, source: (() -> Unit)? = null, next: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    val web=com.social_downloader.engine.UrlTools.check(item.url).isValid
    androidx.compose.foundation.layout.Box {
        TextButton(onClick={open=true}){Text("More")}
        androidx.compose.material3.DropdownMenu(open,{open=false}) {
            if(item.status == DownloadStatus.QUEUED && next!=null) androidx.compose.material3.DropdownMenuItem(text={Text("Download next")},onClick={next();open=false})
            if(again!=null) androidx.compose.material3.DropdownMenuItem(text={Text("Download again")},enabled=web,onClick={again();open=false})
            if(source!=null) androidx.compose.material3.DropdownMenuItem(text={Text("Open original")},enabled=web,onClick={source();open=false})
            if(web) androidx.compose.material3.DropdownMenuItem(text={Text("Copy link")},onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.url));open=false})
            else androidx.compose.material3.DropdownMenuItem(text={Text("Original website link unavailable")},enabled=false,onClick={})
            if(item.status == DownloadStatus.FAILED) androidx.compose.material3.DropdownMenuItem(text={Text("Copy error")},onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.error.orEmpty()));open=false})
        }
    }
}
