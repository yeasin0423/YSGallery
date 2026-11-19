package com.gallery.app.ui.editor

import android.app.Activity
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallery.app.model.MediaItem
import com.gallery.app.ui.design.DialogButton
import com.gallery.app.ui.design.DialogButtonStyle
import com.gallery.app.ui.design.MenuEntry
import com.gallery.app.ui.design.ThemedDialog
import com.gallery.app.ui.design.ThemedPopupMenu
import com.gallery.app.ui.design.ThemedProgressBar
import com.gallery.app.ui.design.ThemedToolItem
import com.gallery.app.ui.design.blockTouchesBelow
import com.gallery.app.ui.design.pressScale
import com.gallery.app.ui.i18n.strings
import com.gallery.app.ui.theme.LocalAppTheme
import com.gallery.app.ui.theme.onAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Called after an edited copy was saved, so the gallery can reload. Provided by MainActivity. */
val LocalOnMediaEdited = staticCompositionLocalOf<() -> Unit> { {} }

private enum class CropHandle { NONE, MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** The inline panel under the preview. Only one is open at a time. */
private enum class EditorPanel { None, Rotate, Ratio }

/** Everything an undo step restores. The selected tab and open panel are UI state, not image state. */
private data class EditorSnapshot(
    val rotation: Int,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val straighten: Float,
    val crop: NormalizedRect,
    val ratio: CropRatio,
    val adjustments: EditorAdjustments,
    val filter: EditorFilter,
    val focusBlur: FocusBlur,
    val bokeh: BokehStyle,
    val frame: EditorFrame,
    val strokes: List<DrawStroke>,
    val blurStrokes: List<BlurStroke>,
    val texts: List<TextOverlay>
)

private const val MaxHistory = 60

/** How far the photo can be pinched in before the editor stops zooming. */
private const val MaxPreviewZoom = 6f

/** A text-box drag in progress: which control was grabbed and the label state when it began. */
private data class TextGesture(
    val handle: TextHandle,
    val start: Offset,
    val center: Offset,
    val rotation: Float,
    val size: Float,
    val startAngle: Float,
    val startDistance: Float
)

private const val MIN_CROP = 0.1f

/**
 * A crop ratio: free, the whole frame, or a fixed width:height measured in pixels of the photo as it
 * is currently shown (after rotation and mirroring).
 */
private sealed interface CropRatio {
    data object Free : CropRatio
    data object Original : CropRatio
    data class Fixed(val width: Float, val height: Float) : CropRatio

    /**
     * The crop's width/height in normalized frame coordinates, or null for a free crop. A frame that
     * is wider than it is tall needs a narrower normalized width for the same 3:2 pixel crop.
     */
    fun normalizedAspect(frameAspect: Float): Float? = when (this) {
        Free -> null
        Original -> 1f
        is Fixed -> (width / height) / frameAspect
    }
}

/**
 * Photo editor: free or fixed-ratio crop, 90° rotation and horizontal/vertical mirroring. Saving keeps
 * the original untouched by default; the overflow menu can overwrite it instead, after a confirmation
 * and, for photos owned by other apps, Android's write-access request.
 */
@Composable
fun MediaEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalAppTheme.current.palette
    val density = LocalDensity.current
    val onEdited = LocalOnMediaEdited.current
    val scope = rememberCoroutineScope()

