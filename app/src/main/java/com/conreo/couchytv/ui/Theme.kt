package com.conreo.couchytv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val ACCENTS = listOf(
    Color(0xFF8AB4F8),
    Color(0xFF81C995),
    Color(0xFFFDD663),
    Color(0xFFF28B82),
    Color(0xFFD7AEFB),
    Color(0xFF78D9EC),
)

data class WallpaperPreset(val name: String, val colors: List<Color>)

val WALLPAPERS = listOf(
    WallpaperPreset("Midnight", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    WallpaperPreset("Aurora", listOf(Color(0xFF13547A), Color(0xFF2B825B), Color(0xFF80D0C7))),
    WallpaperPreset("Sunset", listOf(Color(0xFF41295A), Color(0xFF8F4A3E), Color(0xFFD76D77))),
    WallpaperPreset("Deep", listOf(Color(0xFF000428), Color(0xFF004683))),
    WallpaperPreset("Charcoal", listOf(Color(0xFF16161A), Color(0xFF232526), Color(0xFF2F3437))),
)

fun WallpaperPreset.brush(): Brush = Brush.linearGradient(colors)

/** Deterministic tile color for apps that ship no banner artwork. */
fun tileColor(pkg: String): Color {
    val palette = listOf(
        Color(0xFF37474F), Color(0xFF4E342E), Color(0xFF1B5E20),
        Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF880E4F),
        Color(0xFF3E2723), Color(0xFF263238), Color(0xFF33691E),
    )
    var h = 0
    for (c in pkg) h = h * 31 + c.code
    return palette[((h % palette.size) + palette.size) % palette.size]
}

/**
 * Palette and metrics for the system-TV-settings skin used by the settings
 * overlay and shared pickers. These tokens are intentionally isolated from
 * the launcher theme so the dock/wallpaper/cards stay untouched.
 */
object SettingsColors {
    val pageBg = Color(0xFF1E232C)
    val title = Color(0xFFE8EAED)
    val subtitle = Color(0xFF979AA0)
    val sectionLabel = Color(0xFF9AA0A6)

    val focusPill = Color(0xFFE8EAED)
    val titleFocused = Color(0xFF0E0E0F)
    val subtitleFocused = Color(0xFF656668)

    val iconChip = Color(0xFF263041)
    val iconGlyph = Color(0xFFAEB2B9)
    val iconChipFocused = Color(0xFFD3E0F5)
    val iconGlyphFocused = Color(0xFF4285F4)

    val switchTrackOn = Color(0xFF3E4C68)
    val switchThumbOn = Color(0xFF3A76E2)
    val switchTrackOff = Color(0xFF2A3341)
    val switchThumbOff = Color(0xFF8A8F96)

    val sheetShadow = Color(0x45000000)
}

@Composable
fun LiteTvTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            surface = Color(0xEE1C1E24),
            onSurface = Color(0xFFE8EAED),
            surfaceVariant = Color(0xFF303440),
            onSurfaceVariant = Color(0xFF9AA0A6),
        ),
        content = content,
    )
}
