package com.social_downloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.social_downloader.data.Experience
import com.social_downloader.engine.AudioFormat

val LocalExperience = staticCompositionLocalOf { Experience() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Personalization(vm: MainViewModel) {
    val e by vm.settings.experience.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val export=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) {
            val profile=com.social_downloader.data.ProfileBackup.Profile(vm.settings.experience.value,vm.settings.themeMode.value,vm.settings.dynamicColor.value)
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(com.social_downloader.data.ProfileBackup.encode(profile)) } ?: error("Cannot write file") }
                    vm.say("Customization exported.")
                } catch(e: Exception) { vm.say("Couldn't export customization.") }
            }
        }
    }
    val import=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            try {
                val profile=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use(com.social_downloader.data.ProfileBackup::read) ?: error("Cannot read file") }
                vm.settings.customize { profile.experience }; vm.settings.setThemeMode(profile.theme); vm.settings.setDynamicColor(profile.dynamic)
                vm.say("Customization imported.")
            } catch(e: Exception) { vm.say("Couldn't import: choose a supported AnyDown customization JSON file (up to 16 KB).") }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Close customization" else "Customize your app", style = MaterialTheme.typography.titleMedium) }
            if (expanded) {
                Text("Appearance updates live. These settings do not change your launcher name or system permissions.", style = MaterialTheme.typography.bodySmall)
                OptionMenu("Color palette", listOf("System", "Indigo", "Ocean", "Forest", "Rose", "Amber", "Violet"), e.palette) { v -> vm.settings.customize { it.copy(palette=v,customHex="") } }
                var hex by remember(e.customHex) { mutableStateOf(e.customHex) }
                val validHex=Regex("[0-9a-fA-F]{6}").matches(hex.removePrefix("#"))
                OutlinedTextField(hex,{hex=it.take(7)},label={Text("Custom accent · RGB hex")},singleLine=true,isError=hex.isNotEmpty()&&!validHex,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={vm.settings.customize { it.copy(customHex=hex) }},enabled=validHex||hex.isEmpty()){Text("Apply custom accent (empty restores palette)")}
                Text("Accent shades adjust for readable contrast.",style=MaterialTheme.typography.bodySmall)
                Text("Text size · ${(e.textScale*100).toInt()}%")
                Slider(e.textScale, { v -> vm.settings.customize { it.copy(textScale=v) } }, valueRange=.85f..1.35f, steps=9)
                OptionMenu("Typeface", listOf("System sans", "Serif", "Monospace"), e.typeface) { v -> vm.settings.customize { it.copy(typeface=v) } }
                OptionMenu("Corners", listOf("Subtle", "Rounded", "Extra rounded"), e.corners) { v -> vm.settings.customize { it.copy(corners=v) } }
                OptionMenu("Content width", listOf("Compact", "Standard", "Wide"), listOf(590,760,1000).indexOf(e.width).coerceAtLeast(0)) { v -> vm.settings.customize { it.copy(width=listOf(590,760,1000)[v]) } }
                OptionMenu("Start screen", Tab.entries.map { it.label }, Tab.entries.indexOfFirst { it.name == e.startTab }.coerceAtLeast(0)) { v -> vm.settings.customize { it.copy(startTab=Tab.entries[v].name) } }
                PreferenceSwitch("Compact download cards", e.compact) { v -> vm.settings.customize { it.copy(compact=v) } }
                PreferenceSwitch("Show inactive tab labels", e.navLabels) { v -> vm.settings.customize { it.copy(navLabels=v) } }
                PreferenceSwitch("Show thumbnails", e.thumbnails) { v -> vm.settings.customize { it.copy(thumbnails=v) } }
                PreferenceSwitch("Show home counters", e.showStats) { v -> vm.settings.customize { it.copy(showStats=v) } }
                PreferenceSwitch("Open website directory by default", e.directoryOpen) { v -> vm.settings.customize { it.copy(directoryOpen=v) } }
                var title by remember(e.appTitle) { mutableStateOf(e.appTitle) }
                var subtitle by remember(e.subtitle) { mutableStateOf(e.subtitle) }
                OutlinedTextField(title, { title=it.take(24) }, label={ Text("In-app name") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(subtitle, { subtitle=it.take(120) }, label={ Text("Home message") }, modifier=Modifier.fillMaxWidth())
                TextButton(onClick={ vm.settings.customize { it.copy(appTitle=title,subtitle=subtitle) } }) { Text("Save name and message") }
                Text("Download defaults", style=MaterialTheme.typography.titleSmall)
                PreferenceSwitch("Prefer audio-only downloads", e.audioDefault) { v -> vm.settings.customize { it.copy(audioDefault=v) } }
                OptionMenu("Preferred video resolution", listOf("Best", "360p", "480p", "720p", "1080p", "2160p"), listOf(0,360,480,720,1080,2160).indexOf(e.videoHeight).coerceAtLeast(0)) { v -> vm.settings.customize { it.copy(videoHeight=listOf(0,360,480,720,1080,2160)[v]) } }
                val format = runCatching { AudioFormat.valueOf(e.audioFormat) }.getOrDefault(AudioFormat.M4A)
                OptionMenu("Default audio format", AudioFormat.entries.map { it.label }, AudioFormat.entries.indexOf(format)) { v -> vm.settings.customize { it.copy(audioFormat=AudioFormat.entries[v].name,audioBitrate=AudioFormat.entries[v].defaultBitrate ?: 0) } }
                if (!format.lossless) OptionMenu("Default audio bitrate", format.bitrates.map { "$it kbps" }, format.bitrates.indexOf(format.validBitrate(e.audioBitrate)).coerceAtLeast(0)) { v -> vm.settings.customize { it.copy(audioBitrate=format.bitrates[v]) } }
                Text("Back up appearance and download presets. Accounts, history and files are excluded.",style=MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick={export.launch("AnyDown-customization.json")}){Text("Export customization")}
                    TextButton(onClick={import.launch(arrayOf("application/json","text/plain"))}){Text("Import customization")}
                }
                TextButton(onClick={ reset=true }) { Text("Reset customization and presets") }
            }
        }
    }
    if (reset) AlertDialog(onDismissRequest={reset=false}, title={Text("Reset customization?")}, text={Text("Restores appearance and download defaults. Files, favorites, history and account cookies stay unchanged.")}, confirmButton={TextButton(onClick={vm.settings.resetCustomization();reset=false}){Text("Reset")}}, dismissButton={TextButton(onClick={reset=false}){Text("Cancel")}})
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f)); Switch(checked,change)
    }
}
@Composable
fun OptionMenu(label: String, options: List<String>, selected: Int, choose: (Int)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick={open=true}) { Text("$label: ${options.getOrElse(selected) { options.first() }}") }
        DropdownMenu(open,{open=false}) { options.forEachIndexed { i, option -> DropdownMenuItem(text={Text(option)},onClick={choose(i);open=false}) } }
    }
}

