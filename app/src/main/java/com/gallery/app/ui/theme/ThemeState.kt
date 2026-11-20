package com.gallery.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.gallery.app.util.WallpaperAccents

private const val THEME_PREFS_NAME = "amoled_gallery_theme"
private const val KEY_PALETTE_ID = "palette_id"
private const val KEY_CUSTOM_BG = "custom_background"
private const val KEY_CUSTOM_ON_BG = "custom_onBackground"
private const val KEY_CUSTOM_TEXT_MUTED = "custom_textMuted"
private const val KEY_CUSTOM_SURFACE = "custom_surface"
private const val KEY_CUSTOM_SURFACE_VARIANT = "custom_surfaceVariant"
private const val KEY_CUSTOM_ACCENT = "custom_accent"
private const val KEY_CUSTOM_CARD_BORDER = "custom_cardBorder"
private const val KEY_CUSTOM_OVERLAY_SCRIM = "custom_overlayScrim"
private const val KEY_BRIGHTNESS_MODE = "brightness_mode"

/** The palette the app shipped with before the wallpaper accent became the default. */
private const val STOCK_DARK_PALETTE_ID = "oneui_blue"

/** How the app picks light or dark: Automatic follows the phone, Light and Dark pin it. */
enum class BrightnessMode { SYSTEM, LIGHT, DARK }

/** Full theme bundle handed out via [LocalAppTheme] for structural (non-color) styling decisions. */
data class AppTheme(
    val language: DesignLanguage,
    val palette: AppColorPalette,
    val shapes: AppShapeTokens
)

val LocalAppTheme = compositionLocalOf {
    AppTheme(DesignLanguage.SAMSUNG_ONEUI, presetsFor(DesignLanguage.SAMSUNG_ONEUI).first(), shapeTokensFor(DesignLanguage.SAMSUNG_ONEUI))
}

/**
 * Live-swappable app-wide color/shape state. Colors are exposed as plain (non-@Composable)
 * State-backed top-level properties in [Color.kt] under their original names (AmoledBlack,
 * PureWhite, ...) so every existing call site across the app keeps compiling unchanged while
 * still recomposing automatically whenever the user switches palette.
 */
object ThemeState {
    private val defaultPalette = presetsFor(DesignLanguage.SAMSUNG_ONEUI).first()

    var background by mutableStateOf(defaultPalette.background)
        private set
    var onBackground by mutableStateOf(defaultPalette.onBackground)
        private set
    var textMuted by mutableStateOf(defaultPalette.textMuted)
        private set
    var surface by mutableStateOf(defaultPalette.surface)
        private set
    var surfaceVariant by mutableStateOf(defaultPalette.surfaceVariant)
        private set
    var accent by mutableStateOf(defaultPalette.accent)
        private set
    var cardBorder by mutableStateOf(defaultPalette.cardBorder)
        private set
    var overlayScrim by mutableStateOf(defaultPalette.overlayScrim)
        private set

    val designLanguage: DesignLanguage = DesignLanguage.SAMSUNG_ONEUI
    var palette by mutableStateOf(defaultPalette)
        private set

    /** Automatic (follow the phone) unless the user pinned light or dark on the Appearance page. */
    var brightnessMode by mutableStateOf(BrightnessMode.SYSTEM)
        private set
    var shapes by mutableStateOf(shapeTokensFor(DesignLanguage.SAMSUNG_ONEUI))
        private set

    fun currentTheme(): AppTheme = AppTheme(designLanguage, palette, shapes)

