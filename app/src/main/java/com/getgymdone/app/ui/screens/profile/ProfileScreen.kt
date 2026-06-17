package com.getgymdone.app.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.REST_SESSION_NOTE
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.WeightUnit
import com.getgymdone.app.domain.displayToKg
import com.getgymdone.app.domain.maxConsecutiveRestDays
import com.getgymdone.app.domain.kgToDisplay
import com.getgymdone.app.ui.components.Avatar
import com.getgymdone.app.ui.components.ProfileFields
import com.getgymdone.app.ui.components.Trend
import com.getgymdone.app.ui.components.TrendArrow
import com.getgymdone.app.ui.components.trendOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Weeks shown in the GitHub-style consistency grid (columns). */
private const val HEATMAP_WEEKS = 18

/**
 * GitHub-style contribution grid. [weeks] are columns (oldest → newest); each holds 7 day cells
 * ordered Sunday → Saturday. A cell value of -1 is a future/blank day (after today), 0 is an
 * inactive day, and >0 is the activity count. [monthLabels] is one entry per week — the short month
 * name when that week starts a new month, else "".
 */
data class Heatmap(
    val weeks: List<List<Int>> = emptyList(),
    val monthLabels: List<String> = emptyList(),
)

/**
 * One exercise's personal-record history: the running best (top-set) weight over sessions, plus
 * the all-time best and how many sets have been logged (for sorting most-trained first).
 */
data class ExercisePr(
    val id: String,
    val name: String,
    val logCount: Int,
    val currentLabel: String,
    val series: List<Float>,
)

/** One exercise's progression over time: top-set weight and the reps at that set, per session. */
data class ExerciseProgress(
    val id: String,
    val name: String,
    val logCount: Int,
    val currentLabel: String,
    val deltaLabel: String,
    val weightSeries: List<Float>,
    val repsSeries: List<Float>,
)

/** A body metric's history for graphing. */
data class MetricSeries(
    val currentLabel: String,
    val deltaLabel: String,
    val values: List<Float>,
)

/** One logged body-metric entry, formatted for the expandable history table. */
data class BodyMetricRow(
    val dateLabel: String,
    val bodyweight: String,
    val bodyFat: String,
    val muscleMass: String,
)

/** Selectable window for all Profile graphs. [days] = null means all time. */
enum class TimeRange(val label: String, val days: Int?) {
    Month1("1M", 30),
    Month3("3M", 90),
    Month6("6M", 180),
    Year1("1Y", 365),
    All("All", null),
}