    var source by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        source = MediaEditor.loadPreview(context, item)
        loadFailed = source == null
    }

    var rotation by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var straighten by remember { mutableFloatStateOf(0f) }
    var crop by remember { mutableStateOf(NormalizedRect.Full) }
    var ratio by remember { mutableStateOf<CropRatio>(CropRatio.Free) }
    var tab by remember { mutableStateOf(EditorTab.Crop) }
    var selectedAdjustment by remember { mutableStateOf(EditorAdjustment.Brightness) }
    var adjustments by remember { mutableStateOf(EditorAdjustments()) }
    var filter by remember { mutableStateOf(EditorFilterNone) }
    // Portrait background blur: a sharp circle over a softened frame, part of the image.
    var focusBlur by remember { mutableStateOf(FocusBlur()) }
    // Renders the selected bokeh light set over the photo; part of the image, so it is undoable.
    var bokehStyle by remember { mutableStateOf(BokehStyle.None) }
    var frame by remember { mutableStateOf(EditorFrame.None) }
    var drawColor by remember { mutableStateOf(EditorDrawColors.first()) }
    var drawWidth by remember { mutableFloatStateOf(DefaultDrawWidth) }
    // Every width-slider move bumps this, so a ring shows the real brush/eraser size over the photo.
    var sizePreviewToken by remember { mutableIntStateOf(0) }
    var showSizePreview by remember { mutableStateOf(false) }
    var strokes by remember { mutableStateOf<List<DrawStroke>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<DrawStroke?>(null) }
    // Live points of the stroke under the finger; a snapshot list keeps long strokes cheap to extend.
    val activePoints = remember { mutableStateListOf<Offset>() }
    var drawMode by remember { mutableStateOf(DrawMode.Brush) }
    var blurShape by remember { mutableStateOf(BlurShape.Stroke) }
    var blurStrokes by remember { mutableStateOf<List<BlurStroke>>(emptyList()) }
    var activeBlur by remember { mutableStateOf<BlurStroke?>(null) }
    val activeBlurPoints = remember { mutableStateListOf<Offset>() }
    // Where the eraser or blur brush is, so a ring can show the area it covers.
    var toolCursor by remember { mutableStateOf<Offset?>(null) }
    var textOverlays by remember { mutableStateOf<List<TextOverlay>>(emptyList()) }
    var selectedTextId by remember { mutableStateOf<Long?>(null) }
    // The writing bar only shows while a label is actually being edited, not on a plain selection.
    var textBarOpen by remember { mutableStateOf(false) }
    // The field's text plus its caret, so opening an existing label puts the caret after its text.
    var textField by remember { mutableStateOf(TextFieldValue("")) }
    // Set when a box is created blank; the first typed character turns it into one undo step.
    var pendingTextCreate by remember { mutableStateOf<EditorSnapshot?>(null) }
    // Bumped whenever a fresh box should raise the keyboard in the text panel.
    var textFocusToken by remember { mutableIntStateOf(0) }
    var textColor by remember { mutableStateOf(Color.White) }
    var textSize by remember { mutableFloatStateOf(DefaultTextSize) }
    // The snapshot before a size-slider drag, so the whole drag is one undo step.
    var textSizeGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var textFont by remember { mutableStateOf(TextFont.Bold) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    // One text paint shared by every preview frame, so the preview matches the export's metrics.
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    // The floating writing bar owns the keyboard; the label on the photo is just painted text.
    val textFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var undoStack by remember { mutableStateOf<List<EditorSnapshot>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<EditorSnapshot>>(emptyList()) }
    // One snapshot per gesture, so dragging a slider or a crop handle is a single undo step.
    var adjustGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var cropGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var strokeGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var eraseGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var blurGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var focusGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var straightenGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var textGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }
    var textGesture by remember { mutableStateOf<TextGesture?>(null) }

    var openPanel by remember { mutableStateOf(EditorPanel.None) }
    // Kept after closing so the collapse animation still has content to draw.
    var shownPanel by remember { mutableStateOf(EditorPanel.Rotate) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var saveMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingOverwrite by remember { mutableStateOf<Pair<PendingIntent, File>?>(null) }

    val rotateAnimation = remember { Animatable(0f) }
    val flipXAnimation = remember { Animatable(1f) }
    val flipYAnimation = remember { Animatable(1f) }
    // Pinch zoom and two-finger pan. They only move the preview rectangle, so every tool keeps
    // working in the same coordinates it always used.
    var previewScale by remember { mutableFloatStateOf(1f) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    // Before/after: a split view, a hold-to-peek flag, and where the divider sits (0..1).
    var compareOpen by remember { mutableStateOf(false) }
    var peekOriginal by remember { mutableStateOf(false) }
    var compareSplit by remember { mutableFloatStateOf(0.5f) }

    // Straighten re-renders the whole preview, so it is quantized to half a degree for dragging.
    val straightenPreview = (straighten * 2f).roundToInt() / 2f
    val preview: ImageBitmap? = remember(source, rotation, flipHorizontal, flipVertical, straightenPreview) {
        source?.let {
            MediaEditor.transform(
                it,
                rotation,
                flipHorizontal,
                flipVertical,
                straightenPreview
            ).asImageBitmap()
        }
    }
    val frameAspect = preview?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
    val cropAspect = ratio.normalizedAspect(frameAspect)
    val colorMatrix = remember(adjustments, filter) { editorColorMatrix(adjustments, filter) }
    val previewColorFilter = remember(colorMatrix) {
        ColorFilter.colorMatrix(ColorMatrix(values = colorMatrix))
    }
    // The size ring stays up for a moment after the slider is released so the final width is readable.
    LaunchedEffect(sizePreviewToken) {
        if (sizePreviewToken == 0) return@LaunchedEffect
        showSizePreview = true
        delay(1100)
        showSizePreview = false
    }
    // The blur and mosaic tools show a softened or pixelated copy of the frame under their masks. The
    // copies are rebuilt only when the frame changes or the tool becomes active, never on every stroke;
    // they stay tiny, so letting the collector reclaim an old copy is safer than recycling one a draw
    // pass may still hold.
    val blurActive = blurStrokes.any { !it.pixelate } || (tab == EditorTab.Draw && drawMode == DrawMode.Blur)
    val mosaicActive =
        blurStrokes.any { it.pixelate } || (tab == EditorTab.Draw && drawMode == DrawMode.Mosaic)
    val blurredPreview = remember(preview, blurActive) {
        if (!blurActive) null else preview?.let { blurredBitmap(it.asAndroidBitmap()) }
    }
    val mosaicPreview = remember(preview, mosaicActive) {
        if (!mosaicActive) null else preview?.let { pixelatedBitmap(it.asAndroidBitmap()) }
    }
    // The blurred background under the portrait effect; the sharp subject is redrawn on top. The
    // strength is quantized so dragging the slider re-blurs in small steps instead of rebuilding the
    // whole copy for every pixel of travel.
    val focusPreviewStrength = (focusBlur.strength * 25f).roundToInt() / 25f
    val focusPreview = remember(preview, focusPreviewStrength) {
        if (!focusBlur.active) null else preview?.let {
            focusBlurredBitmap(it.asAndroidBitmap(), focusPreviewStrength)
        }
    }
    // The same colour matrix the photo is drawn with, so a softened or pixelated patch matches its
    // surroundings. The mosaic copy is stretched with filtering off, which is what makes it blocky.
    val blurPaint = remember(colorMatrix) {
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
    }
    val mosaicPaint = remember(colorMatrix) {
        // No filtering and no anti-aliasing: every pixel of the tiny copy becomes a hard square.
        Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
    }
    val changed = rotation != 0 || flipHorizontal || flipVertical || straighten != 0f ||
            !crop.isFull ||
            !adjustments.isNeutral || filter.id != EditorFilterNone.id || focusBlur.active ||
            bokehStyle != BokehStyle.None ||
            frame != EditorFrame.None || strokes.isNotEmpty() || blurStrokes.isNotEmpty() ||
            textOverlays.any { it.text.isNotBlank() }
    val canOverwrite = item.isImage && MediaEditor.canOverwrite(item)
    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()

    /** Closing with unsaved edits asks first; an untouched photo just closes. */
    fun requestClose() {
        if (saving) return
        if (changed) showDiscardDialog = true else onClose()
    }

    fun snapshot() = EditorSnapshot(
        rotation = rotation,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        straighten = straighten,
        crop = crop,
        ratio = ratio,
        adjustments = adjustments,
        filter = filter,
        focusBlur = focusBlur,
        bokeh = bokehStyle,
        frame = frame,
        strokes = strokes,
        blurStrokes = blurStrokes,
        texts = textOverlays
    )

    /** Pushes [before] onto the undo stack and drops any redo branch. */
    fun pushHistory(before: EditorSnapshot = snapshot()) {
        undoStack = (undoStack + before).takeLast(MaxHistory)
        redoStack = emptyList()
    }

    fun applySnapshot(state: EditorSnapshot) {
        rotation = state.rotation
        flipHorizontal = state.flipHorizontal
        flipVertical = state.flipVertical
        straighten = state.straighten
        crop = state.crop
        ratio = state.ratio
        adjustments = state.adjustments
        filter = state.filter
        focusBlur = state.focusBlur
        bokehStyle = state.bokeh
        frame = state.frame
        strokes = state.strokes
        blurStrokes = state.blurStrokes
        // Empty boxes only exist while being typed into; history keeps them out of the way.
        textOverlays = state.texts.filter { it.text.isNotBlank() }
        if (textOverlays.none { it.id == selectedTextId }) selectedTextId = null
        textOverlays.firstOrNull { it.id == selectedTextId }?.let {
            textColor = it.color
            textSize = it.size
            textFont = it.font
        }
        pendingTextCreate = null
        activeStroke = null
        activePoints.clear()
        activeBlur = null
        activeBlurPoints.clear()
        adjustGestureStart = null
        cropGestureStart = null
        strokeGestureStart = null
        eraseGestureStart = null
        blurGestureStart = null
        focusGestureStart = null
        straightenGestureStart = null
        textGestureStart = null
        textGesture = null
        openPanel = EditorPanel.None
    }

    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        redoStack = (redoStack + snapshot()).takeLast(MaxHistory)
        undoStack = undoStack.dropLast(1)
        applySnapshot(previous)
    }

    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = (undoStack + snapshot()).takeLast(MaxHistory)
        redoStack = redoStack.dropLast(1)
        applySnapshot(next)
    }

    fun onSaved(saved: ImageSaveResult.Saved) {
        saving = false
        Toast.makeText(
            context,
            if (saved.overwritten) strings.overwritten else strings.editSaved,
            Toast.LENGTH_SHORT
        ).show()
        onEdited()
        onClose()
    }

    fun onSaveFailed(error: Throwable) {
        saving = false
        Toast.makeText(context, strings.editFailed(error.localizedMessage), Toast.LENGTH_LONG).show()
    }

    // Android 11+ asks the user to let this app modify a photo owned by another app.
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pending = pendingOverwrite
        pendingOverwrite = null
        if (pending != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                scope.launch {
                    runCatching { MediaEditor.completeOverwrite(context, item, pending.second) }
                        .onSuccess {
                            Toast.makeText(context, strings.overwritten, Toast.LENGTH_SHORT).show()
                            saving = false
                            onEdited()
                            onClose()
                        }
                        .onFailure {
                            saving = false
                            Toast.makeText(context, strings.editFailed(it.localizedMessage), Toast.LENGTH_LONG).show()
                        }
                }
            } else {
                MediaEditor.discardOverwrite(pending.second)
                saving = false
                Toast.makeText(context, strings.overwriteCancelled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Rotates the frame a quarter turn; the inline panel's two buttons pick the direction. */
    fun rotate(clockwise: Boolean) {
        pushHistory()
        rotation = (rotation + if (clockwise) 90 else 270) % 360
        // The frame's width and height swap, so the same pixel ratio gets a different normalized one.
        val locked = ratio.normalizedAspect(1f / frameAspect)
        crop = if (locked == null) {
            if (clockwise) crop.rotateClockwise() else crop.rotateCounterClockwise()
        } else {
            fitCrop(crop, locked)
        }
        strokes = strokes.map { if (clockwise) it.rotateClockwise() else it.rotateCounterClockwise() }
        blurStrokes = blurStrokes.map {
            if (clockwise) it.rotateClockwise() else it.rotateCounterClockwise()
        }
        focusBlur = if (clockwise) focusBlur.rotateClockwise() else focusBlur.rotateCounterClockwise()
        textOverlays = textOverlays.map {
            if (clockwise) it.rotateClockwise() else it.rotateCounterClockwise()
        }
        scope.launch {
            // A positive angle runs clockwise, so the preview spins in from the opposite side.
            rotateAnimation.snapTo(if (clockwise) -90f else 90f)
            rotateAnimation.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
        }
    }

    // Mirroring always follows the screen axes: after a 90°/270° rotation the source's other axis flips.
    fun mirrorHorizontally() {
        pushHistory()
        if (rotation % 180 == 0) flipHorizontal = !flipHorizontal else flipVertical = !flipVertical
        crop = crop.flipHorizontal()
        strokes = strokes.map { it.flipHorizontal() }
        blurStrokes = blurStrokes.map { it.flipHorizontal() }
        focusBlur = focusBlur.flipHorizontal()
        textOverlays = textOverlays.map { it.flipHorizontal() }
        scope.launch {
            flipXAnimation.snapTo(-1f)
            flipXAnimation.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 350f))
        }
    }

    fun mirrorVertically() {
        pushHistory()
        if (rotation % 180 == 0) flipVertical = !flipVertical else flipHorizontal = !flipHorizontal
        crop = crop.flipVertical()
        strokes = strokes.map { it.flipVertical() }
        blurStrokes = blurStrokes.map { it.flipVertical() }
        focusBlur = focusBlur.flipVertical()
        textOverlays = textOverlays.map { it.flipVertical() }
        scope.launch {
            flipYAnimation.snapTo(-1f)
            flipYAnimation.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 350f))
        }
    }

    fun reset() {
        pushHistory()
        rotation = 0
        flipHorizontal = false
        flipVertical = false
        straighten = 0f
        crop = NormalizedRect.Full
        ratio = CropRatio.Free
        adjustments = EditorAdjustments()
        selectedAdjustment = EditorAdjustment.Brightness
        filter = EditorFilterNone
        focusBlur = FocusBlur()
        bokehStyle = BokehStyle.None
        frame = EditorFrame.None
        strokes = emptyList()
        activeStroke = null
        blurStrokes = emptyList()
        activeBlur = null
        activeBlurPoints.clear()
        toolCursor = null
        textOverlays = emptyList()
        selectedTextId = null
        textBarOpen = false
        pendingTextCreate = null
        drawMode = DrawMode.Brush
        blurShape = BlurShape.Stroke
        showSizePreview = false
        previewScale = 1f
        previewOffset = Offset.Zero
        compareOpen = false
        peekOriginal = false
        compareSplit = 0.5f
        openPanel = EditorPanel.None
    }

    fun chooseRatio(newRatio: CropRatio) {
        if (newRatio == ratio) return
        pushHistory()
        ratio = newRatio
        newRatio.normalizedAspect(frameAspect)?.let { crop = fitCrop(crop, it) }
    }

    fun togglePanel(panel: EditorPanel) {
        if (openPanel == panel) {
            openPanel = EditorPanel.None
        } else {
            shownPanel = panel
            openPanel = panel
        }
    }

    fun normalizedPoint(position: Offset, rect: Rect) = Offset(
        ((position.x - rect.left) / rect.width).coerceIn(0f, 1f),
        ((position.y - rect.top) / rect.height).coerceIn(0f, 1f)
    )

    /** A touch mapped to the focus circle's cropped-frame coordinates, clamped inside the crop. */
    fun focusPoint(position: Offset, rect: Rect): Offset {
        val focusRect = cropRectIn(rect, crop)
        if (focusRect.width <= 0f || focusRect.height <= 0f) return focusBlur.center
        return Offset(
            ((position.x - focusRect.left) / focusRect.width).coerceIn(0f, 1f),
            ((position.y - focusRect.top) / focusRect.height).coerceIn(0f, 1f)
        )
    }

    fun beginStroke(position: Offset, rect: Rect) {
        if (!rect.contains(position)) return
        strokeGestureStart = snapshot()
        activePoints.clear()
        activePoints.add(normalizedPoint(position, rect))
        activeStroke = DrawStroke(activePoints, drawColor, drawWidth)
    }

    fun extendStroke(position: Offset, rect: Rect) {
        val current = activeStroke ?: return
        val point = normalizedPoint(position, rect)
        val last = activePoints.lastOrNull() ?: current.points.last()
        // Skip sub-pixel moves; they only bloat the stroke and the undo snapshots.
        if (abs((point.x - last.x) * rect.width) < 1f && abs((point.y - last.y) * rect.height) < 1f) return
        activePoints.add(point)
    }

    fun finishStroke() {
        val finished = activeStroke ?: return
        val before = strokeGestureStart
        activeStroke = null
        strokeGestureStart = null
        strokes = strokes + finished.copy(points = activePoints.toList())
        activePoints.clear()
        if (before != null) pushHistory(before)
    }

    fun beginErase() {
        eraseGestureStart = snapshot()
    }

    /** Drops an in-progress stroke without leaving a dot or an undo step. */
    fun cancelStroke() {
        activeStroke = null
        activePoints.clear()
        strokeGestureStart = null
    }

    /** A pinch interrupted the eraser: put back what it wiped. */
    fun cancelErase() {
        eraseGestureStart?.let { strokes = it.strokes }
        eraseGestureStart = null
    }

    fun cancelBlur() {
        activeBlur = null
        activeBlurPoints.clear()
        blurGestureStart = null
    }

    /** Removes every stroke that passes under the eraser. One undo step per erase gesture. */
    fun eraseAt(position: Offset, rect: Rect, radiusPx: Float) {
        if (strokes.isEmpty() || rect.width <= 0f || rect.height <= 0f) return
        val touchX = (position.x - rect.left) / rect.width
        val touchY = (position.y - rect.top) / rect.height
        val remaining = strokes.filterNot { stroke ->
            val reach = radiusPx + stroke.width * min(rect.width, rect.height) / 2f
            val reachSquared = reach * reach
            stroke.points.any { point ->
                val dx = (point.x - touchX) * rect.width
                val dy = (point.y - touchY) * rect.height
                dx * dx + dy * dy <= reachSquared
            }
        }
        if (remaining.size != strokes.size) strokes = remaining
    }

    fun finishErase() {
        eraseGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
        eraseGestureStart = null
    }

    fun beginBlur(position: Offset, rect: Rect) {
        if (!rect.contains(position)) return
        blurGestureStart = snapshot()
        activeBlurPoints.clear()
        activeBlurPoints.add(normalizedPoint(position, rect))
        activeBlur = BlurStroke(
            points = activeBlurPoints,
            width = drawWidth,
            pixelate = drawMode == DrawMode.Mosaic
        )
    }

    fun extendBlur(position: Offset, rect: Rect) {
        if (activeBlur == null) return
        val point = normalizedPoint(position, rect)
        // A round patch follows the finger as one spot; the band grows point by point.
        if (blurShape == BlurShape.Circle) {
            activeBlurPoints.clear()
            activeBlurPoints.add(point)
            return
        }
        val last = activeBlurPoints.lastOrNull() ?: return
        if (abs((point.x - last.x) * rect.width) < 1f &&
            abs((point.y - last.y) * rect.height) < 1f
        ) {
            return
        }
        activeBlurPoints.add(point)
    }

    fun finishBlur() {
        val finished = activeBlur ?: return
        val before = blurGestureStart
        activeBlur = null
        blurGestureStart = null
        blurStrokes = blurStrokes + finished.copy(points = activeBlurPoints.toList())
        activeBlurPoints.clear()
        if (before != null) pushHistory(before)
    }

    /**
     * Measures a label's selection box in view pixels with the same paint the preview and export use,
     * padded so the outline never sits on the glyphs themselves.
     */
    fun textBoxOf(overlay: TextOverlay, rect: Rect): TextBox {
        val frameMin = min(rect.width, rect.height)
        textPaint.typeface = overlay.font.typeface()
        textPaint.textSize = overlay.size * frameMin
        val metrics = textPaint.fontMetrics
        val padX = with(density) { 10.dp.toPx() }
        val padY = with(density) { 5.dp.toPx() }
        return TextBox(
            center = Offset(
                rect.left + overlay.center.x * rect.width,
                rect.top + overlay.center.y * rect.height
            ),
            halfWidth = textPaint.measureText(overlay.text).coerceAtLeast(1f) / 2f + padX,
            halfHeight = (metrics.descent - metrics.ascent).coerceAtLeast(1f) / 2f + padY,
            rotation = overlay.rotation,
            handleGap = with(density) { 26.dp.toPx() }
        )
    }

    /** The selected label, when its box is still in the list. */
    fun selectedText(): TextOverlay? =
        selectedTextId?.let { id -> textOverlays.firstOrNull { it.id == id } }

    fun replaceText(id: Long, update: (TextOverlay) -> TextOverlay) {
        textOverlays = textOverlays.map { if (it.id == id) update(it) else it }
    }

    /**
     * The topmost label whose box or visible handle sits under [position]. The remove cross only
     * exists on the selected box, so it is ignored here and handled separately.
     */
    fun textHitAt(position: Offset, rect: Rect): TextOverlay? {
        val radius = with(density) { 22.dp.toPx() }
        return textOverlays.lastOrNull {
            val handle = textBoxOf(it, rect).hitTest(position, radius)
            handle != TextHandle.None && handle != TextHandle.Delete
        }
    }

    /** Drops the empty box a tap opened but the user never typed into. Not an undo step. */
    fun discardBlankText() {
        val selected = selectedText()
        if (selected != null && selected.text.isBlank()) {
            textOverlays = textOverlays.filterNot { it.id == selected.id }
            selectedTextId = null
        }
        pendingTextCreate = null
    }

    /** Ticking the writing bar finishes the label; an empty box is dropped without an undo step. */
    fun finishTextEdit() {
        discardBlankText()
        selectedTextId = null
        textBarOpen = false
        keyboardController?.hide()
    }

    fun selectText(overlay: TextOverlay) {
        if (selectedTextId == overlay.id) return
        discardBlankText()
        selectedTextId = overlay.id
    }

    /** Adds an empty box at [center] (normalized) and focuses the field so typing fills it in. */
    fun createText(center: Offset, focus: Boolean = true) {
        discardBlankText()
        pendingTextCreate = snapshot()
        val overlay = createTextOverlay("", center, textColor, textSize, textFont)
        textOverlays = textOverlays + overlay
        selectedTextId = overlay.id
        textBarOpen = true
        textField = TextFieldValue("")
        // A tap on the photo should raise the keyboard; typing into an empty panel already has it.
        if (focus) textFocusToken += 1
    }

    fun createTextAt(position: Offset, rect: Rect) {
        createText(normalizedPoint(position, rect))
    }

    /** Removes a label; an empty box leaves no undo step behind. */
    fun deleteText(overlay: TextOverlay) {
        if (overlay.text.isNotBlank()) pushHistory()
        textOverlays = textOverlays.filterNot { it.id == overlay.id }
        if (selectedTextId == overlay.id) {
            selectedTextId = null
            textBarOpen = false
        }
        pendingTextCreate = null
    }

    /**
     * Grabs a box's corner (resize), floating knob (rotate), cross (remove) or body (move), and selects
     * it first so the panel edits the label the finger touched.
     */
    fun beginTextGesture(position: Offset, rect: Rect): Boolean {
        val radius = with(density) { 22.dp.toPx() }
        // Only the selected box paints the cross, so only its cross can fire.
        selectedText()?.let { selected ->
            if (textBoxOf(selected, rect).hitTest(position, radius) == TextHandle.Delete) {
                deleteText(selected)
                return true
            }
        }
        val overlay = textHitAt(position, rect) ?: return false
        selectText(overlay)
        val box = textBoxOf(overlay, rect)
        val handle = box.hitTest(position, radius)
        if (handle == TextHandle.None) return false
        // Any change other than typing makes the pre-creation snapshot stale.
        pendingTextCreate = null
        val fromCenter = position - box.center
        textGesture = TextGesture(
            handle = handle,
            start = position,
            center = overlay.center,
            rotation = overlay.rotation,
            size = overlay.size,
            startAngle = Math.toDegrees(atan2(fromCenter.y.toDouble(), fromCenter.x.toDouble())).toFloat(),
            startDistance = max(fromCenter.getDistance(), 1f)
        )
        textGestureStart = snapshot()
        return true
    }

    /** Applies an in-progress text drag; one undo step is recorded when it ends. */
    fun updateTextGesture(position: Offset, rect: Rect) {
        val gesture = textGesture ?: return
        val overlay = selectedText() ?: return
        when (gesture.handle) {
            TextHandle.Move -> {
                val dx = (position.x - gesture.start.x) / rect.width
                val dy = (position.y - gesture.start.y) / rect.height
                replaceText(overlay.id) {
                    it.copy(
                        center = Offset(
                            (gesture.center.x + dx).coerceIn(0f, 1f),
                            (gesture.center.y + dy).coerceIn(0f, 1f)
                        )
                    )
                }
            }

            TextHandle.Rotate -> {
                val center = Offset(
                    rect.left + gesture.center.x * rect.width,
                    rect.top + gesture.center.y * rect.height
                )
                val offset = position - center
                val degrees =
                    Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())).toFloat()
                // Fold the delta into one turn so crossing the -180/180 seam doesn't snap the label.
                var delta = degrees - gesture.startAngle
                while (delta > 180f) delta -= 360f
                while (delta < -180f) delta += 360f
                var rotation = (gesture.rotation + delta) % 360f
                if (rotation < 0f) rotation += 360f
                replaceText(overlay.id) { it.copy(rotation = rotation) }
            }

            TextHandle.Resize -> {
                val center = Offset(
                    rect.left + gesture.center.x * rect.width,
                    rect.top + gesture.center.y * rect.height
                )
                val distance = (position - center).getDistance()
                val size = (gesture.size * (distance / gesture.startDistance))
                    .coerceIn(MinTextSize, MaxTextSize)
                if (size != overlay.size) {
                    // New boxes inherit the last size the user dragged to.
                    textSize = size
                    replaceText(overlay.id) { it.copy(size = size) }
                }
            }

            // The cross removes the box on touch-down, so it never becomes a drag.
            TextHandle.Delete -> Unit
            TextHandle.None -> Unit
        }
    }

    fun finishTextGesture() {
        textGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
        textGestureStart = null
        textGesture = null
    }

    /** A pinch interrupted a label drag: put the box back where it started. */
    fun cancelTextGesture() {
        textGestureStart?.let { textOverlays = it.texts }
        textGestureStart = null
        textGesture = null
    }

    /** Drops whatever the photo was being painted with when the compare button takes over. */
    fun cancelToolGesture() {
        cancelStroke()
        cancelErase()
        cancelBlur()
        cancelTextGesture()
        toolCursor = null
    }

    /**
     * Edits the selected label, or creates a centred one when nothing is selected. Typing before a new
     * box's first character counts as one step, so undo removes the box whole; clearing all text is its
     * own step.
     */
    fun changeText(value: String) {
        val cleaned = value.replace('\n', ' ')
        val existing = selectedText() ?: run {
            if (cleaned.isBlank()) return
            createText(Offset(0.5f, 0.5f), focus = false)
            selectedText()
        } ?: return
        if (cleaned.isBlank()) {
            if (existing.text.isBlank()) return
            pushHistory()
            replaceText(existing.id) { it.copy(text = "") }
            return
        }
        if (existing.text.isBlank()) {
            pushHistory(pendingTextCreate ?: snapshot())
            pendingTextCreate = null
        }
        replaceText(existing.id) { it.copy(text = cleaned) }
    }

    fun chooseTextColor(color: Color) {
        textColor = color
        val overlay = selectedText() ?: return
        if (overlay.color == color) return
        // A colour chosen before typing still belongs to the new box, so keep creation as the boundary.
        if (overlay.text.isBlank() && pendingTextCreate != null) {
            pushHistory(pendingTextCreate!!)
            pendingTextCreate = null
        } else {
            pushHistory()
        }
        replaceText(overlay.id) { it.copy(color = color) }
    }

    /** Resizes the selected label live; the slider commits one undo step when released. */
    fun chooseTextSize(size: Float) {
        textSize = size
        val overlay = selectedText() ?: return
        if (abs(overlay.size - size) < 0.0005f) return
        if (textSizeGestureStart == null) {
            textSizeGestureStart =
                if (overlay.text.isBlank() && pendingTextCreate != null) pendingTextCreate else snapshot()
        }
        if (overlay.text.isBlank() && pendingTextCreate != null) pendingTextCreate = null
        replaceText(overlay.id) { it.copy(size = size) }
    }

    fun finishTextSize() {
        textSizeGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
        textSizeGestureStart = null
    }

    /** Applies a font style to the selected label, or remembers it for the next one. */
    fun chooseTextFont(font: TextFont) {
        textFont = font
        val overlay = selectedText() ?: return
        if (overlay.font == font) return
        if (overlay.text.isBlank() && pendingTextCreate != null) {
            pushHistory(pendingTextCreate!!)
            pendingTextCreate = null
        } else {
            pushHistory()
        }
        replaceText(overlay.id) { it.copy(font = font) }
    }

    fun saveCopy() {
        if (saving || !changed) return
        saving = true
        progress = 0f
        val spec = EditSpec(
            rotation = rotation,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            straighten = straighten,
            crop = crop,
            colorMatrix = colorMatrix,
            focusBlur = focusBlur,
            bokeh = bokehStyle,
            drawStrokes = strokes,
            frame = frame,
            blurStrokes = blurStrokes,
            textOverlays = textOverlays
        )
        scope.launch {
            if (item.isVideo) {
                runCatching { MediaEditor.saveVideo(context, item, spec) { progress = it } }
                    .onSuccess {
                        saving = false
                        Toast.makeText(context, strings.editSaved, Toast.LENGTH_SHORT).show()
                        onEdited()
                        onClose()
                    }
                    .onFailure { onSaveFailed(it) }
                return@launch
            }
            runCatching { MediaEditor.saveImage(context, item, spec, overwrite = false) }
                .onSuccess { result ->
                    when (result) {
                        is ImageSaveResult.Saved -> onSaved(result)
                        is ImageSaveResult.NeedsWritePermission -> {
                            MediaEditor.discardOverwrite(result.tempFile)
                            onSaveFailed(IllegalStateException())
                        }
                    }
                }
                .onFailure { onSaveFailed(it) }
        }
    }

    fun overwrite() {
        if (saving || !changed || !canOverwrite) return
        saving = true
        progress = 0f
        val spec = EditSpec(
            rotation = rotation,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            straighten = straighten,
            crop = crop,
            colorMatrix = colorMatrix,
            focusBlur = focusBlur,
            bokeh = bokehStyle,
            drawStrokes = strokes,
            frame = frame,
            blurStrokes = blurStrokes,
            textOverlays = textOverlays
        )
        scope.launch {
            runCatching { MediaEditor.saveImage(context, item, spec, overwrite = true) }
                .onSuccess { result ->
                    when (result) {
                        is ImageSaveResult.Saved -> onSaved(result)
                        is ImageSaveResult.NeedsWritePermission -> {
                            pendingOverwrite = result.pendingIntent to result.tempFile
                            writeLauncher.launch(
                                IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                            )
                        }
                    }
                }
                .onFailure { onSaveFailed(it) }
        }
    }

    // A rendered overwrite waiting for the system prompt is deleted if the editor goes away.
    DisposableEffect(Unit) {
        onDispose { pendingOverwrite?.let { MediaEditor.discardOverwrite(it.second) } }
    }

    // The keyboard must float over the editor, not shrink it. Edge-to-edge alone is not enough: the
    // window can still follow the IME and resize (which also reports an IME inset of zero), so the
    // editor asks the window for ADJUST_NOTHING while it is open and puts the previous mode back when
    // it closes. API 29 derives IME insets from the resized window, so it keeps the default there.
    val editorView = LocalView.current
    DisposableEffect(editorView) {
        val window = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            editorView.context.findActivity()?.window
        } else {
            null
        }
        val previousMode = window?.attributes?.softInputMode
        if (window != null && previousMode != null) {
            window.setSoftInputMode(
                (previousMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }
        onDispose {
            if (window != null && previousMode != null) window.setSoftInputMode(previousMode)
        }
    }

    BackHandler {
        if (saving) return@BackHandler
        when {
            compareOpen -> compareOpen = false
            openPanel != EditorPanel.None -> openPanel = EditorPanel.None
            tab == EditorTab.Text && selectedTextId != null -> finishTextEdit()
            tab != EditorTab.Crop -> {
                if (tab == EditorTab.Text) {
                    discardBlankText()
                    selectedTextId = null
                }
                tab = EditorTab.Crop
            }
            else -> requestClose()
        }
    }

    // The writing bar moves with whichever is taller, the IME or the navigation bar. Using their
    // union means the bar follows the keyboard animation down and never dips past its final spot
    // when the keyboard closes; switching between the two insets caused that visible overshoot.
    val textBarInsets = WindowInsets.ime.union(WindowInsets.navigationBars)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .blockTouchesBelow()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorTopBar(
                closeEnabled = !saving,
                saveEnabled = changed && !saving && preview != null,
                canOverwrite = canOverwrite,
                undoEnabled = !saving && canUndo,
                redoEnabled = !saving && canRedo,
                resetEnabled = changed && !saving && preview != null,
                onUndo = ::undo,
                onRedo = ::redo,
                onReset = ::reset,
                onClose = ::requestClose,
                onSaveCopy = ::saveCopy,
                onSaveOptions = { showSaveMenu = true },
                onOptionsBounds = { saveMenuAnchor = it }
            )

            // The before/after control sits above the photo, centred, so it never covers the image.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                CompareButton(
                    enabled = !saving && preview != null,
                    comparisonOpen = compareOpen,
                    onToggle = {
                        if (compareOpen) {
                            compareOpen = false
                        } else {
                            cancelToolGesture()
                            compareSplit = 0.5f
                            compareOpen = true
                        }
                    },
                    onPeekChange = { peeking ->
                        if (peeking) cancelToolGesture()
                        peekOriginal = peeking
                    }
                )
            }

            // Preview with the crop frame drawn on top.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val image = preview
                if (image == null) {
                    if (loadFailed) {
                        Text(strings.unknown, color = palette.textMuted)
                    } else {
                        CircularProgressIndicator(color = palette.accent)
                    }
                    return@BoxWithConstraints
                }
                val areaWidth = constraints.maxWidth.toFloat()
                val areaHeight = constraints.maxHeight.toFloat()
                val fitScale = min(areaWidth / image.width, areaHeight / image.height)
                val baseWidth = image.width * fitScale
                val baseHeight = image.height * fitScale

                /** Keeps the photo from being panned fully out of the viewport. */
                fun clampPreviewOffset(offset: Offset, zoom: Float): Offset {
                    val maxX = ((baseWidth * zoom - areaWidth) / 2f).coerceAtLeast(0f)
                    val maxY = ((baseHeight * zoom - areaHeight) / 2f).coerceAtLeast(0f)
                    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
                }

                /** Pinch zoom around [centroid] plus two-finger pan, both in view pixels. */
                fun applyTransform(zoomChange: Float, pan: Offset, centroid: Offset) {
                    val oldZoom = previewScale
                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, MaxPreviewZoom)
                    val ratio = newZoom / oldZoom
                    val center = Offset(areaWidth / 2f, areaHeight / 2f)
                    val fromCenter = centroid - center
                    val moved = previewOffset * ratio + fromCenter * (1f - ratio) + pan
                    previewScale = newZoom
                    previewOffset = clampPreviewOffset(moved, newZoom)
                }

                val visibleOffset = clampPreviewOffset(previewOffset, previewScale)
                // Named apart from the brush width so tool code never picks this up by accident.
                val frameDrawWidth = baseWidth * previewScale
                val frameDrawHeight = baseHeight * previewScale
                val imageRect = Rect(
                    Offset(
                        areaWidth / 2f + visibleOffset.x - frameDrawWidth / 2f,
                        areaHeight / 2f + visibleOffset.y - frameDrawHeight / 2f
                    ),
                    Size(frameDrawWidth, frameDrawHeight)
                )
                // Gesture handlers outlive recomposition, so they read the rect through updated state.
                val liveImageRect = rememberUpdatedState(imageRect)
                // The finished patches' masks only change when a stroke lands, not on every frame.
                // Blur and mosaic bands are separated so each one is filled with its own copy.
                val blurMask = remember(blurStrokes, imageRect) {
                    blurMaskPath(
                        strokes = blurStrokes.filter { !it.pixelate },
                        originX = imageRect.left,
                        originY = imageRect.top,
                        scaleX = imageRect.width,
                        scaleY = imageRect.height,
                        strokeScale = min(imageRect.width, imageRect.height)
                    )
                }
                val mosaicMask = remember(blurStrokes, imageRect) {
                    blurMaskPath(
                        strokes = blurStrokes.filter { it.pixelate },
                        originX = imageRect.left,
                        originY = imageRect.top,
                        scaleX = imageRect.width,
                        scaleY = imageRect.height,
                        strokeScale = min(imageRect.width, imageRect.height)
                    )
                }
                var activeHandle by remember { mutableStateOf(CropHandle.NONE) }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotateAnimation.value
                            scaleX = flipXAnimation.value
                            scaleY = flipYAnimation.value
                        }
                        .pointerInput(
                            tab,
                            drawMode,
                            cropAspect,
                            areaWidth,
                            areaHeight,
                            baseWidth,
                            baseHeight,
                            compareOpen,
                            peekOriginal,
                            shownPanel
                        ) {
                            // The split view owns the photo while it is open, and holds are read-only.
                            if (compareOpen || peekOriginal) return@pointerInput
                            if (tab == EditorTab.Adjust || tab == EditorTab.Filters) {
                                // Nothing is dragged on these tabs, so one finger pans and two zoom.
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    applyTransform(zoom, pan, centroid)
                                }
                                return@pointerInput
                            }
                            if (tab == EditorTab.Blur) {
                                // A touch places the focus circle; dragging moves it. Two fingers
                                // still zoom and pan, so a large photo can be inspected closely.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val rect = liveImageRect.value
                                    val moving = cropRectIn(rect, crop).contains(down.position)
                                    var transformed = false
                                    if (moving) {
                                        focusGestureStart = snapshot()
                                        // Moving the circle never changes the blur intensity: the
                                        // slider on its own decides how soft the background is.
                                        focusBlur = focusBlur.copy(center = focusPoint(down.position, rect))
                                    }
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } >= 2) {
                                            transformed = true
                                            event.changes.forEach { it.consume() }
                                            applyTransform(
                                                zoomChange = event.calculateZoom(),
                                                pan = event.calculatePan(),
                                                centroid = event.calculateCentroid(useCurrent = true)
                                            )
                                        } else if (moving && !transformed) {
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change != null && change.pressed) {
                                                change.consume()
                                                focusBlur =
                                                    focusBlur.copy(center = focusPoint(change.position, rect))
                                            }
                                        }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    if (!transformed) {
                                        focusGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
                                    }
                                    focusGestureStart = null
                                }
                                return@pointerInput
                            }
                            if (tab == EditorTab.Draw) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val rect = liveImageRect.value
                                    // The eraser is as wide as the slider says, like the brush.
                                    val eraseRadius = (drawWidth * min(rect.width, rect.height) / 2f)
                                        .coerceAtLeast(10.dp.toPx())
                                    // A touch in the letterbox around the photo does nothing.
                                    var drawing = rect.contains(down.position)
                                    if (drawing) {
                                        when (drawMode) {
                                            DrawMode.Eraser -> {
                                                toolCursor = down.position
                                                beginErase()
                                                eraseAt(down.position, rect, eraseRadius)
                                            }

                                            DrawMode.Blur, DrawMode.Mosaic -> {
                                                toolCursor = down.position
                                                beginBlur(down.position, rect)
                                            }

                                            DrawMode.Brush -> beginStroke(down.position, rect)
                                        }
                                    }
                                    var transformed = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } >= 2) {
                                            // A second finger turns this into zoom/pan: drop the tool
                                            // action without leaving a dot or an undo step behind.
                                            if (drawing) {
                                                when (drawMode) {
                                                    DrawMode.Eraser -> cancelErase()
                                                    DrawMode.Blur, DrawMode.Mosaic -> cancelBlur()
                                                    DrawMode.Brush -> cancelStroke()
                                                }
                                                drawing = false
                                                toolCursor = null
                                            }
                                            transformed = true
                                            event.changes.forEach { it.consume() }
                                            applyTransform(
                                                zoomChange = event.calculateZoom(),
                                                pan = event.calculatePan(),
                                                centroid = event.calculateCentroid(useCurrent = true)
                                            )
                                        } else if (drawing && !transformed) {
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change != null && change.pressed) {
                                                change.consume()
                                                when (drawMode) {
                                                    DrawMode.Eraser -> {
                                                        toolCursor = change.position
                                                        eraseAt(change.position, rect, eraseRadius)
                                                    }

                                                    DrawMode.Blur, DrawMode.Mosaic -> {
                                                        toolCursor = change.position
                                                        extendBlur(change.position, rect)
                                                    }

                                                    DrawMode.Brush ->
                                                        extendStroke(change.position, rect)
                                                }
                                            }
                                        }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    if (drawing) {
                                        toolCursor = null
                                        when (drawMode) {
                                            DrawMode.Eraser -> finishErase()
                                            DrawMode.Blur, DrawMode.Mosaic -> finishBlur()
                                            DrawMode.Brush -> finishStroke()
                                        }
                                    }
                                }
                                return@pointerInput
                            }
                            if (tab == EditorTab.Text) {
                                // Tap a box to grab it (move, resize, rotate); tap empty canvas to
                                // start another label there. The down point is used directly so small
                                // handles are easy to catch.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val rect = liveImageRect.value
                                    val tapSlop = 8.dp.toPx()
                                    // Remembered so a tap on an already-selected box can open the
                                    // writing bar while a first tap only selects it.
                                    val previousSelection = selectedTextId
                                    // A delete fires here and leaves no gesture behind.
                                    val handled = beginTextGesture(down.position, rect)
                                    var active = textGesture != null
                                    val bodyGrab = textGesture?.handle == TextHandle.Move
                                    var moved = false
                                    var transformed = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } >= 2) {
                                            if (active) {
                                                cancelTextGesture()
                                                active = false
                                            }
                                            transformed = true
                                            event.changes.forEach { it.consume() }
                                            applyTransform(
                                                zoomChange = event.calculateZoom(),
                                                pan = event.calculatePan(),
                                                centroid = event.calculateCentroid(useCurrent = true)
                                            )
                                        } else if (!transformed) {
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change != null && change.pressed) {
                                                change.consume()
                                                if ((change.position - down.position).getDistance() >
                                                    tapSlop
                                                ) {
                                                    moved = true
                                                }
                                                if (active) updateTextGesture(change.position, rect)
                                            }
                                        }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    if (active) {
                                        finishTextGesture()
                                        // The first tap selects a box (so it can be dragged straight
                                        // away); tapping it again, once selected, opens the writing bar.
                                        val tappedSelectedBox = bodyGrab && !moved &&
                                            previousSelection != null &&
                                            previousSelection == selectedTextId
                                        if (tappedSelectedBox) {
                                            textBarOpen = true
                                            selectedText()?.let { overlay ->
                                                textField = TextFieldValue(
                                                    overlay.text,
                                                    TextRange(overlay.text.length)
                                                )
                                            }
                                            textFocusToken += 1
                                        } else if (bodyGrab) {
                                            // Selecting a different box closes the bar; a second tap
                                            // on it opens the bar again.
                                            textBarOpen = false
                                            keyboardController?.hide()
                                        }
                                    } else if (!handled && !transformed && !moved &&
                                        rect.contains(down.position)
                                    ) {
                                        createTextAt(down.position, rect)
                                    }
                                }
                                return@pointerInput
                            }
                            if (tab != EditorTab.Crop) return@pointerInput
                            val touchSlop = 28.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val rect = liveImageRect.value
                                val handle = hitTest(
                                    down.position,
                                    cropRectIn(rect, crop),
                                    touchSlop,
                                    cornersOnly = cropAspect != null
                                )
                                var transformed = false
                                if (handle != CropHandle.NONE) {
                                    activeHandle = handle
                                    cropGestureStart = snapshot()
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        // A pinch takes over: undo the half-made crop change.
                                        cropGestureStart?.let { crop = it.crop }
                                        cropGestureStart = null
                                        activeHandle = CropHandle.NONE
                                        transformed = true
                                        event.changes.forEach { it.consume() }
                                        // With the rotate tools open, a twist turns the photo itself,
                                        // exactly like twisting a collage photo. Free turn: the
                                        // angle is not capped, the photo is grown to stay full.
                                        val twist = event.calculateRotation()
                                        if (shownPanel == EditorPanel.Rotate && twist != 0f) {
                                            if (straightenGestureStart == null) {
                                                straightenGestureStart = snapshot()
                                            }
                                            straighten = normalizeAngle(straighten + twist)
                                        }
                                        applyTransform(
                                            zoomChange = event.calculateZoom(),
                                            pan = event.calculatePan(),
                                            centroid = event.calculateCentroid(useCurrent = true)
                                        )
                                    } else if (!transformed && handle != CropHandle.NONE) {
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null && change.pressed) {
                                            change.consume()
                                            val drag = change.position - change.previousPosition
                                            crop = applyDrag(
                                                crop,
                                                handle,
                                                drag.x / rect.width,
                                                drag.y / rect.height,
                                                cropAspect
                                            )
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                                activeHandle = CropHandle.NONE
                                if (!transformed) {
                                    cropGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
                                }
                                cropGestureStart = null
                                straightenGestureStart?.takeIf { it != snapshot() }
                                    ?.let { pushHistory(it) }
                                straightenGestureStart = null
                            }
                        }
                ) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                        dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
                        colorFilter = previewColorFilter
                    )
                    val frameMin = min(imageRect.width, imageRect.height)
                    val inkBounds = cropRectIn(imageRect, crop)
                    // Ink is clipped to the crop so the preview hides what the export would cut off.
                    clipRect(inkBounds.left, inkBounds.top, inkBounds.right, inkBounds.bottom) {
                        // Portrait blur: the softened frame fills the crop, then the sharp subject is
                        // laid back on top through a feathered circle. Both use the photo's colour
                        // matrix so the effect matches the exported pixels.
                        if (focusBlur.active) {
                            val focusRect = cropRectIn(imageRect, crop)
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                val destination = RectF(
                                    imageRect.left,
                                    imageRect.top,
                                    imageRect.right,
                                    imageRect.bottom
                                )
                                focusPreview?.let { native.drawBitmap(it, null, destination, blurPaint) }
                                drawFocusedSubject(
                                    canvas = native,
                                    source = image.asAndroidBitmap(),
                                    destination = destination,
                                    paint = blurPaint,
                                    centerX = focusRect.left + focusBlur.center.x * focusRect.width,
                                    centerY = focusRect.top + focusBlur.center.y * focusRect.height,
                                    radius = focusBlur.focusRadius * min(focusRect.width, focusRect.height)
                                )
                            }
                        }
                        // Softened and pixelated patches sit under the ink and text, matching the
                        // export order; the band under the finger is drawn with the same mask maths.
                        val active = activeBlur
                        val activeMask = if (active == null) {
                            null
                        } else {
                            blurMaskPath(
                                strokes = listOf(active),
                                originX = imageRect.left,
                                originY = imageRect.top,
                                scaleX = imageRect.width,
                                scaleY = imageRect.height,
                                strokeScale = frameMin
                            )
                        }
                        if (blurredPreview != null || mosaicPreview != null) {
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                val destination = RectF(
                                    imageRect.left,
                                    imageRect.top,
                                    imageRect.right,
                                    imageRect.bottom
                                )
                                fun patch(mask: Path, bitmap: Bitmap, paint: Paint) {
                                    if (mask.isEmpty) return
                                    val save = native.save()
                                    native.clipPath(mask)
                                    native.drawBitmap(bitmap, null, destination, paint)
                                    native.restoreToCount(save)
                                }
                                blurredPreview?.let { patch(blurMask, it, blurPaint) }
                                mosaicPreview?.let { patch(mosaicMask, it, mosaicPaint) }
                                if (activeMask != null && active != null) {
                                    if (active.pixelate) {
                                        mosaicPreview?.let { patch(activeMask, it, mosaicPaint) }
                                    } else {
                                        blurredPreview?.let { patch(activeMask, it, blurPaint) }
                                    }
                                }
                            }
                        }
                        // Bokeh lights sit above the patches and below the ink, matching the export.
                        if (bokehStyle != BokehStyle.None) {
                            bokehStyle.orbs.forEach { orb ->
                                val center = Offset(
                                    imageRect.left + orb.center.x * imageRect.width,
                                    imageRect.top + orb.center.y * imageRect.height
                                )
                                val radius = orb.radius * frameMin
                                if (radius <= 0f) return@forEach
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0f to orb.color.copy(alpha = orb.alpha),
                                            0.55f to orb.color.copy(alpha = orb.alpha * 0.5f),
                                            1f to Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center
                                )
                            }
                        }
                        strokes.forEach { stroke ->
                            drawEditorStroke(
                                stroke = stroke,
                                left = imageRect.left,
                                top = imageRect.top,
                                width = imageRect.width,
                                height = imageRect.height,
                                strokeWidth = stroke.width * frameMin
                            )
                        }
                        activeStroke?.let { stroke ->
                            drawEditorStroke(
                                stroke = stroke,
                                left = imageRect.left,
                                top = imageRect.top,
                                width = imageRect.width,
                                height = imageRect.height,
                                strokeWidth = stroke.width * frameMin
                            )
                        }
                        // Text sits on top of the ink, exactly like the exporter paints it.
                        textOverlays.forEach { overlay ->
                            if (overlay.text.isBlank()) return@forEach
                            textPaint.color = overlay.color.toArgb()
                            textPaint.applyTextFont(overlay.font, overlay.size * frameMin)
                            val centerX = imageRect.left + overlay.center.x * imageRect.width
                            val centerY = imageRect.top + overlay.center.y * imageRect.height
                            val metrics = textPaint.fontMetrics
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                val save = native.save()
                                native.rotate(overlay.rotation, centerX, centerY)
                                native.drawText(
                                    overlay.text,
                                    centerX,
                                    centerY - (metrics.ascent + metrics.descent) / 2f,
                                    textPaint
                                )
                                native.restoreToCount(save)
                            }
                        }
                    }
                    // The frame covers the photo edges, so it goes on after the ink and labels; the
                    // selection box is still drawn above it so handles stay grabbable.
                    if (frame != EditorFrame.None && !compareOpen && !peekOriginal) {
                        drawEditorFrame(frame, inkBounds)
                    }
                    // The selection box sits above the clip so an off-crop label can still be grabbed.
                    if (tab == EditorTab.Text) {
                        textOverlays.firstOrNull { it.id == selectedTextId }?.let { overlay ->
                            val box = textBoxOf(overlay, imageRect)
                            val accent = palette.accent
                            // A rounded, softly filled frame with rounded-square handles reads calmer
                            // than the old sharp rectangle with round dots.
                            val radius = 10.dp.toPx()
                            val edge = 1.25.dp.toPx()
                            val boxTopLeft = Offset(
                                box.center.x - box.halfWidth,
                                box.center.y - box.halfHeight
                            )
                            val boxSize = Size(box.halfWidth * 2f, box.halfHeight * 2f)
                            rotate(degrees = overlay.rotation, pivot = box.center) {
                                drawRoundRect(
                                    color = accent.copy(alpha = 0.08f),
                                    topLeft = boxTopLeft,
                                    size = boxSize,
                                    cornerRadius = CornerRadius(radius)
                                )
                                drawRoundRect(
                                    color = accent.copy(alpha = 0.9f),
                                    topLeft = boxTopLeft,
                                    size = boxSize,
                                    cornerRadius = CornerRadius(radius),
                                    style = Stroke(edge)
                                )
                                val knob = Offset(box.center.x, box.center.y - box.halfHeight)
                                drawLine(
                                    color = accent.copy(alpha = 0.55f),
                                    start = knob,
                                    end = Offset(knob.x, knob.y - box.handleGap),
                                    strokeWidth = edge
                                )
                                // White fill keeps the handles visible on bright photos.
                                val handleSize = 11.dp.toPx()
                                val handleRadius = CornerRadius(handleSize * 0.38f)
                                listOf(
                                    Offset(box.center.x - box.halfWidth, box.center.y - box.halfHeight),
                                    Offset(box.center.x + box.halfWidth, box.center.y - box.halfHeight),
                                    Offset(box.center.x - box.halfWidth, box.center.y + box.halfHeight),
                                    Offset(box.center.x + box.halfWidth, box.center.y + box.halfHeight)
                                ).forEach { corner ->
                                    val topLeft = Offset(
                                        corner.x - handleSize / 2f,
                                        corner.y - handleSize / 2f
                                    )
                                    val size = Size(handleSize, handleSize)
                                    drawRoundRect(Color.White, topLeft, size, handleRadius)
                                    drawRoundRect(
                                        color = accent,
                                        topLeft = topLeft,
                                        size = size,
                                        cornerRadius = handleRadius,
                                        style = Stroke(1.5.dp.toPx())
                                    )
                                }
                                // A small pill at the top turns the label; its arc hints at rotation.
                                val knobCenter = Offset(knob.x, knob.y - box.handleGap)
                                val pillWidth = 24.dp.toPx()
                                val pillHeight = 15.dp.toPx()
                                val pillTopLeft = Offset(
                                    knobCenter.x - pillWidth / 2f,
                                    knobCenter.y - pillHeight / 2f
                                )
                                val pillSize = Size(pillWidth, pillHeight)
                                val pillRadius = CornerRadius(pillHeight / 2f)
                                drawRoundRect(Color.White, pillTopLeft, pillSize, pillRadius)
                                drawRoundRect(
                                    color = accent.copy(alpha = 0.9f),
                                    topLeft = pillTopLeft,
                                    size = pillSize,
                                    cornerRadius = pillRadius,
                                    style = Stroke(edge)
                                )
                                val arcSize = 8.dp.toPx()
                                drawArc(
                                    color = accent,
                                    startAngle = 150f,
                                    sweepAngle = 240f,
                                    useCenter = false,
                                    topLeft = Offset(
                                        knobCenter.x - arcSize / 2f,
                                        knobCenter.y - arcSize / 2f
                                    ),
                                    size = Size(arcSize, arcSize),
                                    style = Stroke(1.6.dp.toPx())
                                )
                                // The remove cross sits just outside the top-left corner and is
                                // hit-tested before the corners, so it never gets swallowed.
                                val gap = box.handleGap * 0.8f
                                val delete = Offset(
                                    box.center.x - box.halfWidth - gap,
                                    box.center.y - box.halfHeight - gap
                                )
                                val deleteRadius = 10.dp.toPx()
                                drawCircle(Color.White, deleteRadius, delete)
                                drawCircle(
                                    accent.copy(alpha = 0.9f),
                                    deleteRadius,
                                    delete,
                                    style = Stroke(edge)
                                )
                                val arm = deleteRadius * 0.42f
                                drawLine(
                                    color = accent,
                                    start = Offset(delete.x - arm, delete.y - arm),
                                    end = Offset(delete.x + arm, delete.y + arm),
                                    strokeWidth = 1.6.dp.toPx()
                                )
                                drawLine(
                                    color = accent,
                                    start = Offset(delete.x - arm, delete.y + arm),
                                    end = Offset(delete.x + arm, delete.y - arm),
                                    strokeWidth = 1.6.dp.toPx()
                                )
                            }
                        }
                    }
                    // Ink and text are clipped to the crop; show its edge so nothing vanishes unexplained.
                    if ((tab == EditorTab.Draw || tab == EditorTab.Text) && !crop.isFull &&
                        !compareOpen && !peekOriginal
                    ) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.35f),
                            topLeft = inkBounds.topLeft,
                            size = inkBounds.size,
                            style = Stroke(1.dp.toPx())
                        )
                    }
                    if (tab == EditorTab.Crop && !compareOpen && !peekOriginal) {
                        val cropRect = cropRectIn(imageRect, crop)
                        val shade = Color.Black.copy(alpha = 0.55f)
                        // Dim everything outside the crop.
                        drawRect(shade, Offset(imageRect.left, imageRect.top), Size(imageRect.width, cropRect.top - imageRect.top))
                        drawRect(shade, Offset(imageRect.left, cropRect.bottom), Size(imageRect.width, imageRect.bottom - cropRect.bottom))
                        drawRect(shade, Offset(imageRect.left, cropRect.top), Size(cropRect.left - imageRect.left, cropRect.height))
                        drawRect(shade, Offset(cropRect.right, cropRect.top), Size(imageRect.right - cropRect.right, cropRect.height))
                        // Rule-of-thirds grid while adjusting.
                        if (activeHandle != CropHandle.NONE) {
                            for (i in 1..2) {
                                val gx = cropRect.left + cropRect.width * i / 3f
                                val gy = cropRect.top + cropRect.height * i / 3f
                                drawLine(Color.White.copy(alpha = 0.4f), Offset(gx, cropRect.top), Offset(gx, cropRect.bottom), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.4f), Offset(cropRect.left, gy), Offset(cropRect.right, gy), 1.dp.toPx())
                            }
                        }
                        drawRect(Color.White, cropRect.topLeft, cropRect.size, style = Stroke(1.5.dp.toPx()))
                        // Corner handles
                        val handleLength = 20.dp.toPx()
                        val handleStroke = 3.5.dp.toPx()
                        val handleColor = Color.White
                        listOf(
                            Triple(cropRect.topLeft, 1f, 1f),
                            Triple(Offset(cropRect.right, cropRect.top), -1f, 1f),
                            Triple(Offset(cropRect.left, cropRect.bottom), 1f, -1f),
                            Triple(cropRect.bottomRight, -1f, -1f)
                        ).forEach { (corner, dx, dy) ->
                            drawLine(handleColor, corner, Offset(corner.x + handleLength * dx, corner.y), handleStroke)
                            drawLine(handleColor, corner, Offset(corner.x, corner.y + handleLength * dy), handleStroke)
                        }
                    }
                    // The portrait focus circle: drag it to move the sharp area.
                    if (tab == EditorTab.Blur && !compareOpen && !peekOriginal) {
                        val focusRect = cropRectIn(imageRect, crop)
                        if (focusRect.width > 0f && focusRect.height > 0f) {
                            val center = Offset(
                                focusRect.left + focusBlur.center.x * focusRect.width,
                                focusRect.top + focusBlur.center.y * focusRect.height
                            )
                            val radius =
                                (focusBlur.focusRadius * min(focusRect.width, focusRect.height))
                                    .coerceAtLeast(8.dp.toPx())
                            drawCircle(Color.White.copy(alpha = 0.10f), radius, center)
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.30f),
                                radius = radius + 1.5.dp.toPx(),
                                center = center,
                                style = Stroke(3.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = radius,
                                center = center,
                                style = Stroke(1.5.dp.toPx())
                            )
                            drawCircle(Color.White, 2.5.dp.toPx(), center)
                        }
                    }
                    // The eraser and blur brush cursor: a white ring around the area a drag touches,
                    // with a faint shadow ring so it stays visible on white photos.
                    if (tab == EditorTab.Draw && drawMode != DrawMode.Brush &&
                        !compareOpen && !peekOriginal
                    ) {
                        toolCursor?.let { point ->
                            val radius =
                                (drawWidth * frameMin / 2f).coerceAtLeast(10.dp.toPx())
                            drawCircle(
                                color = Color.White.copy(alpha = 0.18f),
                                radius = radius,
                                center = point
                            )
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.35f),
                                radius = radius + 1.5.dp.toPx(),
                                center = point,
                                style = Stroke(2.5.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = radius,
                                center = point,
                                style = Stroke(2.dp.toPx())
                            )
                        }
                    }
                    // While the width slider moves, show the exact brush, eraser or blur footprint
                    // in the middle of the visible photo; it lingers a moment after release.
                    if (tab == EditorTab.Draw && showSizePreview &&
                        (drawMode == DrawMode.Brush || drawMode == DrawMode.Eraser ||
                            drawMode == DrawMode.Blur || drawMode == DrawMode.Mosaic) &&
                        !compareOpen && !peekOriginal
                    ) {
                        val radius = (drawWidth * frameMin / 2f).coerceAtLeast(4.dp.toPx())
                        val visible = imageRect.intersect(Rect(Offset.Zero, size))
                        if (!visible.isEmpty) {
                            val center = visible.center
                            drawCircle(Color.White.copy(alpha = 0.10f), radius, center)
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.30f),
                                radius = radius + 1.5.dp.toPx(),
                                center = center,
                                style = Stroke(3.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = radius,
                                center = center,
                                style = Stroke(1.5.dp.toPx())
                            )
                        }
                    }
                }

                // Before/after: the untouched frame is drawn over the left part of the photo, or
                // over all of it while peeking. The edited half is the canvas underneath.
                if (compareOpen || peekOriginal) {
                    val split = if (compareOpen) compareSplit else 1f
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = rotateAnimation.value
                                scaleX = flipXAnimation.value
                                scaleY = flipYAnimation.value
                            }
                            .then(
                                if (compareOpen) {
                                    Modifier.pointerInput(Unit) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val rect = liveImageRect.value
                                            if (rect.width > 0f) {
                                                compareSplit =
                                                    (compareSplit + dragAmount / rect.width)
                                                        .coerceIn(0f, 1f)
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        clipRect(
                            imageRect.left,
                            imageRect.top,
                            imageRect.left + imageRect.width * split,
                            imageRect.bottom
                        ) {
                            drawImage(
                                image = image,
                                dstOffset = IntOffset(
                                    imageRect.left.roundToInt(),
                                    imageRect.top.roundToInt()
                                ),
                                dstSize = IntSize(
                                    imageRect.width.roundToInt(),
                                    imageRect.height.roundToInt()
                                )
                            )
                        }
                        if (compareOpen) {
                            val x = imageRect.left + imageRect.width * compareSplit
                            drawLine(
                                color = Color.Black.copy(alpha = 0.35f),
                                start = Offset(x, imageRect.top),
                                end = Offset(x, imageRect.bottom),
                                strokeWidth = 3.dp.toPx()
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(x, imageRect.top),
                                end = Offset(x, imageRect.bottom),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            // A round grip with chevrons, so the divider reads as draggable.
                            val center = Offset(x, imageRect.center.y)
                            val radius = 14.dp.toPx()
                            drawCircle(Color.Black.copy(alpha = 0.22f), radius + 1.dp.toPx(), center)
                            drawCircle(Color.White, radius, center)
                            val arm = radius * 0.42f
                            val arrow = Color(0xFF111111)
                            drawLine(
                                arrow,
                                Offset(center.x - arm, center.y - arm),
                                Offset(center.x - arm * 1.8f, center.y),
                                2.dp.toPx()
                            )
                            drawLine(
                                arrow,
                                Offset(center.x - arm * 1.8f, center.y),
                                Offset(center.x - arm, center.y + arm),
                                2.dp.toPx()
                            )
                            drawLine(
                                arrow,
                                Offset(center.x + arm, center.y - arm),
                                Offset(center.x + arm * 1.8f, center.y),
                                2.dp.toPx()
                            )
                            drawLine(
                                arrow,
                                Offset(center.x + arm * 1.8f, center.y),
                                Offset(center.x + arm, center.y + arm),
                                2.dp.toPx()
                            )
                        }
                    }
                }
            }

            // A fresh box (or a tap on an existing one) raises the writing bar's keyboard after the
            // layout settles. Only those explicit taps bump the token, so grabbing a corner or knob
            // never opens the keyboard mid-drag and resizes the preview under the finger.
            LaunchedEffect(textFocusToken) {
                if (textFocusToken == 0 || tab != EditorTab.Text || selectedTextId == null) {
                    return@LaunchedEffect
                }
                delay(120)
                runCatching { textFocusRequester.requestFocus() }
                keyboardController?.show()
            }

            // Opening the bar always starts after the existing text, never at the beginning.
            LaunchedEffect(textBarOpen, selectedTextId) {
                if (!textBarOpen) return@LaunchedEffect
                val text = selectedText()?.text.orEmpty()
                textField = TextFieldValue(text, TextRange(text.length))
            }

            when (tab) {
                EditorTab.Crop -> {
                    AnimatedVisibility(
                        visible = openPanel != EditorPanel.None,
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                    ) {
                        EditorInlinePanel(
                            panel = shownPanel,
                            enabled = !saving && preview != null,
                            ratio = ratio,
                            straighten = straighten,
                            onStraightenReset = {
                                if (straighten != 0f) {
                                    pushHistory()
                                    straighten = 0f
                                }
                            },
                            onRotateLeft = { rotate(clockwise = false) },
                            onRotateRight = { rotate(clockwise = true) },
                            onSelectRatio = ::chooseRatio
                        )
                    }

                    EditorToolbar(
                        enabled = !saving && preview != null,
                        openPanel = openPanel,
                        onToggleRotate = { togglePanel(EditorPanel.Rotate) },
                        // Any one-shot tool closes whatever inline panel was open first.
                        onMirrorHorizontally = {
                            openPanel = EditorPanel.None
                            mirrorHorizontally()
                        },
                        onMirrorVertically = {
                            openPanel = EditorPanel.None
                            mirrorVertically()
                        },
                        onToggleRatio = { togglePanel(EditorPanel.Ratio) }
                    )
                }

                EditorTab.Adjust -> AdjustPanel(
                    adjustments = adjustments,
                    selected = selectedAdjustment,
                    enabled = !saving && preview != null,
                    onSelect = { selectedAdjustment = it },
                    onChange = { adjustment, value ->
                        // A drag is one undo step: remember where it started, commit when it ends.
                        if (adjustGestureStart == null) adjustGestureStart = snapshot()
                        adjustments = adjustments.with(adjustment, value)
                    },
                    onChangeFinished = {
                        adjustGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
                        adjustGestureStart = null
                    },
                    onNudge = { adjustment, delta ->
                        val current = adjustments.valueOf(adjustment)
                        val next = (current + delta).coerceIn(-1f, 1f)
                        if (next != current) {
                            pushHistory()
                            adjustments = adjustments.with(adjustment, next)
                        }
                    }
                )

                EditorTab.Filters -> {
                    val image = preview
                    if (image != null) {
                        FilterPanel(
                            preview = image,
                            filters = EditorFilters,
                            selected = filter,
                            bokeh = bokehStyle,
                            frame = frame,
                            enabled = !saving,
                            matrixFor = { candidate -> editorColorMatrix(adjustments, candidate) },
                            onSelect = { candidate ->
                                if (candidate.id != filter.id) {
                                    pushHistory()
                                    filter = candidate
                                }
                            },
                            onSelectBokeh = { chosen ->
                                if (chosen != bokehStyle) {
                                    pushHistory()
                                    bokehStyle = chosen
                                }
                            },
                            onSelectFrame = { chosen ->
                                if (chosen != frame) {
                                    pushHistory()
                                    frame = chosen
                                }
                            }
                        )
                    }
                }

                EditorTab.Blur -> BlurPanel(
                    focusRadius = focusBlur.focusRadius,
                    blurStrength = focusBlur.strength,
                    enabled = !saving && preview != null,
                    onFocusRadiusChange = { value ->
                        if (focusGestureStart == null) focusGestureStart = snapshot()
                        // Resizing the circle must not switch the blur on; the intensity slider is
                        // the only control that does.
                        focusBlur = focusBlur.copy(focusRadius = value)
                    },
                    onFocusRadiusFinished = {
                        focusGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
                        focusGestureStart = null
                    },
                    onBlurStrengthChange = { value ->
                        if (focusGestureStart == null) focusGestureStart = snapshot()
                        focusBlur = focusBlur.copy(strength = value)
                    },
                    onBlurStrengthFinished = {
                        focusGestureStart?.takeIf { it != snapshot() }?.let { pushHistory(it) }
                        focusGestureStart = null
                    }
                )

                EditorTab.Draw -> DrawPanel(
                    color = drawColor,
                    brushWidth = drawWidth,
                    mode = drawMode,
                    blurShape = blurShape,
                    enabled = !saving && preview != null,
                    onSelectColor = {
                        drawColor = it
                        if (drawMode == DrawMode.Eraser) drawMode = DrawMode.Brush
                    },
                    onBrushWidthChange = {
                        drawWidth = it
                        sizePreviewToken += 1
                    },
                    onModeChange = { mode ->
                        drawMode = mode
                        toolCursor = null
                    },
                    onBlurShapeChange = { blurShape = it }
                )

                EditorTab.Text -> TextPanel(
                    color = selectedText()?.color ?: textColor,
                    font = selectedText()?.font ?: textFont,
                    size = selectedText()?.size ?: textSize,
                    enabled = !saving && preview != null,
                    onSelectColor = ::chooseTextColor,
                    onSelectFont = ::chooseTextFont,
                    onSizeChange = ::chooseTextSize,
                    onSizeChangeFinished = ::finishTextSize
                )
            }

            // The section pills sit at the very bottom; their controls open in the space above them.
            EditorTabs(
                selected = tab,
                enabled = !saving,
                onSelect = { selected ->
                    if (tab == EditorTab.Text && selected != EditorTab.Text) {
                        discardBlankText()
                        selectedTextId = null
                        textBarOpen = false
                    }
                    tab = selected
                    openPanel = EditorPanel.None
                    adjustGestureStart = null
                    cropGestureStart = null
                    strokeGestureStart = null
                    eraseGestureStart = null
                    blurGestureStart = null
                    focusGestureStart = null
                    textGestureStart = null
                    textGesture = null
                    toolCursor = null
                }
            )
        }

        // While the writing bar is open it is the only control. A transparent layer swallows every
        // touch outside the bar, so a stray tap cannot select, move or paint on the photo behind it.
        if (tab == EditorTab.Text && textBarOpen && selectedText() != null && !saving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        // The writing bar is a free-floating overlay: it rides above the keyboard while typing and
        // drops to the bottom of the page, still floating over the editor, when the keyboard hides.
        AnimatedVisibility(
            visible = tab == EditorTab.Text && selectedText() != null && textBarOpen && !saving,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(textBarInsets)
                .padding(bottom = 8.dp),
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            TextEntryBar(
                value = textField,
                enabled = !saving,
                focusRequester = textFocusRequester,
                onValueChange = { field ->
                    textField = field
                    changeText(field.text)
                },
                onDone = ::finishTextEdit
            )
        }

        if (saving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.overlayScrim)
                    .blockTouchesBelow(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (item.isVideo) {
                        ThemedProgressBar(progress = progress, modifier = Modifier.width(220.dp))
                    } else {
                        CircularProgressIndicator(color = palette.accent)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(strings.saving, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }

    ThemedPopupMenu(
        expanded = showSaveMenu,
        onDismissRequest = { showSaveMenu = false },
        anchorBounds = saveMenuAnchor,
        entries = buildList {
            // Saving a copy is the main button; this menu only offers the destructive alternative.
            if (canOverwrite) {
                add(MenuEntry(strings.overwriteOriginal, destructive = true) { showOverwriteDialog = true })
            }
        }
    )

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
                DialogButton(strings.saveCopy, style = DialogButtonStyle.Primary) {
                    showDiscardDialog = false
                    saveCopy()
                }
            )
        )
    }

    if (showOverwriteDialog) {
        ThemedDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = strings.overwriteTitle,
            message = strings.overwriteMessage,
            buttons = listOf(
                DialogButton(strings.cancel, style = DialogButtonStyle.Cancel) {
                    showOverwriteDialog = false
                },
                DialogButton(strings.overwriteAction, style = DialogButtonStyle.Destructive) {
                    showOverwriteDialog = false
                    overwrite()
                }
            )
        )
    }
}

