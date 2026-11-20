package com.gallery.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private const val PREFS_NAME = "prism_wallpaper_accent"
private const val KEY_ACCENT = "wallpaper_accent"

/**
 * Remembers which wallpaper colour the current accent was taken from, so the accent picker can mark
 * that swatch. Kept apart from the palette prefs because it describes where the accent came from,
 * not what the palette is, and the home-screen wallpaper can change without the palette changing.
 */
object WallpaperAccentMemory {

    /** The wallpaper colour behind the current accent, or null if the accent was picked by hand. */
    fun read(context: Context): Color? = prefs(context)
        .takeIf { it.contains(KEY_ACCENT) }
        ?.let { Color(it.getInt(KEY_ACCENT, 0)) }

    fun remember(context: Context, color: Color) {
        prefs(context).edit().putInt(KEY_ACCENT, color.toArgb()).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().remove(KEY_ACCENT).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
