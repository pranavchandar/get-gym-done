package com.getgymdone.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.db.entities.WorkoutDay
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.REST_SESSION_NOTE
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.WeightUnit
import com.getgymdone.app.domain.kgToDisplay
import com.getgymdone.app.domain.maxConsecutiveRestDays
import com.getgymdone.app.domain.nextWorkoutDay
import com.getgymdone.app.ui.WorkoutCelebrationSignal
import com.getgymdone.app.ui.components.ConfettiOverlay
import com.getgymdone.app.ui.components.Trend
import com.getgymdone.app.ui.components.TrendArrow
import com.getgymdone.app.ui.components.trendOf
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
    val splitId: String = "",
    val splitName: String = "",
    val days: List<WorkoutDay> = emptyList(),
    val nextDay: WorkoutDay? = null,
    val nextDayExercises: Int = 0,
    val nextDaySets: Int = 0,
    val dayMeta: Map<String, DayMeta> = emptyMap(),
    val streakDays: Int = 0,
    /** True when a streak was broken by missing training: 0 days now, but training history exists. */
    val streakLost: Boolean = false,
    val sessionsThisWeek: Int = 0,
    val sessionCount: Int = 0,
    val bodyweight: String = "—",
    /** Bodyweight movement vs the previously logged entry. */
    val bodyweightTrend: Trend = Trend.Flat,
    /** Completed sessions keyed by local epoch-day → the day number trained (latest wins). */
    val completedByEpochDay: Map<Long, Int> = emptyMap(),
    /** Logged activities keyed by local epoch-day → activity type (latest wins), for days with no workout. */
    val activityByEpochDay: Map<Long, String> = emptyMap(),
    /** Day numbers completed within the trailing 7 days. */
    val weekDoneDayNumbers: Set<Int> = emptySet(),
    /** True when today's scheduled day is a rest day (auto-logged to the calendar). */
    val todayIsRest: Boolean = false,
)

