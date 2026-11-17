package com.gallery.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * A decorative frame around the exported photo. Sizes are fractions of the photo's shorter side, so a
 * frame looks the same on a small screenshot and on a large camera photo.
 *
 * [background] also fills the border in the preview, which is drawn over the photo edges rather than
 * around them, so the preview keeps the photo the same size it always had.
 */
enum class EditorFrame(
    val background: Color,
    val borderFraction: Float,
    val extraBottomFraction: Float = 0f,
    /** The thin rule drawn just inside the border, or null for a flat frame. */
    val lineColor: Color? = null
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
    Cinema(Color(0xFF0A0A0A), 0.06f, lineColor = Color(0xFFD8B25A))
}

/** Border thickness in pixels of a photo whose shorter side is [shorter]. */
fun EditorFrame.borderPx(shorter: Float): Float = shorter * borderFraction

/** The extra strip a polaroid leaves under the photo, in pixels of the same scale. */
fun EditorFrame.extraBottomPx(shorter: Float): Float = shorter * extraBottomFraction

/**
 * Paints the frame inside [bounds] for the live preview: four strips along the edges, plus the thin
 * inner rule of the double frame. The exporter draws the same proportions around the cropped bitmap.
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
            style = Stroke(1.dp.toPx())
        )
    }
}
