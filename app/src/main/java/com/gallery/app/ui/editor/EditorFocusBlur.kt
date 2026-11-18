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

/**
 * The softened copy is built on a copy whose short side is at most this many pixels. The blur keeps
 * its radius in this space, so cost does not depend on the photo's megapixels, and the upscale back
 * to the frame is smooth because the pixels really are blurred rather than merely enlarged.
 */
private const val BlurWorkingSide = 384f

/** Three box passes approximate a Gaussian closely enough to read as a lens, not as blocks. */
private const val BoxPasses = 3

/** Radius of one box pass, in working-copy pixels, at the heaviest strength. */
private const val MaxBoxRadius = 20f

/**
 * A smoothly softened copy of [source] for [strength]. Unlike a plain downscale, the pixels are
 * actually filtered: a separable box blur runs three times over a working copy, so the result has
 * soft gradients instead of the noisy, blocky look a stretched thumbnail gives.
 */
fun focusBlurredBitmap(source: Bitmap, strength: Float): Bitmap {
    val amount = strength.coerceIn(0f, 1f)
    if (amount <= 0.01f) return Bitmap.createBitmap(source)
    val shortest = min(source.width, source.height).coerceAtLeast(1)
    val working = if (shortest > BlurWorkingSide) {
        val scale = BlurWorkingSide / shortest
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    val radius = (1f + MaxBoxRadius * amount).roundToInt().coerceAtLeast(1)
    val blurred = boxBlur(working, radius)
    if (working !== source) working.recycle()
    return blurred
}

/**
 * Separable box blur over premultiplied channels, so semi-transparent edges fade instead of turning
 * dark. Each plane is blurred horizontally and vertically [BoxPasses] times; edges replicate the
 * border pixel, which avoids the bright rim a zero-padded convolution leaves behind.
 */
private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source.copy(Bitmap.Config.ARGB_8888, false)
    if (width < 3 || height < 3) return source.copy(Bitmap.Config.ARGB_8888, false)
    val count = width * height
    val alpha = IntArray(count)
    val red = IntArray(count)
    val green = IntArray(count)
    val blue = IntArray(count)
    val pixels = IntArray(count)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in 0 until count) {
        val color = pixels[index]
        val a = (color ushr 24) and 0xFF
        alpha[index] = a
        red[index] = ((color shr 16) and 0xFF) * a
        green[index] = ((color shr 8) and 0xFF) * a
        blue[index] = (color and 0xFF) * a
    }
    val scratch = IntArray(count)
    val planes = arrayOf(alpha, red, green, blue)
    for (plane in planes) {
        repeat(BoxPasses) {
            blurPlane(plane, scratch, width, height, radius, horizontal = true)
            blurPlane(scratch, plane, width, height, radius, horizontal = false)
        }
    }
    for (index in 0 until count) {
        val a = alpha[index]
        if (a <= 0) {
            pixels[index] = 0
            continue
        }
        val r = ((red[index] + a / 2) / a).coerceIn(0, 255)
        val g = ((green[index] + a / 2) / a).coerceIn(0, 255)
        val b = ((blue[index] + a / 2) / a).coerceIn(0, 255)
        pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

/** One sliding-window pass; [dst] may not be the same array as [src]. */
private fun blurPlane(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean
) {
    val span = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val step = if (horizontal) 1 else width
    val window = (radius * 2 + 1).toLong()
    for (line in 0 until lines) {
        val start = if (horizontal) line * width else line
        var sum = 0L
        for (offset in -radius..radius) {
            sum += src[start + offset.coerceIn(0, span - 1) * step]
        }
        for (position in 0 until span) {
            dst[start + position * step] = (sum / window).toInt()
            val leaving = (position - radius).coerceIn(0, span - 1)
            val entering = (position + radius + 1).coerceIn(0, span - 1)
            sum += src[start + entering * step] - src[start + leaving * step]
        }
    }
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