data class DayMeta(val exercises: Int, val sets: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
    private val splits: SplitRepository,
    private val sessions: SessionRepository,
    private val metrics: MetricsRepository,
    private val celebration: WorkoutCelebrationSignal,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** One-shot confetti signal armed by the workout-complete screen; played once on arrival. */
    val celebrate: StateFlow<Boolean> = celebration.pending

    fun consumeCelebration() = celebration.consume()

    /** Guards the auto rest-day log so overlapping refreshes don't double-log the same day. */
    private var autoLoggedRestDay: Long = -1L

    init {
        viewModelScope.launch {
            combine(
                prefs.observe(),
                sessions.observeHistory(),
                splits.observeDays(),
                splits.observeDayExercises(),
            ) { _, _, _, _ -> }.collect { refresh() }
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
            // Rotation follows the last logged *workout* (or rest day); activity logs don't advance it.
            val lastWorkout = sessions.getLastCompletedWorkout()
            val lastDayNumber = lastWorkout?.let { s -> days.firstOrNull { it.id == s.workoutDayId }?.dayNumber }
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

            // Map every completed session onto its calendar day + the day number trained. The
            // calendar is universal across routines: a finished workout marks its day no matter
            // which split it belonged to, so we resolve day numbers from every split's days — not
            // just the active one. The "this week" schedule below stays scoped to the current
            // routine, so only the active split's days earn a checkmark there.
            val zone = ZoneId.systemDefault()
            val dayNumberById = splits.getAllDays().associate { it.id to it.dayNumber }
            val currentDayIds = days.mapTo(HashSet()) { it.id }
            val weekCutoff = LocalDate.now().minusDays(6).toEpochDay()
            val completed = metrics.completedSessions()
            val completedByEpochDay = LinkedHashMap<Long, Int>()
            val activityByEpochDay = LinkedHashMap<Long, String>()
            val weekDone = mutableSetOf<Int>()
            completed.forEach { s ->
                val completedAt = s.completedAt ?: return@forEach
                val epochDay = Instant.ofEpochMilli(completedAt)
                    .atZone(zone).toLocalDate().toEpochDay()
                // A session tied to a workout day counts as that day (even an activity logged "as
                // today's workout"); a day-less activity gets its own tag.
                val dn = s.workoutDayId?.let { dayNumberById[it] }
                if (dn != null) {
                    completedByEpochDay[epochDay] = dn // later session on a day overrides
                    if (epochDay >= weekCutoff && s.workoutDayId in currentDayIds) weekDone += dn
                } else if (s.activityType != null) {
                    activityByEpochDay[epochDay] = s.activityType // later activity on a day overrides
                }
            }

            // Workout counts exclude logged rest days so the "this week" tally and totals stay
            // about training, even though rest days still show on the calendar above.
            val trainingCompleted = completed.filter { it.notes != REST_SESSION_NOTE }
            val weekCutoffMs = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

            // Today's rest day: the next day in raw sequence (which, unlike "up next", does not
            // skip rest) is a rest day. When that's the case and nothing is logged today yet, we
            // auto-mark the rest day done so it lands on the calendar without any tap.
            val maxNumber = days.maxOfOrNull { it.dayNumber } ?: 0
            val rawNextNumber = if (maxNumber <= 0) 0 else ((lastDayNumber ?: 0) % maxNumber) + 1
            val restCandidate = days.firstOrNull { it.dayNumber == rawNextNumber && it.isRestDay }
            val lastCompletedEpochDay = lastSession?.completedAt
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
            val todayEpochDay = LocalDate.now().toEpochDay()
            val restLoggedToday = completed.any { s ->
                s.notes == REST_SESSION_NOTE && s.completedAt?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay()
                } == todayEpochDay
            }
            val trainedToday = completed.any { s ->
                s.notes != REST_SESSION_NOTE && s.completedAt?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay()
                } == todayEpochDay
            }
            if (restCandidate != null && lastCompletedEpochDay != todayEpochDay &&
                autoLoggedRestDay != todayEpochDay
            ) {
                autoLoggedRestDay = todayEpochDay
                sessions.completeRestDay(restCandidate.id)
            }
            // Today is a rest day only when a rest is on the calendar for today (or one is due now)
            // AND no workout was logged today. Training today always wins, so the card never lingers
            // on a day you actually trained — even if a rest was auto-logged earlier that same day.
            val todayIsRest = !trainedToday &&
                (restLoggedToday || (restCandidate != null && lastCompletedEpochDay != todayEpochDay))

            // Use the bodyweight history (not just the latest row, which might only carry body-fat
            // or muscle) so the value and its trend reflect the last two actual weigh-ins.
            val bwSeries = metrics.bodyweightSeries()
            val bw = bwSeries.lastOrNull()?.bodyweightKg
            val bwText = bw?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—"
            val bwTrend = trendOf(bwSeries.getOrNull(bwSeries.size - 2)?.bodyweightKg, bw)

            val streak = metrics.currentStreakDays(maxConsecutiveRestDays(days))

            _state.value = HomeState(
                loading = false,
                splitId = splitId.orEmpty(),
                splitName = split?.name.orEmpty(),
                days = days,
                nextDay = nextDay,
                nextDayExercises = nextDay?.let { dayMeta[it.id]?.exercises } ?: 0,
                nextDaySets = nextDay?.let { dayMeta[it.id]?.sets } ?: 0,
                dayMeta = dayMeta,
                streakDays = streak,
                streakLost = streak == 0 && trainingCompleted.isNotEmpty(),
                sessionsThisWeek = trainingCompleted.count { (it.completedAt ?: 0) >= weekCutoffMs },
                sessionCount = trainingCompleted.size,
                bodyweight = bwText,
                bodyweightTrend = bwTrend,
                completedByEpochDay = completedByEpochDay,
                activityByEpochDay = activityByEpochDay,
                weekDoneDayNumbers = weekDone,
                todayIsRest = todayIsRest,
            )
        }
    }

    /**
     * Log a non-gym activity (run, sport, etc.); always counts toward streak/calendar/consistency.
     * When [asTodaysWorkout] is true it's logged against today's scheduled day, counting as that
     * day's workout (advances the rotation); otherwise it's a standalone activity.
     */
    fun logActivity(type: String, durationMin: Int?, notes: String?, asTodaysWorkout: Boolean) = viewModelScope.launch {
        val dayId = if (asTodaysWorkout) _state.value.nextDay?.id else null
        sessions.logActivity(type, durationMin, notes, dayId)
        refresh()
    }

    fun addDay() = viewModelScope.launch {
        val id = _state.value.splitId
        if (id.isNotEmpty()) { splits.addDay(id); refresh() }
    }

    /** Move the day at [index] by [delta] positions (−1 up, +1 down). */
    fun moveDay(index: Int, delta: Int) = viewModelScope.launch {
        val days = _state.value.days
        val target = index + delta
        if (index in days.indices && target in days.indices) {
            splits.swapDays(days[index].id, days[target].id)
            refresh()
        }
    }

    fun removeDay(dayId: String, onBlocked: () -> Unit) = viewModelScope.launch {
        if (splits.removeDay(dayId)) refresh() else onBlocked()
    }
}

