package com.getgymdone.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.db.entities.WorkoutDay
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.WeightUnit
import com.getgymdone.app.domain.kgToDisplay
import com.getgymdone.app.domain.nextWorkoutDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val splitName: String = "",
    val days: List<WorkoutDay> = emptyList(),
    val nextDay: WorkoutDay? = null,
    val nextDayExercises: Int = 0,
    val nextDaySets: Int = 0,
    val dayMeta: Map<String, DayMeta> = emptyMap(),
    val streakDays: Int = 0,
    val sessionsThisWeek: Int = 0,
    val sessionCount: Int = 0,
    val bodyweight: String = "—",
    /** Completed sessions keyed by local epoch-day → the day number trained (latest wins). */
    val completedByEpochDay: Map<Long, Int> = emptyMap(),
    /** Day numbers completed within the trailing 7 days. */
    val weekDoneDayNumbers: Set<Int> = emptySet(),
)

data class DayMeta(val exercises: Int, val sets: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
    private val splits: SplitRepository,
    private val sessions: SessionRepository,
    private val metrics: MetricsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.observe(), sessions.observeHistory()) { _, _ -> }.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val p = prefs.get()
            val unit = WeightUnit.fromStored(p.units)
            val splitId = p.activeSplitId
            val split = splitId?.let { splits.getById(it) }
            val days = splitId?.let { splits.getDays(it) }.orEmpty()

            val lastSession = sessions.getLastCompleted()
            val lastDayNumber = lastSession?.let { s -> days.firstOrNull { it.id == s.workoutDayId }?.dayNumber }
            // Rest days never spawn a session, so the "up next" card always points at the next
            // trainable day; the rotation skips rest days rather than stalling on them.
            val nextDay = nextWorkoutDay(days, lastDayNumber)

            // Per-day exercise + set counts for the schedule rows and the up-next meta.
            val dayMeta = days.associate { day ->
                val items = splits.getDayExercises(day.id)
                day.id to DayMeta(
                    exercises = items.size,
                    sets = items.sumOf { it.prescribedSets },
                )
            }

            // Map every completed session onto its calendar day + the day number trained.
            val zone = ZoneId.systemDefault()
            val dayNumberById = days.associate { it.id to it.dayNumber }
            val completed = metrics.completedSessions()
            val completedByEpochDay = LinkedHashMap<Long, Int>()
            completed.forEach { s ->
                val dn = dayNumberById[s.workoutDayId] ?: return@forEach
                val epochDay = Instant.ofEpochMilli(s.completedAt ?: return@forEach)
                    .atZone(zone).toLocalDate().toEpochDay()
                completedByEpochDay[epochDay] = dn // later session on a day overrides
            }
            val weekCutoff = LocalDate.now().minusDays(6).toEpochDay()
            val weekDone = completedByEpochDay.filterKeys { it >= weekCutoff }.values.toSet()

            val bw = metrics.latestBodyMetric()?.bodyweightKg
            val bwText = bw?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—"

            _state.value = HomeState(
                loading = false,
                splitName = split?.name.orEmpty(),
                days = days,
                nextDay = nextDay,
                nextDayExercises = nextDay?.let { dayMeta[it.id]?.exercises } ?: 0,
                nextDaySets = nextDay?.let { dayMeta[it.id]?.sets } ?: 0,
                dayMeta = dayMeta,
                streakDays = metrics.currentStreakDays(),
                sessionsThisWeek = metrics.sessionsInLastDays(7),
                sessionCount = completed.size,
                bodyweight = bwText,
                completedByEpochDay = completedByEpochDay,
                weekDoneDayNumbers = weekDone,
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenDay: (String) -> Unit,
    onStartToday: (String) -> Unit,
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val weekday = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val dayById = remember(state.days) { state.days.associateBy { it.dayNumber } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 50.dp, bottom = 24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY · ${weekday.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text("READY TO", style = MaterialTheme.typography.displaySmall)
                Row {
                    Text("GET IT", style = MaterialTheme.typography.displaySmall)
                    Text(".", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconSquare(Icons.Rounded.NotificationsNone, contentDescription = "Notifications")
        }

        Spacer(Modifier.height(18.dp))

        // Stat strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPill("Streak", if (state.streakDays > 0) "${state.streakDays}d" else "—", Icons.Rounded.LocalFireDepartment, Modifier.weight(1f))
            StatPill("This week", "${state.sessionsThisWeek}/${state.days.count { !it.isRestDay }.coerceAtLeast(1)}", Icons.Rounded.Check, Modifier.weight(1f))
            StatPill("Bodyweight", state.bodyweight, Icons.Rounded.TrendingUp, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        // Up-next card
        state.nextDay?.let { day ->
            UpNextCard(
                day = day,
                exercises = state.nextDayExercises,
                sets = state.nextDaySets,
                onStart = { onStartToday(day.id) },
                onSwap = { onOpenDay(day.id) },
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        } ?: run {
            if (!state.loading) EmptyRoutineNote(Modifier.padding(horizontal = 22.dp))
        }

        // Calendar
        if (state.days.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            CalendarSection(
                completedByEpochDay = state.completedByEpochDay,
                sessionCount = state.sessionCount,
                onOpenDay = { dayNumber ->
                    dayById[dayNumber]?.let { onOpenDay(it.id) }
                },
                onOpenToday = { state.nextDay?.let { onOpenDay(it.id) } },
                modifier = Modifier.padding(horizontal = 22.dp),
            )

            // This week schedule
            Spacer(Modifier.height(24.dp))
            Text(
                "THIS WEEK",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.days.forEach { day ->
                    WeekDayRow(
                        day = day,
                        meta = state.dayMeta[day.id],
                        isNext = day.id == state.nextDay?.id,
                        completed = day.dayNumber in state.weekDoneDayNumbers,
                        onClick = { onOpenDay(day.id) },
                    )
                }
            }
        }
    }
}

// ── Header bits ──────────────────────────────────────────────────────────────

@Composable
private fun IconSquare(icon: ImageVector, contentDescription: String?, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatPill(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}

// ── Up next ──────────────────────────────────────────────────────────────────

@Composable
private fun UpNextCard(
    day: WorkoutDay,
    exercises: Int,
    sets: Int,
    onStart: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clipToBounds()
            .padding(22.dp),
    ) {
        // Big watermark D-number, bleeding off the top-right corner.
        Text(
            text = "D${day.dayNumber}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 150.sp),
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { translationX = 24.dp.toPx(); translationY = (-30).dp.toPx() },
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
                Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(12.dp))
            Text("DAY ${day.dayNumber}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary)
            Text(day.name.uppercase(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "$exercises exercises · ~${exercises * 11} min · $sets sets",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onPrimary)
                        .clickable(onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("START WORKOUT →", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))
                        .clickable(onClick = onSwap),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = "Day overview", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyRoutineNote(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(20.dp),
    ) {
        Text("NO ROUTINE YET", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick a split in Settings → Reset routine to get your week scheduled.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Calendar ─────────────────────────────────────────────────────────────────

@Composable
private fun CalendarSection(
    completedByEpochDay: Map<Long, Int>,
    sessionCount: Int,
    onOpenDay: (Int) -> Unit,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val ym = remember { YearMonth.from(today) }
    val monthLabel = remember { "${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}" }
    val firstOfMonth = remember { ym.atDay(1) }
    // Leading blanks so day-of-week aligns (Sun=0 column).
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
    val daysInMonth = ym.lengthOfMonth()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(monthLabel.uppercase(), style = MaterialTheme.typography.headlineMedium)
            Text("$sessionCount sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        // Weekday header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val cells = buildList {
            repeat(leadingBlanks) { add(null) }
            for (d in 1..daysInMonth) add(ym.atDay(d))
        }
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date == null) {
                            Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                        } else {
                            val dn = completedByEpochDay[date.toEpochDay()]
                            CalendarCell(
                                day = date.dayOfMonth,
                                dayNumber = dn,
                                isToday = date == today,
                                onClick = {
                                    when {
                                        date == today -> onOpenToday()
                                        dn != null -> onOpenDay(dn)
                                    }
                                },
                            )
                        }
                    }
                }
                // pad final partial week
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(filled = true, "Completed")
            LegendDot(filled = false, "Today")
        }
    }
}

@Composable
private fun CalendarCell(day: Int, dayNumber: Int?, isToday: Boolean, onClick: () -> Unit) {
    val done = dayNumber != null
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        done -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        done -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(bg, shape)
            .then(if (isToday && !done) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(enabled = done || isToday, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                style = if (done) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                color = fg,
            )
            if (done) {
                Text("D$dayNumber", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp), color = fg.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun LegendDot(filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shape = RoundedCornerShape(3.dp)
        Box(
            modifier = Modifier
                .size(10.dp)
                .then(
                    if (filled) Modifier.background(MaterialTheme.colorScheme.primary, shape)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape),
                ),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Week schedule ────────────────────────────────────────────────────────────

@Composable
private fun WeekDayRow(
    day: WorkoutDay,
    meta: DayMeta?,
    isNext: Boolean,
    completed: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val isRest = day.isRestDay
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isRest) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape)
            .border(
                1.dp,
                if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${day.dayNumber}",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isRest) "REST" else day.name.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val subtitle = when {
                isRest -> "Recovery day"
                meta != null -> "${meta.exercises} exercises · ${meta.sets} sets"
                else -> null
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            isRest -> Icon(Icons.Rounded.Bedtime, contentDescription = "Rest day", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            completed -> Icon(Icons.Rounded.Check, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            isNext -> NextChip()
            else -> Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NextChip() {
    val shape = RoundedCornerShape(100.dp)
    Text(
        "NEXT",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
