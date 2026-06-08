package com.getgymdone.app.ui.screens.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.WeightUnit
import com.getgymdone.app.domain.kgToDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val HEATMAP_DAYS = 70

data class PrEvent(val exercise: String, val weightLabel: String, val dateLabel: String)

data class ExOption(val id: String, val name: String)

data class ProfileState(
    val loading: Boolean = true,
    val totalVolumeLabel: String = "0",
    val sessionCount: Int = 0,
    val unitLabel: String = "kg",
    val progressionName: String = "",
    val progressionSeries: List<Float> = emptyList(),
    val progressionCurrentLabel: String = "",
    val progressionDeltaLabel: String = "",
    val progressionOptions: List<ExOption> = emptyList(),
    val selectedExerciseId: String? = null,
    val bodyweightLabel: String = "—",
    val bodyweightSeries: List<Float> = emptyList(),
    val heatmap: List<Int> = emptyList(),
    val prs: List<PrEvent> = emptyList(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val metrics: MetricsRepository,
    private val prefs: UserPrefsRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    /** Survives [refresh] so the chosen progression lift sticks across data updates. */
    private var selectedExerciseId: String? = null

    init {
        viewModelScope.launch {
            combine(prefs.observe(), sessions.observeHistory()) { _, _ -> }.collect { refresh() }
        }
    }

    fun selectExercise(id: String) {
        selectedExerciseId = id
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val unit = WeightUnit.fromStored(prefs.get().units)
            val sets = metrics.allSets()
            val sessions = metrics.completedSessions()
            val byId = metrics.exercises().associateBy { it.id }

            val totalVolume = sets.sumOf { it.weightKg * it.reps }

            // Progression: one top-set point per session, per exercise. Offer every lift with
            // at least two logged sessions as a selectable chip; the user's pick sticks.
            fun seriesFor(exId: String): List<Float> =
                sets.filter { it.exerciseId == exId }
                    .groupBy { it.sessionId }
                    .map { (_, g) -> g.maxOf { it.completedAt } to g.maxOf { it.weightKg } }
                    .sortedBy { it.first }
                    .map { it.second.kgToDisplay(unit).toFloat() }

            val logCount = sets.groupingBy { it.exerciseId }.eachCount()
            val options = logCount.keys
                .filter { seriesFor(it).size >= 2 }
                .sortedByDescending { logCount[it] ?: 0 }
                .map { ExOption(it, byId[it]?.name.orEmpty()) }

            val chosenId = selectedExerciseId?.takeIf { id -> options.any { it.id == id } }
                ?: options.firstOrNull()?.id
            selectedExerciseId = chosenId

            val progressionSeries: List<Float>
            val progressionName: String
            val progressionCurrent: String
            val progressionDelta: String
            if (chosenId != null) {
                val series = seriesFor(chosenId)
                progressionSeries = series
                progressionName = byId[chosenId]?.name.orEmpty()
                progressionCurrent = series.lastOrNull()?.let { "%.1f %s".format(it, unit.label) } ?: "—"
                progressionDelta = if (series.size >= 2 && series.first() > 0f) {
                    val pct = ((series.last() - series.first()) / series.first() * 100).toInt()
                    if (pct >= 0) "+$pct%" else "$pct%"
                } else ""
            } else {
                progressionSeries = emptyList(); progressionName = ""; progressionCurrent = "—"; progressionDelta = ""
            }

            val bw = metrics.bodyweightSeries()
            val bwLabel = bw.lastOrNull()?.bodyweightKg
                ?.let { "%.1f %s".format(it.kgToDisplay(unit), unit.label) } ?: "—"
            val bwSeries = bw.mapNotNull { it.bodyweightKg?.kgToDisplay(unit)?.toFloat() }

            _state.value = ProfileState(
                loading = false,
                totalVolumeLabel = compactVolume(totalVolume.kgToDisplay(unit)),
                sessionCount = sessions.size,
                unitLabel = unit.label,
                progressionName = progressionName,
                progressionSeries = progressionSeries,
                progressionCurrentLabel = progressionCurrent,
                progressionDeltaLabel = progressionDelta,
                progressionOptions = options,
                selectedExerciseId = chosenId,
                bodyweightLabel = bwLabel,
                bodyweightSeries = bwSeries,
                heatmap = buildHeatmap(sets.map { it.completedAt }),
                prs = recentPrs(sets, byId, unit),
            )
        }
    }

    private fun buildHeatmap(setTimes: List<Long>): List<Int> {
        val dayMs = 24L * 60 * 60 * 1000
        val today = System.currentTimeMillis() / dayMs
        val counts = setTimes.groupingBy { it / dayMs }.eachCount()
        return (HEATMAP_DAYS - 1 downTo 0).map { back -> counts[today - back] ?: 0 }
    }

    private fun recentPrs(
        sets: List<com.getgymdone.app.data.db.entities.SetLog>,
        byId: Map<String, com.getgymdone.app.data.db.entities.Exercise>,
        unit: WeightUnit,
    ): List<PrEvent> {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        val events = mutableListOf<Triple<Long, String, Double>>()
        sets.groupBy { it.exerciseId }.forEach { (exId, exSets) ->
            var runningMax = 0.0
            exSets.sortedBy { it.completedAt }.forEach { s ->
                if (s.weightKg > runningMax) {
                    runningMax = s.weightKg
                    events += Triple(s.completedAt, byId[exId]?.name ?: exId, s.weightKg)
                }
            }
        }
        return events.sortedByDescending { it.first }.take(3).map { (time, name, weight) ->
            PrEvent(
                exercise = name,
                weightLabel = "%.1f %s".format(weight.kgToDisplay(unit), unit.label),
                dateLabel = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt),
            )
        }
    }

    private fun compactVolume(v: Double): String = when {
        v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        v >= 1_000 -> "%.1fk".format(v / 1_000)
        else -> v.toInt().toString()
    }
}