@Composable
fun BatchDownloads(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { if(it) vm.startBatch() else vm.say("Storage permission is required on Android 9.") }
    TextButton(onClick={open=true}) { Text(if(vm.batchRunning) "View batch progress" else "Batch download links") }
    if(open) AlertDialog(onDismissRequest={open=false}, title={Text("Batch downloads")}, text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Paste up to 25 links. Uses your download defaults; identical links and already pending URLs are skipped. Keep the app open while preparing links.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(vm.batchInput,{vm.batchInput=it.take(20000)},enabled=!vm.batchRunning,modifier=Modifier.fillMaxWidth().heightIn(max=200.dp),label={Text("Links (one per line)")})
            vm.batchStatus?.let { Text(it) }
            if(vm.batchFailures.isNotEmpty()) {
                Text("Failed links",style=MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=140.dp)) {
                    items(vm.batchFailures.size) { i -> val failure=vm.batchFailures[i]; Text("${failure.first}\n${failure.second}",style=MaterialTheme.typography.bodySmall) }
                }
                if(!vm.batchRunning) TextButton(onClick={vm.batchInput=vm.batchFailures.joinToString("\n"){it.first};vm.startBatch()}){Text("Retry failed batch links")}
            }
        }
    }, confirmButton={
        TextButton(onClick={
            if(vm.batchRunning) vm.cancelBatch()
            else if(android.os.Build.VERSION.SDK_INT<29 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else vm.startBatch()
        }){Text(if(vm.batchRunning) "Cancel preparation" else "Queue links")}
    },dismissButton={TextButton(onClick={open=false}){Text("Close")}})
}
