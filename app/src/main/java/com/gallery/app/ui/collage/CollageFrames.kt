package com.gallery.app.ui.collage

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * How one photo sits inside its cell. Offsets are fractions of the cell's width and height, so they
 * survive preview-to-export scaling; [rotation] is in degrees.
 */
data class CollageFrame(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f
) {
    /** True when the photo still fills its cell without panning (a base frame can be dragged out). */
    val atBase: Boolean get() = zoom <= 1.02f && abs(rotation) <= 0.5f

    /**
     * How far the photo may slide without exposing a corner. Only the user's zoom counts: the extra
     * scale that hides a rotation's corners is already used up by the rotation itself.
     */
    private val panLimit: Float get() = ((zoom - 1f) / 2f).coerceAtLeast(0f)

    /** Moves the photo, keeping the cell covered. */
    fun panned(dx: Float, dy: Float, cellWidth: Float, cellHeight: Float): CollageFrame = copy(
        offsetX = (offsetX + dx).coerceIn(-panLimit, panLimit),
        offsetY = (offsetY + dy).coerceIn(-panLimit, panLimit)
    )

    /** Pulls the photo back inside the cell after a zoom or rotation change. */
    fun clamped(cellWidth: Float, cellHeight: Float): CollageFrame = copy(
        offsetX = offsetX.coerceIn(-panLimit, panLimit),
        offsetY = offsetY.coerceIn(-panLimit, panLimit)
    )
}

/**
 * The scale the bitmap is drawn at: [CollageFrame.zoom] on top of the extra cover needed to hide the
 * empty corners a rotation would otherwise expose.
 */
fun CollageFrame.effectiveScale(cellWidth: Float, cellHeight: Float): Float {
    if (cellWidth <= 0f || cellHeight <= 0f) return zoom
    val radians = Math.toRadians(rotation.toDouble())
    val c = abs(cos(radians)).toFloat()
    val s = abs(sin(radians)).toFloat()
    val cover = max(
        (c * cellWidth + s * cellHeight) / cellWidth,
        (s * cellWidth + c * cellHeight) / cellHeight
    )
    return zoom * max(1f, cover)
}