@Composable
fun HomeScreen(
    onOpenDay: (String) -> Unit,
    onStartToday: (String) -> Unit,
    onResetRoutine: () -> Unit,
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val celebrate by vm.celebrate.collectAsState()
    val weekday = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    var confirmReset by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var showLogActivity by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(celebrate) {
        if (celebrate) {
            showConfetti = true
            vm.consumeCelebration()
        }
    }

    Box(Modifier.fillMaxSize()) {
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
            IconSquare(Icons.Rounded.RestartAlt, contentDescription = "Reset split", onClick = { confirmReset = true })
        }

        Spacer(Modifier.height(18.dp))

        // Stat strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPill(
                label = if (state.streakLost) "Streak lost" else "Streak",
                value = when {
                    state.streakDays > 0 -> "${state.streakDays}d"
                    state.streakLost -> "0d"
                    else -> "—"
                },
                icon = if (state.streakLost) Icons.Rounded.SentimentVeryDissatisfied else Icons.Rounded.LocalFireDepartment,
                modifier = Modifier.weight(1f),
            )
            StatPill("This week", "${state.sessionsThisWeek}/${state.days.count { !it.isRestDay }.coerceAtLeast(1)}", Icons.Rounded.Check, Modifier.weight(1f))
            StatPill("Bodyweight", state.bodyweight, Icons.Rounded.TrendingUp, Modifier.weight(1f), trend = state.bodyweightTrend)
        }

        Spacer(Modifier.height(18.dp))

        // Rest-day note — shown when today is a rest day (already auto-logged to the calendar).
        if (state.todayIsRest) {
            RestDayCard(modifier = Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(14.dp))
        }

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

        // Log a non-gym activity (run, sport, etc.) — counts toward streak/calendar/consistency.
        Spacer(Modifier.height(10.dp))
        LogActivityButton(
            onClick = { showLogActivity = true },
            modifier = Modifier.padding(horizontal = 22.dp),
        )

        // Calendar
        if (state.days.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            CalendarSection(
                completedByEpochDay = state.completedByEpochDay,
                activityByEpochDay = state.activityByEpochDay,
                sessionCount = state.sessionCount,
                isRestToday = state.todayIsRest,
                onOpenToday = { state.nextDay?.let { onOpenDay(it.id) } },
                modifier = Modifier.padding(horizontal = 22.dp),
            )

            // This week schedule (with inline routine editing)
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (editingRoutine) "EDIT ROUTINE" else "THIS WEEK",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditToggle(editing = editingRoutine, onToggle = { editingRoutine = !editingRoutine })
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editingRoutine) {
                    state.days.forEachIndexed { idx, day ->
                        EditDayRow(
                            day = day,
                            exerciseCount = state.dayMeta[day.id]?.exercises ?: 0,
                            canMoveUp = idx > 0,
                            canMoveDown = idx < state.days.lastIndex,
                            onMoveUp = { vm.moveDay(idx, -1) },
                            onMoveDown = { vm.moveDay(idx, +1) },
                            onRemove = {
                                vm.removeDay(day.id) {
                                    Toast.makeText(
                                        context,
                                        "Can't remove — this day has logged workouts.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                    AddDayButton(onClick = vm::addDay)
                } else {
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

        if (confirmReset) {
            ResetRoutineDialog(
                onConfirm = {
                    confirmReset = false
                    onResetRoutine()
                },
                onDismiss = { confirmReset = false },
            )
        }

        if (showLogActivity) {
            LogActivityDialog(
                todayWorkoutLabel = state.nextDay?.let { "Day ${it.dayNumber} · ${it.name}" },
                onDismiss = { showLogActivity = false },
                onSave = { type, durationMin, notes, asTodaysWorkout ->
                    vm.logActivity(type, durationMin, notes, asTodaysWorkout)
                    showLogActivity = false
                },
            )
        }

        if (showConfetti) {
            ConfettiOverlay(onFinished = { showConfetti = false })
        }
    }
}

// ── Reset confirmation ─────────────────────────────────────────────────────────

@Composable
private fun ResetRoutineDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Text("RESET ROUTINE?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "Picking a new split will reset your current routine. Any customizations to it will be lost.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    label = "Cancel",
                    filled = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogButton(
                    label = "Reset",
                    filled = true,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (filled) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f) else Color.Transparent
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(bg, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        )
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
private fun StatPill(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, trend: Trend = Trend.Flat) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            TrendArrow(trend, size = 16.dp)
        }
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

@Composable
private fun RestDayCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("TODAY · REST DAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Recovery day — automatically logged to your calendar. Eat, sleep, and let the muscles rebuild.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Log activity ─────────────────────────────────────────────────────────────

@Composable
private fun LogActivityButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("LOG ACTIVITY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

private val ACTIVITY_PRESETS = listOf(
    "Running", "Walking", "Cycling", "Swimming",
    "Pickleball", "Tennis", "Table Tennis", "Basketball",
    "Soccer", "Yoga", "Hiking",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogActivityDialog(
    todayWorkoutLabel: String?,
    onDismiss: () -> Unit,
    onSave: (type: String, durationMin: Int?, notes: String?, asTodaysWorkout: Boolean) -> Unit,
) {
    var type by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var asTodaysWorkout by remember { mutableStateOf(false) }
    val canSave = type.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("LOG ACTIVITY", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Log a run or sport — it counts toward your streak, calendar, and consistency even on a skipped day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Text("ACTIVITY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACTIVITY_PRESETS.forEach { preset ->
                    ActivityChip(label = preset, selected = type == preset, onClick = { type = preset })
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogTextField(
                value = type,
                onValueChange = { type = it },
                placeholder = "Activity name",
                keyboardType = KeyboardType.Text,
            )

            Spacer(Modifier.height(14.dp))
            Text("DURATION (MIN)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            DialogTextField(
                value = duration,
                onValueChange = { new -> duration = new.filter { it.isDigit() }.take(4) },
                placeholder = "e.g. 45",
                keyboardType = KeyboardType.Number,
            )

            Spacer(Modifier.height(14.dp))
            Text("NOTES (OPTIONAL)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            DialogTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Distance, score, how it felt…",
                keyboardType = KeyboardType.Text,
            )

            // Offer to count this as today's scheduled workout (advances the rotation) — only when
            // there's a day up next to stand in for.
            if (todayWorkoutLabel != null) {
                Spacer(Modifier.height(16.dp))
                MarkAsWorkoutToggle(
                    label = todayWorkoutLabel,
                    checked = asTodaysWorkout,
                    onToggle = { asTodaysWorkout = !asTodaysWorkout },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(label = "Cancel", filled = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton(
                    label = "Save",
                    filled = true,
                    enabled = canSave,
                    onClick = { onSave(type.trim(), duration.toIntOrNull(), notes.trim().ifBlank { null }, asTodaysWorkout) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarkAsWorkoutToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val boxShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(boxShape)
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, boxShape)
                .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, boxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("COUNT AS TODAY'S WORKOUT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
) {
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
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Calendar ─────────────────────────────────────────────────────────────────

@Composable
private fun CalendarSection(
    completedByEpochDay: Map<Long, Int>,
    activityByEpochDay: Map<Long, String>,
    sessionCount: Int,
    isRestToday: Boolean,
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
                            val activity = activityByEpochDay[date.toEpochDay()]
                            CalendarCell(
                                day = date.dayOfMonth,
                                dayNumber = dn,
                                activity = activity,
                                isToday = date == today,
                                // Only today's cell is actionable, and only when nothing is logged
                                // and it isn't a rest day. Done days are never clickable.
                                clickable = date == today && dn == null && activity == null && !isRestToday,
                                onClick = onOpenToday,
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
private fun CalendarCell(day: Int, dayNumber: Int?, activity: String?, isToday: Boolean, clickable: Boolean, onClick: () -> Unit) {
    val done = dayNumber != null || activity != null
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
            .clickable(enabled = clickable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                style = if (done) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                color = fg,
            )
            // Workout days show their D-number; activity-only days show a short type tag.
            val tag = when {
                dayNumber != null -> "D$dayNumber"
                activity != null -> activity.take(3).uppercase()
                else -> null
            }
            if (tag != null) {
                Text(tag, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp), color = fg.copy(alpha = 0.75f))
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
            // Rest days have nothing to open, so they aren't tappable.
            .then(
                if (isRest) Modifier
                else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
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

// ── Routine editing (shared shape with the Workouts page) ──────────────────────

@Composable
private fun EditToggle(editing: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    val bg = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (editing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!editing) Icon(Icons.Rounded.Edit, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(if (editing) "DONE" else "EDIT", style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun EditDayRow(
    day: WorkoutDay,
    exerciseCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (day.isRestDay) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            SmallIconButton(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = canMoveUp, onClick = onMoveUp)
            Spacer(Modifier.height(4.dp))
            SmallIconButton(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = canMoveDown, onClick = onMoveDown)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${day.dayNumber}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (day.isRestDay) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (day.isRestDay) "REST" else day.name.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (day.isRestDay) "Recovery day" else "$exerciseCount exercises",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallIconButton(Icons.Rounded.Close, "Remove day", enabled = true, onClick = onRemove)
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AddDayButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("ADD DAY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
