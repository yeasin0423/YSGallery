package com.gallery.app.ui.collage

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.app.model.MediaItem
import com.gallery.app.ui.design.DialogButton
import com.gallery.app.ui.design.DialogButtonStyle
import com.gallery.app.ui.design.ThemedDialog
import com.gallery.app.ui.editor.MediaEditor
import com.gallery.app.ui.i18n.strings
import com.gallery.app.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Landscape page like the gallery's own collage sheets: preview on top, templates underneath. */
private const val PageAspect = 3f / 4f

private const val PreviewWidth = 900
private const val PreviewHeight = 1200
private const val ExportWidth = 2400
private const val ExportHeight = 3200

/**
 * Builds one image out of [items] (2 to 9 photos). The lead photo's editor is not involved: this page
 * only arranges the photos, and saving writes a new image into the gallery.
 */
@Composable
fun CollageEditorScreen(
    items: List<MediaItem>,
    onSaved: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val scope = rememberCoroutineScope()

    val layouts = remember(items.size) { collageLayouts(items.size) }
    var layoutIndex by remember(items.size) { mutableIntStateOf(0) }
    var bitmaps by remember(items.size) { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var selectedIndex by remember(items.size) { mutableStateOf<Int?>(null) }
    val frames = remember(items.size) {
        mutableStateListOf<CollageFrame>().apply { repeat(items.size) { add(CollageFrame()) } }
    }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var interacting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val layout = layouts[layoutIndex.coerceIn(layouts.indices)]
    val ready = bitmaps.size == items.size && bitmaps.all { it != null }
    val frameSnapshot = frames.toList()

    fun pageRect(): Rect = collagePageRect(previewSize.width.toFloat(), previewSize.height.toFloat())

    fun cellRect(index: Int): Rect? {
        if (previewSize.width <= 0 || previewSize.height <= 0) return null
        val page = pageRect()
        val cell = CollageRenderer.slotRects(layout, page.width, page.height).getOrNull(index) ?: return null
        return Rect(
            page.left + cell.left,
            page.top + cell.top,
            page.left + cell.right,
            page.top + cell.bottom
        )
    }

    fun slotAt(position: Offset): Int? {
        if (previewSize.width <= 0 || previewSize.height <= 0) return null
        return collageSlotAt(layout, pageRect(), position)
    }

    /**
     * Panning a photo that still fills its cell moves it freely so it can be dropped on another
     * cell; once it is zoomed or rotated the pan is clamped to keep the cell covered.
     */
    fun transform(index: Int, pan: Offset, zoom: Float, rotation: Float) {
        val cell = cellRect(index) ?: return
        val current = frames.getOrNull(index) ?: return
        val width = cell.width.coerceAtLeast(1f)
        val height = cell.height.coerceAtLeast(1f)
        if (current.atBase && zoom == 1f && rotation == 0f) {
            frames[index] = current.copy(
                offsetX = current.offsetX + pan.x / width,
                offsetY = current.offsetY + pan.y / height
            )
        } else {
            val zoomed = current.copy(
                zoom = (current.zoom * zoom).coerceIn(1f, 4f),
                rotation = normalizeDegrees(current.rotation + rotation)
            )
            frames[index] = zoomed.panned(pan.x / width, pan.y / height, width, height)
        }
        changed = true
    }

    fun release(index: Int, position: Offset) {
        val target = slotAt(position)
        if (target != null && target != index) {
            val swapped = bitmaps.toMutableList()
            if (index in swapped.indices && target in swapped.indices) {
                val moved = swapped[index]
                swapped[index] = swapped[target]
                swapped[target] = moved
                bitmaps = swapped
                frames[index] = CollageFrame()
                frames[target] = CollageFrame()
                selectedIndex = target
                changed = true
            }
            return
        }
        val cell = cellRect(index) ?: return
        frames[index] = frames[index].clamped(cell.width, cell.height)
    }

    LaunchedEffect(items) {
        bitmaps = List(items.size) { null }
        // One at a time keeps peak memory flat on phones that only allow small heaps.
        bitmaps = items.map { item -> MediaEditor.loadPreview(context, item, 1400) }
    }

    // The live page is re-rendered while a finger moves, so the working copy is smaller then; it
    // snaps back to the full preview size when the gesture ends.
    val previewWidth = if (interacting) 540 else PreviewWidth
    val previewHeight = if (interacting) 720 else PreviewHeight
    val previewBitmap = remember(layout, bitmaps, frameSnapshot, previewWidth) {
        CollageRenderer.render(layout, bitmaps, previewWidth, previewHeight, frameSnapshot)
    }
    val previewImage = remember(previewBitmap) { previewBitmap.asImageBitmap() }

    fun requestClose() {
        if (saving) return
        if (changed) showDiscardDialog = true else onClose()
    }

    fun save() {
        if (saving || !ready) return
        saving = true
        val activeLayout = layout
        val sources = bitmaps
        val activeFrames = frames.toList()
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                CollageRenderer.render(activeLayout, sources, ExportWidth, ExportHeight, activeFrames)
            }
            val result = runCatching {
                MediaEditor.saveCollage(context, output)
            }
            output.recycle()
            result.onSuccess {
                Toast.makeText(context, strings.collageSaved, Toast.LENGTH_SHORT).show()
                saving = false
                changed = false
                onSaved()
                onClose()
            }.onFailure { error ->
                saving = false
                Toast.makeText(context, strings.editFailed(error.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = !saving) { requestClose() }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                IconButton(
                    onClick = ::requestClose,
                    enabled = !saving,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings.back, tint = palette.onBackground)
                }
                Text(
                    text = strings.collage,
                    color = palette.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = ::save,
                    enabled = !saving && ready,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        strings.save,
                        tint = if (!saving && ready) palette.accent else palette.textMuted
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { previewSize = it }
                        .pointerInput(layout) {
                            collageGestures(
                                layout = layout,
                                onSelect = { selectedIndex = it },
                                onTransform = { index, pan, zoom, rotation ->
                                    interacting = true
                                    transform(index, pan, zoom, rotation)
                                },
                                onRelease = { index, position ->
                                    interacting = false
                                    release(index, position)
                                }
                            )
                        }
                ) {
                    val page = collagePageRect(size.width, size.height)
                    drawImage(
                        image = previewImage,
                        dstOffset = IntOffset(page.left.roundToInt(), page.top.roundToInt()),
                        dstSize = IntSize(
                            page.width.roundToInt().coerceAtLeast(1),
                            page.height.roundToInt().coerceAtLeast(1)
                        ),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                    )
                    selectedIndex?.let { index ->
                        val cell = CollageRenderer
                            .slotRects(layout, page.width, page.height)
                            .getOrNull(index) ?: return@let
                        drawRoundRect(
                            color = palette.accent,
                            topLeft = Offset(page.left + cell.left, page.top + cell.top),
                            size = Size(cell.width(), cell.height()),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                if (!ready) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }

            Text(
                text = strings.collageGestures,
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 2.dp)
            )

            Text(
                text = strings.collageChooseTemplate,
                color = palette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                layouts.forEachIndexed { index, candidate ->
                    val selected = index == layoutIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = !saving) {
                            if (index != layoutIndex) {
                                layoutIndex = index
                                selectedIndex = null
                                frames.indices.forEach { frames[it] = CollageFrame() }
                                changed = true
                            }
                        }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(58.dp)
                                .height((58f / PageAspect).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, palette.accent, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            drawTemplate(candidate)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        if (saving) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = palette.accent)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(strings.saving, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }

    if (showDiscardDialog) {
        ThemedDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = strings.unsavedTitle,
            message = strings.unsavedMessage,
            buttons = listOf(
                DialogButton(strings.cancel, style = DialogButtonStyle.Cancel) {
                    showDiscardDialog = false
                },
                DialogButton(strings.discard, style = DialogButtonStyle.Destructive) {
                    showDiscardDialog = false
                    onClose()
                },
                DialogButton(strings.save, style = DialogButtonStyle.Primary) {
                    showDiscardDialog = false
                    save()
                }
            )
        )
    }
}

/** Draws one template thumbnail: the page's own background with grey cells. */
private fun DrawScope.drawTemplate(layout: CollageLayout) {
    drawRect(color = Color(layout.background))
    val margin = size.minDimension * layout.margin
    val gap = size.minDimension * layout.gap
    val left = margin
    val top = margin
    val right = size.width - margin
    val bottom = size.height - margin
    layout.slots.forEach { slot ->
        val cellLeft = left + slot.left * (right - left) + gap / 2f
        val cellTop = top + slot.top * (bottom - top) + gap / 2f
        val cellRight = left + slot.right * (right - left) - gap / 2f
        val cellBottom = top + slot.bottom * (bottom - top) - gap / 2f
        val cellWidth = (cellRight - cellLeft).coerceAtLeast(0f)
        val cellHeight = (cellBottom - cellTop).coerceAtLeast(0f)
        val shape = slot.shape ?: layout.shape
        if (shape == CollageShape.Circle) {
            drawOval(
                color = Color(0xFFD6D6D6),
                topLeft = Offset(cellLeft, cellTop),
                size = Size(cellWidth, cellHeight)
            )
        } else {
            drawRoundRect(
                color = Color(0xFFD6D6D6),
                topLeft = Offset(cellLeft, cellTop),
                size = Size(cellWidth, cellHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}

/** The centered 3:4 page inside a preview canvas of [width] x [height]. */
private fun collagePageRect(width: Float, height: Float): Rect {
    if (width <= 0f || height <= 0f) return Rect.Zero
    val pageWidth = min(width, height * PageAspect)
    val pageHeight = pageWidth / PageAspect
    val left = (width - pageWidth) / 2f
    val top = (height - pageHeight) / 2f
    return Rect(left, top, left + pageWidth, top + pageHeight)
}

/** Which cell is under [position], or null on the page background. */
private fun collageSlotAt(layout: CollageLayout, page: Rect, position: Offset): Int? {
    if (page.width <= 0f || page.height <= 0f || !page.contains(position)) return null
    val localX = position.x - page.left
    val localY = position.y - page.top
    return CollageRenderer.slotRects(layout, page.width, page.height)
        .indexOfFirst { it.contains(localX, localY) }
        .takeIf { it >= 0 }
}

/**
 * One-finger drag pans a photo (or starts a swap when it still fills its cell), and two fingers
 * zoom and twist it. The photo under the first finger becomes the selection immediately.
 */
private suspend fun PointerInputScope.collageGestures(
    layout: CollageLayout,
    onSelect: (Int?) -> Unit,
    onTransform: (Int, Offset, Float, Float) -> Unit,
    onRelease: (Int, Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val page = collagePageRect(size.width.toFloat(), size.height.toFloat())
        val index = collageSlotAt(layout, page, down.position)
        onSelect(index)
        if (index == null) return@awaitEachGesture

        var pastTouchSlop = false
        var panTotal = Offset.Zero
        var zoomTotal = 1f
        var rotationTotal = 0f
        var lastPosition = down.position
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val rotation = event.calculateRotation()
                panTotal += pan
                zoomTotal *= zoom
                rotationTotal += rotation
                if (!pastTouchSlop) {
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val motion = panTotal.getDistance() +
                        abs(1f - zoomTotal) * centroidSize +
                        abs(rotationTotal * PI.toFloat() / 180f) * centroidSize
                    if (motion > viewConfiguration.touchSlop) pastTouchSlop = true
                }
                if (pastTouchSlop) {
                    event.changes.firstOrNull { it.pressed }?.let { lastPosition = it.position }
                    onTransform(index, pan, zoom, rotation)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        if (pastTouchSlop) onRelease(index, lastPosition)
    }
}

/** Keeps a free rotation readable: -180..180 degrees. */
private fun normalizeDegrees(value: Float): Float {
    var degrees = value % 360f
    if (degrees > 180f) degrees -= 360f
    if (degrees < -180f) degrees += 360f
    return degrees
}
