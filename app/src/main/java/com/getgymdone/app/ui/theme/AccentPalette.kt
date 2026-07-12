package com.getgymdone.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Selectable accent palettes (chosen in Settings). [primary] drives buttons/highlights,
 * [secondary] the accent-2 slot (cancel/remove text, errors), and [onAccent] is the readable
 * text/icon colour placed on top of the accent.
 */
enum class AccentPalette(
    val key: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val onAccent: Color,
) {
    Lime("lime", "Electric lime", Color(0xFFB7EF09), Color(0xFFFF5453), Color(0xFF0A0A09)),
    Ember("ember", "Ember", Color(0xFFFF7C00), Color(0xFFF46EB4), Color(0xFF0A0A09)),
    Blue("blue", "Electric blue", Color(0xFF00CDFF), Color(0xFF73D25D), Color(0xFF0A0A09)),
    BloodRed("blood_red", "Blood red", Color(0xFFEE343B), Color(0xFFFBC600), Color(0xFFF7F5F0)),
    Violet("violet", "Violet", Color(0xFF9658FF), Color(0xFFFBC600), Color(0xFFF7F5F0)),
    HyperGreen("hyper_green", "Hyper green", Color(0xFF4DF83F), Color(0xFFFF6661), Color(0xFF0A0A09)),
    Magenta("magenta", "Magenta", Color(0xFFFF2391), Color(0xFF00E2ED), Color(0xFF0A0A09)),
    Gold("gold", "Gold", Color(0xFFF3C530), Color(0xFFEE343B), Color(0xFF0A0A09)),
    Cyan("cyan", "Cyan", Color(0xFF1EE6E7), Color(0xFFFF7C00), Color(0xFF0A0A09)),
    Mono("mono", "Mono", Color(0xFFF7F5F0), Color(0xFFA8A59A), Color(0xFF0A0A09));

    companion object {
        fun fromKey(key: String): AccentPalette = entries.firstOrNull { it.key == key } ?: Lime
    }
}
