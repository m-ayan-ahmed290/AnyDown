@file:OptIn(ExperimentalMaterial3Api::class)

package com.social_downloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        if (granted) vm.download()
        else vm.say("Storage permission is required on Android 9 to save videos to Downloads.")
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
        topBar = { TopAppBar(title = { Text(if (vm.tab == Tab.HOME) "Social Downloader" else vm.tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = {
                            if (tab == Tab.QUEUE && pendingCount > 0) {
                                BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                    Icon(tab.icon(), contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon(), contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm, engineState, onDownload = startDownload)
                Tab.QUEUE -> QueueScreen(items, vm)
                Tab.LIBRARY -> LibraryScreen(items, vm)
                Tab.HISTORY -> HistoryScreen(items, vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
