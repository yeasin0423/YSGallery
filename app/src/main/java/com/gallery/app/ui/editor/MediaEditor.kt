@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.gallery.app.ui.editor

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.compose.ui.graphics.toArgb
import com.gallery.app.data.ThumbnailManager
import com.gallery.app.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.media3.common.MediaItem as ExoMediaItem

/** Crop rectangle in 0..1 coordinates of the rotated/flipped frame (y grows downwards). */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    /** The same region after the frame is rotated 90° clockwise. */
    fun rotateClockwise() = NormalizedRect(1f - bottom, left, 1f - top, right)

    /** The same region after the frame is rotated 90° counter-clockwise. */
    fun rotateCounterClockwise() = NormalizedRect(top, 1f - right, bottom, 1f - left)

    /** The same region after the frame is mirrored left-right. */
    fun flipHorizontal() = NormalizedRect(1f - right, top, 1f - left, bottom)

    /** The same region after the frame is mirrored top-bottom. */
    fun flipVertical() = NormalizedRect(left, 1f - bottom, right, 1f - top)

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/**
 * An edit: mirror first ([flipHorizontal]/[flipVertical] on the source frame), then rotate clockwise by
 * [rotation] degrees, then crop to [crop] (expressed in the resulting frame), then apply [colorMatrix]
 * (the combined adjustments and filter), then the portrait [focusBlur], then soften or pixelate the
 * bands in [blurStrokes], add the [bokeh] lights, paint [drawStrokes], put [textOverlays] on top and
 * finally wrap it all in [frame].
 */
data class EditSpec(
    val rotation: Int,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    /** Free-hand straighten in degrees, applied after the quarter turns and before the crop. */
    val straighten: Float = 0f,
    val crop: NormalizedRect,
    val colorMatrix: FloatArray? = null,
    val focusBlur: FocusBlur = FocusBlur(),
    val bokeh: BokehStyle = BokehStyle.None,
    val drawStrokes: List<DrawStroke> = emptyList(),
    val frame: EditorFrame = EditorFrame.None,
    val blurStrokes: List<BlurStroke> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList()
)

/** Result of saving an edited image. */
sealed interface ImageSaveResult {
    /** The edited image was stored, either as a new copy or over the original. */
    data class Saved(val uri: Uri, val overwritten: Boolean) : ImageSaveResult

    /**
     * The original is owned by another app and Android must ask the user for write access first.
     * [tempFile] holds the rendered image and is deleted by [MediaEditor.completeOverwrite] or
     * [MediaEditor.discardOverwrite].
     */
    data class NeedsWritePermission(val pendingIntent: PendingIntent, val tempFile: File) : ImageSaveResult
}

/** A rendered edit waiting in the cache directory. */
private data class RenderedImage(val file: File, val extension: String, val mimeType: String)

/** Loading previews and exporting edited copies of images and videos. */
object MediaEditor {

    /** Largest side of a saved image; keeps full-resolution decodes of 50 MP photos within memory. */
    private const val MAX_EXPORT_SIDE = 4096

