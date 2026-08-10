package com.getgymdone.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch

/** One item in the [MediaViewer] — an on-device file plus the id the caller uses to delete it. */
data class MediaViewerItem(val id: String, val filePath: String)

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Above this we treat the page as zoomed: the pager locks and pans belong to the image. */
private const val ZOOMED_THRESHOLD = 1.01f

/**
 * Full-screen viewer for user-uploaded exercise media.
 *
 * Decodes at the file's original resolution ([coil.size.Size.ORIGINAL]) instead of letting Coil
 * downsample to the view box, so pinching in reveals real pixels rather than an upscaled thumbnail.
 * Swipe between every item, pinch/double-tap to zoom, drag down to dismiss, tap to hide the chrome.
 */
@Composable
fun MediaViewer(
    items: List<MediaViewerItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: ((MediaViewerItem) -> Unit)? = null,
) {
    // Deleting the last item leaves nothing to look at — close instead of showing a black hole.
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onDismiss() }
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomedIn by remember { mutableStateOf(false) }
    var scrimAlpha by remember { mutableFloatStateOf(1f) }

    // Each page owns its own zoom; moving to another one starts from fit again.
    LaunchedEffect(pagerState.currentPage) {
        zoomedIn = false
        scrimAlpha = 1f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 1f))),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // While zoomed the pan gesture owns horizontal movement; otherwise the two fight.
                userScrollEnabled = !zoomedIn,
                key = { page -> items.getOrNull(page)?.id ?: page },
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                ZoomablePage(
                    filePath = item.filePath,
                    isCurrent = page == pagerState.currentPage,
                    onZoomChange = { zoomedIn = it },
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onScrimAlpha = { scrimAlpha = it },
                    onDismiss = onDismiss,
                )
            }

            AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    if (onDelete != null) {
                        HeroCircleButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete photo",
                            onClick = {
                                items.getOrNull(pagerState.currentPage)?.let(onDelete)
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                        )
                    }
                    HeroCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                    val shape = RoundedCornerShape(100.dp)
                    Text(
                        text = "${pagerState.currentPage + 1} / ${items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .clip(shape)
                            .background(Color.Black.copy(alpha = 0.45f), shape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    filePath: String,
    isCurrent: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    onScrimAlpha: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Original pixel size of the decoded image, used to clamp panning to the image's own edges.
    var sourceSize by remember { mutableStateOf(Size.Unspecified) }

    // A page scrolled away from drops its zoom, so coming back to it starts at fit.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            dismissDrag = 0f
        }
    }

    /** Size of the letterboxed image inside the container at scale 1 (ContentScale.Fit). */
    fun contentSize(): Size {
        val cw = containerSize.width.toFloat()
        val ch = containerSize.height.toFloat()
        if (cw <= 0f || ch <= 0f) return Size(0f, 0f)
        val src = sourceSize
        if (!src.isSpecified || src.width <= 0f || src.height <= 0f) return Size(cw, ch)
        val srcAspect = src.width / src.height
        return if (srcAspect > cw / ch) Size(cw, cw / srcAspect) else Size(ch * srcAspect, ch)
    }

    /** Keep the scaled image's edges from being dragged inside the container. */
    fun clampX(value: Float, s: Float): Float {
        val max = ((contentSize().width * s - containerSize.width) / 2f).coerceAtLeast(0f)
        return value.coerceIn(-max, max)
    }

    fun clampY(value: Float, s: Float): Float {
        val max = ((contentSize().height * s - containerSize.height) / 2f).coerceAtLeast(0f)
        return value.coerceIn(-max, max)
    }

    fun dismissProgress(drag: Float): Float {
        val limit = (containerSize.height * 0.45f).takeIf { it > 1f } ?: 600f
        return (drag / limit).coerceIn(0f, 1f)
    }

    fun scrimFor(drag: Float): Float = 1f - 0.55f * dismissProgress(drag)

    val request = remember(filePath) {
        ImageRequest.Builder(context)
            .data(File(filePath))
            // The whole point of the viewer: decode the file as-is instead of to the view box.
            .size(coil.size.Size.ORIGINAL)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    // Original pixel size, once decoded. Held in state (not read straight from the painter) because
    // the gesture handlers below are remembered and would otherwise capture a stale value.
    val intrinsic = painter.intrinsicSize
    LaunchedEffect(intrinsic) {
        if (intrinsic.isSpecified) sourceSize = intrinsic
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(isCurrent) {
                if (!isCurrent) return@pointerInput
                detectTapGestures(
                    // Tapping dismisses nothing — it only gets the chrome out of the way, which is
                    // what you want while inspecting a zoomed-in photo.
                    onTap = { onToggleChrome() },
                    onDoubleTap = { tap ->
                        val target = if (scale > ZOOMED_THRESHOLD) MIN_SCALE else DOUBLE_TAP_SCALE
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val targetX: Float
                        val targetY: Float
                        if (target <= MIN_SCALE) {
                            targetX = 0f
                            targetY = 0f
                        } else {
                            // Anchor the tapped point: it stays under the finger as we scale up.
                            val ratio = target / scale
                            val rawX = (tap.x - centerX) - ((tap.x - centerX) - offsetX) * ratio
                            val rawY = (tap.y - centerY) - ((tap.y - centerY) - offsetY) * ratio
                            targetX = clampX(rawX, target)
                            targetY = clampY(rawY, target)
                        }
                        onZoomChange(target > ZOOMED_THRESHOLD)
                        scope.launch {
                            val startScale = scale
                            val startX = offsetX
                            val startY = offsetY
                            animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                                scale = startScale + (target - startScale) * t
                                offsetX = startX + (targetX - startX) * t
                                offsetY = startY + (targetY - startY) * t
                            }
                            scale = target
                            offsetX = targetX
                            offsetY = targetY
                        }
                    },
                )
            }
            .pointerInput(isCurrent) {
                if (!isCurrent) return@pointerInput
                detectZoomPanGestures(
                    onGesture = { centroid, pan, zoom ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        if (newScale > ZOOMED_THRESHOLD) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val ratio = newScale / scale
                            val anchoredX =
                                (centroid.x - centerX) - ((centroid.x - centerX) - offsetX) * ratio
                            val anchoredY =
                                (centroid.y - centerY) - ((centroid.y - centerY) - offsetY) * ratio
                            scale = newScale
                            offsetX = clampX(anchoredX + pan.x, newScale)
                            offsetY = clampY(anchoredY + pan.y, newScale)
                            if (dismissDrag != 0f) {
                                dismissDrag = 0f
                                onScrimAlpha(1f)
                            }
                            onZoomChange(true)
                            true
                        } else {
                            if (scale != MIN_SCALE) {
                                scale = MIN_SCALE
                                offsetX = 0f
                                offsetY = 0f
                            }
                            onZoomChange(false)
                            // At fit scale a downward drag dismisses. Anything else (a horizontal
                            // swipe, a pinch) is left unconsumed so the pager can take it.
                            val ownsDrag = zoom == 1f &&
                                (dismissDrag > 0f || (pan.y > 0f && abs(pan.y) > abs(pan.x)))
                            if (ownsDrag) {
                                dismissDrag = (dismissDrag + pan.y).coerceAtLeast(0f)
                                onScrimAlpha(scrimFor(dismissDrag))
                                true
                            } else {
                                false
                            }
                        }
                    },
                    onGestureEnd = {
                        val drag = dismissDrag
                        if (drag > dismissThresholdPx) {
                            onDismiss()
                        } else if (drag != 0f) {
                            scope.launch {
                                animate(drag, 0f, animationSpec = tween(180)) { v, _ ->
                                    dismissDrag = v
                                    onScrimAlpha(scrimFor(v))
                                }
                                dismissDrag = 0f
                                onScrimAlpha(1f)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = "Exercise media",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY + dismissDrag
                    alpha = 1f - 0.35f * dismissProgress(dismissDrag)
                },
        )
    }
}

/**
 * Pinch-zoom / pan / drag-to-dismiss for a single page.
 *
 * This is `detectTransformGestures` with one behavioural change: it consumes only the movement
 * [onGesture] says it used. The stock detector consumes *every* move once past touch slop, and
 * because a child sees the main pointer pass before its ancestors that would swallow the pager's
 * horizontal swipe — an un-zoomed page could never be swiped away from. Returning false from
 * [onGesture] leaves the change unconsumed so the pager (or anything else upstream) can have it.
 */
private suspend fun PointerInputScope.detectZoomPanGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Boolean,
    onGestureEnd: () -> Unit,
) {
    awaitEachGesture {
        var zoomAccum = 1f
        var panAccum = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoomAccum *= zoomChange
                    panAccum += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoomAccum) * centroidSize
                    val panMotion = panAccum.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) pastTouchSlop = true
                }

                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (onGesture(centroid, panChange, zoomChange)) {
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        onGestureEnd()
    }
}