@Composable
private fun EditorTopBar(
    closeEnabled: Boolean,
    saveEnabled: Boolean,
    canOverwrite: Boolean,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    resetEnabled: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onSaveCopy: () -> Unit,
    onSaveOptions: () -> Unit,
    onOptionsBounds: (Rect) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(68.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val closeInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressScale(closeInteraction, 0.9f)
                .clip(CircleShape)
                .background(palette.surface)
                .clickable(
                    interactionSource = closeInteraction,
                    indication = ripple(color = palette.onBackground),
                    enabled = closeEnabled,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.close,
                tint = if (closeEnabled) palette.onBackground else palette.textMuted,
                modifier = Modifier.size(25.dp)
            )
        }
        Text(
            text = strings.edit,
            color = palette.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 6.dp)
        )
        TopBarIconButton(
            icon = Icons.AutoMirrored.Rounded.Undo,
            contentDescription = strings.undo,
            enabled = undoEnabled,
            iconSize = 26.dp,
            onClick = onUndo
        )
        Spacer(modifier = Modifier.width(2.dp))
        TopBarIconButton(
            icon = Icons.AutoMirrored.Rounded.Redo,
            contentDescription = strings.redo,
            enabled = redoEnabled,
            iconSize = 26.dp,
            onClick = onRedo
        )
        Spacer(modifier = Modifier.width(2.dp))
        TopBarIconButton(
            icon = Icons.Rounded.Restore,
            contentDescription = strings.reset,
            enabled = resetEnabled,
            iconSize = 26.dp,
            onClick = onReset
        )
        Spacer(modifier = Modifier.width(4.dp))
        SavePill(
            enabled = saveEnabled,
            canOverwrite = canOverwrite,
            onSaveCopy = onSaveCopy,
            onSaveOptions = onSaveOptions,
            onOptionsBounds = onOptionsBounds
        )
    }
}

