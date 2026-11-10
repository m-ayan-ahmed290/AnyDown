package com.social_downloader.data

import android.content.Context
import com.social_downloader.engine.UpdateChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(prefs.getInt("max_concurrent", 2).coerceIn(1, 5))
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _fragments = MutableStateFlow(prefs.getInt("fragments", 8).coerceIn(1, 16))
    val fragments: StateFlow<Int> = _fragments.asStateFlow()
    fun setFragments(v: Int) { _fragments.value = v.coerceIn(1, 16); prefs.edit().putInt("fragments", _fragments.value).apply() }

    private val _showAdultSites = MutableStateFlow(prefs.getBoolean("show_adult_sites", false))
    val showAdultSites: StateFlow<Boolean> = _showAdultSites.asStateFlow()
    fun setShowAdultSites(v: Boolean) { _showAdultSites.value = v; prefs.edit().putBoolean("show_adult_sites", v).apply() }

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean("wifi_only", false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _useSubfolder = MutableStateFlow(prefs.getBoolean("use_subfolder", true))
    val useSubfolder: StateFlow<Boolean> = _useSubfolder.asStateFlow()

    private val _updateChannel = MutableStateFlow(
        runCatching { UpdateChannel.valueOf(prefs.getString("update_channel", "STABLE")!!) }
            .getOrDefault(UpdateChannel.STABLE),
    )
    val updateChannel: StateFlow<UpdateChannel> = _updateChannel.asStateFlow()

    fun setThemeMode(v: ThemeMode) { _themeMode.value = v; prefs.edit().putString("theme", v.name).apply() }
    fun setDynamicColor(v: Boolean) { _dynamicColor.value = v; prefs.edit().putBoolean("dynamic_color", v).apply() }
    fun setMaxConcurrent(v: Int) { _maxConcurrent.value = v.coerceIn(1, 5); prefs.edit().putInt("max_concurrent", v.coerceIn(1, 5)).apply() }
    fun setWifiOnly(v: Boolean) { _wifiOnly.value = v; prefs.edit().putBoolean("wifi_only", v).apply() }
    fun setUseSubfolder(v: Boolean) { _useSubfolder.value = v; prefs.edit().putBoolean("use_subfolder", v).apply() }
    fun setUpdateChannel(v: UpdateChannel) { _updateChannel.value = v; prefs.edit().putString("update_channel", v.name).apply() }
}
