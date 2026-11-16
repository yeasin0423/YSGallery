package com.gallery.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import kotlin.math.min
import kotlin.math.roundToInt

/** Which tool the Draw tab paints with. */
enum class DrawMode { Brush, Eraser, Blur, Mosaic }

/** How a blur or mosaic patch is applied: a dragged band or one round patch at the finger. */
enum class BlurShape { Stroke, Circle }

/**
 * One blurred or pixelated band, stored in the same normalized frame coordinates as [DrawStroke] so it
 * follows rotation, mirroring and cropping with the photo. A single point renders as a round patch.
 *
 * [pixelate] picks the treatment: a softened copy or a mosaic copy of the frame under the same mask.
 */
data class BlurStroke(
    val points: List<Offset>,
    /** Band width as a fraction of the frame's shorter side. */
    val width: Float,
    /** True for the mosaic brush, false for the blur brush. */
    val pixelate: Boolean = false
)

fun BlurStroke.rotateClockwise() = copy(points = points.map { Offset(1f - it.y, it.x) })

fun BlurStroke.rotateCounterClockwise() = copy(points = points.map { Offset(it.y, 1f - it.x) })

fun BlurStroke.flipHorizontal() = copy(points = points.map { Offset(1f - it.x, it.y) })

fun BlurStroke.flipVertical() = copy(points = points.map { Offset(it.x, 1f - it.y) })

/**
 * The shortest side a blurred copy is reduced to before the canvas stretches it back over the photo.
 * Bilinear filtering of that small copy is a cheap stand-in for a gaussian pass: it keeps the preview
 * responsive, keeps the export's memory flat, and avoids RenderEffect (API 31 and a hardware canvas).
 */
private const val BlurTargetSide = 96

/**
 * A softened copy of [source] whose shortest side is about [BlurTargetSide] pixels. Draw it stretched
 * over the full frame with bitmap filtering on; the reduction is what blurs it.
 */
fun blurredBitmap(source: Bitmap): Bitmap {
    val shortest = min(source.width, source.height).coerceAtLeast(1)
    if (shortest <= BlurTargetSide) return Bitmap.createBitmap(source)
    val scale = BlurTargetSide.toFloat() / shortest
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).roundToInt().coerceAtLeast(1),
        (source.height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

/**
 * The shortest side a mosaic copy is reduced to. Stretching it back with bitmap filtering off turns
 * every source pixel into one flat square, so the shortest side is also the block count.
 */
private const val MosaicTargetSide = 34

/** A pixelated copy of [source]: tiny and drawn stretched with filtering off. */
fun pixelatedBitmap(source: Bitmap): Bitmap {
    val shortest = min(source.width, source.height).coerceAtLeast(1)
    if (shortest <= MosaicTargetSide) return Bitmap.createBitmap(source)
    val scale = MosaicTargetSide.toFloat() / shortest
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).roundToInt().coerceAtLeast(1),
        (source.height * scale).roundToInt().coerceAtLeast(1),
        false
    )
}

/**
 * Builds one clip path covering every band in [strokes]. A stroke is expanded into an outline with
 * round caps so the mask matches what the finger painted. Normalized points map to pixels with
 * `origin + point * scale`, which lets the preview and the exporter share the same code.
 */
fun blurMaskPath(
    strokes: List<BlurStroke>,
    originX: Float,
    originY: Float,
    scaleX: Float,
    scaleY: Float,
    strokeScale: Float
): Path {
    val mask = Path()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val width = (stroke.width * strokeScale).coerceAtLeast(1f)
        paint.strokeWidth = width
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            mask.addCircle(
                originX + point.x * scaleX,
                originY + point.y * scaleY,
                width / 2f,
                Path.Direction.CW
            )
            return@forEach
        }
        val line = Path()
        stroke.points.forEachIndexed { index, point ->
            val x = originX + point.x * scaleX
            val y = originY + point.y * scaleY
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        // Expand this band on its own, then add it to the mask, so one fill can never replace another.
        val outline = Path()
        if (paint.getFillPath(line, outline) && !outline.isEmpty) mask.addPath(outline)
    }
    return mask
}
