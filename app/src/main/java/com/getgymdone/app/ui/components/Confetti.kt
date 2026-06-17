package com.getgymdone.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * One confetti piece described by a closed-form projectile: it launches from [x0],[y0] with velocity
 * [vx],[vy] and is pulled by [gravity]. Positions/velocities are fractions of the canvas (x in width,
 * y and gravity in height) per second, so the burst scales to any size. It also spins ([rotationSpeed])
 * and tumbles edge-on ([flipSpeed]) to fake a third dimension, with a little [swayAmp] flutter.
 */
private data class ConfettiPiece(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val gravity: Float,
    val color: Color,
    val widthDp: Float,
    val heightDp: Float,
    val isCircle: Boolean,
    val rotationStart: Float,
    val rotationSpeed: Float, // deg/sec
    val flipPhase: Float,
    val flipSpeed: Float,     // rad/sec — edge-on tumble
    val swayAmp: Float,       // width-fraction flutter
    val swayFreq: Float,      // rad/sec
    val delay: Float,         // seconds before it appears
)

private val DefaultConfettiColors = listOf(
    Color(0xFFB7EF09), // lime
    Color(0xFFFF5453), // coral
    Color(0xFF00CDFF), // cyan
    Color(0xFFFBC600), // gold
    Color(0xFF9658FF), // violet
    Color(0xFFFF2391), // magenta
    Color(0xFF4DF83F), // green
)

/** Picks a piece's size + shape: mostly little rectangles, some long streamers, a few dots. */
private fun rollShape(): Triple<Float, Float, Boolean> {
    val r = Random.nextFloat()
    return when {
        r < 0.55f -> Triple(Random.nextFloat() * 5f + 7f, Random.nextFloat() * 6f + 9f, false)   // rect
        r < 0.82f -> Triple(Random.nextFloat() * 2f + 3f, Random.nextFloat() * 8f + 12f, false)  // streamer
        else -> Triple(Random.nextFloat() * 4f + 6f, 0f, true)                                   // dot
    }
}

private fun piece(
    x0: Float, y0: Float, vx: Float, vy: Float, gravity: Float,
    colors: List<Color>, delay: Float,
): ConfettiPiece {
    val (w, h, circle) = rollShape()
    return ConfettiPiece(
        x0 = x0, y0 = y0, vx = vx, vy = vy, gravity = gravity,
        color = colors[Random.nextInt(colors.size)],
        widthDp = w, heightDp = h, isCircle = circle,
        rotationStart = Random.nextFloat() * 360f,
        rotationSpeed = (Random.nextFloat() - 0.5f) * 1100f,
        flipPhase = Random.nextFloat() * (2f * Math.PI.toFloat()),
        flipSpeed = Random.nextFloat() * 9f + 5f,
        swayAmp = Random.nextFloat() * 0.03f + 0.008f,
        swayFreq = Random.nextFloat() * 6f + 6f,
        delay = delay,
    )
}

/**
 * A self-contained, dependency-free confetti celebration. Two cannons fire up-and-inward from the
 * bottom corners in an immediate burst, then a lighter shower drifts down from the top to fill in.
 * Everything is driven off one [Animatable] time value via closed-form projectile motion, so it's a
 * single Canvas redraw per frame. [onFinished] fires once the run completes so the caller can drop
 * the overlay. Draws nothing and consumes no touches.
 */
@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    pieceCount: Int = 120,
    durationMillis: Int = 2800,
    colors: List<Color> = DefaultConfettiColors,
    onFinished: () -> Unit = {},
) {
    val pieces = remember(pieceCount, colors) {
        val burstCount = (pieceCount * 0.6f).toInt()
        val showerCount = pieceCount - burstCount
        buildList {
            // Bottom-corner cannons: alternate left/right, launch up with an inward horizontal kick.
            repeat(burstCount) { i ->
                val fromLeft = i % 2 == 0
                val up = Random.nextFloat() * 0.9f + 1.5f          // 1.5..2.4 (heights/sec, upward)
                val sideways = Random.nextFloat() * 1.0f + 0.2f    // 0.2..1.2 inward
                add(
                    piece(
                        x0 = if (fromLeft) -0.02f else 1.02f,
                        y0 = 1.03f,
                        vx = if (fromLeft) sideways else -sideways,
                        vy = -up,
                        gravity = Random.nextFloat() * 0.8f + 2.5f, // 2.5..3.3
                        colors = colors,
                        delay = Random.nextFloat() * 0.12f,
                    ),
                )
            }
            // Shower: sprinkles down from above after the pop lands.
            repeat(showerCount) {
                add(
                    piece(
                        x0 = Random.nextFloat(),
                        y0 = -0.05f,
                        vx = (Random.nextFloat() - 0.5f) * 0.3f,
                        vy = Random.nextFloat() * 0.25f + 0.15f,
                        gravity = Random.nextFloat() * 0.5f + 0.8f,
                        colors = colors,
                        delay = Random.nextFloat() * 0.9f + 0.15f,
                    ),
                )
            }
        }
    }

    val totalSeconds = durationMillis / 1000f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis, easing = LinearEasing))
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value * totalSeconds
        // Everything fades out together over the last fifth of the run for a clean finish.
        val endFade = 1f - ((progress.value - 0.8f) / 0.2f).coerceIn(0f, 1f)
        if (endFade <= 0f) return@Canvas

        pieces.forEach { piece ->
            val tau = t - piece.delay
            if (tau <= 0f) return@forEach

            val x = (piece.x0 + piece.vx * tau + piece.swayAmp * sin(piece.swayFreq * tau + piece.flipPhase)) * w
            val y = (piece.y0 + piece.vy * tau + 0.5f * piece.gravity * tau * tau) * h
            if (y > h * 1.15f) return@forEach // fallen offscreen — skip

            val alpha = (tau / 0.06f).coerceAtMost(1f) * endFade
            if (alpha <= 0f) return@forEach
            val color = piece.color.copy(alpha = alpha)

            if (piece.isCircle) {
                drawCircle(color = color, radius = piece.widthDp.dp.toPx() / 2f, center = Offset(x, y))
            } else {
                val wPx = piece.widthDp.dp.toPx()
                val hPx = piece.heightDp.dp.toPx()
                // |cos| sweeps the width to near-zero and back, so the piece looks like it's tumbling
                // edge-on through 3D; the rotation spins it in the 2D plane on top of that.
                val flip = max(0.12f, abs(cos(piece.flipPhase + piece.flipSpeed * tau)))
                rotate(degrees = piece.rotationStart + piece.rotationSpeed * tau, pivot = Offset(x, y)) {
                    scale(scaleX = flip, scaleY = 1f, pivot = Offset(x, y)) {
                        drawRect(color = color, topLeft = Offset(x - wPx / 2f, y - hPx / 2f), size = Size(wPx, hPx))
                    }
                }
            }
        }
    }
}
