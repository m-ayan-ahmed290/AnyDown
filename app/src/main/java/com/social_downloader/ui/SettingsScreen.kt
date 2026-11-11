@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.social_downloader.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.social_downloader.engine.Platform
import com.social_downloader.engine.SiteLogin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.social_downloader.BuildConfig
import com.social_downloader.data.ThemeMode
import com.social_downloader.download.MediaSaver
import com.social_downloader.engine.EngineState
import com.social_downloader.engine.UpdateChannel

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val migrationNotice by vm.downloads.migrationNotice.collectAsStateWithLifecycle()
    val s = vm.settings
    val theme by s.themeMode.collectAsStateWithLifecycle()
    val dynamic by s.dynamicColor.collectAsStateWithLifecycle()
    val concurrent by s.maxConcurrent.collectAsStateWithLifecycle()
    val fragments by s.fragments.collectAsStateWithLifecycle()
    val showAdultSites by s.showAdultSites.collectAsStateWithLifecycle()
    val wifiOnly by s.wifiOnly.collectAsStateWithLifecycle()
    val subfolder by s.useSubfolder.collectAsStateWithLifecycle()
    val channel by s.updateChannel.collectAsStateWithLifecycle()
    val engineState by vm.engine.state.collectAsStateWithLifecycle()
    val version by vm.engine.version.collectAsStateWithLifecycle()
    val signedIn by vm.cookies.signedIn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var importFor by remember { mutableStateOf<Platform?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = importFor
        if (uri != null && p != null) vm.importCookies(context, p, uri)
        importFor = null
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Appearance") {
            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Choice(
                options = ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selectedIndex = ThemeMode.entries.indexOf(theme),
                onSelect = { s.setThemeMode(ThemeMode.entries[it]) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ToggleRow("Dynamic color", "Use your wallpaper colors (Material You)", dynamic, s::setDynamicColor)
            }
        }

        Section("Website directory") {
            ToggleRow("Show adult sites", "Show adult website names in the directory. Off by default.", showAdultSites, s::setShowAdultSites)
        }

        Section("Downloads") {
            Text("Simultaneous downloads", style = MaterialTheme.typography.labelLarge)
            Choice(
                options = listOf("1", "2", "3", "4", "5"),
                selectedIndex = concurrent - 1,
                onSelect = { s.setMaxConcurrent(it + 1) },
            )
            Text("Fragment connections per download", style = MaterialTheme.typography.labelLarge)
            Choice(listOf("2", "4", "8", "16"), listOf(2, 4, 8, 16).indexOf(fragments)) { s.setFragments(listOf(2, 4, 8, 16)[it]) }
            Text("More connections can help segmented streams. Site limits and network speed still apply.", style = MaterialTheme.typography.bodySmall)
            ToggleRow("Wi-Fi only", "Don't download over mobile data", wifiOnly, s::setWifiOnly)
            ToggleRow(
                "Organize by website",
                if (subfolder) "Downloads/${MediaSaver.SUBFOLDER}/<Website>" else "Downloads",
                subfolder, s::setUseSubfolder,
            )
        }

        Section("Storage upgrade") {
            Text("Old SocialDownloader files are moved into AnyDowns by website. Files from another app or installation may need to be moved with your file manager.",
                style = MaterialTheme.typography.bodySmall)
            migrationNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = vm.downloads::retryStorageMigration) { Text("Retry folder migration") }
        }

        Section("Accounts") {
            Text(
                "Some sites only show certain videos to signed-in users. Sign in with your OWN account and the " +
                    "app can fetch what you could already watch there. Only session cookies are kept, privately " +
                    "on this device, and they're only sent to that site. Private posts you can't view yourself " +
                    "stay blocked. Tip: use a secondary account — sites may limit accounts that download a lot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SiteLogin.all.forEach { site ->
                val isIn = site.platform in signedIn
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(site.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            if (isIn) "Signed in" else "Not signed in",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isIn) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.loginSite = site }) { Text(if (isIn) "Sign in again" else "Sign in") }
                        TextButton(onClick = { importFor = site.platform; picker.launch(arrayOf("*/*")) }) {
                            Text("Import cookies.txt")
                        }
                        if (isIn) TextButton(onClick = { vm.signOut(site.platform) }) { Text("Sign out") }
                    }
                }
            }
            Text(
                "YouTube is not offered: Google blocks in-app sign-in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Download engine") {
            Text(
                when (engineState) {
                    EngineState.Initializing -> "Starting…"
                    EngineState.Ready -> "yt-dlp ${version ?: ""}".trim()
                    is EngineState.Failed -> "Failed to start: ${(engineState as EngineState.Failed).message}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Sites change often. If a site stops working, update yt-dlp — it downloads the latest " +
                    "release straight from the official yt-dlp GitHub project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Choice(
                options = listOf("Stable", "Nightly"),
                selectedIndex = UpdateChannel.entries.indexOf(channel),
                onSelect = { s.setUpdateChannel(UpdateChannel.entries[it]) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::updateEngine, enabled = !vm.updating) {
                    if (vm.updating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.updating) "Updating…" else "Update yt-dlp")
                }
            }
            vm.updateResult?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Section("About") {
            Text("AnyDown ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Open source (GPL-3.0). Everything runs on your device using yt-dlp and FFmpeg – " +
                    "no server, no accounts, no tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Only download content you own or have permission to save, and respect each " +
                    "platform's terms of service and local copyright law. DRM-protected media is not supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}
