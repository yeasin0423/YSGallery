package com.gallery.app.ui.components
import com.gallery.app.ui.i18n.strings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import coil3.compose.AsyncImage
import com.gallery.app.model.MediaItem
import com.gallery.app.ui.design.MenuEntry
import com.gallery.app.ui.design.ThemedButton
import com.gallery.app.ui.design.ThemedPopupMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs

/** How far a double-tap skips, in seconds. Settings can change this. */
val LocalVideoSeekSeconds = compositionLocalOf { 10 }

/** The speed menu, from slow motion to double speed. */
private val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

// The player's canvas is always black (even in the light theme), so its chrome is always white:
// palette.onBackground would turn the icons black on black in light mode.
private val PlayerChrome = Color.White
private val PlayerChromeMuted = Color.White.copy(alpha = 0.72f)

/** Auto follows the phone; the other two lock playback to one orientation until the player closes. */
private enum class PlayerOrientation { AUTO, LANDSCAPE, PORTRAIT }

/**
 * Prefers the device's hardware decoders for video.
 *
 * Some devices (notably MediaTek ones) report no profile/level support for a format that their
 * hardware decoder plays perfectly well. Media3 then ranks the software decoder first - and the
 * software H.264 decoder crashes in `media.swcodec` on those devices. Hardware decoders are
 * therefore used exclusively whenever one exists for the MIME type; formats without any hardware
 * decoder (and all audio) keep the default list, so nothing loses support.
 */
@OptIn(UnstableApi::class)
private val preferHardwareVideoCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    if (!mimeType.startsWith("video/")) {
        all
    } else {
        all.filter { it.hardwareAccelerated }.ifEmpty { all }
    }
}