    private val IdentityColorMatrix = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    suspend fun loadPreview(context: Context, item: MediaItem, maxSize: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (item.isVideo) loadVideoFrame(context, item.uri, maxSize) else decodeImage(context, item.uri, maxSize)
        }.getOrNull()
    }

    /**
     * Applies mirroring, then a clockwise quarter rotation, then [straighten] (the preview and export
     * share this order). Straightening grows the photo just enough to keep the frame full; its size
     * and crop therefore do not change.
     */
    fun transform(
        source: Bitmap,
        rotation: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        straighten: Float = 0f
    ): Bitmap {
        val angled = straighten % 360f != 0f
        if (rotation % 360 == 0 && !flipHorizontal && !flipVertical && !angled) return source
        if (!angled) {
            val matrix = Matrix().apply {
                postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f, source.width / 2f, source.height / 2f)
                postRotate(rotation.toFloat())
            }
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        // Mirroring and the quarter turn first, so the straighten scale is measured on the frame the
        // user actually sees.
        val quarter = if (rotation % 360 == 0 && !flipHorizontal && !flipVertical) {
            source
        } else {
            val matrix = Matrix().apply {
                postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f, source.width / 2f, source.height / 2f)
                postRotate(rotation.toFloat())
            }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        val straightened = straightenBitmap(quarter, straighten)
        if (quarter !== source && quarter !== straightened) quarter.recycle()
        return straightened
    }

    /** Rotates [source] by [degrees] inside its own bounds, scaling up so no empty corner shows. */
    private fun straightenBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val width = source.width
        val height = source.height
        if (width < 2 || height < 2) return source
        val radians = Math.toRadians(abs(degrees).toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val scale = max(
            (c * width + s * height) / width,
            (s * width + c * height) / height
        )
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(degrees)
        canvas.scale(scale, scale)
        canvas.translate(-width / 2f, -height / 2f)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return output
    }

    private fun decodeImage(context: Context, uri: Uri, maxSize: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder already applies the EXIF orientation.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSize) {
                val scale = maxSize.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }

    private fun loadVideoFrame(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)
                ?: return null
            // Some retrievers return frames without the container rotation applied; detect that by
            // comparing the frame's orientation with the displayed orientation and rotate if needed.
            if (rotation % 180 != 0 && videoWidth > 0 && videoHeight > 0) {
                val displayedLandscape = videoHeight >= videoWidth
                val frameLandscape = frame.width >= frame.height
                if (displayedLandscape != frameLandscape) return transform(frame, rotation, false, false)
            }
            return frame
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Saves an edited image. With [overwrite] false the result is stored as a new copy next to the
     * original. With [overwrite] true the original file is replaced in place; if Android does not let
     * this app write it, [ImageSaveResult.NeedsWritePermission] carries the request to show.
     */
    suspend fun saveImage(
        context: Context,
        item: MediaItem,
        spec: EditSpec,
        overwrite: Boolean = false
    ): ImageSaveResult = withContext(Dispatchers.IO) {
        if (overwrite) {
            val rendered = renderToCache(context, item, spec, overwrite = true)
            try {
                writeToOriginal(context, item.uri, rendered.file)
                rendered.file.delete()
                invalidateCaches(context, item)
                ImageSaveResult.Saved(item.uri, overwritten = true)
            } catch (e: SecurityException) {
                val pending = writeRequest(context, item.uri)
                if (pending == null) {
                    rendered.file.delete()
                    throw e
                }
                ImageSaveResult.NeedsWritePermission(pending, rendered.file)
            } catch (e: FileNotFoundException) {
                val pending = writeRequest(context, item.uri)
                if (pending == null) {
                    rendered.file.delete()
                    throw e
                }
                ImageSaveResult.NeedsWritePermission(pending, rendered.file)
            } catch (e: Exception) {
                rendered.file.delete()
                throw e
            }
        } else {
            val rendered = renderToCache(context, item, spec, overwrite = false)
            try {
                ImageSaveResult.Saved(insertCopy(context, item, rendered), overwritten = false)
            } finally {
                rendered.file.delete()
            }
        }
    }

    /** Saves a rendered collage as a new JPEG in Pictures and returns its uri. */
    suspend fun saveCollage(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val name = "Collage_${System.currentTimeMillis()}.jpg"
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = insertPending(
            context,
            collection,
            name,
            "image/jpeg",
            Environment.DIRECTORY_PICTURES,
            allowedDirectories = listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES),
            fallbackDirectory = Environment.DIRECTORY_PICTURES
        )
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) error("compress failed")
            } ?: error("no output stream")
            publish(context, uri)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    /**
     * True when an edited image can replace the original in place. JPEG and PNG always; WebP only on
     * Android 11+, where the lossy encoder exists.
     */
    fun canOverwrite(item: MediaItem): Boolean {
        if (item.isVideo) return false
        val mime = item.mimeType.lowercase()
        val extension = item.displayName.substringAfterLast('.', "").lowercase()
        val isJpeg = mime.contains("jpeg") || mime.contains("jpg") || extension == "jpg" || extension == "jpeg"
        val isPng = mime.contains("png") || extension == "png"
        val isWebp = mime.contains("webp") || extension == "webp"
        return isJpeg || isPng || (isWebp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    }

    /** Writes the rendered image into the original after the user granted write access. */
    suspend fun completeOverwrite(context: Context, item: MediaItem, tempFile: File): Uri = withContext(Dispatchers.IO) {
        try {
            writeToOriginal(context, item.uri, tempFile)
            invalidateCaches(context, item)
            item.uri
        } finally {
            tempFile.delete()
        }
    }

    /** Deletes the rendered image when the user declined the overwrite request. */
    fun discardOverwrite(tempFile: File) {
        runCatching { tempFile.delete() }
    }

    /** Decodes, rotates, mirrors, crops and compresses the edit into a cache file. */
    private fun renderToCache(
        context: Context,
        item: MediaItem,
        spec: EditSpec,
        overwrite: Boolean
    ): RenderedImage {
        val decoded = decodeImage(context, item.uri, MAX_EXPORT_SIDE)
        val transformed = transform(
            decoded,
            spec.rotation,
            spec.flipHorizontal,
            spec.flipVertical,
            spec.straighten
        )
        val crop = spec.crop
        val x = (crop.left * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val y = (crop.top * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val width = ((crop.right - crop.left) * transformed.width).roundToInt().coerceIn(1, transformed.width - x)
        val height = ((crop.bottom - crop.top) * transformed.height).roundToInt().coerceIn(1, transformed.height - y)
        val cropped = if (crop.isFull) transformed else Bitmap.createBitmap(transformed, x, y, width, height)
        // Adjustments and the selected filter are baked in after cropping so the export matches the preview.
        val colored = spec.colorMatrix?.let { applyColorMatrix(cropped, it) } ?: cropped
        // Portrait blur works on the cropped frame, then the drawn patches sit on top of it.
        val focused = if (spec.focusBlur.active) {
            applyFocusBlur(colored, spec.focusBlur)
        } else {
            colored
        }
        // Softened and pixelated patches sit on the adjusted image, then ink, then labels: that is
        // the preview order.
        val softened = if (spec.blurStrokes.isEmpty()) {
            focused
        } else {
            applyPatchStrokes(focused, spec.blurStrokes, crop, transformed.width, transformed.height)
        }
        val lit = if (spec.bokeh != BokehStyle.None) {
            applyBokeh(softened, crop, transformed.width, transformed.height, spec.bokeh)
        } else {
            softened
        }
        val inked = if (spec.drawStrokes.isEmpty()) {
            lit
        } else {
            applyDrawStrokes(lit, spec.drawStrokes, crop, transformed.width, transformed.height)
        }
        // Labels sit above the ink, in the same order the preview draws them.
        val labelBitmaps = mutableListOf<Bitmap>()
        var result = inked
        spec.textOverlays.filter { it.text.isNotBlank() }.forEach { overlay ->
            result = applyTextOverlay(result, overlay, crop, transformed.width, transformed.height)
            labelBitmaps += result
        }
        // A frame is the only step that changes the canvas size: it adds a border around the photo.
        val framed = if (spec.frame == EditorFrame.None) result else applyFrame(result, spec.frame)

        val (format, extension, mimeType) = outputFormat(item, overwrite)
        val file = File(context.cacheDir, "edit_${System.currentTimeMillis()}.$extension")
        try {
            file.outputStream().use { stream ->
                if (!framed.compress(format, 95, stream)) error("compress failed")
            }
            // Keep the camera metadata when replacing the original. The pixels are already upright,
            // so the orientation tag is reset instead of copied.
            if (overwrite && format == Bitmap.CompressFormat.JPEG) copyExif(context, item.uri, file)
            return RenderedImage(file, extension, mimeType)
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            labelBitmaps.forEach { it.recycle() }
            if (inked !== lit) inked.recycle()
            if (lit !== softened) lit.recycle()
            if (softened !== colored) softened.recycle()
            if (focused !== colored && focused !== softened) focused.recycle()
            if (colored !== cropped) colored.recycle()
            if (cropped !== transformed) cropped.recycle()
            if (transformed !== decoded) transformed.recycle()
            decoded.recycle()
        }
    }

    /** Draws [source] through [values] into a new bitmap; returns the source when nothing changes. */
    private fun applyColorMatrix(source: Bitmap, values: FloatArray): Bitmap {
        if (values.contentEquals(IdentityColorMatrix)) return source
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(values))
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Paints the freehand strokes over the cropped bitmap. Points live in full-frame coordinates, so
     * they are mapped through [crop]; the brush width stays relative to the full frame's shorter side,
     * which keeps the ink the same size on the photo after a crop.
     */
    private fun applyDrawStrokes(
        source: Bitmap,
        strokes: List<DrawStroke>,
        crop: NormalizedRect,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)
        val frameMin = min(frameWidth, frameHeight).toFloat()
        val scaleX = source.width / crop.width
        val scaleY = source.height / crop.height
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val color = stroke.color.toArgb()
            val widthPx = (stroke.width * frameMin).coerceAtLeast(1f)
            line.color = color
            line.strokeWidth = widthPx
            dot.color = color
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                canvas.drawCircle(
                    (point.x - crop.left) * scaleX,
                    (point.y - crop.top) * scaleY,
                    widthPx / 2f,
                    dot
                )
            } else {
                val path = Path()
                stroke.points.forEachIndexed { index, point ->
                    val x = (point.x - crop.left) * scaleX
                    val y = (point.y - crop.top) * scaleY
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, line)
            }
        }
        return output
    }

    /**
     * Replaces the bands under [strokes] with softened or pixelated copies of [source]. Every mask uses
     * the same round-capped outline as the preview, and each copy is built only once per export.
     */
    private fun applyPatchStrokes(
        source: Bitmap,
        strokes: List<BlurStroke>,
        crop: NormalizedRect,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        val blurBands = strokes.filter { !it.pixelate }
        val mosaicBands = strokes.filter { it.pixelate }
        val scaleX = source.width / crop.width
        val scaleY = source.height / crop.height
        val strokeScale = min(frameWidth, frameHeight).toFloat()
        fun maskOf(bands: List<BlurStroke>) = blurMaskPath(
            strokes = bands,
            originX = -crop.left * scaleX,
            originY = -crop.top * scaleY,
            scaleX = scaleX,
            scaleY = scaleY,
            strokeScale = strokeScale
        )
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)
        val destination = RectF(0f, 0f, output.width.toFloat(), output.height.toFloat())
        if (blurBands.isNotEmpty()) {
            val copy = blurredBitmap(source)
            // The blurred copy is tiny; stretch it over the output with filtering, as the preview does.
            paintPatch(canvas, copy, maskOf(blurBands), destination, Paint(Paint.FILTER_BITMAP_FLAG))
            copy.recycle()
        }
        if (mosaicBands.isNotEmpty()) {
            val copy = pixelatedBitmap(source)
            // No filtering here: every pixel of the tiny copy becomes one flat block.
            paintPatch(canvas, copy, maskOf(mosaicBands), destination, Paint())
            copy.recycle()
        }
        return output
    }

    /**
     * Paints the chosen [BokehStyle]'s orbs over [source]. Each orb is a radial gradient from its
     * colour to transparent, placed in normalized coordinates so it follows the crop like the ink.
     */
    private fun applyBokeh(
        source: Bitmap,
        crop: NormalizedRect,
        frameWidth: Int,
        frameHeight: Int,
        style: BokehStyle
    ): Bitmap {
        val scaleX = source.width / crop.width
        val scaleY = source.height / crop.height
        val radiusScale = min(frameWidth, frameHeight).toFloat()
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)
        style.orbs.forEach { orb ->
            val radius = orb.radius * radiusScale
            if (radius <= 0f) return@forEach
            val x = -crop.left * scaleX + orb.center.x * scaleX
            val y = -crop.top * scaleY + orb.center.y * scaleY
            val core = orb.color.copy(alpha = orb.alpha).toArgb()
            val halo = orb.color.copy(alpha = orb.alpha * 0.5f).toArgb()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    x,
                    y,
                    radius,
                    intArrayOf(core, halo, android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(x, y, radius, paint)
        }
        return output
    }

    /**
     * Wraps [source] in [frame]: a border on every side plus, for a polaroid, a wider strip under the
     * photo. A frame with a line colour adds a thin rule, film frames punch holes along the top and
     * bottom borders, corner frames draw L marks, and captioned frames print a word in the strip.
     */
    private fun applyFrame(source: Bitmap, frame: EditorFrame): Bitmap {
        val shorter = min(source.width, source.height).toFloat()
        val border = frame.borderPx(shorter).roundToInt().coerceAtLeast(2)
        val extraBottom = frame.extraBottomPx(shorter).roundToInt()
        val output = Bitmap.createBitmap(
            source.width + border * 2,
            source.height + border * 2 + extraBottom,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawColor(frame.background.toArgb())
        canvas.drawBitmap(source, border.toFloat(), border.toFloat(), null)
        frame.lineColor?.let { line ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = max(1f, border * 0.12f)
            }
            val inset = border * 0.45f
            canvas.drawRect(
                border + inset,
                border + inset,
                output.width - border - inset,
                output.height - border - inset,
                paint
            )
        }
        if (frame.perforated) {
            val hole = border * 0.34f
            val gap = hole * 1.7f
            if (hole > 0f && gap > 0f) {
                val count = ((output.width - border) / gap).toInt().coerceAtLeast(2)
                val span = (count - 1) * gap
                val start = output.width / 2f - span / 2f - hole / 2f
                val top = (border - hole) / 2f
                val bottom = output.height - border + (border - hole) / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = frame.accentColor.toArgb()
                }
                repeat(count) { index ->
                    val x = start + index * gap
                    canvas.drawRect(x, top, x + hole, top + hole, paint)
                    canvas.drawRect(x, bottom, x + hole, bottom + hole, paint)
                }
            }
        }
        if (frame.cornerMarks) {
            val inset = border * 1.4f
            val length = (border * 0.9f).coerceAtLeast(2f)
            val left = inset
            val right = output.width - inset
            val top = inset
            val bottom = output.height - inset
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = frame.accentColor.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = max(1f, border * 0.12f)
            }
            canvas.drawLine(left, top, left + length, top, paint)
            canvas.drawLine(left, top, left, top + length, paint)
            canvas.drawLine(right, top, right - length, top, paint)
            canvas.drawLine(right, top, right, top + length, paint)
            canvas.drawLine(left, bottom, left + length, bottom, paint)
            canvas.drawLine(left, bottom, left, bottom - length, paint)
            canvas.drawLine(right, bottom, right - length, bottom, paint)
            canvas.drawLine(right, bottom, right, bottom - length, paint)
        }
        frame.caption?.let { caption ->
            val textSize = shorter * 0.036f
            if (textSize > 0f) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = frame.captionColor.toArgb()
                    textAlign = Paint.Align.CENTER
                    this.textSize = textSize
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    letterSpacing = 0.18f
                }
                val centerY = output.height - border - extraBottom / 2f
                val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(caption, output.width / 2f, baseline, paint)
            }
        }
        return output
    }

    /** Fills [mask] with [copy], stretched over [destination], and leaves the rest of [canvas] alone. */
    private fun paintPatch(canvas: Canvas, copy: Bitmap, mask: Path, destination: RectF, paint: Paint) {
        val save = canvas.save()
        canvas.clipPath(mask)
        canvas.drawBitmap(copy, null, destination, paint)
        canvas.restoreToCount(save)
    }

    /**
     * Paints the text label over the cropped bitmap. The preview uses the same bold sans-serif paint and
     * baseline maths, so the label lands exactly where it was dragged.
     */
    private fun applyTextOverlay(
        source: Bitmap,
        overlay: TextOverlay,
        crop: NormalizedRect,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        if (overlay.text.isBlank()) return source
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.color.toArgb()
            textAlign = Paint.Align.CENTER
            applyTextFont(overlay.font, overlay.size * min(frameWidth, frameHeight))
        }
        val x = (overlay.center.x - crop.left) * (source.width / crop.width)
        val y = (overlay.center.y - crop.top) * (source.height / crop.height)
        val metrics = paint.fontMetrics
        val save = canvas.save()
        canvas.rotate(overlay.rotation, x, y)
        canvas.drawText(overlay.text, x, y - (metrics.ascent + metrics.descent) / 2f, paint)
        canvas.restoreToCount(save)
        return output
    }

    /** Encoded format used for a copy, or for an overwrite (which must keep the original format). */
    private fun outputFormat(item: MediaItem, overwrite: Boolean): Triple<Bitmap.CompressFormat, String, String> {
        val mime = item.mimeType.lowercase()
        val extension = item.displayName.substringAfterLast('.', "").lowercase()
        val isJpeg = mime.contains("jpeg") || mime.contains("jpg") || extension == "jpg" || extension == "jpeg"
        val isPng = mime.contains("png") || extension == "png"
        val isWebp = mime.contains("webp") || extension == "webp"

        if (overwrite) {
            return when {
                isJpeg -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
                isPng -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
                isWebp -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Triple(Bitmap.CompressFormat.WEBP_LOSSY, "webp", "image/webp")
                } else {
                    @Suppress("DEPRECATION")
                    val webp = Bitmap.CompressFormat.WEBP
                    Triple(webp, "webp", "image/webp")
                }
                else -> error("This format can't be overwritten")
            }
        }

        return when {
            isPng -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
            isWebp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Triple(Bitmap.CompressFormat.WEBP_LOSSY, "webp", "image/webp")
            else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
        }
    }

    /** Stores a rendered image as a new copy next to the original and returns its uri. */
    private fun insertCopy(context: Context, item: MediaItem, rendered: RenderedImage): Uri {
        val volume = volumeNameOf(context, item)
        val collection = MediaStore.Images.Media.getContentUri(volume)
        val uri = insertPending(
            context, collection, editedName(item, rendered.extension), rendered.mimeType, item.relativePath,
            allowedDirectories = listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES),
            fallbackDirectory = Environment.DIRECTORY_PICTURES
        )
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                rendered.file.inputStream().use { it.copyTo(out) }
            } ?: error("no output stream")
            publish(context, uri)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Writes the rendered bytes over the original MediaStore file. */
    private fun writeToOriginal(context: Context, uri: Uri, file: File) {
        val stream = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            context.contentResolver.openOutputStream(uri, "w")
        } ?: error("no output stream")

        stream.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }

        // Some providers keep DATE_MODIFIED at the old value. Updating it makes the gallery reload
        // and the thumbnail cache (keyed by id + dateModified) pick up the new pixels.
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                },
                null,
                null
            )
        }
    }

    /** Android's "allow this app to modify this photo" request, available from Android 11. */
    private fun writeRequest(context: Context, uri: Uri): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
        }.getOrNull()
    }

    /**
     * Drops the caches that may still hold the old pixels after an overwrite: the permanent
     * thumbnail file and Coil's in-memory/on-disk copies of the full image.
     */
    private suspend fun invalidateCaches(context: Context, item: MediaItem) {
        runCatching { ThumbnailManager.getInstance(context).getThumbnailFile(item).delete() }
        runCatching { coil3.SingletonImageLoader.get(context).memoryCache?.clear() }
        runCatching { coil3.SingletonImageLoader.get(context).diskCache?.clear() }
    }

    /**
     * Copies the camera metadata from the original to the rendered JPEG. Without this an overwrite
     * would drop DATE_TAKEN, GPS and camera details, and the photo could lose its place in the timeline.
     */
    private fun copyExif(context: Context, source: Uri, target: File) {
        runCatching {
            val original = context.contentResolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
            val edited = ExifInterface(target.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP
            )
            tags.forEach { tag ->
                original.getAttribute(tag)?.let { edited.setAttribute(tag, it) }
            }
            edited.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            edited.saveAttributes()
        }
    }

    /**
     * Exports an edited copy of a video with Media3 Transformer (rotation/mirroring and crop as GPU
     * effects), then stores it next to the original. [onProgress] reports 0..1.
     */
    suspend fun saveVideo(context: Context, item: MediaItem, spec: EditSpec, onProgress: (Float) -> Unit): Uri {
        val output = File(context.cacheDir, "edit_${System.currentTimeMillis()}.mp4")
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val effects = mutableListOf<Effect>()
                    if (spec.rotation % 360 != 0 || spec.flipHorizontal || spec.flipVertical) {
                        effects += ScaleAndRotateTransformation.Builder()
                            .setScale(if (spec.flipHorizontal) -1f else 1f, if (spec.flipVertical) -1f else 1f)
                            // Media3 rotates counter-clockwise; the editor rotates clockwise.
                            .setRotationDegrees(((360 - spec.rotation % 360) % 360).toFloat())
                            .build()
                    }
                    if (!spec.crop.isFull) {
                        val c = spec.crop
                        // Normalized device coordinates: x and y in -1..1, y pointing up.
                        effects += Crop(2f * c.left - 1f, 2f * c.right - 1f, 1f - 2f * c.bottom, 1f - 2f * c.top)
                    }
                    val edited = EditedMediaItem.Builder(ExoMediaItem.fromUri(item.uri))
                        .setEffects(Effects(emptyList(), effects))
                        .build()

                    val handler = Handler(Looper.getMainLooper())
                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer.start(edited, output.absolutePath)

                    val holder = ProgressHolder()
                    val poll = object : Runnable {
                        override fun run() {
                            if (!continuation.isActive) return
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                            handler.postDelayed(this, 150)
                        }
                    }
                    handler.post(poll)
                    continuation.invokeOnCancellation {
                        handler.removeCallbacks(poll)
                        transformer.cancel()
                    }
                }
            }
            onProgress(1f)

            return withContext(Dispatchers.IO) {
                val volume = volumeNameOf(context, item)
                val collection = MediaStore.Video.Media.getContentUri(volume)
                val uri = insertPending(
                    context, collection, editedName(item, "mp4"), "video/mp4", item.relativePath,
                    allowedDirectories = listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES),
                    fallbackDirectory = Environment.DIRECTORY_MOVIES
                )
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        output.inputStream().use { it.copyTo(out) }
                    } ?: error("no output stream")
                    publish(context, uri)
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
                uri
            }
        } finally {
            output.delete()
        }
    }

    /**
     * The storage volume the original lives on, so the copy lands next to it. Gallery uris use the
     * merged "external" volume, which inserts into internal storage - SD card originals then got their
     * copy in a same-named album on internal storage.
     */
    private fun volumeNameOf(context: Context, item: MediaItem): String {
        val fromRow = runCatching {
            context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.VOLUME_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromRow?.takeIf { it.isNotBlank() && it != MediaStore.VOLUME_EXTERNAL }
            ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
    }

    private fun editedName(item: MediaItem, extension: String): String {
        val base = item.displayName.substringBeforeLast('.').ifBlank { "media" }
        return "${base}_edited_${System.currentTimeMillis() / 1000}.$extension"
    }

    private fun insertPending(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        allowedDirectories: List<String>,
        fallbackDirectory: String
    ): Uri {
        // MediaStore only accepts certain top-level folders per media type (images: DCIM/Pictures,
        // videos: DCIM/Movies/Pictures). Originals elsewhere - the storage root, Download, app
        // folders - get their copy in the default folder instead.
        val cleanPath = relativePath.trim('/')
        val topDirectory = cleanPath.substringBefore('/')
        val targetPath = if (cleanPath.isNotEmpty() && allowedDirectories.any { it.equals(topDirectory, ignoreCase = true) }) {
            "$cleanPath/"
        } else {
            "$fallbackDirectory/"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values) ?: error("MediaStore insert failed")
    }

    private fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }
}
