@file:OptIn(ExperimentalLayoutApi::class)

package com.social_downloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.social_downloader.data.DownloadStatus
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.social_downloader.engine.EngineState
import com.social_downloader.engine.MediaInfo
import com.social_downloader.engine.Platform
import com.social_downloader.engine.QualityOption
import com.social_downloader.util.Format

@Composable
fun HomeScreen(
    vm: MainViewModel,
    engineState: EngineState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showAdultSites by vm.settings.showAdultSites.collectAsStateWithLifecycle()
    val downloads by vm.downloads.items.collectAsStateWithLifecycle()
    val organized by vm.settings.useSubfolder.collectAsStateWithLifecycle()
    val experience by vm.settings.experience.collectAsStateWithLifecycle()
    var browseSites by remember(experience.directoryOpen) { mutableStateOf(experience.directoryOpen) }
    val focus = LocalFocusManager.current
    val state = vm.fetchState

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Save a video or audio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(experience.subtitle, style = MaterialTheme.typography.bodyMedium)
                if (experience.showStats) Row {
                    TextButton(onClick = { vm.tab = Tab.LIBRARY }) { Text("${downloads.count { it.status == DownloadStatus.COMPLETED }} saved") }
                    TextButton(onClick = { vm.tab = Tab.QUEUE }) { Text("${downloads.count { it.status.isPending }} in queue") }
                }
            }
        }
        item { UrlInputCard(vm, focus) }
        item { BatchDownloads(vm) }

        item {
            when (state) {
                FetchState.Idle -> Unit
                FetchState.Loading -> LoadingCard(engineState)
                is FetchState.Error -> ErrorCard(state.message, onRetry = { focus.clearFocus(); vm.fetch() })
                is FetchState.Success -> PreviewCard(
                    info = state.info,
                    selected = vm.selectedQuality,
                    onSelect = vm::selectQuality,
                    onDownload = onDownload,
                    organized = organized,
                    onAudioFormat = vm::selectAudioFormat,
                    onAudioBitrate = vm::selectAudioBitrate,
                )
            }
        }

        if (state is FetchState.Idle) {
            item { TextButton(onClick = { browseSites = !browseSites }) { Text(if (browseSites) "Hide website directory" else "Browse supported websites") } }
            if (browseSites) item { SupportedSitesCard(showAdultSites) }
        }
    }
}

@Composable
private fun UrlInputCard(vm: MainViewModel, focus: FocusManager) {
    val clipboard = LocalClipboardManager.current
    val check = vm.inputCheck
    val platform = check.url?.let { com.social_downloader.engine.UrlTools.detect(it) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = vm.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Media link") },
                placeholder = { Text("Paste a link…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    if (vm.input.isEmpty()) {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(vm::onPasted)
                        }) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste from clipboard") }
                    } else {
                        IconButton(onClick = vm::clearInput) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                isError = check.error != null,
                supportingText = { check.error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); vm.fetch() }),
                shape = MaterialTheme.shapes.medium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = platform != null) {
                    if (platform != null) PlatformChip(platform)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { focus.clearFocus(); vm.fetch() },
                    enabled = check.isValid && vm.fetchState !is FetchState.Loading,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Find media")
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(engineState: EngineState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Column {
                Text("Fetching media details…", style = MaterialTheme.typography.titleSmall)
                if (engineState is EngineState.Initializing) {
                    Text(
                        "Preparing the download engine (first launch only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Rounded.ErrorOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Try again") }
        }
    }
}

@Composable
private fun PreviewCard(
    info: MediaInfo,
    selected: QualityOption?,
    onSelect: (QualityOption) -> Unit,
    onDownload: () -> Unit,
    organized: Boolean,
    onAudioFormat: (com.social_downloader.engine.AudioFormat) -> Unit,
    onAudioBitrate: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(
                url = info.thumbnail,
                durationSec = info.durationSec,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            ) {
                PlatformChip(info.platform, Modifier.align(Alignment.TopStart).padding(8.dp))
            }

            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(info.uploader, info.extractor?.takeIf { info.platform == Platform.OTHER })
                    .joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Quality",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info.qualities.forEach { q ->
                    FilterChip(
                        selected = q.id == selected?.id,
                        onClick = { onSelect(q) },
                        label = { Text(q.label + (q.detail?.let { " · $it" } ?: "")) },
                        leadingIcon = if (q.audioOnly) {
                            { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }

            if (selected?.audioOnly == true) {
                Text("Audio format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.social_downloader.engine.AudioFormat.entries.forEach { format ->
                        FilterChip(selected = selected.audioFormat == format, onClick = { onAudioFormat(format) }, label = { Text(format.label) })
                    }
                }
                if (!selected.audioFormat.lossless) {
                    Text("Audio quality", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.audioFormat.bitrates.forEach { bitrate ->
                            FilterChip(selected = selected.audioBitrateKbps == bitrate, onClick = { onAudioBitrate(bitrate) }, label = { Text("$bitrate kbps") })
                        }
                    }
                }
                Text(if (selected.audioFormat.lossless) "Lossless output preserves decoded audio; it cannot restore detail missing from the source. Files can be large."
                    else "Output bitrate controls file size. Higher settings cannot improve the source audio.", style = MaterialTheme.typography.bodySmall)
            }

            val estimate = if (selected?.audioOnly == true && selected.audioBitrateKbps != null && info.durationSec > 0)
                selected.audioBitrateKbps * 1000L / 8L * info.durationSec else selected?.estimatedBytes
            val size = estimate?.let { "≈ ${Format.bytes(it)}" }
            Button(
                onClick = onDownload,
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                val what = when {
                    selected == null -> "Download"
                    selected.audioOnly -> "Download ${selected.audioFormat.label} · ${selected.audioFormat.qualityLabel(selected.audioBitrateKbps)}"
                    else -> "Download MP4 · ${selected.label}"
                }
                Text(listOfNotNull(what, size).joinToString("  ·  "))
            }
            Text(
                if (organized) "Saved to Downloads/AnyDowns/${info.platform.folderName}." else "Saved to Downloads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SupportedSitesCard(showAdultSites: Boolean) {
    var search by remember { mutableStateOf("") }
    val sites = Platform.directory(showAdultSites, search)
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A world of media", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Your favorites, all in one place.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search websites") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sites.forEach { PlatformChip(it) }
            }
            if (sites.isEmpty()) Text("No matching sites. You can still try a direct media link.")
            Text("Plus hundreds more through yt-dlp. Support varies by site, region and sign-in requirements. DRM-protected media isn't supported.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Share a link from any app → AnyDown. Choose quality, then save.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
