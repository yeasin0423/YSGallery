package com.gallery.app.ui.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a [CollageLayout] onto a bitmap for the live preview and for saving. */
object CollageRenderer {

    /** Corner radius of the photo cells, as a fraction of the page's shorter side. */
    private const val DefaultCorner = 0.012f

    /**
     * Renders [layout] with [bitmaps] at [width] x [height]. A null bitmap (still loading) leaves a
     * light placeholder in its cell. The caller owns the returned bitmap.
     */
    fun render(
        layout: CollageLayout,
        bitmaps: List<Bitmap?>,
        width: Int,
        height: Int
    ): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val output = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawBackground(canvas, layout, safeWidth.toFloat(), safeHeight.toFloat())
        drawBands(canvas, layout, safeWidth.toFloat(), safeHeight.toFloat())

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val corner = min(safeWidth, safeHeight) * DefaultCorner
        slotRects(layout, safeWidth.toFloat(), safeHeight.toFloat()).forEachIndexed { index, rect ->
            val slotShape = layout.slots.getOrNull(index)?.shape ?: layout.shape
            drawPhoto(
                canvas = canvas,
                bitmap = bitmaps.getOrNull(index),
                rect = rect,
                shape = slotShape,
                frame = layout.frame,
                frameWidth = layout.frameWidth,
                corner = corner,
                paint = paint
            )
        }

