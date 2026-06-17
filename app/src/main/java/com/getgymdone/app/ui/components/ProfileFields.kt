package com.getgymdone.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.getgymdone.app.ui.theme.AccentPalette
import com.getgymdone.app.util.AvatarCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The shared identity editor — a tappable display-picture avatar, a name field, and a color picker.
 * Used by both the Friends opt-in panel and the Profile edit sheet so the same profile is set
 * everywhere. State is hoisted; [onPhoto] receives the newly encoded base64 thumbnail (or null).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileFields(
    name: String,
    onName: (String) -> Unit,
    color: String,
    onColor: (String) -> Unit,
    photo: String?,
    onPhoto: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onPhoto(withContext(Dispatchers.IO) { AvatarCodec.fromUri(context, uri) }) }
    }
    val pick: () -> Unit = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(
                photo = photo,
                colorKey = color,
                name = name,
                size = 84.dp,
                initialsStyle = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clip(CircleShape).clickable(onClick = pick),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (photo == null) "Add photo" else "Change photo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(100.dp)).clickable(onClick = pick).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("DISPLAY NAME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        NameField(value = name, onValueChange = { if (it.length <= 20) onName(it) })
        Spacer(Modifier.height(14.dp))
        Text("YOUR COLOR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        ColorPicker(selected = color, onSelect = onColor)
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text("e.g. Pranav", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentPalette.entries.forEach { p ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(p.primary, CircleShape)
                    .border(
                        width = if (p.key == selected) 3.dp else 1.dp,
                        color = if (p.key == selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(p.key) },
            )
        }
    }
}
