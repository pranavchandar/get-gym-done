package com.getgymdone.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.getgymdone.app.ui.theme.AccentPalette
import com.getgymdone.app.util.AvatarCodec

/**
 * A round display picture: shows the decoded [photo] thumbnail if present, otherwise a colored
 * circle (from [colorKey]) with the [name]'s initials. Used on Profile, the opt-in panel, and the
 * Friends leaderboard so one identity renders everywhere.
 */
@Composable
fun Avatar(
    photo: String?,
    colorKey: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    initialsStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val image = remember(photo) { AvatarCodec.decode(photo) }
    val palette = AccentPalette.fromKey(colorKey)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(palette.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(initials(name), color = palette.onAccent, style = initialsStyle)
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
