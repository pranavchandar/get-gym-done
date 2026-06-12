package com.getgymdone.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getgymdone.app.ui.theme.TrendDown
import com.getgymdone.app.ui.theme.TrendUp

/** Direction of a body metric versus the previously logged value. */
enum class Trend { Up, Down, Flat }

/** [curr] relative to [prev]; Flat when either is missing or the two are equal. */
fun trendOf(prev: Double?, curr: Double?): Trend = when {
    prev == null || curr == null || curr == prev -> Trend.Flat
    curr > prev -> Trend.Up
    else -> Trend.Down
}

/** A small green up / red down arrow. Renders nothing when [trend] is Flat. */
@Composable
fun TrendArrow(trend: Trend, size: Dp = 14.dp) {
    when (trend) {
        Trend.Up -> Icon(Icons.Rounded.ArrowUpward, contentDescription = "up", tint = TrendUp, modifier = Modifier.size(size))
        Trend.Down -> Icon(Icons.Rounded.ArrowDownward, contentDescription = "down", tint = TrendDown, modifier = Modifier.size(size))
        Trend.Flat -> Unit
    }
}
