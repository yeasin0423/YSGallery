package com.gallery.app.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A text label painted on the photo. [center] lives in normalized coordinates of the rotated/flipped
 * frame and [size] is a fraction of that frame's shorter side, so the label follows rotation, mirroring
 * and cropping the same way the ink does.
 *
 * [id] keeps each label addressable while the list around it changes, so undo, selection and the
 * selection box never chase a shifting list index.
 */
data class TextOverlay(
    val text: String,
    val center: Offset,
    val color: Color,
    /** Font size as a fraction of the frame's shorter side. */
    val size: Float,
    /** Clockwise rotation in degrees, drawn around [center]. */
    val rotation: Float = 0f,
    val font: TextFont = TextFont.Bold,
    val id: Long = 0L
)

/** Decoration beyond the typeface: plain fill, outline only, a coloured glow, or a drop shadow. */
enum class TextRender { Fill, Stroke, Glow, Shadow }

/** The typefaces the text panel offers. Bold is the default, as labels were always drawn bold. */
enum class TextFont(val render: TextRender = TextRender.Fill) {
    Bold, Regular, Serif, Handwritten, Italic, BoldItalic,
    SerifBold, Condensed, Light, Medium, Black, Monospace,
    Thin, CondensedLight, SmallCaps, SerifMono, Casual,
    Elegant, Editorial, Marker, Typewriter,
    Outline(TextRender.Stroke), Neon(TextRender.Glow), Shadow(TextRender.Shadow)
}

/** The Android typeface for a label style; the preview and the export both use this one mapping. */
fun TextFont.typeface(): Typeface = when (this) {
    TextFont.Bold -> Typeface.DEFAULT_BOLD
    TextFont.Regular -> Typeface.DEFAULT
    TextFont.Serif -> Typeface.SERIF
    // "cursive" resolves to the device's handwriting face on every Android version.
    TextFont.Handwritten -> Typeface.create("cursive", Typeface.NORMAL)
    TextFont.Italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    TextFont.BoldItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
    TextFont.SerifBold -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
    // Platform family names every Android release ships, so no font files are needed.
    TextFont.Condensed -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    TextFont.Light -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    TextFont.Medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    TextFont.Black -> Typeface.create("sans-serif-black", Typeface.NORMAL)
    TextFont.Monospace -> Typeface.MONOSPACE
    TextFont.Thin -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
    TextFont.CondensedLight -> Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)
    TextFont.SmallCaps -> Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
    TextFont.SerifMono -> Typeface.create("serif-monospace", Typeface.NORMAL)
    TextFont.Casual -> Typeface.create("casual", Typeface.NORMAL)
    TextFont.Elegant -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    TextFont.Editorial -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
    TextFont.Marker -> Typeface.create("casual", Typeface.BOLD)
    TextFont.Typewriter -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    TextFont.Outline, TextFont.Neon, TextFont.Shadow -> Typeface.DEFAULT_BOLD
}

/**
 * Applies the font's typeface and decoration to [paint]. The live preview and the exporter both call
 * this, so an outline, glow or shadow looks identical in the saved file.
 */
fun Paint.applyTextFont(font: TextFont, textSize: Float) {
    typeface = font.typeface()
    this.textSize = textSize
    isAntiAlias = true
    clearShadowLayer()
    when (font.render) {
        TextRender.Fill -> style = Paint.Style.FILL
        TextRender.Stroke -> {
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.05f
        }
        TextRender.Glow -> {
            style = Paint.Style.FILL
            setShadowLayer(textSize * 0.16f, 0f, 0f, color)
        }
        TextRender.Shadow -> {
            style = Paint.Style.FILL
            setShadowLayer(
                textSize * 0.09f,
                textSize * 0.03f,
                textSize * 0.05f,
                android.graphics.Color.argb(190, 0, 0, 0)
            )
        }
    }
}

private var nextTextId = 0L

/** Creates a label with a fresh [TextOverlay.id]; copies made with `copy` keep their id. */
fun createTextOverlay(
    text: String,
    center: Offset,
    color: Color,
    size: Float,
    font: TextFont = TextFont.Bold
): TextOverlay =
    TextOverlay(
        text = text,
        center = center,
        color = color,
        size = size,
        font = font,
        id = ++nextTextId
    )

/** The part of the text selection box a drag grabbed. */
internal enum class TextHandle { None, Move, Resize, Rotate, Delete }

/**
 * The label's selection box in view pixels: a rectangle of [halfWidth] x [halfHeight] around [center],
 * turned by [rotation] degrees, with the rotate handle floating [handleGap] above its top edge.
 */
internal data class TextBox(
    val center: Offset,
    val halfWidth: Float,
    val halfHeight: Float,
    val rotation: Float,
    val handleGap: Float
) {
    /** Rotates [offset] clockwise by [degrees] around the origin. */
    private fun rotate(offset: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        return Offset(offset.x * cos - offset.y * sin, offset.x * sin + offset.y * cos)
    }

    /** Turns [point] back into the box's unrotated space, relative to its centre. */
    fun toLocal(point: Offset): Offset = rotate(point - center, -rotation)

    /** The four corners in unrotated box space, relative to the centre. */
    fun localCorners(): List<Offset> = listOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight)
    )

    fun corners(): List<Offset> = localCorners().map { center + rotate(it, rotation) }

    fun rotateHandle(): Offset =
        center + rotate(Offset(0f, -halfHeight - handleGap), rotation)

    /** The remove cross, just outside the top-left corner. */
    fun deleteHandle(): Offset =
        center + rotate(Offset(-halfWidth - handleGap * 0.8f, -halfHeight - handleGap * 0.8f), rotation)

    fun contains(point: Offset): Boolean {
        val local = toLocal(point)
        return abs(local.x) <= halfWidth && abs(local.y) <= halfHeight
    }

    /** Which control sits under [point], given the radius a finger may miss by. */
    fun hitTest(point: Offset, touchRadius: Float): TextHandle {
        if ((point - deleteHandle()).getDistance() <= touchRadius) return TextHandle.Delete
        if ((point - rotateHandle()).getDistance() <= touchRadius) return TextHandle.Rotate
        val local = toLocal(point)
        if (localCorners().any { (it - local).getDistance() <= touchRadius }) return TextHandle.Resize
        return if (contains(point)) TextHandle.Move else TextHandle.None
    }
}

// The size slider is gone; dragging a corner covers the whole range. The bounds only stop a label
// from collapsing to nothing or growing until its handles leave the screen.
const val MinTextSize = 0.015f
const val MaxTextSize = 1f
const val DefaultTextSize = 0.07f

fun TextOverlay.rotateClockwise() = copy(
    center = Offset(1f - center.y, center.x),
    rotation = (rotation + 90f) % 360f
)

fun TextOverlay.rotateCounterClockwise() = copy(
    center = Offset(center.y, 1f - center.x),
    rotation = (rotation + 270f) % 360f
)

fun TextOverlay.flipHorizontal() = copy(
    center = Offset(1f - center.x, center.y),
    rotation = (360f - rotation) % 360f
)

fun TextOverlay.flipVertical() = copy(
    center = Offset(center.x, 1f - center.y),
    rotation = (360f - rotation) % 360f
)