data class ProfileState(
    val loading: Boolean = true,
    val profileName: String = "",
    val profileColor: String = "lime",
    val profilePhoto: String? = null,
    val totalVolumeLabel: String = "0",
    val sessionCount: Int = 0,
    val unitLabel: String = "kg",
    val range: TimeRange = TimeRange.All,
    val bodyweightLabel: String = "—",
    val bodyFatLabel: String = "—",
    val muscleMassLabel: String = "—",
    val bodyweightTrend: Trend = Trend.Flat,
    val bodyFatTrend: Trend = Trend.Flat,
    val muscleMassTrend: Trend = Trend.Flat,
    /** Every logged entry, most recent first — shown when the BODY card is expanded. */
    val bodyHistory: List<BodyMetricRow> = emptyList(),
    val bodyweight: MetricSeries = MetricSeries("—", "", emptyList()),
    val bodyFat: MetricSeries = MetricSeries("—", "", emptyList()),
    val muscleMass: MetricSeries = MetricSeries("—", "", emptyList()),
    /** Latest values as plain display numbers (no unit suffix), shown as hints in the log sheet. */
    val bodyweightInput: String = "",
    val bodyFatInput: String = "",
    val muscleMassInput: String = "",
    val exerciseProgress: List<ExerciseProgress> = emptyList(),
    val heatmap: Heatmap = Heatmap(),
    val prExercises: List<ExercisePr> = emptyList(),
    val selectedPrId: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val metrics: MetricsRepository,
    private val prefs: UserPrefsRepository,
    private val sessions: SessionRepository,
    private val splits: SplitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    /** Survives [refresh] so the chosen graph window sticks across data updates. */
    private var range = TimeRange.All

    /** Survives [refresh] so the chosen PR exercise stays selected across data updates. */
    private var selectedPrId: String? = null

    init {
        viewModelScope.launch {
            combine(prefs.observe(), sessions.observeHistory()) { _, _ -> }.collect { refresh() }
        }
    }

    fun setRange(newRange: TimeRange) {
        range = newRange
        refresh()
    }

    fun selectPr(id: String) {
        selectedPrId = id
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val p = prefs.get()
            val unit = WeightUnit.fromStored(p.units)
            val restGap = maxConsecutiveRestDays(p.activeSplitId?.let { splits.getDays(it) }.orEmpty())
            val sets = metrics.allSets()
            val sessions = metrics.completedSessions()
            val byId = metrics.exercises().associateBy { it.id }
            // Cutoff for the selected window; null = all time. Applied only to the graphs.
            val cutoff = range.days?.let { System.currentTimeMillis() - it * 86_400_000L }

            val totalVolume = sets.sumOf { it.weightKg * it.reps }

            // Per-exercise progression: one top-set point per session (the heaviest set and the
            // reps done at it), within the window, sorted oldest → newest. Listed most-trained first.
            val exerciseProgress = sets.groupBy { it.exerciseId }.mapNotNull { (exId, exSets) ->
                val perSession = exSets.groupBy { it.sessionId }
                    .map { (_, g) -> g.maxByOrNull { it.weightKg }!! }
                    .filter { cutoff == null || it.completedAt >= cutoff }
                    .sortedBy { it.completedAt }
                if (perSession.size < 2) return@mapNotNull null
                val weights = perSession.map { it.weightKg.kgToDisplay(unit).toFloat() }
                val reps = perSession.map { it.reps.toFloat() }
                ExerciseProgress(
                    id = exId,
                    name = byId[exId]?.name ?: exId,
                    logCount = exSets.size,
                    currentLabel = "%.1f %s".format(weights.last(), unit.label),
                    deltaLabel = deltaPct(weights),
                    weightSeries = weights,
                    repsSeries = reps,
                )
            }.sortedByDescending { it.logCount }

            // Body metrics: current values come from the latest reading ever; the graph series is
            // limited to the selected window.
            val bodyMetrics = metrics.bodyMetrics()
            val windowMetrics = bodyMetrics.filter { cutoff == null || it.recordedAt >= cutoff }
            val bwSeries = windowMetrics.mapNotNull { it.bodyweightKg?.kgToDisplay(unit)?.toFloat() }
            val bfSeries = windowMetrics.mapNotNull { it.bodyFatPct?.toFloat() }
            val mmSeries = windowMetrics.mapNotNull { it.muscleMassKg?.kgToDisplay(unit)?.toFloat() }
            val latestBw = bodyMetrics.lastOrNull { it.bodyweightKg != null }?.bodyweightKg
            val latestBf = bodyMetrics.lastOrNull { it.bodyFatPct != null }?.bodyFatPct
            val latestMm = bodyMetrics.lastOrNull { it.muscleMassKg != null }?.muscleMassKg
            val bwLabel = latestBw?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—"
            val bfLabel = latestBf?.let { "%.1f%%".format(it) } ?: "—"
            val mmLabel = latestMm?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—"

            // Trend = latest reading vs the one before it (all-time, ignoring the graph window), per
            // metric, skipping entries that didn't record that metric.
            fun trend(select: (com.getgymdone.app.data.db.entities.BodyMetric) -> Double?): Trend {
                val vals = bodyMetrics.mapNotNull(select)
                return trendOf(vals.getOrNull(vals.size - 2), vals.lastOrNull())
            }
            val bwTrend = trend { it.bodyweightKg }
            val bfTrend = trend { it.bodyFatPct }
            val mmTrend = trend { it.muscleMassKg }

            // Full log, newest first, for the expandable history table.
            val zone = ZoneId.systemDefault()
            val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
            val bodyHistory = bodyMetrics.sortedByDescending { it.recordedAt }.map { m ->
                BodyMetricRow(
                    dateLabel = Instant.ofEpochMilli(m.recordedAt).atZone(zone).toLocalDate().format(dateFmt),
                    bodyweight = m.bodyweightKg?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—",
                    bodyFat = m.bodyFatPct?.let { "%.1f%%".format(it) } ?: "—",
                    muscleMass = m.muscleMassKg?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—",
                )
            }

            // PR history per exercise: the running best (top-set) weight over sessions, all-time.
            // Listed most-trained first; tapping one graphs its PR progression.
            val prExercises = sets.groupBy { it.exerciseId }.mapNotNull { (exId, exSets) ->
                val perSession = exSets.groupBy { it.sessionId }
                    .map { (_, g) -> g.maxByOrNull { it.weightKg }!! }
                    .sortedBy { it.completedAt }
                if (perSession.isEmpty()) return@mapNotNull null
                var best = 0.0
                val series = perSession.map { s ->
                    if (s.weightKg > best) best = s.weightKg
                    best.kgToDisplay(unit).toFloat()
                }
                ExercisePr(
                    id = exId,
                    name = byId[exId]?.name ?: exId,
                    logCount = exSets.size,
                    currentLabel = "%.1f %s".format(series.last(), unit.label),
                    series = series,
                )
            }.sortedByDescending { it.logCount }
            val selectedPr = selectedPrId?.takeIf { id -> prExercises.any { it.id == id } }
                ?: prExercises.firstOrNull()?.id
            selectedPrId = selectedPr

            _state.value = ProfileState(
                loading = false,
                profileName = p.socialHandle.orEmpty(),
                profileColor = p.socialColor ?: "lime",
                profilePhoto = p.avatarPhoto,
                totalVolumeLabel = compactVolume(totalVolume.kgToDisplay(unit)),
                sessionCount = sessions.count { it.notes != REST_SESSION_NOTE },
                unitLabel = unit.label,
                range = range,
                bodyweightLabel = bwLabel,
                bodyFatLabel = bfLabel,
                muscleMassLabel = mmLabel,
                bodyweightTrend = bwTrend,
                bodyFatTrend = bfTrend,
                muscleMassTrend = mmTrend,
                bodyHistory = bodyHistory,
                bodyweight = MetricSeries(bwLabel, deltaPct(bwSeries), bwSeries),
                bodyFat = MetricSeries(bfLabel, deltaPct(bfSeries), bfSeries),
                muscleMass = MetricSeries(mmLabel, deltaPct(mmSeries), mmSeries),
                bodyweightInput = latestBw?.let { formatNum(it.kgToDisplay(unit)) }.orEmpty(),
                bodyFatInput = latestBf?.let { formatNum(it) }.orEmpty(),
                muscleMassInput = latestMm?.let { formatNum(it.kgToDisplay(unit)) }.orEmpty(),
                exerciseProgress = exerciseProgress,
                // Both training (set timestamps) and logged rest days fill a consistency square;
                // unlogged scheduled rest days between two active days bridge in too.
                heatmap = buildHeatmap(
                    sets.map { it.completedAt } +
                        sessions.filter { it.notes == REST_SESSION_NOTE }.mapNotNull { it.completedAt },
                    restGap,
                ),
                prExercises = prExercises,
                selectedPrId = selectedPr,
            )
        }
    }

    /** Percent change from first to last point, formatted with sign; blank if not computable. */
    private fun deltaPct(series: List<Float>): String {
        if (series.size < 2 || series.first() <= 0f) return ""
        val pct = ((series.last() - series.first()) / series.first() * 100).toInt()
        return if (pct >= 0) "+$pct%" else "$pct%"
    }

    /** Save the profile identity (name, color, photo). Propagated to friends on the next sync. */
    fun saveProfile(name: String, color: String, photo: String?) {
        viewModelScope.launch {
            prefs.update { it.copy(socialHandle = name.trim(), socialColor = color, avatarPhoto = photo) }
        }
    }

    /** Persist a body-metric entry; values arrive as display units (kg/lbs) and percent. */
    fun logBodyMetrics(bodyweightDisplay: Double?, bodyFatPct: Double?, muscleMassDisplay: Double?) {
        viewModelScope.launch {
            val unit = WeightUnit.fromStored(prefs.get().units)
            metrics.logBodyMetric(
                bodyweightKg = bodyweightDisplay?.displayToKg(unit),
                bodyFatPct = bodyFatPct,
                muscleMassKg = muscleMassDisplay?.displayToKg(unit),
            )
            refresh()
        }
    }

    private fun buildHeatmap(setTimes: List<Long>, maxRestGap: Int): Heatmap {
        val zone = ZoneId.systemDefault()
        val counts = HashMap<Long, Int>()
        setTimes.forEach { t ->
            val day = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()
            counts[day] = (counts[day] ?: 0) + 1
        }
        val today = LocalDate.now(zone)
        // Weeks start on Sunday (GitHub style). Walk back to the Sunday of the earliest week shown.
        val daysSinceSunday = today.dayOfWeek.value % 7 // Mon=1..Sat=6, Sun=0
        val startSunday = today.minusDays(daysSinceSunday.toLong()).minusWeeks((HEATMAP_WEEKS - 1).toLong())
        val todayEpoch = today.toEpochDay()

        // Bridge scheduled rest gaps across the whole date range: empty days between two active days
        // fill in as light rest squares when the run of empties is no wider than the rest block.
        val filled = HashMap<Long, Int>()
        var lastActive = Long.MIN_VALUE
        var epoch = startSunday.toEpochDay()
        while (epoch <= todayEpoch) {
            val count = counts[epoch] ?: 0
            filled[epoch] = count
            if (count > 0) {
                if (lastActive != Long.MIN_VALUE && (epoch - lastActive - 1).toInt() in 1..maxRestGap) {
                    var f = lastActive + 1
                    while (f < epoch) { filled[f] = 1; f++ }
                }
                lastActive = epoch
            }
            epoch++
        }

        val weeks = (0 until HEATMAP_WEEKS).map { w ->
            (0 until 7).map { dow ->
                val date = startSunday.plusDays((w * 7 + dow).toLong())
                if (date.isAfter(today)) -1 else (filled[date.toEpochDay()] ?: 0)
            }
        }
        val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        var prevMonth = -1
        val monthLabels = (0 until HEATMAP_WEEKS).map { w ->
            val firstOfWeek = startSunday.plusDays((w * 7).toLong())
            if (firstOfWeek.monthValue != prevMonth) {
                prevMonth = firstOfWeek.monthValue
                firstOfWeek.format(monthFmt)
            } else ""
        }
        return Heatmap(weeks, monthLabels)
    }

    private fun compactVolume(v: Double): String = when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        v >= 1_000 -> "%.1fk".format(v / 1_000)
        else -> v.toInt().toString()
    }
}

