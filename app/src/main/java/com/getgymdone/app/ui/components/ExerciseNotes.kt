package com.getgymdone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.getgymdone.app.util.detectLinks
import com.getgymdone.app.util.isSafeWebUrl

private const val NOTE_URL_TAG = "note_url"

/**
 * Collapsible per-exercise notes card.
 *
 * Collapsed it is a single line — the label plus a preview (or an "add notes" nudge). Expanded and
 * idle it renders the note read-only with its URLs underlined in the accent colour and tappable;
 * tapping the body (or the pencil) switches to editing. The draft is committed on focus loss, on
 * collapse and on disposal, so nothing is lost by navigating away mid-edit.
 *
 * **Callers must wrap this in `key(exerciseId) { … }`** when the same slot can show different
 * exercises (the active-workout list does): that disposes the editor on switch, which is what
 * flushes the in-progress draft to the right exercise.
 *
 * Pass [flat] when the card is already inside another outlined card, so the two don't nest.
 */
@Composable
fun ExerciseNotesSection(
    note: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(note) }
    var wasFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current

    // Follow the stored note (first load from Room, a restore) unless the user is mid-edit.
    LaunchedEffect(note, editing) { if (!editing) draft = note }

    fun commit() {
        if (draft.trim() != note.trim()) onSave(draft)
        editing = false
    }

    // Flush an in-progress edit if this card leaves the composition (exercise switched, screen left).
    val latestDraft by rememberUpdatedState(draft)
    val latestNote by rememberUpdatedState(note)
    val latestEditing by rememberUpdatedState(editing)
    val latestSave by rememberUpdatedState(onSave)
    DisposableEffect(Unit) {
        onDispose {
            if (latestEditing && latestDraft.trim() != latestNote.trim()) latestSave(latestDraft)
        }
    }

    LaunchedEffect(editing) {
        if (editing) runCatching { focusRequester.requestFocus() }
    }

    val shape = RoundedCornerShape(14.dp)
    val container = if (flat) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(
                1.dp,
                if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape,
            )
            .padding(14.dp)
    }
    Column(modifier = container) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (expanded) {
                        if (editing) commit()
                        expanded = false
                    } else {
                        expanded = true
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NOTES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            if (expanded) {
                Spacer(Modifier.weight(1f))
                if (!editing) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit notes",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { editing = true }
                            .padding(1.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
            } else {
                Text(
                    text = note.ifBlank { "add notes" }.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
            }
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse notes" else "Expand notes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            if (editing) {
                NoteEditor(
                    value = draft,
                    onValueChange = { draft = it },
                    focusRequester = focusRequester,
                    onFocusLost = {
                        if (wasFocused) {
                            wasFocused = false
                            commit()
                        }
                    },
                    onFocusGained = { wasFocused = true },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "DONE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        // Clearing focus runs the same save path as tapping away.
                        .clickable { focusManager.clearFocus() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else if (note.isBlank()) {
                Text(
                    "Tap to add notes — cues, machine settings, a link to a form video.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = true },
                )
            } else {
                LinkifiedNote(
                    text = note,
                    onLinkClick = { url -> openWebLink(uriHandler, url) },
                    onBodyClick = { editing = true },
                )
            }
        }
    }
}

@Composable
private fun NoteEditor(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusLost: () -> Unit,
    onFocusGained: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                "Cues, machine settings, links…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) onFocusGained() else onFocusLost()
                },
        )
    }
}

/** Read-only note with tappable links; a tap anywhere else hands back to [onBodyClick]. */
@Composable
private fun LinkifiedNote(
    text: String,
    onLinkClick: (String) -> Unit,
    onBodyClick: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { buildNoteText(text, linkColor) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(annotated) {
                detectTapGestures { position ->
                    val result = layout
                    val url = result
                        ?.getOffsetForPosition(position)
                        ?.let { offset ->
                            annotated.getStringAnnotations(NOTE_URL_TAG, offset, offset).firstOrNull()
                        }
                        ?.item
                    if (url != null) onLinkClick(url) else onBodyClick()
                }
            },
    )
}

/** Underline + accent-colour every detected URL, tagging it so a tap can resolve back to it. */
private fun buildNoteText(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    detectLinks(text).forEach { span ->
        if (span.start < cursor || span.end > text.length) return@forEach
        if (span.start > cursor) append(text.substring(cursor, span.start))
        pushStringAnnotation(NOTE_URL_TAG, span.url)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(text.substring(span.start, span.end))
        }
        pop()
        cursor = span.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/**
 * Open a link from a note. Notes are user-entered text, so only http/https ever reach the system —
 * never `intent:`, `file:`, `javascript:` or a custom app scheme. A device with no browser at all
 * throws from [UriHandler.openUri], which must not take the app down with it.
 */
private fun openWebLink(uriHandler: UriHandler, url: String) {
    if (!isSafeWebUrl(url)) return
    runCatching { uriHandler.openUri(url) }
}
