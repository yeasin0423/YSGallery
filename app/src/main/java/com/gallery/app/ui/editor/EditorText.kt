package com.gallery.app.ui.editor

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
    val id: Long = 0L
)

private var nextTextId = 0L

/** Creates a label with a fresh [TextOverlay.id]; copies made with `copy` keep their id. */
fun createTextOverlay(text: String, center: Offset, color: Color, size: Float): TextOverlay =
    TextOverlay(text = text, center = center, color = color, size = size, id = ++nextTextId)

/** The part of the text selection box a drag grabbed. */
internal enum class TextHandle { None, Move, Resize, Rotate }

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

    fun contains(point: Offset): Boolean {
        val local = toLocal(point)
        return abs(local.x) <= halfWidth && abs(local.y) <= halfHeight
    }

    /** Which control sits under [point], given the radius a finger may miss by. */
    fun hitTest(point: Offset, touchRadius: Float): TextHandle {
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
