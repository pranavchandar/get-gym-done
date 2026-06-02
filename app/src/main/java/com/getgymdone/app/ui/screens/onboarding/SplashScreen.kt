package com.getgymdone.app.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.getgymdone.app.ui.theme.AccentLime
import com.getgymdone.app.ui.theme.AccentLimeFg
import com.getgymdone.app.ui.theme.AntonFamily
import com.getgymdone.app.ui.theme.GymFg3Dark
import com.getgymdone.app.ui.theme.GymFg3Light
import com.getgymdone.app.ui.theme.InterFamily
import com.getgymdone.app.ui.theme.JetBrainsMonoFamily

// ── Pixel-perfect token shorthands. Anchors the screen to the spec values rather than the
// shared Material theme typography, which is shared with the rest of the app. ──────────
private val EyebrowStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 11.sp,
    letterSpacing = 0.14.em,
)

// The display headline. Three knobs matter:
//   1. FontWeight.Bold + FontSynthesis.Weight — Anton ships at a single weight (400). On
//      Anton this means Compose adds synthetic stroke weight on top of the already-heavy
//      condensed face; on the system-font fallback it lifts to real bold.
//   2. lineHeight 64.sp — well below the 88.sp font size; combined with Trim.Both this
//      pulls the three lines flush against each other (no visible space).
//   3. includeFontPadding = false + LineHeightStyle.Trim.Both — kills Android's legacy
//      ascent/descent padding so the lines actually butt up against each other.
// All three lines render inside a single Text composable (see TopGroup) because separate
// Text composables introduce per-block layout space that lineHeight can't reach.
private val HeadlineStyle = TextStyle(
    fontFamily = AntonFamily,
    fontWeight = FontWeight.Bold,
    fontSynthesis = FontSynthesis.Weight,
    fontSize = 88.sp,
    lineHeight =78.sp,
    letterSpacing = 0.01.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private val SubtitleStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = (16 * 1.4f).sp,
    letterSpacing = 0.em,
)

private val PrimaryCtaStyle = TextStyle(
    fontFamily = AntonFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 26.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.04.em,
)

private val GhostCtaStyle = TextStyle(
    fontFamily = AntonFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.04.em,
)

private val HelperStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = (11 * 1.45f).sp,
    letterSpacing = 0.em,
)

private val HeroLabelStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 11.sp,
    letterSpacing = 0.08.em,
)

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    onSkipToHome: () -> Unit,
    isOnboardingComplete: Boolean,
) {
    val context = LocalContext.current
    val skip: () -> Unit = if (isOnboardingComplete) onSkipToHome else onContinue
    val onSignIn: () -> Unit = {
        Toast.makeText(
            context,
            "Cloud sync is coming soon. Skip to keep going with local storage.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    // Read the screen height from the configuration so SpaceBetween still works on tall
    // phones (matching the spec's "magazine poster" feel), while verticalScroll engages
    // automatically when content exceeds the viewport — so Skip + helper line stay reachable
    // on shorter devices. Reading from LocalConfiguration avoids the subcomposition cost of
    // BoxWithConstraints.
    val viewportHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 0.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            TopGroup()
            BottomGroup(onSignIn = onSignIn, onSkip = skip)
        }
    }
}

@Composable
private fun TopGroup() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "V1.0 — BETA",
            style = EyebrowStyle,
            color = AccentLime,
        )
        Spacer(Modifier.height(0.dp))
        // All three headline lines live inside a single Text — separate Text composables
        // each have their own block-layout space that lineHeight can't collapse, so the
        // only way to get truly zero gap between GET / GYM / DONE. is one composable with
        // hard newlines. The lime "DONE." is colored via an AnnotatedString span.
        val fgColor = MaterialTheme.colorScheme.onBackground
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = fgColor)) {
                    append("GET\nGYM\n")
                }
                withStyle(SpanStyle(color = AccentLime)) {
                    append("DONE.")
                }
            },
            style = HeadlineStyle,
        )
        Spacer(Modifier.height(0.dp))
        Text(
            text = "A logbook that knows what day it is, what's next, and how strong you're getting.",
            style = SubtitleStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun BottomGroup(onSignIn: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HeroPlaceholder()
        Spacer(Modifier.height(6.dp))
        PrimaryCta(label = "Sign up / Sign in", onClick = onSignIn)
        Spacer(Modifier.height(3.dp))
        GhostCta(label = "Skip — keep it local", onClick = onSkip)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Sign in to sync across devices · Skip to start logging now, all data stays on this phone",
            style = HelperStyle,
            color = fg3(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * The handover spec calls for the `--fg-3` token (#696657 dark / #908a78 light) for the
 * helper line and striped-placeholder label. Material's standard scheme doesn't expose a
 * slot at this depth, so we resolve it from the active scheme's background luminance.
 */
@Composable
private fun fg3(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) GymFg3Dark else GymFg3Light

@Composable
private fun HeroPlaceholder() {
    val stripeBg = MaterialTheme.colorScheme.surfaceVariant
    val stripeFg = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(stripeBg),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Same diagonal-stripe rhythm as the shared StripedPlaceholder — kept inline so
            // we can match the spec's 18.dp radius without forcing a param onto the shared
            // component used elsewhere.
            val stripeWidth = 12f
            val gap = 18f
            val total = stripeWidth + gap
            val length = size.width + size.height
            var offset = -size.height
            val clip = Path().apply { addRect(Rect(Offset.Zero, size)) }
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
            text = "hero — athlete",
            style = HeroLabelStyle,
            color = fg3(),
        )
    }
}

@Composable
private fun PrimaryCta(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (pressed) 0.98f else 1f)
            .clip(shape)
            .background(AccentLime, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = PrimaryCtaStyle,
            color = AccentLimeFg,
        )
    }
}

@Composable
private fun GhostCta(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (pressed)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (pressed) 0.98f else 1f)
            .clip(shape)
            .border(1.5.dp, borderColor, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = GhostCtaStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
