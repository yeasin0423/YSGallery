package com.gallery.app.ui.collage

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    var saving by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val layout = layouts[layoutIndex.coerceIn(layouts.indices)]
    val ready = bitmaps.size == items.size && bitmaps.all { it != null }

    LaunchedEffect(items) {
        bitmaps = List(items.size) { null }
        // One at a time keeps peak memory flat on phones that only allow small heaps.
        bitmaps = items.map { item -> MediaEditor.loadPreview(context, item, 1400) }
    }

    val previewBitmap = remember(layout, bitmaps) {
        CollageRenderer.render(layout, bitmaps, PreviewWidth, PreviewHeight)
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
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                CollageRenderer.render(activeLayout, sources, ExportWidth, ExportHeight)
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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pageWidth = min(size.width, size.height * PageAspect)
                    val pageHeight = pageWidth / PageAspect
                    drawImage(
                        image = previewImage,
                        dstOffset = IntOffset(
                            ((size.width - pageWidth) / 2f).roundToInt(),
                            ((size.height - pageHeight) / 2f).roundToInt()
                        ),
                        dstSize = IntSize(pageWidth.roundToInt(), pageHeight.roundToInt()),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                    )
                }
                if (!ready) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }

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

/** Draws one template thumbnail: a white page with grey cells. */
private fun DrawScope.drawTemplate(layout: CollageLayout) {
    val margin = size.minDimension * 0.035f
    val gap = size.minDimension * 0.014f
    val left = margin
    val top = margin
    val right = size.width - margin
    val bottom = size.height - margin
    layout.slots.forEach { slot ->
        val cellLeft = left + slot.left * (right - left) + gap / 2f
        val cellTop = top + slot.top * (bottom - top) + gap / 2f
        val cellRight = left + slot.right * (right - left) - gap / 2f
        val cellBottom = top + slot.bottom * (bottom - top) - gap / 2f
        drawRoundRect(
            color = Color(0xFFD6D6D6),
            topLeft = Offset(cellLeft, cellTop),
            size = Size(
                (cellRight - cellLeft).coerceAtLeast(0f),
                (cellBottom - cellTop).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
    }
}