@Composable
fun ProfileScreen() {
    val vm: ProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 24.dp),
    ) {
        ProfileHeader(sessionCount = state.sessionCount)

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigStat("Total volume", "${state.totalVolumeLabel} ${state.unitLabel}", Modifier.weight(1f))
            BigStat("Sessions", "${state.sessionCount}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        if (state.progressionSeries.size >= 2) {
            ProgressionCard(state)
            if (state.progressionOptions.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.progressionOptions.forEach { opt ->
                        ExerciseChip(
                            label = opt.name,
                            selected = opt.id == state.selectedExerciseId,
                            onClick = { vm.selectExercise(opt.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        BodyweightCard(state)

        Spacer(Modifier.height(14.dp))

        ConsistencyCard(state.heatmap)

        if (state.prs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            RecentPrsCard(state.prs)
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
}

@Composable
private fun ProfileHeader(sessionCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("YOU", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text("ATHLETE", style = MaterialTheme.typography.headlineMedium)
            Text(
                "$sessionCount sessions logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun ProgressionCard(state: ProfileState) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("PROGRESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(state.progressionName.uppercase(), style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(state.progressionCurrentLabel, style = MaterialTheme.typography.headlineSmall)
                if (state.progressionDeltaLabel.isNotEmpty()) {
                    Text(state.progressionDeltaLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Sparkline(
            values = state.progressionSeries,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
    }
}

@Composable
private fun BodyweightCard(state: ProfileState) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("BODYWEIGHT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(state.bodyweightLabel, style = MaterialTheme.typography.headlineMedium)
            }
            if (state.bodyweightSeries.size >= 2) {
                Sparkline(
                    values = state.bodyweightSeries,
                    modifier = Modifier.fillMaxWidth(0.5f).height(48.dp),
                )
            }
        }
    }
}

@Composable
private fun ConsistencyCard(heatmap: List<Int>) {
    Card {
        Text("CONSISTENCY · 10 WEEKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        // 7 rows (days) × 10 columns (weeks); fill column-major from oldest.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until 7) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 10) {
                        val idx = col * 7 + row
                        val intensity = heatmap.getOrElse(idx) { 0 }
                        HeatCell(intensity)
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
        intensity <= 0 -> base
        intensity < 4 -> accent.copy(alpha = 0.4f)
        intensity < 8 -> accent.copy(alpha = 0.7f)
        else -> accent
    }
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

@Composable
private fun RecentPrsCard(prs: List<PrEvent>) {
    Card {
        Text("RECENT PRS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        prs.forEachIndexed { i, pr ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(pr.exercise, style = MaterialTheme.typography.titleMedium)
                    Text(pr.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(pr.weightLabel, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (i < prs.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val fill = accent.copy(alpha = 0.14f)
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
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
private fun ExerciseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
