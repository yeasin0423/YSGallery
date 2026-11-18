package com.gallery.app.ui.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a [CollageLayout] onto a bitmap for the live preview and for saving. */
object CollageRenderer {

    /** Fraction of the page left empty around the photos. */
    private const val MarginFraction = 0.035f

    /** Fraction of the page used as the gap between two photos. */
    private const val GapFraction = 0.014f

    /** Corner radius as a fraction of the page's shorter side. */
    private const val CornerFraction = 0.012f

    /**
     * Renders [layout] with [bitmaps] at [width] x [height]. A null bitmap (still loading) leaves a
     * light placeholder in its cell. The caller owns the returned bitmap.
     */
    fun render(
        layout: CollageLayout,
        bitmaps: List<Bitmap?>,
        width: Int,
        height: Int,
        background: Int = Color.WHITE
    ): Bitmap {
        val output = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val corner = min(width, height) * CornerFraction
        slotRects(layout, width.toFloat(), height.toFloat()).forEachIndexed { index, destination ->
            val bitmap = bitmaps.getOrNull(index)
            if (bitmap == null || bitmap.isRecycled) {
                canvas.drawRoundRect(destination, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFE4E4E4.toInt()
                })
                return@forEachIndexed
            }
            val save = canvas.save()
            val clip = Path().apply {
                addRoundRect(destination, corner, corner, Path.Direction.CW)
            }
            canvas.clipPath(clip)
            canvas.drawBitmap(
                bitmap,
                centerCropSource(bitmap, destination.width() / destination.height()),
                destination,
                paint
            )
            canvas.restoreToCount(save)
        }
        return output
    }

    /** Where each cell lands on a page of [width] x [height], margin and gaps included. */
    fun slotRects(layout: CollageLayout, width: Float, height: Float): List<RectF> {
        val margin = min(width, height) * MarginFraction
        val gap = min(width, height) * GapFraction
        val left = margin
        val top = margin
        val right = width - margin
        val bottom = height - margin
        return layout.slots.map { slot ->
            RectF(
                left + slot.left * (right - left) + gap / 2f,
                top + slot.top * (bottom - top) + gap / 2f,
                left + slot.right * (right - left) - gap / 2f,
                top + slot.bottom * (bottom - top) - gap / 2f
            )
        }
    }

    /** The largest centre crop of [source] that matches [targetAspect] (width / height). */
    private fun centerCropSource(source: Bitmap, targetAspect: Float): Rect {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return Rect(0, 0, 1, 1)
        val aspect = width.toFloat() / height
        return if (aspect > targetAspect) {
            val cropWidth = (height * targetAspect).roundToInt().coerceIn(1, width)
            val x = (width - cropWidth) / 2
            Rect(x, 0, x + cropWidth, height)
        } else {
            val cropHeight = (width / targetAspect).roundToInt().coerceIn(1, height)
            val y = (height - cropHeight) / 2
            Rect(0, y, width, y + cropHeight)
        }
    }
}
