package com.gallery.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The portrait-style background blur: a sharp subject inside a feathered circle and a softened
 * background everywhere else. Coordinates are normalized to the cropped frame, so the circle stays
 * where it was put when the photo is cropped, and rotation and mirroring carry it along like the ink.
 *
 * [focusRadius] is the sharp circle's radius as a fraction of the cropped frame's shorter side.
 * [strength] is the blur radius: 0 leaves the photo sharp and 1 is the heaviest softening.
 */
data class FocusBlur(
    val center: Offset = Offset(0.5f, 0.5f),
    val focusRadius: Float = 0.32f,
    val strength: Float = 0f
) {
    val active: Boolean get() = strength > 0.01f
}

fun FocusBlur.rotateClockwise() = copy(center = Offset(1f - center.y, center.x))

fun FocusBlur.rotateCounterClockwise() = copy(center = Offset(center.y, 1f - center.x))

fun FocusBlur.flipHorizontal() = copy(center = Offset(1f - center.x, center.y))

fun FocusBlur.flipVertical() = copy(center = Offset(center.x, 1f - center.y))

/** How small the sharp circle can be made, as a fraction of the cropped frame's shorter side. */
const val MinFocusRadius = 0.08f

/** The largest sharp circle; beyond this the background blur has nothing left to soften. */
const val MaxFocusRadius = 0.55f

/** The blur used when the focus circle is first moved, so placing it shows the effect at once. */
const val DefaultFocusStrength = 0.45f

/** Shortest-side targets for the softened copy at the lightest and heaviest settings. */
private const val LightBlurSide = 220f
private const val HeavyBlurSide = 26f

/**
 * A softened copy of [source] for [strength]. Like [blurredBitmap] it shrinks the frame and lets
 * bilinear filtering stretch it back, which keeps the preview cheap; [strength] only changes how far
 * it is reduced, so a heavier blur is a smaller copy.
 */
fun focusBlurredBitmap(source: Bitmap, strength: Float): Bitmap {
    val amount = strength.coerceIn(0f, 1f)
    val shortest = min(source.width, source.height).coerceAtLeast(1)
    val target = (LightBlurSide + (HeavyBlurSide - LightBlurSide) * amount)
        .roundToInt()
        .coerceAtLeast(8)
        .coerceAtMost(shortest)
    if (target >= shortest) return Bitmap.createBitmap(source)
    val scale = target.toFloat() / shortest
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).roundToInt().coerceAtLeast(1),
        (source.height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

/**
 * Draws [source] over [destination] but keeps only the feathered circle around [centerX]/[centerY].
 * The circle is fully sharp in the middle and fades into the blurred background at [radius], which is
 * what makes the edge read as a lens rather than a cut-out.
 */
fun drawFocusedSubject(
    canvas: Canvas,
    source: Bitmap,
    destination: RectF,
    paint: Paint,
    centerX: Float,
    centerY: Float,
    radius: Float
) {
    val safeRadius = radius.coerceAtLeast(1f)
    val layer = canvas.saveLayer(destination, null)
    canvas.drawBitmap(source, null, destination, paint)
    val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            centerX,
            centerY,
            safeRadius,
            intArrayOf(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    // Fill the whole destination with the mask: inside the circle it is opaque and it fades to
    // transparent at the edge, so the sharp copy is erased everywhere the blur should show.
    canvas.drawRect(destination, mask)
    canvas.restoreToCount(layer)
}

/**
 * The export-side focus blur: [source] is the cropped frame, so the normalized circle maps straight
 * onto its pixels. Returns [source] itself when the effect is off.
 */
fun applyFocusBlur(source: Bitmap, focus: FocusBlur): Bitmap {
    if (!focus.active) return source
    val width = source.width.coerceAtLeast(1)
    val height = source.height.coerceAtLeast(1)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val blurred = focusBlurredBitmap(source, focus.strength)
    canvas.drawBitmap(blurred, null, destination, paint)
    blurred.recycle()
    drawFocusedSubject(
        canvas = canvas,
        source = source,
        destination = destination,
        paint = paint,
        centerX = focus.center.x * width,
        centerY = focus.center.y * height,
        radius = focus.focusRadius * min(width, height)
    )
    return output
}