    /** Reads the persisted palette at app start. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        val savedPaletteId = prefs.getString(KEY_PALETTE_ID, null)
        val language = DesignLanguage.SAMSUNG_ONEUI
        // Builds before automatic mode existed have no brightness key. Its absence is used once, to
        // keep the brightness an upgraded install was already showing instead of following the phone.
        val seenBrightnessChoice = prefs.contains(KEY_BRIGHTNESS_MODE)
        brightnessMode = runCatching {
            BrightnessMode.valueOf(
                prefs.getString(KEY_BRIGHTNESS_MODE, null) ?: BrightnessMode.SYSTEM.name
            )
        }.getOrDefault(BrightnessMode.SYSTEM)

        val savedIsLight = when {
            seenBrightnessChoice -> preferredLight(context)
            savedPaletteId == null -> preferredLight(context)
            savedPaletteId == CUSTOM_PALETTE_ID ->
                Color(prefs.getInt(KEY_CUSTOM_BG, Color.Black.toArgb())).luminance() > 0.5f
            else -> presetsFor(language).find { it.id == savedPaletteId }?.isLight ?: false
        }

        val takeWallpaperAccent = savedPaletteId == null ||
            (!seenBrightnessChoice && savedPaletteId == STOCK_DARK_PALETTE_ID)
        if (takeWallpaperAccent) {
            // A fresh install starts on the phone's own wallpaper colour, not a fixed blue.
            val base = presetsFor(language, savedIsLight).first()
            val wallpaper = WallpaperAccents.read(context, savedIsLight).firstOrNull()
            if (!seenBrightnessChoice) {
                brightnessMode =
                    if (savedPaletteId == null) BrightnessMode.SYSTEM else BrightnessMode.DARK
            }
            if (wallpaper == null) {
                apply(base, persist = false, context = context)
            } else {
                WallpaperAccentMemory.remember(context, wallpaper)
                apply(
                    base.copy(id = CUSTOM_PALETTE_ID, label = "Custom palette", accent = wallpaper),
                    persist = true,
                    context = context
                )
                // Only now is the migration done; without a wallpaper colour the next launch tries
                // again instead of locking the stock blue in for good.
                prefs.edit().putString(KEY_BRIGHTNESS_MODE, brightnessMode.name).apply()
            }
            return
        }

        val resolved = if (savedPaletteId == CUSTOM_PALETTE_ID) {
            val fallback = presetsFor(language, savedIsLight).first()
            AppColorPalette(
                id = CUSTOM_PALETTE_ID,
                label = "Custom palette",
                background = Color(prefs.getInt(KEY_CUSTOM_BG, fallback.background.toArgb())),
                onBackground = Color(prefs.getInt(KEY_CUSTOM_ON_BG, fallback.onBackground.toArgb())),
                textMuted = Color(prefs.getInt(KEY_CUSTOM_TEXT_MUTED, fallback.textMuted.toArgb())),
                surface = Color(prefs.getInt(KEY_CUSTOM_SURFACE, fallback.surface.toArgb())),
                surfaceVariant = Color(prefs.getInt(KEY_CUSTOM_SURFACE_VARIANT, fallback.surfaceVariant.toArgb())),
                accent = Color(prefs.getInt(KEY_CUSTOM_ACCENT, fallback.accent.toArgb())),
                cardBorder = Color(prefs.getInt(KEY_CUSTOM_CARD_BORDER, fallback.cardBorder.toArgb())),
                overlayScrim = Color(prefs.getInt(KEY_CUSTOM_OVERLAY_SCRIM, fallback.overlayScrim.toArgb()))
            )
        } else {
            presetsFor(language).find { it.id == savedPaletteId } ?: presetsFor(language, savedIsLight).first()
        }
        apply(resolved, persist = false, context = context)
        if (!seenBrightnessChoice) {
            brightnessMode = if (savedIsLight) BrightnessMode.LIGHT else BrightnessMode.DARK
            prefs.edit().putString(KEY_BRIGHTNESS_MODE, brightnessMode.name).apply()
        }
        // The saved palette is only the last snapshot; in automatic mode follow the phone now (and
        // whenever the phone switches, through [syncWithSystem]).
        syncWithSystem(context)
    }

    /** The user picked Automatic, Light or Dark on the Appearance page. */
    fun setBrightnessMode(context: Context, mode: BrightnessMode) {
        brightnessMode = mode
        context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BRIGHTNESS_MODE, mode.name)
            .apply()
        applyBrightness(preferredLight(context))
        persistToPrefs(context, palette)
    }

    /** Re-applies the phone's brightness when Automatic is on; called on a configuration change. */
    fun syncWithSystem(context: Context) {
        if (brightnessMode != BrightnessMode.SYSTEM) return
        applyBrightness(preferredLight(context))
    }

    /** Whether the palette should be light right now, given [brightnessMode]. */
    fun preferredLight(context: Context): Boolean = when (brightnessMode) {
        BrightnessMode.LIGHT -> true
        BrightnessMode.DARK -> false
        BrightnessMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
                Configuration.UI_MODE_NIGHT_YES
    }

    /** Switches light/dark in place, keeping the accent and the preset slot. */
    private fun applyBrightness(light: Boolean) {
        val current = palette
        if (current.isLight == light) return
        val target = presetsFor(designLanguage, light)
        val next = if (current.id == CUSTOM_PALETTE_ID) {
            target.first().copy(id = CUSTOM_PALETTE_ID, label = current.label, accent = current.accent)
        } else {
            val slot = presetsFor(designLanguage, current.isLight).indexOfFirst { it.id == current.id }
            target.getOrElse(slot) { target.first() }
        }
        apply(next, persist = false)
    }

    /** Switches the live theme immediately and (by default) persists the choice. */
    fun apply(newPalette: AppColorPalette, persist: Boolean = true, context: Context? = null) {
        // The user's pick is persisted as chosen; on screen the accent is kept readable.
        val live = newPalette.withReadableAccent()
        palette = live
        shapes = shapeTokensFor(designLanguage)
        background = live.background
        onBackground = live.onBackground
        textMuted = live.textMuted
        surface = live.surface
        surfaceVariant = live.surfaceVariant
        accent = live.accent
        cardBorder = live.cardBorder
        overlayScrim = live.overlayScrim

        if (persist && context != null) {
            persistToPrefs(context, newPalette)
        }
    }

    private fun persistToPrefs(context: Context, palette: AppColorPalette) {
        context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_PALETTE_ID, palette.id)
            if (palette.id == CUSTOM_PALETTE_ID) {
                putInt(KEY_CUSTOM_BG, palette.background.toArgb())
                putInt(KEY_CUSTOM_ON_BG, palette.onBackground.toArgb())
                putInt(KEY_CUSTOM_TEXT_MUTED, palette.textMuted.toArgb())
                putInt(KEY_CUSTOM_SURFACE, palette.surface.toArgb())
                putInt(KEY_CUSTOM_SURFACE_VARIANT, palette.surfaceVariant.toArgb())
                putInt(KEY_CUSTOM_ACCENT, palette.accent.toArgb())
                putInt(KEY_CUSTOM_CARD_BORDER, palette.cardBorder.toArgb())
                putInt(KEY_CUSTOM_OVERLAY_SCRIM, palette.overlayScrim.toArgb())
            }
        }.apply()
    }
}
