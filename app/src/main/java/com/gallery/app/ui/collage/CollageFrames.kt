package com.gallery.app.ui.collage

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How one photo sits inside its cell. Offsets are fractions of the cell's width and height, so they
 * survive preview-to-export scaling; [rotation] is in degrees. At [zoom] 1 the photo is shown whole,
 * fitted inside the cell, rather than cropped to fill it.
 */
data class CollageFrame(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f
) {
    /** True when the photo still rests at its fitted base framing (a base frame can be dragged out). */
    val atBase: Boolean get() = zoom <= 1.02f && abs(rotation) <= 0.5f

    /** Moves the photo, keeping a zoomed photo from leaving a gap inside the cell. */
    fun panned(
        dx: Float,
        dy: Float,
        cellWidth: Float,
        cellHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ): CollageFrame {
        val (limitX, limitY) = panLimits(cellWidth, cellHeight, contentWidth, contentHeight)
        return copy(
            offsetX = (offsetX + dx).coerceIn(-limitX, limitX),
            offsetY = (offsetY + dy).coerceIn(-limitY, limitY)
        )
    }

    /** Pulls the photo back inside the cell after a zoom or rotation change. */
    fun clamped(
        cellWidth: Float,
        cellHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ): CollageFrame {
        val (limitX, limitY) = panLimits(cellWidth, cellHeight, contentWidth, contentHeight)
        return copy(
            offsetX = offsetX.coerceIn(-limitX, limitX),
            offsetY = offsetY.coerceIn(-limitY, limitY)
        )
    }

    /**
     * How far the photo may slide on each axis. The limits come from the rotated bounding box, so a
     * fitted photo cannot be dragged out of view, while a zoomed one can be positioned edge to edge.
     */
    private fun panLimits(
        cellWidth: Float,
        cellHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ): Pair<Float, Float> {
        if (cellWidth <= 0f || cellHeight <= 0f) return 0f to 0f
        val radians = Math.toRadians(rotation.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val width = contentWidth * zoom
        val height = contentHeight * zoom
        val boxWidth = width * c + height * s
        val boxHeight = height * c + width * s
        val limitX = (max(0f, boxWidth - cellWidth) / 2f) / cellWidth
        val limitY = (max(0f, boxHeight - cellHeight) / 2f) / cellHeight
        return limitX to limitY
    }
}

/**
 * The size of the whole photo when fitted inside a cell: the largest copy that fits on both axes.
 * The renderer and the gesture code share it so their pan limits agree.
 */
fun fitContent(
    cellWidth: Float,
    cellHeight: Float,
    sourceWidth: Int,
    sourceHeight: Int
): Pair<Float, Float> {
    if (cellWidth <= 0f || cellHeight <= 0f || sourceWidth <= 0 || sourceHeight <= 0) {
        return cellWidth to cellHeight
    }
    val scale = min(cellWidth / sourceWidth, cellHeight / sourceHeight)
    return sourceWidth * scale to sourceHeight * scale
}
