package com.getgymdone.app.ui.screens.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.ui.components.PillChip
import com.getgymdone.app.ui.components.PillStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DayRow(
    val id: String,
    val dayNumber: Int,
    val name: String,
    val exerciseCount: Int,
    val setCount: Int,
    val completedCount: Int,
    val preview: List<String>,
    val isRestDay: Boolean,
)

data class WorkoutsState(
    val loading: Boolean = true,
    val splitName: String = "",
    val days: List<DayRow> = emptyList(),
)

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
    private val splits: SplitRepository,
    private val exercises: ExerciseRepository,
    private val sessions: SessionRepository,
    private val metrics: MetricsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutsState())
    val state: StateFlow<WorkoutsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.observe(), sessions.observeHistory()) { _, _ -> }.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val splitId = prefs.get().activeSplitId
            val split = splitId?.let { splits.getById(it) }
            val days = splitId?.let { splits.getDays(it) }.orEmpty()
            val byId = exercises.all().associateBy { it.id }
            val completedByDay = metrics.completedSessions().groupingBy { it.workoutDayId }.eachCount()

            val rows = days.map { day ->
                val items = splits.getDayExercises(day.id).sortedBy { it.orderIndex }
                DayRow(
                    id = day.id,
                    dayNumber = day.dayNumber,
                    name = day.name,
                    exerciseCount = items.size,
                    setCount = items.sumOf { it.prescribedSets },
                    completedCount = completedByDay[day.id] ?: 0,
                    preview = items.mapNotNull { byId[it.exerciseId]?.name }.take(4),
                    isRestDay = day.isRestDay,
                )
            }
            _state.value = WorkoutsState(loading = false, splitName = split?.name.orEmpty(), days = rows)
        }
    }
}

@Composable
fun WorkoutsListScreen(onOpenDay: (String) -> Unit) {
    val vm: WorkoutsViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 16.dp),
    ) {
        Text(
            text = "ROUTINE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.splitName.ifEmpty { "WORKOUTS" }.uppercase(),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(20.dp))

        if (state.days.isEmpty() && !state.loading) {
            Text(
                "No routine set. Pick a split from Settings first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.days, key = { it.id }) { day ->
                DayCard(day = day, onClick = { onOpenDay(day.id) })
            }
        }
    }
}

@Composable
private fun DayCard(day: DayRow, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (day.isRestDay) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${day.dayNumber}",
                style = MaterialTheme.typography.displaySmall,
                color = if (day.isRestDay) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (day.isRestDay) "REST" else day.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (day.isRestDay)
                        "Recovery day — rest, eat, sleep"
                    else
                        "${day.exerciseCount} exercises · ${day.setCount} sets · ${day.completedCount} completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!day.isRestDay && day.completedCount > 0) {
                PillChip(text = "${day.completedCount}×", style = PillStyle.Solid)
            }
        }
        if (!day.isRestDay && day.preview.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                day.preview.forEach { name ->
                    PreviewChip(name)
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(name: String) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
