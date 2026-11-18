package com.gallery.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.app.ui.i18n.strings
import com.gallery.app.ui.theme.LocalAppTheme
import com.gallery.app.ui.theme.onAccent
import kotlin.math.min
import kotlin.math.roundToInt

/** The six bottom sections of the editor. Crop keeps the existing rotate/mirror/ratio tools. */
internal enum class EditorTab { Crop, Adjust, Filters, Blur, Draw, Text }

@Composable
internal fun EditorTabs(selected: EditorTab, enabled: Boolean, onSelect: (EditorTab) -> Unit) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // A little breathing room under the pills so they don't hug the gesture bar.
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 18.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        EditorTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) palette.accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabLabel(tab),
                    color = if (isSelected) palette.onAccent else palette.onBackground,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private fun tabLabel(tab: EditorTab): String = when (tab) {
    EditorTab.Crop -> strings.crop
    EditorTab.Adjust -> strings.adjust
    EditorTab.Filters -> strings.filters
    EditorTab.Blur -> strings.blur
    EditorTab.Draw -> strings.draw
    EditorTab.Text -> strings.text
}

/** One slider plus a scrollable row of the eight adjustment names. */
@Composable
internal fun AdjustPanel(
    adjustments: EditorAdjustments,
    selected: EditorAdjustment,
    enabled: Boolean,
    onSelect: (EditorAdjustment) -> Unit,
    onChange: (EditorAdjustment, Float) -> Unit,
    onChangeFinished: () -> Unit,
    onNudge: (EditorAdjustment, Float) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = adjustmentLabel(selected),
                color = palette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = adjustmentValue(adjustments.valueOf(selected)),
                color = palette.textMuted,
                fontSize = 13.sp
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdjustmentStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = strings.decrease,
                enabled = enabled,
                onClick = { onNudge(selected, -AdjustmentStep) }
            )
            Slider(
                value = adjustments.valueOf(selected),
                onValueChange = { onChange(selected, it) },
                onValueChangeFinished = onChangeFinished,
                valueRange = -1f..1f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.surfaceVariant,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            AdjustmentStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = strings.increase,
                enabled = enabled,
                onClick = { onNudge(selected, AdjustmentStep) }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorAdjustment.entries.forEach { adjustment ->
                AdjustmentChip(
                    label = adjustmentLabel(adjustment),
                    selected = adjustment == selected,
                    onClick = { onSelect(adjustment) }
                )
            }
        }
    }
}

/** How much the +/- buttons move a slider; small enough to fine-tune and big enough to feel. */
private const val AdjustmentStep = 0.05f

@Composable
private fun AdjustmentStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(palette.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = palette.accent),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) palette.onBackground else palette.textMuted,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun AdjustmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) palette.accent else palette.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) palette.onAccent else palette.onBackground,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

private fun adjustmentLabel(adjustment: EditorAdjustment): String = when (adjustment) {
    EditorAdjustment.Brightness -> strings.brightness
    EditorAdjustment.Contrast -> strings.contrast
    EditorAdjustment.Saturation -> strings.saturation
    EditorAdjustment.Warmth -> strings.warmth
    EditorAdjustment.Tint -> strings.tint
    EditorAdjustment.BlackPoint -> strings.blackPoint
    EditorAdjustment.WhitePoint -> strings.whitePoint
    EditorAdjustment.Highlights -> strings.highlights
}

private fun adjustmentValue(value: Float): String {
    val percent = (value * 100f).roundToInt()
    return if (percent > 0) "+$percent" else percent.toString()
}

/**
 * Presets as live thumbnails (each already including the current adjustments) and frames as larger
 * previews. The two live behind a small switch so the panel only shows one row at a time.
 */
