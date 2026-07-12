package com.getgymdone.app.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.getgymdone.app.ui.theme.AccentLime
import com.getgymdone.app.ui.theme.AccentLimeFg
import com.getgymdone.app.ui.theme.AntonFamily
import com.getgymdone.app.ui.theme.GymFg3Dark
import com.getgymdone.app.ui.theme.GymFg3Light
import com.getgymdone.app.ui.theme.InterFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Static option catalog. Mirrors §16 SPLIT_OPTIONS in the handover — array order
// determines display order. ids match the seeded Split.id so navigation can route to
// RoutineMethod with a real DB key. The "custom" entry is UI-only and routes to
// CustomizeRoutine instead. ──────────────────────────────────────────────────────
data class SplitOption(
    val id: String,
    val name: String,
    val sub: String,
    val days: List<String>,
    val recommended: Boolean = false,
)

private val SPLIT_OPTIONS: List<SplitOption> = listOf(
    SplitOption("ppl_6day", "Push Pull Legs", "3 or 6 day variants",
        listOf("Push", "Pull", "Legs"), recommended = true),
    SplitOption("upper_lower_4day", "Upper / Lower", "4 day classic",
        listOf("Upper", "Lower", "Upper", "Lower")),
    SplitOption("phul_4day", "PHUL", "Power + hypertrophy",
        listOf("Upper Power", "Lower Power", "Upper Hyper", "Lower Hyper")),
    SplitOption("bro_5day", "Bro Split", "One muscle a day",
        listOf("Chest", "Back", "Shoulders", "Arms", "Legs")),
    SplitOption("arnold_6day", "Arnold Split", "Paired antagonists, 2x/wk",
        listOf("Chest+Back", "Shoulders+Arms", "Legs")),
    SplitOption("glute_focused_5day", "Glute Focused", "Lower-body emphasis",
        listOf("Glutes", "Push", "Lower", "Pull", "Glutes & Arms")),
    SplitOption("full_body_3day", "Full Body", "3x per week",
        listOf("Full A", "Full B", "Full C")),
    SplitOption("custom", "Build my own", "Pick the days",
        emptyList()),
)

private const val CUSTOM_ID = "custom"
private const val COMMIT_HOLD_MS = 180L

@HiltViewModel
class PickSplitViewModel @Inject constructor(
    private val splits: SplitRepository,
    private val prefs: UserPrefsRepository,
) : ViewModel() {
    // User-built (custom) splits, surfaced so a saved routine can be re-selected after switching
    // away — otherwise it lives in the DB but is unreachable from this preset-only catalog.
    private val _customSplits = MutableStateFlow<List<SplitOption>>(emptyList())
    val customSplits: StateFlow<List<SplitOption>> = _customSplits.asStateFlow()

    init {
        viewModelScope.launch {
            _customSplits.value = splits.observeAll().first()
                .filter { it.isCustom }
                .map { s ->
                    val days = splits.getDays(s.id).filter { !it.isRestDay }.map { it.name }
                    SplitOption(id = s.id, name = s.name, sub = "Your routine", days = days)
                }
        }
    }

    /** Activate an already-built split as-is (no rebuild), then invoke [onDone] on the main thread. */
    fun activate(splitId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.update { it.copy(activeSplitId = splitId, onboardingComplete = true) }
            onDone()
        }
    }
}

// ── Pixel-perfect type tokens. Tied to the handover §04 spec rather than the shared
// Material typography so the screen survives theme tweaks without drift. ─────────
private val EyebrowStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = (11 * 1.45f).sp,
    letterSpacing = 0.14.em,
)

// Anton ships at a single weight; FontWeight.Normal keeps Compose from synthesizing
// bold strokes that would over-darken the title. lineHeight at 0.95× collapses the
// gap between "PICK YOUR" and "SPLIT" the way the spec mock shows.
private val TitleStyle = TextStyle(
    fontFamily = AntonFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = (36 * 0.95f).sp,
    letterSpacing = 0.02.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private val IntroStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = (14 * 1.45f).sp,
)

private val OptNameStyle = TextStyle(
    fontFamily = AntonFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.015.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private val OptSubStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = (12 * 1.4f).sp,
)

private val BadgeStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 9.sp,
    lineHeight = 9.sp,
    letterSpacing = 0.1.em,
)

private val ChipStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = (11 * 1.4f).sp,
)

private val HintStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = (11 * 1.4f).sp,
    letterSpacing = 0.04.em,
)

// --fg-3 has no direct Material slot. Resolve from active background luminance so the
// token flips with the theme — same pattern as SplashScreen.fg3.
@Composable
private fun fg3(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) GymFg3Dark else GymFg3Light