private fun formatNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@Composable
fun ProfileScreen() {
    val vm: ProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var logSheetOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var bodyExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 24.dp),
    ) {
        ProfileHeader(
            name = state.profileName,
            photo = state.profilePhoto,
            color = state.profileColor,
            sessionCount = state.sessionCount,
            onEdit = { editOpen = true },
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigStat("Total volume", "${state.totalVolumeLabel} ${state.unitLabel}", Modifier.weight(1f))
            BigStat("Sessions", "${state.sessionCount}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Metrics")
        Spacer(Modifier.height(12.dp))
        RangeSelector(selected = state.range, onSelect = vm::setRange)
        Spacer(Modifier.height(12.dp))

        // ── Body metrics first: current values + per-metric graphs over time. ──
        BodyCard(
            state = state,
            expanded = bodyExpanded,
            onToggle = { bodyExpanded = !bodyExpanded },
            onLog = { logSheetOpen = true },
        )
        if (state.bodyweight.values.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MetricGraphCard("Bodyweight", state.bodyweight)
        }
        if (state.bodyFat.values.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MetricGraphCard("Body fat", state.bodyFat)
        }
        if (state.muscleMass.values.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MetricGraphCard("Muscle mass", state.muscleMass)
        }

        // ── Then exercises, most-trained first, each plotting weight + reps. ──
        if (state.exerciseProgress.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Exercise progression")
            Spacer(Modifier.height(12.dp))
            state.exerciseProgress.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                ExerciseProgressCard(p, state.unitLabel)
            }
        }

        Spacer(Modifier.height(24.dp))

        ConsistencyCard(state.heatmap)

        if (state.prExercises.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PrsCard(
                exercises = state.prExercises,
                selectedId = state.selectedPrId,
                onSelect = vm::selectPr,
            )
        }

        if (!state.loading && state.sessionCount == 0) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Finish a workout to start tracking your numbers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

        if (logSheetOpen) {
            LogBodyMetricsSheet(
                unitLabel = state.unitLabel,
                lastBodyweight = state.bodyweightInput,
                lastBodyFat = state.bodyFatInput,
                lastMuscleMass = state.muscleMassInput,
                onSave = { bw, bf, mm ->
                    vm.logBodyMetrics(bw, bf, mm)
                    logSheetOpen = false
                },
                onDismiss = { logSheetOpen = false },
            )
        }

        if (editOpen) {
            ProfileEditSheet(
                initialName = state.profileName,
                initialColor = state.profileColor,
                initialPhoto = state.profilePhoto,
                onSave = { n, c, p ->
                    vm.saveProfile(n, c, p)
                    editOpen = false
                },
                onDismiss = { editOpen = false },
            )
        }
    }
}

