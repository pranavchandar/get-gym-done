package com.getgymdone.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Striped diagonal placeholder used wherever a real GIF or photo will go.
 * Honest about being a placeholder — labeled in lowercase mono.
 */
@Composable
fun StripedPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
) {
    val stripeBg = MaterialTheme.colorScheme.surfaceVariant
    val stripeFg = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(stripeBg)
            val stripeWidth = 12f
            val gap = 18f
            val total = stripeWidth + gap
            val length = size.width + size.height
            var offset = -size.height
            val clip = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
            clipPath(clip) {
                while (offset < length) {
                    drawLine(
                        color = stripeFg,
                        start = Offset(offset, 0f),
                        end = Offset(offset + size.height, size.height),
                        strokeWidth = stripeWidth,
                    )
                    offset += total
                }
            }
        }
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(8.dp),
        )
    }
}