@Composable
fun PickSplitScreen(
    onPicked: (String) -> Unit,
    onPickCustom: () -> Unit,
    onUseExisting: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: PickSplitViewModel = hiltViewModel()
    val customSplits by vm.customSplits.collectAsState()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var committing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Touch-exploration as a proxy for prefers-reduced-motion: when a screen reader is
    // driving the UI, skip the 180ms confirmation hold so advances feel immediate.
    val a11y = LocalAccessibilityManager.current
    val reduceMotion = a11y?.let {
        runCatching { it.javaClass.getMethod("getTouchExplorationServicesEnabled")
            .invoke(it) as? Boolean ?: false }.getOrDefault(false)
    } ?: false

    val onPick: (String) -> Unit = pick@{ id ->
        if (committing) return@pick
        committing = true
        selected = id
        scope.launch {
            if (!reduceMotion) delay(COMMIT_HOLD_MS)
            committing = false
            if (id == CUSTOM_ID) onPickCustom() else onPicked(id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        TopBar(onBack = onBack)
        ProgressBar(progress = 0.33f)
        IntroParagraph()

        // The scroll container starts here per §03: top bar + progress + intro stay
        // pinned above; the option list + hint scroll inside this LazyColumn.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (customSplits.isNotEmpty()) {
                item(key = "__custom_header") { SectionHeader("Your routines") }
                items(customSplits, key = { "custom_${it.id}" }) { opt ->
                    SplitCardRow(
                        option = opt,
                        isSelected = selected == opt.id,
                        onTap = {
                            if (!committing) {
                                committing = true
                                selected = opt.id
                                vm.activate(opt.id, onDone = onUseExisting)
                            }
                        },
                    )
                }
                item(key = "__templates_header") { SectionHeader("Templates") }
            }
            items(SPLIT_OPTIONS, key = { it.id }) { opt ->
                SplitCardRow(
                    option = opt,
                    isSelected = selected == opt.id,
                    onTap = { onPick(opt.id) },
                )
            }
            item(key = "__hint") {
                HintLine()
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = EyebrowStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

// ─── §07 Top bar ──────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BackIconBtn(onClick = onBack)
        Column(modifier = Modifier
            .weight(1f)
            .padding(top = 4.dp)) {
            Text(
                text = "STEP 1 OF 3",
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "PICK YOUR\nSPLIT",
                style = TitleStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun BackIconBtn(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val tint = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(bg, shape)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        // Mirrors the SVG in §07: M19 12H5 M12 19l-7-7 7-7. 20×20 viewbox scaled to 20.dp.
        androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
            // Coordinate normalization: spec path uses 0–24 viewBox.
            val s = size.width / 24f
            val stroke = Stroke(
                width = 2f * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            val path = Path().apply {
                // M19 12 H5
                moveTo(19f * s, 12f * s)
                lineTo(5f * s, 12f * s)
                // M12 19 l-7 -7 7 -7
                moveTo(12f * s, 19f * s)
                lineTo(5f * s, 12f * s)
                lineTo(12f * s, 5f * s)
            }
            drawPath(path = path, color = tint, style = stroke)
        }
    }
}

// ─── §08 Progress bar ─────────────────────────────────────────────────────────────
@Composable
private fun ProgressBar(progress: Float) {
    val trackShape = RoundedCornerShape(100.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            .height(6.dp)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(AccentLime, RoundedCornerShape(100.dp)),
        )
    }
}

// ─── §09 Intro paragraph ──────────────────────────────────────────────────────────
@Composable
private fun IntroParagraph() {
    Text(
        text = "The split decides what muscles you train on which day. You can change this anytime.",
        style = IntroStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
    )
}

// ─── §10 Split option card + §11 badge anchoring ──────────────────────────────────
// For recommended rows, an 8.dp top Spacer inside the inner Column reserves space
// for the badge to overhang the card edge. The badge itself aligns to the outer
// Box's TopEnd (sitting in that reserved space), inset 14.dp from the right per spec.
@Composable
private fun SplitCardRow(
    option: SplitOption,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (option.recommended) Spacer(Modifier.height(8.dp))
            SplitCard(
                option = option,
                isSelected = isSelected,
                onTap = onTap,
            )
        }
        if (option.recommended) {
            RecommendedBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-14).dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitCard(
    option: SplitOption,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    // Selected-card surface: --accent at 12% over --surface. lerp models the spec's
    // color-mix; a hard-coded #1d2517 would break under future palette swaps.
    val selectedTint = lerp(surface, AccentLime, 0.12f)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val targetBg = when {
        isSelected -> selectedTint
        pressed -> surfaceVariant
        else -> surface
    }
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 120),
        label = "split-card-bg",
    )
    val borderColor = if (isSelected) AccentLime else outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(animatedBg, shape)
            .border(1.5.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onTap,
            )
            .semantics {
                selected = isSelected
                role = Role.RadioButton
            }
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name.uppercase(),
                    style = OptNameStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = option.sub,
                    style = OptSubStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioDot(isSelected = isSelected)
        }

        if (option.days.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                option.days.forEachIndexed { idx, dayName ->
                    DayChip(label = "D${idx + 1} · $dayName")
                }
            }
        }
    }
}

// ─── §11 Recommended badge ────────────────────────────────────────────────────────
@Composable
private fun RecommendedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(AccentLime, RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "RECOMMENDED",
            style = BadgeStyle,
            color = AccentLimeFg,
        )
    }
}

// ─── §12 Radio indicator ──────────────────────────────────────────────────────────
@Composable
private fun RadioDot(isSelected: Boolean) {
    val idleColor = fg3()
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) AccentLime else idleColor,
        animationSpec = tween(durationMillis = 120),
        label = "radio-ring",
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(2.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(AccentLime),
            )
        }
    }
}

// ─── §13 Day chip ─────────────────────────────────────────────────────────────────
@Composable
private fun DayChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = ChipStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── §14 Hint line ────────────────────────────────────────────────────────────────
@Composable
private fun HintLine() {
    Text(
        text = "Tap a split to continue",
        style = HintStyle,
        color = fg3(),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    )
}