@Composable
private fun ProfileHeader(name: String, photo: String?, color: String, sessionCount: Int, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit),
    ) {
        Avatar(
            photo = photo,
            colorKey = color,
            name = name.ifBlank { "You" },
            size = 56.dp,
            initialsStyle = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { "ATHLETE" }.uppercase(), style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(
                "$sessionCount sessions logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "EDIT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProfileEditSheet(
    initialName: String,
    initialColor: String,
    initialPhoto: String?,
    onSave: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var photo by remember { mutableStateOf(initialPhoto) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("EDIT PROFILE", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your name and photo show on your profile and to friends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            ProfileFields(
                name = name, onName = { name = it },
                color = color, onColor = { color = it },
                photo = photo, onPhoto = { photo = it },
            )
            Spacer(Modifier.height(20.dp))
            SaveButton(enabled = true) { onSave(name, color, photo) }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun RangeSelector(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TimeRange.entries.forEach { r ->
            val active = r == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricGraphCard(title: String, metric: MetricSeries) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(metric.currentLabel, style = MaterialTheme.typography.titleMedium)
                if (metric.deltaLabel.isNotEmpty()) {
                    Text(metric.deltaLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Sparkline(values = metric.values, modifier = Modifier.fillMaxWidth().height(60.dp))
    }
}

@Composable
private fun ExerciseProgressCard(p: ExerciseProgress, unitLabel: String) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${p.logCount} sets logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(p.currentLabel, style = MaterialTheme.typography.headlineSmall)
                if (p.deltaLabel.isNotEmpty()) {
                    Text(p.deltaLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DualSparkline(
            primary = p.weightSeries,
            secondary = p.repsSeries,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SeriesLegend(MaterialTheme.colorScheme.primary, "Weight ($unitLabel)")
            SeriesLegend(MaterialTheme.colorScheme.secondary, "Reps")
        }
    }
}

@Composable
private fun SeriesLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Two line series on one chart, each normalized to its own min/max so both trends are readable
 * despite different scales (weight in kg/lbs vs. rep counts). The primary line gets a soft area
 * fill; the secondary is a thinner line.
 */
@Composable
private fun DualSparkline(primary: List<Float>, secondary: List<Float>, modifier: Modifier = Modifier) {
    val pColor = MaterialTheme.colorScheme.primary
    val sColor = MaterialTheme.colorScheme.secondary
    val fill = pColor.copy(alpha = 0.14f)
    Canvas(modifier = modifier) {
        fun points(values: List<Float>): List<Offset> {
            if (values.size < 2) return emptyList()
            val min = values.min()
            val max = values.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (values.size - 1)
            // Inset vertically so flat lines and extremes aren't clipped at the edges.
            val pad = size.height * 0.12f
            val usable = size.height - pad * 2
            return values.mapIndexed { i, v ->
                Offset(i * stepX, pad + (usable - ((v - min) / range) * usable))
            }
        }

        val pPts = points(primary)
        if (pPts.isNotEmpty()) {
            val line = Path().apply {
                moveTo(pPts.first().x, pPts.first().y)
                pPts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, fill)
            drawPath(line, pColor, style = Stroke(width = 3.dp.toPx()))
        }
        val sPts = points(secondary)
        if (sPts.isNotEmpty()) {
            val line = Path().apply {
                moveTo(sPts.first().x, sPts.first().y)
                sPts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, sColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun BodyCard(state: ProfileState, expanded: Boolean, onToggle: () -> Unit, onLog: () -> Unit) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // Tapping the header (label + chevron) expands the full history; the LOG chip is separate.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle),
            ) {
                Text("BODY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse history" else "Expand history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            LogChip(onClick = onLog)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BodyMetricStat("Weight", state.bodyweightLabel, state.bodyweightTrend, Modifier.weight(1f))
            BodyMetricStat("Body fat", state.bodyFatLabel, state.bodyFatTrend, Modifier.weight(1f))
            BodyMetricStat("Muscle", state.muscleMassLabel, state.muscleMassTrend, Modifier.weight(1f))
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(16.dp))
                BodyHistoryTable(state.bodyHistory)
            }
        }
    }
}

@Composable
private fun BodyMetricStat(label: String, value: String, trend: Trend, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            TrendArrow(trend, size = 16.dp)
        }
    }
}

@Composable
private fun BodyHistoryTable(history: List<BodyMetricRow>) {
    if (history.isEmpty()) {
        Text(
            "No entries yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        BodyHistoryRow("DATE", "WEIGHT", "FAT", "MUSCLE", header = true)
        Spacer(Modifier.height(6.dp))
        history.forEach { row ->
            BodyHistoryRow(row.dateLabel, row.bodyweight, row.bodyFat, row.muscleMass)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun BodyHistoryRow(date: String, weight: String, fat: String, muscle: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(date, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1.4f))
        Text(weight, style = style, color = color, maxLines = 1, modifier = Modifier.weight(1f))
        Text(fat, style = style, color = color, maxLines = 1, modifier = Modifier.weight(0.8f))
        Text(muscle, style = style, color = color, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LogChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        text = "+ LOG",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun LogBodyMetricsSheet(
    unitLabel: String,
    lastBodyweight: String,
    lastBodyFat: String,
    lastMuscleMass: String,
    onSave: (Double?, Double?, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Start every field blank so only what's actually typed gets logged; the repository upsert keeps
    // the day's other metrics untouched. The last reading shows as a hint for reference, not a value.
    var bodyweight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var muscleMass by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .padding(20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("LOG BODY METRICS", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fill in only what you want to log — blank fields are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            NumericField(label = "Bodyweight ($unitLabel)", hint = lastBodyweight, onValueChange = { bodyweight = it })
            Spacer(Modifier.height(12.dp))
            NumericField(label = "Body fat (%)", hint = lastBodyFat, onValueChange = { bodyFat = it })
            Spacer(Modifier.height(12.dp))
            NumericField(label = "Muscle mass ($unitLabel)", hint = lastMuscleMass, onValueChange = { muscleMass = it })
            Spacer(Modifier.height(20.dp))
            val anyValue = listOf(bodyweight, bodyFat, muscleMass).any { it.toDoubleOrNull() != null }
            SaveButton(enabled = anyValue) {
                onSave(bodyweight.toDoubleOrNull(), bodyFat.toDoubleOrNull(), muscleMass.toDoubleOrNull())
            }
        }
    }
}

@Composable
private fun NumericField(label: String, hint: String, onValueChange: (String) -> Unit) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.background, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (text.isEmpty()) {
            // The last logged value as a greyed hint — reference only, never saved unless retyped.
            Text(hint.ifEmpty { "0" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() || it == '.' }
                onValueChange(text)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "SAVE",
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// GitHub-style contribution grid: weeks as columns left→right, weekdays Sun→Sat top→bottom, with
// month labels along the top and day-of-week labels down the left. Scrolls horizontally, scrolled
// to the most recent week on first layout.
private val HEAT_CELL = 13.dp
private val HEAT_GAP = 3.dp
private val HEAT_MONTH_ROW = 14.dp
private val HEAT_MONTH_GAP = 4.dp
private val HEAT_DOW_LABEL_W = 24.dp

@Composable
private fun ConsistencyCard(heatmap: Heatmap) {
    Card {
        Text("CONSISTENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row {
            // Fixed weekday labels (Mon / Wed / Fri, like GitHub), aligned to the grid rows below.
            Column(modifier = Modifier.width(HEAT_DOW_LABEL_W)) {
                Spacer(Modifier.height(HEAT_MONTH_ROW + HEAT_MONTH_GAP))
                Column(verticalArrangement = Arrangement.spacedBy(HEAT_GAP)) {
                    val labels = listOf("", "Mon", "", "Wed", "", "Fri", "")
                    labels.forEach { label ->
                        Box(modifier = Modifier.height(HEAT_CELL), contentAlignment = Alignment.CenterStart) {
                            if (label.isNotEmpty()) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // Scrollable month labels + week columns, kept in sync by sharing one scroll state.
            val scroll = rememberScrollState()
            LaunchedEffect(heatmap.weeks.size) { scroll.scrollTo(scroll.maxValue) }
            Column(modifier = Modifier.horizontalScroll(scroll)) {
                Row(horizontalArrangement = Arrangement.spacedBy(HEAT_GAP)) {
                    heatmap.monthLabels.forEach { label ->
                        Box(modifier = Modifier.width(HEAT_CELL).height(HEAT_MONTH_ROW)) {
                            if (label.isNotEmpty()) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    // Let the label overflow its 1-cell box to the right rather than clip.
                                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(HEAT_MONTH_GAP))
                Row(horizontalArrangement = Arrangement.spacedBy(HEAT_GAP)) {
                    heatmap.weeks.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(HEAT_GAP)) {
                            week.forEach { intensity -> HeatCell(intensity) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatCell(intensity: Int) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val color = when {
        intensity < 0 -> Color.Transparent // future day — leave blank
        intensity == 0 -> base
        intensity < 4 -> accent.copy(alpha = 0.4f)
        intensity < 8 -> accent.copy(alpha = 0.7f)
        else -> accent
    }
    Box(
        modifier = Modifier
            .size(HEAT_CELL)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun PrsCard(exercises: List<ExercisePr>, selectedId: String?, onSelect: (String) -> Unit) {
    val selected = exercises.firstOrNull { it.id == selectedId } ?: exercises.firstOrNull()
    Card {
        Text("PERSONAL RECORDS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected != null) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(selected.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                Text(selected.currentLabel, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Sparkline(values = selected.series, modifier = Modifier.fillMaxWidth().height(64.dp))
        }
        Spacer(Modifier.height(12.dp))
        exercises.forEach { pr ->
            PrRow(pr = pr, selected = pr.id == selected?.id, onClick = { onSelect(pr.id) })
        }
    }
}

@Composable
private fun PrRow(pr: ExercisePr, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pr.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text("${pr.logCount} sets logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(pr.currentLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val fill = accent.copy(alpha = 0.14f)
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        // A single reading can't form a line — show it as a centered marker on a baseline so the
        // graph still reads as "tracking started," filling in once there's a second reading.
        if (values.size == 1) {
            val y = size.height / 2f
            drawLine(fill, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            drawCircle(accent, radius = 4.dp.toPx(), center = Offset(size.width / 2f, y))
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - min) / range) * size.height)
        }
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(line, accent, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        content = content,
    )
}
