package com.gallery.app.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

/**
 * A decorative frame around the exported photo. Sizes are fractions of the photo's shorter side, so a
 * frame looks the same on a small screenshot and on a large camera photo.
 *
 * [background] also fills the border in the preview, which is drawn over the photo edges rather than
 * around them, so the preview keeps the photo the same size it always had. [lineColor] adds a thin
 * rule, [accentColor] paints film perforations or corner marks, and [caption] prints a small word in
 * the extra bottom strip of a polaroid-style frame.
 */
enum class EditorFrame(
    val background: Color,
    val borderFraction: Float,
    val extraBottomFraction: Float = 0f,
    val lineColor: Color? = null,
    val accentColor: Color = Color.White,
    val perforated: Boolean = false,
    val cornerMarks: Boolean = false,
    val caption: String? = null,
    val captionColor: Color = Color(0xFF6B6257)
) {
    None(Color.Transparent, 0f),
    White(Color.White, 0.035f),
    Black(Color.Black, 0.035f),
    DoubleLine(Color.White, 0.05f, lineColor = Color(0xFF303030)),
    Polaroid(Color.White, 0.04f, 0.16f),
    Gold(Color(0xFFF5E6C8), 0.05f, lineColor = Color(0xFFB08948)),
    Modern(Color(0xFF101010), 0.045f, lineColor = Color(0xFFF4F1EA)),
    Vintage(Color(0xFFF1E6D2), 0.055f, lineColor = Color(0xFF9A7350)),
    PolaroidBlack(Color(0xFF141414), 0.04f, 0.16f),
    Cinema(Color(0xFF0A0A0A), 0.06f, lineColor = Color(0xFFD8B25A)),
    Rose(Color(0xFFF9E2E9), 0.05f, lineColor = Color.White),
    Ocean(Color(0xFF0E3B43), 0.05f, lineColor = Color(0xFF9FE0D8)),
    Sunset(Color(0xFFF4A259), 0.055f, lineColor = Color(0xFF5C3A21)),
    Film(Color(0xFF141414), 0.085f, accentColor = Color(0xFFF7F2E8), perforated = true),
    Memories(
        Color(0xFFFFFCF5),
        0.04f,
        0.15f,
        caption = "MEMORIES",
        captionColor = Color(0xFF8A7F70)
    ),
    Today(
        Color(0xFFFFF4DC),
        0.04f,
        0.15f,
        caption = "TODAY",
        captionColor = Color(0xFF8A6B3F)
    ),
    Corner(Color(0xFF0F0F0F), 0.03f, accentColor = Color(0xFFF4F1EA), cornerMarks = true)
}

/** Border thickness in pixels of a photo whose shorter side is [shorter]. */
fun EditorFrame.borderPx(shorter: Float): Float = shorter * borderFraction

/** The extra strip a polaroid leaves under the photo, in pixels of the same scale. */
fun EditorFrame.extraBottomPx(shorter: Float): Float = shorter * extraBottomFraction

/**
 * Paints the frame inside [bounds] for the live preview: four strips along the edges, the optional
 * inner rule, film perforations, corner marks and a printed caption. The exporter draws the same
 * proportions around the cropped bitmap.
 */
fun DrawScope.drawEditorFrame(frame: EditorFrame, bounds: Rect) {
    if (frame == EditorFrame.None || bounds.width <= 0f || bounds.height <= 0f) return
    val shorter = min(bounds.width, bounds.height)
    val border = frame.borderPx(shorter)
    val bottom = border + frame.extraBottomPx(shorter)
    val middleHeight = (bounds.height - border - bottom).coerceAtLeast(0f)
    drawRect(frame.background, bounds.topLeft, Size(bounds.width, border))
    drawRect(
        color = frame.background,
        topLeft = Offset(bounds.left, bounds.bottom - bottom),
        size = Size(bounds.width, bottom)
    )
    drawRect(
        color = frame.background,
        topLeft = Offset(bounds.left, bounds.top + border),
        size = Size(border, middleHeight)
    )
    drawRect(
        color = frame.background,
        topLeft = Offset(bounds.right - border, bounds.top + border),
        size = Size(border, middleHeight)
    )
    frame.lineColor?.let { line ->
        val inset = border * 0.45f
        drawRect(
            color = line,
            topLeft = Offset(bounds.left + inset, bounds.top + inset),
            size = Size(bounds.width - inset * 2f, bounds.height - inset * 2f),
            style = Stroke(max(1f, border * 0.12f))
        )
    }
    if (frame.perforated) drawPerforations(frame.accentColor, bounds, border)
    if (frame.cornerMarks) drawCornerMarks(frame.accentColor, bounds, border)
    frame.caption?.let { caption ->
        drawCaption(caption, frame.captionColor, bounds, border, frame.extraBottomPx(shorter))
    }
}

/** Film-style holes evenly spaced along the top and bottom borders. */
private fun DrawScope.drawPerforations(color: Color, bounds: Rect, border: Float) {
    val hole = border * 0.34f
    val gap = hole * 1.7f
    if (hole <= 0f || gap <= 0f) return
    val count = ((bounds.width - border) / gap).toInt().coerceAtLeast(2)
    val span = (count - 1) * gap
    val start = bounds.center.x - span / 2f - hole / 2f
    val top = bounds.top + (border - hole) / 2f
    val bottom = bounds.bottom - border + (border - hole) / 2f
    repeat(count) { index ->
        val x = start + index * gap
        drawRect(color, Offset(x, top), Size(hole, hole))
        drawRect(color, Offset(x, bottom), Size(hole, hole))
    }
}

/** Small L-shaped marks just inside the four corners. */
private fun DrawScope.drawCornerMarks(color: Color, bounds: Rect, border: Float) {
    val inset = border * 1.4f
    val length = (border * 0.9f).coerceAtLeast(2f)
    val stroke = max(1f, border * 0.12f)
    val left = bounds.left + inset
    val right = bounds.right - inset
    val top = bounds.top + inset
    val bottom = bounds.bottom - inset
    drawLine(color, Offset(left, top), Offset(left + length, top), strokeWidth = stroke)
    drawLine(color, Offset(left, top), Offset(left, top + length), strokeWidth = stroke)
    drawLine(color, Offset(right, top), Offset(right - length, top), strokeWidth = stroke)
    drawLine(color, Offset(right, top), Offset(right, top + length), strokeWidth = stroke)
    drawLine(color, Offset(left, bottom), Offset(left + length, bottom), strokeWidth = stroke)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - length), strokeWidth = stroke)
    drawLine(color, Offset(right, bottom), Offset(right - length, bottom), strokeWidth = stroke)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - length), strokeWidth = stroke)
}

/** A small printed word centred in the extra bottom strip. */
private fun DrawScope.drawCaption(
    text: String,
    color: Color,
    bounds: Rect,
    border: Float,
    extraBottom: Float
) {
    val size = min(bounds.width, bounds.height) * 0.036f
    if (size <= 0f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = size
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.18f
    }
    val centerY = bounds.bottom - border - extraBottom / 2f
    val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, bounds.center.x, baseline, paint)
}
