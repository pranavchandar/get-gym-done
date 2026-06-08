package com.getgymdone.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getgymdone.app.ui.theme.AccentLime

/**
 * Front-view anatomical silhouette, ported from the design's SVG MuscleMap. Muscle groups
 * named in [active] light up in accent; everything else sits in the muted base fill. The
 * source SVG uses a 100×200 viewBox, so we scale every coordinate by the canvas size.
 *
 * Honest representation — a flat silhouette, not anatomical art. Names are matched
 * case-insensitively against the seed muscle vocabulary (e.g. "Lats", "Upper Chest").
 */
@Composable
fun MuscleMap(
    active: List<String>,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
) {
    val on = active.map { it.trim().lowercase() }.toSet()
    val base = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
    val line = androidx.compose.material3.MaterialTheme.colorScheme.outline
    val accent = AccentLime

    Canvas(
        modifier = modifier.then(
            Modifier
                .width(width)
                .height(width * 2f),
        ),
    ) {
        val sx = size.width / 100f
        val sy = size.height / 200f
        fun fill(vararg names: String): Color =
            if (names.any { on.contains(it) }) accent else base

        val stroke = Stroke(width = 0.6f * sx)

        // Quadratic/line path builder in viewBox coordinates.
        fun shape(block: PathBuilder.() -> Unit): Path {
            val p = Path()
            PathBuilder(p, sx, sy).block()
            return p
        }

        fun draw(path: Path, color: Color) {
            drawPath(path, color)
            drawPath(path, line, style = stroke)
        }

        // base torso
        draw(
            shape {
                moveTo(30f, 30f); quadTo(50f, 26f, 70f, 30f)
                lineTo(72f, 80f); quadTo(72f, 100f, 68f, 120f)
                lineTo(62f, 150f); quadTo(58f, 165f, 56f, 180f)
                lineTo(44f, 180f); quadTo(42f, 165f, 38f, 150f)
                lineTo(32f, 120f); quadTo(28f, 100f, 28f, 80f); close()
            },
            base,
        )
        // head
        run {
            val r = 9f * sx
            drawCircle(base, r, androidx.compose.ui.geometry.Offset(50f * sx, 14f * sy))
            drawCircle(line, r, androidx.compose.ui.geometry.Offset(50f * sx, 14f * sy), style = stroke)
        }
        // shoulders / delts (ellipses approximated as ovals via path)
        val delt = fill("shoulders", "front delts", "side delts")
        draw(shape { oval(32f, 38f, 9f, 7f) }, delt)
        draw(shape { oval(68f, 38f, 9f, 7f) }, delt)
        // upper chest
        draw(
            shape {
                moveTo(34f, 36f); quadTo(50f, 36f, 66f, 36f)
                lineTo(64f, 56f); quadTo(50f, 54f, 36f, 56f); close()
            },
            fill("upper chest", "chest"),
        )
        // lower chest
        draw(
            shape {
                moveTo(36f, 56f); quadTo(50f, 60f, 64f, 56f)
                lineTo(62f, 70f); quadTo(50f, 72f, 38f, 70f); close()
            },
            fill("chest"),
        )
        // biceps
        val bi = fill("biceps")
        draw(
            shape {
                moveTo(22f, 42f); quadTo(14f, 60f, 18f, 80f)
                lineTo(28f, 78f); quadTo(28f, 58f, 30f, 44f); close()
            },
            bi,
        )
        draw(
            shape {
                moveTo(78f, 42f); quadTo(86f, 60f, 82f, 80f)
                lineTo(72f, 78f); quadTo(72f, 58f, 70f, 44f); close()
            },
            bi,
        )
        // forearms
        val fore = fill("forearms")
        draw(
            shape {
                moveTo(18f, 80f); quadTo(14f, 100f, 14f, 116f)
                lineTo(22f, 116f); quadTo(24f, 100f, 28f, 80f); close()
            },
            fore,
        )
        draw(
            shape {
                moveTo(82f, 80f); quadTo(86f, 100f, 86f, 116f)
                lineTo(78f, 116f); quadTo(76f, 100f, 72f, 80f); close()
            },
            fore,
        )
        // abs
        draw(
            shape {
                moveTo(40f, 72f); quadTo(50f, 70f, 60f, 72f)
                lineTo(60f, 110f); quadTo(50f, 112f, 40f, 110f); close()
            },
            fill("abs", "core"),
        )
        // quads
        val quad = fill("quads")
        draw(
            shape {
                moveTo(34f, 120f); quadTo(42f, 150f, 42f, 175f)
                lineTo(48f, 175f); quadTo(48f, 150f, 46f, 120f); close()
            },
            quad,
        )
        draw(
            shape {
                moveTo(66f, 120f); quadTo(58f, 150f, 58f, 175f)
                lineTo(52f, 175f); quadTo(52f, 150f, 54f, 120f); close()
            },
            quad,
        )
        // adductors
        draw(
            shape {
                moveTo(46f, 120f); lineTo(54f, 120f); lineTo(52f, 160f); lineTo(48f, 160f); close()
            },
            fill("adductors"),
        )
        // calves
        val calf = fill("calves")
        draw(
            shape {
                moveTo(40f, 180f); quadTo(40f, 192f, 44f, 196f)
                lineTo(48f, 196f); quadTo(46f, 192f, 46f, 180f); close()
            },
            calf,
        )
        draw(
            shape {
                moveTo(60f, 180f); quadTo(60f, 192f, 56f, 196f)
                lineTo(52f, 196f); quadTo(54f, 192f, 54f, 180f); close()
            },
            calf,
        )
    }
}

/** Tiny helper that maps 100×200 viewBox coordinates onto the actual canvas. */
private class PathBuilder(val path: Path, val sx: Float, val sy: Float) {
    fun moveTo(x: Float, y: Float) = path.moveTo(x * sx, y * sy)
    fun lineTo(x: Float, y: Float) = path.lineTo(x * sx, y * sy)
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) =
        path.quadraticBezierTo(cx * sx, cy * sy, x * sx, y * sy)
    fun close() = path.close()

    /** Approximate an SVG ellipse (cx,cy,rx,ry) with four quadratic arcs. */
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float) {
        moveTo(cx - rx, cy)
        quadTo(cx - rx, cy - ry, cx, cy - ry)
        quadTo(cx + rx, cy - ry, cx + rx, cy)
        quadTo(cx + rx, cy + ry, cx, cy + ry)
        quadTo(cx - rx, cy + ry, cx - rx, cy)
        close()
    }
}