        drawDecorations(canvas, layout, safeWidth.toFloat(), safeHeight.toFloat())
        drawTexts(canvas, layout, safeWidth.toFloat(), safeHeight.toFloat())
        return output
    }

    private fun drawBackground(canvas: Canvas, layout: CollageLayout, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (layout.background == layout.backgroundBottom) {
            paint.color = layout.background
        } else {
            paint.shader = LinearGradient(
                0f, 0f, 0f, height,
                layout.background,
                layout.backgroundBottom,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawBands(canvas: Canvas, layout: CollageLayout, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        layout.bands.forEach { band ->
            paint.color = band.color
            canvas.drawRect(
                band.left * width,
                band.top * height,
                band.right * width,
                band.bottom * height,
                paint
            )
        }
    }

    private fun drawPhoto(
        canvas: Canvas,
        bitmap: Bitmap?,
        rect: RectF,
        shape: CollageShape,
        frame: Int?,
        frameWidth: Float,
        corner: Float,
        paint: Paint
    ) {
        val area = shapeRect(rect, shape)
        if (frame != null) {
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = frame }
            canvas.drawPath(shapePath(area, shape, corner), framePaint)
        }
        val inset = if (frame != null) min(area.width(), area.height()) * frameWidth else 0f
        val inner = RectF(
            area.left + inset,
            area.top + inset,
            area.right - inset,
            area.bottom - inset
        )
        val save = canvas.save()
        canvas.clipPath(shapePath(inner, shape, corner))
        if (bitmap == null || bitmap.isRecycled) {
            canvas.drawRect(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E4E4.toInt() })
        } else {
            canvas.drawBitmap(
                bitmap,
                centerCropSource(bitmap, inner.width() / inner.height()),
                inner,
                paint
            )
        }
        canvas.restoreToCount(save)
    }

    private fun drawDecorations(
        canvas: Canvas,
        layout: CollageLayout,
        width: Float,
        height: Float
    ) {
        layout.decors.forEach { decor ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = decor.size * width
                textAlign = Paint.Align.CENTER
            }
            val x = decor.x * width
            val y = decor.y * height
            val save = canvas.save()
            if (decor.rotation != 0f) canvas.rotate(decor.rotation, x, y)
            canvas.drawText(decor.emoji, x, y + paint.textSize * 0.35f, paint)
            canvas.restoreToCount(save)
        }
    }

    private fun drawTexts(canvas: Canvas, layout: CollageLayout, width: Float, height: Float) {
        layout.texts.forEach { text ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = text.color
                textSize = text.size * width
                typeface = typefaceFor(text.style)
                letterSpacing = text.letterSpacing
                textAlign = when (text.align) {
                    CollageAlign.Start -> Paint.Align.LEFT
                    CollageAlign.Center -> Paint.Align.CENTER
                    CollageAlign.End -> Paint.Align.RIGHT
                }
            }
            val left = text.left * width
            val right = text.right * width
            val boxTop = text.top * height
            val boxBottom = text.bottom * height
            val x = when (text.align) {
                CollageAlign.Start -> left
                CollageAlign.Center -> (left + right) / 2f
                CollageAlign.End -> right
            }
            val lines = text.text.split("\n")
            val lineHeight = paint.textSize * 1.24f
            val blockHeight = lineHeight * lines.size
            val metrics = paint.fontMetrics
            val firstBaseline =
                boxTop + (boxBottom - boxTop - blockHeight) / 2f - metrics.ascent

            val save = canvas.save()
            if (text.rotation != 0f) {
                canvas.rotate(text.rotation, (left + right) / 2f, (boxTop + boxBottom) / 2f)
            }
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, x, firstBaseline + index * lineHeight, paint)
            }
            canvas.restoreToCount(save)
        }
    }

    private fun typefaceFor(style: CollageTextStyle): Typeface = when (style) {
        CollageTextStyle.Serif -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        CollageTextStyle.SerifBold -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        CollageTextStyle.Typewriter -> Typeface.MONOSPACE
        CollageTextStyle.Sans -> Typeface.SANS_SERIF
        CollageTextStyle.SansLight -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        CollageTextStyle.Cursive -> Typeface.create("cursive", Typeface.NORMAL)
    }

    /**
     * Circle, heart, hexagon and diamond cells live inside a square centred on the slot, so they stay
     * round even though the page is not square. Rectangular cells use the slot as it is.
     */
    private fun shapeRect(rect: RectF, shape: CollageShape): RectF {
        if (shape == CollageShape.Rounded || shape == CollageShape.Square) return RectF(rect)
        val side = min(rect.width(), rect.height())
        val cx = rect.centerX()
        val cy = rect.centerY()
        return RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    }

    private fun shapePath(rect: RectF, shape: CollageShape, corner: Float): Path {
        val path = Path()
        when (shape) {
            CollageShape.Rounded -> path.addRoundRect(rect, corner, corner, Path.Direction.CW)
            CollageShape.Square -> path.addRect(rect, Path.Direction.CW)
            CollageShape.Circle -> path.addOval(rect, Path.Direction.CW)
            CollageShape.Diamond -> {
                path.moveTo(rect.centerX(), rect.top)
                path.lineTo(rect.right, rect.centerY())
                path.lineTo(rect.centerX(), rect.bottom)
                path.lineTo(rect.left, rect.centerY())
                path.close()
            }

            CollageShape.Hexagon -> {
                val quarter = rect.width() * 0.25f
                path.moveTo(rect.left, rect.centerY())
                path.lineTo(rect.left + quarter, rect.top)
                path.lineTo(rect.right - quarter, rect.top)
                path.lineTo(rect.right, rect.centerY())
                path.lineTo(rect.right - quarter, rect.bottom)
                path.lineTo(rect.left + quarter, rect.bottom)
                path.close()
            }

            CollageShape.Heart -> {
                val cx = rect.centerX()
                val width = rect.width()
                val height = rect.height()
                path.moveTo(cx, rect.bottom)
                path.cubicTo(
                    rect.left - width * 0.06f, rect.top + height * 0.70f,
                    rect.left + width * 0.02f, rect.top - height * 0.03f,
                    cx, rect.top + height * 0.28f
                )
                path.cubicTo(
                    rect.right - width * 0.02f, rect.top - height * 0.03f,
                    rect.right + width * 0.06f, rect.top + height * 0.70f,
                    cx, rect.bottom
                )
                path.close()
            }
        }
        return path
    }

    /** Where each cell lands on a page of [width] x [height], margin and gaps included. */
    fun slotRects(layout: CollageLayout, width: Float, height: Float): List<RectF> {
        val margin = min(width, height) * layout.margin
        val gap = min(width, height) * layout.gap
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
