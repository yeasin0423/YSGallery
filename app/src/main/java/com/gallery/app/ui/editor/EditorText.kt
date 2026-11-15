package com.gallery.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * A text label painted on the photo. [center] lives in normalized coordinates of the rotated/flipped
 * frame and [size] is a fraction of that frame's shorter side, so the label follows rotation, mirroring
 * and cropping the same way the ink does.
 */
data class TextOverlay(
    val text: String,
    val center: Offset,
    val color: Color,
    /** Font size as a fraction of the frame's shorter side. */
    val size: Float
)

const val MinTextSize = 0.03f
const val MaxTextSize = 0.16f
const val DefaultTextSize = 0.07f

fun TextOverlay.rotateClockwise() = copy(center = Offset(1f - center.y, center.x))

fun TextOverlay.rotateCounterClockwise() = copy(center = Offset(center.y, 1f - center.x))

fun TextOverlay.flipHorizontal() = copy(center = Offset(1f - center.x, center.y))

fun TextOverlay.flipVertical() = copy(center = Offset(center.x, 1f - center.y))
