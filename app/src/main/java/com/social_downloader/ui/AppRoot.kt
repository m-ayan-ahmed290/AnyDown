@file:OptIn(ExperimentalMaterial3Api::class)

package com.social_downloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.social_downloader.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.social_downloader.ui.theme.SocialDownloaderTheme

private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Rounded.Download
    Tab.QUEUE -> Icons.Rounded.CloudDownload
    Tab.LIBRARY -> Icons.Rounded.VideoLibrary
    Tab.HISTORY -> Icons.Rounded.History
    Tab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val themeMode by vm.settings.themeMode.collectAsStateWithLifecycle()
    val dynamic by vm.settings.dynamicColor.collectAsStateWithLifecycle()
    SocialDownloaderTheme(themeMode = themeMode, dynamicColor = dynamic) {
        val site = vm.loginSite
        if (site != null) LoginScreen(site, vm) else AppScaffold(vm)
    }
}

@Composable
private fun AppScaffold(vm: MainViewModel) {
    val context = LocalContext.current
    val items by vm.downloads.items.collectAsStateWithLifecycle()
    val engineState by vm.engine.state.collectAsStateWithLifecycle()
    val pendingCount = items.count { it.status.isPending }
    val snackbar = remember { SnackbarHostState() }

    // Notifications permission (Android 13+). Downloads still work if it's denied.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Android 9 only: writing into the public Downloads folder needs the legacy storage permission.
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { vm.downloads.retryStorageMigration(); vm.download() }
        else vm.say("Storage permission is required on Android 9 to save videos to Downloads.")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val prefs = context.getSharedPreferences("migration", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("storage_asked", false) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean("storage_asked", true).apply()
                storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    val startDownload: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.download()
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = if (m.goToQueue) "View" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && m.goToQueue) vm.tab = Tab.QUEUE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandMark(Modifier.size(36.dp))
                    Column {
                        Text(if (vm.tab == Tab.HOME) "AnyDown" else vm.tab.label, fontWeight = FontWeight.Bold)
                        Text("Your media. Your space.", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })
        },
        bottomBar = {
            AnyDownNavigationBar(vm.tab, pendingCount) { vm.tab = it }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm, engineState, onDownload = startDownload, modifier = Modifier.widthIn(max = 760.dp))
                Tab.QUEUE -> QueueScreen(items, vm, Modifier.widthIn(max = 900.dp).fillMaxSize())
                Tab.LIBRARY -> LibraryScreen(items, vm, Modifier.widthIn(max = 900.dp).fillMaxSize())
                Tab.HISTORY -> HistoryScreen(items, vm, Modifier.widthIn(max = 900.dp).fillMaxSize())
                Tab.SETTINGS -> SettingsScreen(vm, Modifier.widthIn(max = 900.dp).fillMaxSize())
            }
        }
    }
}

@Composable
internal fun AnyDownNavigationBar(selectedTab: Tab, pendingCount: Int, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Row(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow, label = "Tab color")
                Surface(modifier = Modifier.weight(1f).testTag("tab-surface-${tab.name}"), color = color, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.selectable(selected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(horizontal = 2.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BadgedBox(badge = {
                            if (tab == Tab.QUEUE && pendingCount > 0) Badge { Text("$pendingCount") }
                        }) {
                            Icon(tab.icon(), contentDescription = null, modifier = Modifier.size(23.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(tab.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
