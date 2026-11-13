package com.social_downloader.data

import org.json.JSONObject

data class Experience(
    val palette: Int = 0, val textScale: Float = 1f, val typeface: Int = 0, val corners: Int = 1,
    val compact: Boolean = false, val navLabels: Boolean = true, val thumbnails: Boolean = true,
    val showStats: Boolean = true, val directoryOpen: Boolean = false, val width: Int = 760,
    val appTitle: String = "AnyDown", val subtitle: String = "Paste a link or share one from another app.",
    val startTab: String = "HOME", val audioDefault: Boolean = false, val videoHeight: Int = 0,
    val audioFormat: String = "M4A", val audioBitrate: Int = 192, val customHex: String = "",
) {
    fun json(): String = JSONObject().apply {
        put("customHex", customHex)
        put("palette", palette); put("textScale", textScale); put("typeface", typeface); put("corners", corners)
        put("compact", compact); put("navLabels", navLabels); put("thumbnails", thumbnails); put("showStats", showStats)
        put("directoryOpen", directoryOpen); put("width", width); put("appTitle", appTitle); put("subtitle", subtitle)
        put("startTab", startTab); put("audioDefault", audioDefault); put("videoHeight", videoHeight)
        put("audioFormat", audioFormat); put("audioBitrate", audioBitrate)
    }.toString()
    companion object {
        fun parse(text: String?): Experience = runCatching {
            val j = JSONObject(text ?: "{}")
            Experience(j.optInt("palette", 0).coerceIn(0,6), j.optDouble("textScale",1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(.85f,1.35f) ?: 1f,
                j.optInt("typeface",0).coerceIn(0,2), j.optInt("corners",1).coerceIn(0,2),
                j.optBoolean("compact"),j.optBoolean("navLabels",true),j.optBoolean("thumbnails",true),j.optBoolean("showStats",true),
                j.optBoolean("directoryOpen"),j.optInt("width",760).coerceIn(480,1200),j.optString("appTitle","AnyDown").take(24).ifBlank { "AnyDown" },
                j.optString("subtitle","Paste a link or share one from another app.").take(120),j.optString("startTab","HOME"),
                j.optBoolean("audioDefault"),j.optInt("videoHeight",0).takeIf { it in listOf(0,360,480,720,1080,2160) } ?: 0,
                j.optString("audioFormat","M4A"),j.optInt("audioBitrate",192),j.optString("customHex","").removePrefix("#").uppercase().takeIf { Regex("[0-9A-F]{6}").matches(it) } ?: "")
        }.getOrDefault(Experience())
    }
}