@Composable
internal fun FilterPanel(
    preview: ImageBitmap,
    filters: List<EditorFilter>,
    selected: EditorFilter,
    bokeh: BokehStyle,
    frame: EditorFrame,
    enabled: Boolean,
    matrixFor: (EditorFilter) -> FloatArray,
    onSelect: (EditorFilter) -> Unit,
    onSelectBokeh: (BokehStyle) -> Unit,
    onSelectFrame: (EditorFrame) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    var section by remember { mutableStateOf(FilterSection.Filters) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedToggle(
            options = listOf(
                FilterSection.Filters to strings.filters,
                FilterSection.Frames to strings.frame,
                FilterSection.Bokeh to strings.bokeh
            ),
            selected = section,
            enabled = enabled,
            onSelect = { section = it }
        )
        when (section) {
            FilterSection.Filters -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filters.forEach { filter ->
                        val isSelected = filter.id == selected.id
                        Column(
                            modifier = Modifier
                                .width(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = enabled) { onSelect(filter) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, palette.accent, RoundedCornerShape(13.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                val side = min(preview.width, preview.height)
                                drawImage(
                                    image = preview,
                                    srcOffset = IntOffset((preview.width - side) / 2, (preview.height - side) / 2),
                                    srcSize = IntSize(side, side),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(values = matrixFor(filter)))
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = filter.name,
                                color = if (isSelected) palette.accent else palette.onBackground,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            FilterSection.Frames -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorFrame.entries.forEach { option ->
                    val isSelected = option == frame
                    Column(
                        modifier = Modifier
                            .width(66.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = enabled) { onSelectFrame(option) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, palette.accent, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            drawRect(Color(0xFF8D939C))
                            drawEditorFrame(option, Rect(Offset.Zero, size))
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = frameLabel(option),
                            color = if (isSelected) palette.accent else palette.onBackground,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            // Bokeh sits next to frames: each thumbnail is the current photo with that light set on it.
            FilterSection.Bokeh -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BokehStyle.entries.forEach { option ->
                    val isSelected = option == bokeh
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = enabled) { onSelectBokeh(option) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, palette.accent, RoundedCornerShape(13.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            val side = min(preview.width, preview.height).toFloat()
                            val srcX = (preview.width - side) / 2f
                            val srcY = (preview.height - side) / 2f
                            drawImage(
                                image = preview,
                                srcOffset = IntOffset(srcX.roundToInt(), srcY.roundToInt()),
                                srcSize = IntSize(side.roundToInt(), side.roundToInt()),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                            )
                            val thumb = min(size.width, size.height)
                            option.orbs.forEach { orb ->
                                val x = (orb.center.x * preview.width - srcX) / side * size.width
                                val y = (orb.center.y * preview.height - srcY) / side * size.height
                                val radius = orb.radius * thumb
                                if (radius <= 0f) return@forEach
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0f to orb.color.copy(alpha = orb.alpha),
                                            0.55f to orb.color.copy(alpha = orb.alpha * 0.5f),
                                            1f to Color.Transparent
                                        ),
                                        center = Offset(x, y),
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = Offset(x, y)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = bokehLabel(option),
                            color = if (isSelected) palette.accent else palette.onBackground,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private enum class FilterSection { Filters, Frames, Bokeh }

private fun bokehLabel(style: BokehStyle): String = when (style) {
    BokehStyle.None -> strings.bokehNone
    BokehStyle.Warm -> strings.bokehWarm
    BokehStyle.Golden -> strings.bokehGolden
    BokehStyle.Cool -> strings.bokehCool
    BokehStyle.Dreamy -> strings.bokehDreamy
    BokehStyle.Night -> strings.bokehNight
}

private fun frameLabel(frame: EditorFrame): String = when (frame) {
    EditorFrame.None -> strings.frameNone
    EditorFrame.White -> strings.frameWhite
    EditorFrame.Black -> strings.frameBlack
    EditorFrame.DoubleLine -> strings.frameDouble
    EditorFrame.Polaroid -> strings.framePolaroid
    EditorFrame.Gold -> strings.frameGold
    EditorFrame.Modern -> strings.frameModern
    EditorFrame.Vintage -> strings.frameVintage
    EditorFrame.PolaroidBlack -> strings.framePolaroidBlack
    EditorFrame.Cinema -> strings.frameCinema
    EditorFrame.Rose -> strings.frameRose
    EditorFrame.Ocean -> strings.frameOcean
    EditorFrame.Sunset -> strings.frameSunset
    EditorFrame.Film -> strings.frameFilm
    EditorFrame.Memories -> strings.frameMemories
    EditorFrame.Today -> strings.frameToday
    EditorFrame.Corner -> strings.frameCorner
}

/** One One UI segmented switch shared by the tool and blur-shape rows. */
@Composable
private fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(CircleShape)
            .background(palette.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) palette.accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) palette.onAccent else palette.onBackground,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** Brush / eraser / blur / mosaic switch for the drawing tab. */
@Composable
private fun DrawModeToggle(
    mode: DrawMode,
    enabled: Boolean,
    onModeChange: (DrawMode) -> Unit
) {
    SegmentedToggle(
        options = listOf(
            DrawMode.Brush to strings.brush,
            DrawMode.Eraser to strings.eraser,
            DrawMode.Blur to strings.blur,
            DrawMode.Mosaic to strings.mosaic
        ),
        selected = mode,
        enabled = enabled,
        onSelect = onModeChange
    )
}

/** Round patch or dragged band, shown only while the blur or mosaic tool is selected. */
@Composable
private fun BlurShapeToggle(
    shape: BlurShape,
    enabled: Boolean,
    onShapeChange: (BlurShape) -> Unit
) {
    Spacer(modifier = Modifier.height(6.dp))
    SegmentedToggle(
        options = listOf(
            BlurShape.Stroke to strings.blurStroke,
            BlurShape.Circle to strings.circle
        ),
        selected = shape,
        enabled = enabled,
        onSelect = onShapeChange
    )
}

/** Shared colour swatches for the draw and text tabs. */
@Composable
private fun DrawColorRow(color: Color, enabled: Boolean, onSelectColor: (Color) -> Unit) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorDrawColors.forEach { swatch ->
            val selected = swatch == color
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelectColor(swatch) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 28.dp else 32.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(1.dp, palette.onBackground.copy(alpha = 0.16f), CircleShape)
                )
            }
        }
    }
}

/**
 * Portrait background blur: the sharp focus circle's size, the blur radius, and a bokeh light set.
 * The circle itself is dragged directly on the photo.
 */
@Composable
internal fun BlurPanel(
    focusRadius: Float,
    blurStrength: Float,
    bokeh: BokehStyle,
    enabled: Boolean,
    onFocusRadiusChange: (Float) -> Unit,
    onFocusRadiusFinished: () -> Unit,
    onBlurStrengthChange: (Float) -> Unit,
    onBlurStrengthFinished: () -> Unit,
    onSelectBokeh: (BokehStyle) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        FocusSlider(
            label = strings.focusRadius,
            value = focusRadius,
            valueRange = MinFocusRadius..MaxFocusRadius,
            enabled = enabled,
            onChange = onFocusRadiusChange,
            onFinished = onFocusRadiusFinished
        )
        FocusSlider(
            label = strings.blurRadius,
            value = blurStrength,
            valueRange = 0f..1f,
            enabled = enabled,
            onChange = onBlurStrengthChange,
            onFinished = onBlurStrengthFinished
        )
        Text(
            text = strings.bokeh,
            color = palette.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BokehStyle.entries.forEach { option ->
                AdjustmentChip(
                    label = bokehLabel(option),
                    selected = option == bokeh,
                    onClick = { onSelectBokeh(option) }
                )
            }
        }
    }
}

/** One labelled blur slider; the value reads as a percentage on the right. */
@Composable
private fun FocusSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = palette.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(value * 100f).roundToInt()}%",
                color = palette.textMuted,
                fontSize = 12.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

/** Freehand drawing: brush, eraser, blur or mosaic, plus colours and a brush-size slider. */
@Composable
internal fun DrawPanel(
    color: Color,
    brushWidth: Float,
    mode: DrawMode,
    blurShape: BlurShape,
    enabled: Boolean,
    onSelectColor: (Color) -> Unit,
    onBrushWidthChange: (Float) -> Unit,
    onModeChange: (DrawMode) -> Unit,
    onBlurShapeChange: (BlurShape) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        DrawModeToggle(mode = mode, enabled = enabled, onModeChange = onModeChange)
        if (mode == DrawMode.Blur || mode == DrawMode.Mosaic) {
            BlurShapeToggle(
                shape = blurShape,
                enabled = enabled,
                onShapeChange = onBlurShapeChange
            )
        }
        if (mode == DrawMode.Brush) {
            Spacer(modifier = Modifier.height(8.dp))
            DrawColorRow(color = color, enabled = enabled, onSelectColor = onSelectColor)
        }
        // Every tool shares the width slider now, so the eraser can be as wide as the brush.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.brushSize,
                color = palette.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            // A dot that shows the current brush thickness.
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size((brushWidth / MaxDrawWidth * 18f).coerceIn(4f, 18f).dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, palette.onBackground.copy(alpha = 0.2f), CircleShape)
                )
            }
        }
        Slider(
            value = brushWidth,
            onValueChange = onBrushWidthChange,
            valueRange = MinDrawWidth..MaxDrawWidth,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

/**
 * Text label editor: the colour palette and the font styles. Typing happens in the floating bar at
 * the bottom, and size is set by dragging a corner on the photo.
 */
@Composable
internal fun TextPanel(
    color: Color,
    font: TextFont,
    size: Float,
    enabled: Boolean,
    onSelectColor: (Color) -> Unit,
    onSelectFont: (TextFont) -> Unit,
    onSizeChange: (Float) -> Unit,
    onSizeChangeFinished: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp)
    ) {
        DrawColorRow(color = color, enabled = enabled, onSelectColor = onSelectColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextFont.entries.forEach { option ->
                FontChip(
                    font = option,
                    selected = option == font,
                    enabled = enabled,
                    onClick = { onSelectFont(option) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.textSize,
                color = palette.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(size * 100f).roundToInt()}%",
                color = palette.textMuted,
                fontSize = 12.sp
            )
        }
        Slider(
            value = size,
            onValueChange = onSizeChange,
            onValueChangeFinished = onSizeChangeFinished,
            valueRange = MinTextSize..MaxTextSize,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

/** One font choice, drawn in the style it applies so the row reads like a type sample. */
@Composable
private fun FontChip(
    font: TextFont,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val family = when (font) {
        TextFont.Serif, TextFont.SerifBold, TextFont.SerifMono,
        TextFont.Elegant, TextFont.Editorial -> FontFamily.Serif
        TextFont.Handwritten, TextFont.Casual, TextFont.Marker -> FontFamily.Cursive
        TextFont.Typewriter -> FontFamily.Monospace
        TextFont.Monospace -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val weight = when (font) {
        TextFont.Bold, TextFont.BoldItalic, TextFont.SerifBold,
        TextFont.Editorial, TextFont.Marker, TextFont.Typewriter,
        TextFont.Outline, TextFont.Neon, TextFont.Shadow -> FontWeight.Bold
        TextFont.Black -> FontWeight.Black
        TextFont.Thin -> FontWeight.Thin
        TextFont.Light -> FontWeight.Light
        TextFont.Medium -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val style = when (font) {
        TextFont.Italic, TextFont.BoldItalic, TextFont.Elegant, TextFont.Editorial -> FontStyle.Italic
        else -> FontStyle.Normal
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) palette.accent else palette.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = fontLabel(font),
            color = if (selected) palette.onAccent else palette.onBackground,
            fontFamily = family,
            fontWeight = weight,
            fontStyle = style,
            fontSize = 13.sp
        )
    }
}

private fun fontLabel(font: TextFont): String = when (font) {
    TextFont.Bold -> strings.fontBold
    TextFont.Regular -> strings.fontRegular
    TextFont.Serif -> strings.fontSerif
    TextFont.Handwritten -> strings.fontHandwritten
    TextFont.Italic -> strings.fontItalic
    TextFont.BoldItalic -> strings.fontBoldItalic
    TextFont.SerifBold -> strings.fontSerifBold
    TextFont.Condensed -> strings.fontCondensed
    TextFont.Light -> strings.fontLight
    TextFont.Medium -> strings.fontMedium
    TextFont.Black -> strings.fontBlack
    TextFont.Monospace -> strings.fontMonospace
    TextFont.Thin -> strings.fontThin
    TextFont.CondensedLight -> strings.fontCondensedLight
    TextFont.SmallCaps -> strings.fontSmallCaps
    TextFont.SerifMono -> strings.fontSerifMono
    TextFont.Casual -> strings.fontCasual
    TextFont.Elegant -> strings.fontElegant
    TextFont.Editorial -> strings.fontEditorial
    TextFont.Marker -> strings.fontMarker
    TextFont.Typewriter -> strings.fontTypewriter
    TextFont.Outline -> strings.fontOutline
    TextFont.Neon -> strings.fontNeon
    TextFont.Shadow -> strings.fontShadow
}
