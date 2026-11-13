package com.social_downloader.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.social_downloader.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E1FF),
    onPrimaryContainer = Color(0xFF15134F),
    secondary = Color(0xFF0E9F8E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F4EC),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFD6336C),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFE4E6F0),
    onSurfaceVariant = Color(0xFF464A58),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFEDEEF5),
    surfaceContainerHigh = Color(0xFFE7E8F0),
    surfaceContainerHighest = Color(0xFFE1E3EB),
    outline = Color(0xFF767A8A),
    outlineVariant = Color(0xFFC6C9D6),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    onPrimary = Color(0xFF1B1B6B),
    primaryContainer = Color(0xFF33369E),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFF5EDCC8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFFC9F4EC),
    tertiary = Color(0xFFFF8FB5),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE6E8F0),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF2A2E3B),
    onSurfaceVariant = Color(0xFFC4C7D4),
    surfaceContainerLowest = Color(0xFF0A0B10),
    surfaceContainerLow = Color(0xFF14171F),
    surfaceContainer = Color(0xFF181B24),
    surfaceContainerHigh = Color(0xFF1F222D),
    surfaceContainerHighest = Color(0xFF292C38),
    outline = Color(0xFF8E92A3),
    outlineVariant = Color(0xFF444857),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun SocialDownloaderTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    experience: com.social_downloader.data.Experience = com.social_downloader.data.Experience(),
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val seeds = listOf(0xFF4F46E5,0xFF4F46E5,0xFF006B9C,0xFF176B46,0xFFAF2458,0xFF825500,0xFF7933B8)
    val seed = Color(if(experience.customHex.isNotEmpty()) ("FF"+experience.customHex).toLong(16) else seeds[experience.palette.coerceIn(0,6)])
    fun contrast(color: Color) = if(color.luminance() > .179f) Color.Black else Color.White
    fun readableAccent(color: Color): Color {
        var shade=color
        repeat(24) {
            val a=shade.luminance(); val b=baseScheme.surface.luminance()
            if((maxOf(a,b)+.05f)/(minOf(a,b)+.05f)>=5f) return shade
            shade=androidx.compose.ui.graphics.lerp(shade,if(dark) Color.White else Color.Black,.12f)
        }
        return if(dark) Color.White else Color.Black
    }
    val primary = readableAccent(if(dark) androidx.compose.ui.graphics.lerp(seed,Color.White,.55f) else seed)
    val container = androidx.compose.ui.graphics.lerp(if(dark) Color.Black else Color.White,seed,if(dark) .55f else .15f)
    val scheme = if(experience.palette == 0 && experience.customHex.isEmpty()) baseScheme else baseScheme.copy(
        primary = primary,
        onPrimary = contrast(primary),
        secondary = primary,
        onSecondary = contrast(primary),
        secondaryContainer = androidx.compose.ui.graphics.lerp(if(dark) Color.Black else Color.White,seed,if(dark) .45f else .12f),
        onSecondaryContainer = if(dark) Color.White else Color.Black,
        tertiary = primary,
        onTertiary = contrast(primary),
        primaryContainer = container,
        onPrimaryContainer = contrast(container))
    val radius = listOf(4,16,28)[experience.corners.coerceIn(0,2)].dp
    val shapes = Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(radius/2),medium=androidx.compose.foundation.shape.RoundedCornerShape(radius),large=androidx.compose.foundation.shape.RoundedCornerShape(radius),extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(radius))
    val family = when(experience.typeface) { 1 -> androidx.compose.ui.text.font.FontFamily.Serif; 2 -> androidx.compose.ui.text.font.FontFamily.Monospace; else -> androidx.compose.ui.text.font.FontFamily.SansSerif }
    val base = androidx.compose.material3.Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily=family),
        displayMedium = base.displayMedium.copy(fontFamily=family),
        displaySmall = base.displaySmall.copy(fontFamily=family),
        headlineLarge = base.headlineLarge.copy(fontFamily=family),
        headlineMedium = base.headlineMedium.copy(fontFamily=family),
        headlineSmall = base.headlineSmall.copy(fontFamily=family),
        titleLarge = base.titleLarge.copy(fontFamily=family),
        titleMedium = base.titleMedium.copy(fontFamily=family),
        titleSmall = base.titleSmall.copy(fontFamily=family),
        bodyLarge = base.bodyLarge.copy(fontFamily=family),
        bodyMedium = base.bodyMedium.copy(fontFamily=family),
        bodySmall = base.bodySmall.copy(fontFamily=family),
        labelLarge = base.labelLarge.copy(fontFamily=family),
        labelMedium = base.labelMedium.copy(fontFamily=family),
        labelSmall = base.labelSmall.copy(fontFamily=family)
    )

    // Keep status/navigation bar icon colours in sync with the *app* theme (not just the system one).
    val activity = context as? Activity
    SideEffect {
        (activity as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
    }

    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
}