/**
 * The video's own page, opened by the Play button in the viewer. It plays immediately, shows the
 * viewer's already-cached sharp first frame until the player has one, and closes back to the pager.
 *
 * It also carries the controls the viewer never had: playback speed, double-tap seek on either side
 * (with the skip length from Settings) and an orientation menu that offers auto rotate or a locked
 * landscape/portrait screen for as long as the player is open. The speed and orientation controls sit
 * on their own row just under the title.
 *
 * Keeping playback on a separate page means the pager itself never creates a player or decoder, so
 * swiping is only ever a thumbnail; and because the sharp frame is already in Coil's cache when
 * this page opens, pressing Play no longer flashes the low-resolution grid thumbnail.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    item: MediaItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val seekSeconds = LocalVideoSeekSeconds.current

    var isPlaying by remember(item.uri) { mutableStateOf(false) }
    var isFirstFrameRendered by remember(item.uri) { mutableStateOf(false) }
    var currentPositionMs by remember(item.uri) { mutableLongStateOf(0L) }
    var durationMs by remember(item.uri) { mutableLongStateOf(item.durationMs) }
    var isScrubbing by remember(item.uri) { mutableStateOf(false) }
    /** False while it plays on its own; true once the user pauses it, so resuming the app leaves it paused. */
    var pausedByUser by remember(item.uri) { mutableStateOf(false) }
    var playbackError by remember(item.uri) { mutableStateOf<PlaybackException?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember(item.uri) { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var speedAnchor by remember { mutableStateOf<Rect?>(null) }
    var orientationMenuOpen by remember { mutableStateOf(false) }
    var orientationAnchor by remember { mutableStateOf<Rect?>(null) }
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var seekToken by remember { mutableIntStateOf(0) }
    var videoAspectRatio by remember(item.uri) { mutableFloatStateOf(0f) }

    val activity = remember(context) { context.findActivity() }
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    var orientation by remember { mutableStateOf(PlayerOrientation.AUTO) }

    // The player canvas is always black, so its system bar icons must stay white even when the gallery
    // is in the light theme. Put the theme's own choice back when the player closes.
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    // Hiding the player's controls also hides Android's status and navigation bars, so playback uses
    // the whole display. The viewer underneath re-applies its own bar state when the player closes.
    val hostView = LocalView.current
    LaunchedEffect(controlsVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, hostView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    val exoPlayer = remember(item.uri) {
        val renderersFactory = DefaultRenderersFactory(context)
            // Without this, Media3 can pick a software H.264 decoder that crashes on some devices.
            .setMediaCodecSelector(preferHardwareVideoCodecSelector)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            // This page only opens when Play was pressed, so it starts playing right away.
            playWhenReady = true
            prepare()
        }
    }

    // The picture is drawn by a TextureView instead of PlayerView's SurfaceView. A SurfaceView is
    // composited by the system outside the view hierarchy, so the page's enter/exit scale and fade
    // would never reach it: the video would pop in at full size and keep playing at full opacity
    // while the page faded away behind it. A TextureView is drawn in the hierarchy, so the whole
    // player page moves and fades as one piece.
    val videoSurface = remember(context) { TextureView(context) }

    DisposableEffect(exoPlayer, videoSurface) {
        exoPlayer.setVideoTextureView(videoSurface)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) durationMs = d
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoAspectRatio = if (videoSize.height > 0) {
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                } else {
                    0f
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error
                isPlaying = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.clearVideoTextureView(videoSurface)
            exoPlayer.release()
        }
    }

    // The player may have locked the screen to one orientation; hand the activity back as it was.
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = originalOrientation }
    }

    // Pause in the background instead of keeping audio running after leaving the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (!pausedByUser) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Controls fade out on their own while playing, like the viewer's chrome. Keep them up while a
    // menu is open so its anchor cannot disappear underneath it.
    LaunchedEffect(controlsVisible, isPlaying, isScrubbing, speedMenuOpen, orientationMenuOpen, seekToken) {
        if (controlsVisible && isPlaying && !isScrubbing && !speedMenuOpen && !orientationMenuOpen) {
            delay(2500)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPlaying, isScrubbing) {
        while (isActive && isPlaying && !isScrubbing) {
            currentPositionMs = exoPlayer.currentPosition
            delay(150)
        }
    }

    // Closing pauses first, so the exit fade never leaves audio running behind the viewer.
    val requestClose = {
        pausedByUser = true
        exoPlayer.pause()
        onClose()
    }
    BackHandler(onBack = requestClose)

    fun applyOrientation(next: PlayerOrientation) {
        orientation = next
        activity?.requestedOrientation = when (next) {
            PlayerOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlayerOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    fun seekBy(forward: Boolean) {
        val delta = seekSeconds * 1000L * if (forward) 1 else -1
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: durationMs
        val position = exoPlayer.currentPosition
        val target = if (duration > 0L) {
            (position + delta).coerceIn(0L, duration)
        } else {
            (position + delta).coerceAtLeast(0L)
        }
        exoPlayer.seekTo(target)
        currentPositionMs = target
        seekToken += 1
        seekFlash = SeekFlash(forward = forward, seconds = seekSeconds, token = seekToken)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(seekSeconds, durationMs) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        seekBy(forward = offset.x >= size.width / 2f)
                    }
                )
            }
    ) {
        // The video itself, kept to its own aspect ratio so portrait clips stay letterboxed instead of
        // being stretched across a landscape screen.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { videoSurface },
                modifier = if (videoAspectRatio > 0f) {
                    Modifier.aspectRatio(videoAspectRatio)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        // The sharp frame the viewer already decoded. It covers the player until the first video
        // frame arrives, so pressing Play never shows the soft grid thumbnail.
        if (!isFirstFrameRendered) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // One UI's player chrome: back and the file name on the first row, with the speed and
        // orientation buttons on a second row just underneath.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = requestClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.back,
                            tint = PlayerChrome,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.displayName,
                        color = PlayerChrome,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerPill(
                        text = strings.speedLabel(playbackSpeed),
                        contentDescription = strings.playbackSpeed,
                        onClick = {
                            orientationMenuOpen = false
                            speedMenuOpen = true
                        },
                        modifier = Modifier.onGloballyPositioned { speedAnchor = it.boundsInRoot() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            speedMenuOpen = false
                            orientationMenuOpen = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .onGloballyPositioned { orientationAnchor = it.boundsInRoot() }
                    ) {
                        Icon(
                            imageVector = when (orientation) {
                                PlayerOrientation.AUTO -> Icons.Rounded.ScreenRotation
                                PlayerOrientation.LANDSCAPE -> Icons.Rounded.ScreenLockLandscape
                                PlayerOrientation.PORTRAIT -> Icons.Rounded.ScreenLockPortrait
                            },
                            contentDescription = when (orientation) {
                                PlayerOrientation.AUTO -> strings.rotateAuto
                                PlayerOrientation.LANDSCAPE -> strings.rotateLandscape
                                PlayerOrientation.PORTRAIT -> strings.rotatePortrait
                            },
                            tint = PlayerChrome,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Center Play/Pause Button
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                            pausedByUser = true
                        } else {
                            pausedByUser = false
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) strings.pause else strings.play,
                        tint = PlayerChrome,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Bottom Scrubber Bar & Time Info. Capped in width so it does not stretch across
                // the whole screen in landscape.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(top = 18.dp, bottom = 18.dp)
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(currentPositionMs),
                            color = PlayerChrome,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTimestamp(durationMs),
                            color = PlayerChromeMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    PlayerSeekBar(
                        positionMs = currentPositionMs,
                        durationMs = durationMs,
                        onSeek = { position ->
                            isScrubbing = true
                            currentPositionMs = position
                        },
                        onSeekFinished = { position ->
                            exoPlayer.seekTo(position)
                            currentPositionMs = position
                            isScrubbing = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Double-tap feedback: which way it skipped and by how much, on the side that was tapped.
        seekFlash?.let { flash ->
            val alpha = remember(flash.token) { Animatable(0f) }
            LaunchedEffect(flash.token) {
                alpha.animateTo(1f, tween(90))
                delay(420)
                alpha.animateTo(0f, tween(150))
                if (seekFlash?.token == flash.token) seekFlash = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value },
                contentAlignment = if (flash.forward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 36.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (flash.forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = PlayerChrome,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (flash.forward) strings.seekForward(flash.seconds) else strings.seekBackward(flash.seconds),
                        color = PlayerChrome,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // A black screen is indistinguishable from a frozen player: say what happened and offer a retry.
        if (playbackError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.videoPlaybackFailed,
                    color = PlayerChrome,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(18.dp))
                ThemedButton(
                    text = strings.retry,
                    icon = Icons.Rounded.Refresh,
                    onClick = {
                        playbackError = null
                        isFirstFrameRendered = false
                        pausedByUser = false
                        currentPositionMs = 0L
                        exoPlayer.seekTo(0L)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                )
            }
        }
    }

    ThemedPopupMenu(
        expanded = speedMenuOpen,
        onDismissRequest = { speedMenuOpen = false },
        anchorBounds = speedAnchor,
        entries = playbackSpeeds.map { speed ->
            MenuEntry(
                text = strings.speedLabel(speed),
                checked = abs(playbackSpeed - speed) < 0.01f
            ) {
                playbackSpeed = speed
                exoPlayer.setPlaybackSpeed(speed)
            }
        }
    )

    // The orientation button opens the same kind of menu as the speed pill, so each choice says what
    // it does instead of making the user cycle through icons to find out.
    ThemedPopupMenu(
        expanded = orientationMenuOpen,
        onDismissRequest = { orientationMenuOpen = false },
        anchorBounds = orientationAnchor,
        entries = listOf(
            PlayerOrientation.AUTO to strings.rotateAuto,
            PlayerOrientation.LANDSCAPE to strings.rotateLandscape,
            PlayerOrientation.PORTRAIT to strings.rotatePortrait
        ).map { (mode, label) ->
            MenuEntry(text = label, checked = orientation == mode) { applyOrientation(mode) }
        }
    )
}

@Composable
private fun PlayerPill(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = PlayerChrome,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class SeekFlash(val forward: Boolean, val seconds: Int, val token: Int)

/**
 * One UI's scrubber: a hairline track with a small round thumb instead of Material's thick
 * expressive track. The dot and time follow the finger; the player seeks when it is released.
 */
@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    val shownPosition = dragPosition ?: positionMs
    val fraction = if (durationMs > 0L) {
        (shownPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val target = seekPosition(offset.x, size.width, 6.dp.toPx(), durationMs)
                    onSeek(target)
                    onSeekFinished(target)
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val target = seekPosition(offset.x, size.width, 6.dp.toPx(), durationMs)
                        dragPosition = target
                        onSeek(target)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val target = seekPosition(change.position.x, size.width, 6.dp.toPx(), durationMs)
                        dragPosition = target
                        onSeek(target)
                    },
                    onDragEnd = {
                        val target = dragPosition
                        dragPosition = null
                        if (target != null) onSeekFinished(target)
                    },
                    onDragCancel = {
                        // A canceled drag must still release the scrub lock, or the position label
                        // would stop following the player.
                        val target = dragPosition
                        dragPosition = null
                        if (target != null) onSeekFinished(target)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val inset = 6.dp.toPx()
            val startX = inset
            val endX = size.width - inset
            val thumbX = startX + (endX - startX) * fraction
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = PlayerChrome,
                start = Offset(startX, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = PlayerChrome,
                radius = 5.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }
    }
}

private fun seekPosition(x: Float, width: Int, inset: Float, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    val usable = (width - inset * 2f).coerceAtLeast(1f)
    val fraction = ((x - inset) / usable).coerceIn(0f, 1f)
    return (durationMs * fraction).toLong()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