/** A compact borderless icon button for the top bar; disabled steps fade to the muted text colour. */
@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    iconSize: Dp,
    onClick: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.onBackground),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) palette.onBackground else palette.textMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * The save button. With an overwritable original it is split: the main half saves a copy, the chevron
 * opens the destructive "Overwrite original" option. Other formats show a single-pill save-copy button.
 */
@Composable
private fun SavePill(
    enabled: Boolean,
    canOverwrite: Boolean,
    onSaveCopy: () -> Unit,
    onSaveOptions: () -> Unit,
    onOptionsBounds: (Rect) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val background = if (enabled) palette.accent else palette.surfaceVariant
    val content = if (enabled) palette.onAccent else palette.textMuted
    val copyInteraction = remember { MutableInteractionSource() }
    val optionsInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .pressScale(copyInteraction, 0.94f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = copyInteraction,
                    indication = ripple(color = content),
                    enabled = enabled,
                    onClick = onSaveCopy
                )
                .padding(start = 18.dp, end = if (canOverwrite) 12.dp else 18.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = strings.save,
                color = content,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (canOverwrite) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(content.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .pressScale(optionsInteraction, 0.9f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = optionsInteraction,
                        indication = ripple(color = content),
                        enabled = enabled,
                        onClick = onSaveOptions
                    )
                    .onGloballyPositioned { onOptionsBounds(it.boundsInRoot()) }
                    .padding(horizontal = 9.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = strings.saveOptions,
                    tint = content,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Rotate / mirror / ratio tools in the One UI tool-row style. */
@Composable
private fun EditorToolbar(
    enabled: Boolean,
    openPanel: EditorPanel,
    onToggleRotate: () -> Unit,
    onMirrorHorizontally: () -> Unit,
    onMirrorVertically: () -> Unit,
    onToggleRatio: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemedToolItem(
            icon = Icons.Rounded.Rotate90DegreesCw,
            label = strings.rotate,
            onClick = onToggleRotate,
            enabled = enabled,
            selected = openPanel == EditorPanel.Rotate
        )
        ThemedToolItem(
            icon = Icons.Rounded.Flip,
            label = strings.flipHorizontal,
            onClick = onMirrorHorizontally,
            enabled = enabled
        )
        ThemedToolItem(
            icon = Icons.Rounded.Flip,
            label = strings.flipVertical,
            onClick = onMirrorVertically,
            enabled = enabled,
            iconRotation = 90f
        )
        ThemedToolItem(
            icon = Icons.Rounded.Crop,
            label = strings.ratio,
            onClick = onToggleRatio,
            enabled = enabled,
            selected = openPanel == EditorPanel.Ratio
        )
    }
}

/** The panel that slides in between the preview and the tool row; only one is open at a time. */
@Composable
private fun EditorInlinePanel(
    panel: EditorPanel,
    enabled: Boolean,
    ratio: CropRatio,
    straighten: Float,
    onStraightenReset: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onSelectRatio: (CropRatio) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
    ) {
        when (panel) {
            EditorPanel.Rotate -> RotatePanel(
                enabled = enabled,
                straighten = straighten,
                onStraightenReset = onStraightenReset,
                onRotateLeft = onRotateLeft,
                onRotateRight = onRotateRight
            )
            EditorPanel.Ratio -> RatioPicker(ratio, onSelectRatio)
            EditorPanel.None -> Unit
        }
    }
}

/**
 * The 90° turns plus a reminder that a two-finger twist straightens the photo directly. The current
 * angle doubles as a reset target once the photo has been tilted.
 */
@Composable
private fun RotatePanel(
    enabled: Boolean,
    straighten: Float,
    onStraightenReset: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.straightenHint,
                color = palette.textMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            if (straighten != 0f && enabled) {
                Text(
                    text = "${straighten.roundToInt()}° · ${strings.reset}",
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onStraightenReset() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PanelAction(
                icon = Icons.Rounded.Rotate90DegreesCcw,
                label = strings.rotateLeft,
                onClick = onRotateLeft,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            PanelAction(
                icon = Icons.Rounded.Rotate90DegreesCw,
                label = strings.rotateRight,
                onClick = onRotateRight,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppTheme.current.palette
    val content = if (enabled) palette.onBackground else palette.textMuted
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = palette.accent),
                enabled = enabled,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The crop ratios as a horizontally scrollable row of One UI chips below the photo. */
@Composable
private fun RatioPicker(selected: CropRatio, onSelect: (CropRatio) -> Unit) {
    val palette = LocalAppTheme.current.palette
    val entries = listOf(
        CropRatio.Free to strings.ratioFree,
        CropRatio.Original to strings.ratioOriginal,
        CropRatio.Fixed(1f, 1f) to strings.ratioSquare,
        CropRatio.Fixed(3f, 2f) to strings.ratio3x2,
        CropRatio.Fixed(2f, 3f) to strings.ratio2x3,
        CropRatio.Fixed(4f, 3f) to strings.ratio4x3,
        CropRatio.Fixed(3f, 4f) to strings.ratio3x4,
        CropRatio.Fixed(16f, 9f) to strings.ratio16x9,
        CropRatio.Fixed(9f, 16f) to strings.ratio9x16
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEach { (ratio, label) ->
            val isSelected = ratio == selected
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) palette.accent else palette.surfaceVariant)
                    .clickable(onClick = { onSelect(ratio) })
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) palette.onAccent else palette.onBackground,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

private fun cropRectIn(imageRect: Rect, crop: NormalizedRect) = Rect(
    imageRect.left + crop.left * imageRect.width,
    imageRect.top + crop.top * imageRect.height,
    imageRect.left + crop.right * imageRect.width,
    imageRect.top + crop.bottom * imageRect.height
)

/** The largest crop with [aspect] (normalized width/height) around the current crop's center. */
private fun fitCrop(current: NormalizedRect, aspect: Float): NormalizedRect {
    var width = 1f
    var height = width / aspect
    if (height > 1f) {
        height = 1f
        width = height * aspect
    }
    val centerX = (current.left + current.right) / 2f
    val centerY = (current.top + current.bottom) / 2f
    val left = (centerX - width / 2f).coerceIn(0f, 1f - width)
    val top = (centerY - height / 2f).coerceIn(0f, 1f - height)
    return NormalizedRect(left, top, left + width, top + height)
}

private fun hitTest(position: Offset, rect: Rect, slop: Float, cornersOnly: Boolean): CropHandle {
    val nearLeft = abs(position.x - rect.left) < slop
    val nearRight = abs(position.x - rect.right) < slop
    val nearTop = abs(position.y - rect.top) < slop
    val nearBottom = abs(position.y - rect.bottom) < slop
    val withinX = position.x > rect.left - slop && position.x < rect.right + slop
    val withinY = position.y > rect.top - slop && position.y < rect.bottom + slop
    val corner = when {
        nearLeft && nearTop -> CropHandle.TOP_LEFT
        nearRight && nearTop -> CropHandle.TOP_RIGHT
        nearLeft && nearBottom -> CropHandle.BOTTOM_LEFT
        nearRight && nearBottom -> CropHandle.BOTTOM_RIGHT
        else -> CropHandle.NONE
    }
    if (corner != CropHandle.NONE) return corner
    // With a locked ratio only the corners can change the size; the whole rect still moves.
    if (cornersOnly) return if (rect.contains(position)) CropHandle.MOVE else CropHandle.NONE
    return when {
        nearLeft && withinY -> CropHandle.LEFT
        nearRight && withinY -> CropHandle.RIGHT
        nearTop && withinX -> CropHandle.TOP
        nearBottom && withinX -> CropHandle.BOTTOM
        rect.contains(position) -> CropHandle.MOVE
        else -> CropHandle.NONE
    }
}

/**
 * Applies a drag in normalized units. A free crop moves every edge; a locked one keeps [aspect] by
 * moving the dragged corner and the two edges that meet there.
 */
private fun applyDrag(
    crop: NormalizedRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    aspect: Float?
): NormalizedRect {
    if (handle == CropHandle.NONE) return crop
    if (handle == CropHandle.MOVE) {
        val width = crop.width
        val height = crop.height
        val left = (crop.left + dx).coerceIn(0f, 1f - width)
        val top = (crop.top + dy).coerceIn(0f, 1f - height)
        return NormalizedRect(left, top, left + width, top + height)
    }
    if (aspect != null) return dragCorner(crop, handle, dx, dy, aspect)

    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom
    if (handle == CropHandle.LEFT || handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT) {
        left = (left + dx).coerceIn(0f, right - MIN_CROP)
    }
    if (handle == CropHandle.RIGHT || handle == CropHandle.TOP_RIGHT || handle == CropHandle.BOTTOM_RIGHT) {
        right = (right + dx).coerceIn(left + MIN_CROP, 1f)
    }
    if (handle == CropHandle.TOP || handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT) {
        top = (top + dy).coerceIn(0f, bottom - MIN_CROP)
    }
    if (handle == CropHandle.BOTTOM || handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM_RIGHT) {
        bottom = (bottom + dy).coerceIn(top + MIN_CROP, 1f)
    }
    return NormalizedRect(left, top, right, bottom)
}

private fun dragCorner(
    crop: NormalizedRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    aspect: Float
): NormalizedRect {
    val fromLeft = handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
    val fromTop = handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
    // The corner opposite the dragged one stays where it is.
    val anchorX = if (fromLeft) crop.right else crop.left
    val anchorY = if (fromTop) crop.bottom else crop.top
    val movingX = (if (fromLeft) crop.left else crop.right) + dx
    val movingY = (if (fromTop) crop.top else crop.bottom) + dy

    val availableWidth = if (fromLeft) anchorX else 1f - anchorX
    val availableHeight = if (fromTop) anchorY else 1f - anchorY
    val limit = min(availableWidth, availableHeight * aspect)
    val minWidth = max(MIN_CROP, MIN_CROP * aspect)
    val desired = max(abs(movingX - anchorX), abs(movingY - anchorY) * aspect)
    val width = desired.coerceIn(min(minWidth, limit), limit)
    val height = width / aspect

    val left = if (fromLeft) anchorX - width else anchorX
    val top = if (fromTop) anchorY - height else anchorY
    return NormalizedRect(left, top, left + width, top + height)
}

/**
 * The before/after button pinned to the right of the photo. A tap opens or closes the split view;
 * holding it shows the untouched frame until the finger lifts.
 */
@Composable
private fun CompareButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    comparisonOpen: Boolean,
    onToggle: () -> Unit,
    onPeekChange: (Boolean) -> Unit
) {
    val palette = LocalAppTheme.current.palette
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.surface.copy(alpha = 0.94f))
            .border(1.dp, palette.onBackground.copy(alpha = 0.10f), CircleShape)
            .pointerInput(enabled, comparisonOpen) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    // Releasing before the long-press timeout is a tap; holding is a peek.
                    val releasedEarly = withTimeoutOrNull(
                        viewConfiguration.longPressTimeoutMillis
                    ) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) break
                            if (!change.pressed) return@withTimeoutOrNull true
                            if ((change.position - down.position).getDistance() > 12.dp.toPx()) {
                                moved = true
                                change.consume()
                                break
                            }
                        }
                        false
                    }
                    when {
                        releasedEarly == true -> onToggle()
                        releasedEarly == false && moved -> Unit
                        releasedEarly == null && !comparisonOpen -> {
                            // Holding shows the original for as long as the finger stays down.
                            onPeekChange(true)
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                            onPeekChange(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Compare,
            contentDescription = strings.compare,
            tint = if (enabled) palette.onBackground else palette.textMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The floating writing bar shown while a label is selected. It owns the keyboard, mirrors the label
 * live as the user types, and its tick finishes the edit and closes the keyboard.
 */
@Composable
private fun TextEntryBar(
    value: TextFieldValue,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit
) {
    val palette = LocalAppTheme.current.palette
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
            .clip(shape)
            .background(palette.surface.copy(alpha = 0.98f))
            .border(1.dp, palette.onBackground.copy(alpha = 0.08f), shape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = palette.onBackground, fontSize = 16.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { field ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            text = strings.textPlaceholder,
                            color = palette.textMuted,
                            fontSize = 16.sp
                        )
                    }
                    field()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (enabled) palette.accent else palette.surfaceVariant)
                .clickable(enabled = enabled, onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = strings.done,
                tint = if (enabled) palette.onAccent else palette.textMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The activity that hosts this composition, used to reach its window. */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Keeps a free turn readable: -180..180 degrees. */
private fun normalizeAngle(value: Float): Float {
    var degrees = value % 360f
    if (degrees > 180f) degrees -= 360f
    if (degrees < -180f) degrees += 360f
    return degrees
}
