package com.getgymdone.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class PillStyle { Solid, Outline }

@Composable
fun PillChip(
    text: String,
    style: PillStyle = PillStyle.Outline,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100.dp)
    val base = when (style) {
        PillStyle.Solid -> Modifier.background(MaterialTheme.colorScheme.primary, shape)
        PillStyle.Outline -> Modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape,
        )
    }
    Text(
        text = text.uppercase(),
        color = if (style == PillStyle.Solid)
            MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier
            .clip(shape)
            .then(base)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
