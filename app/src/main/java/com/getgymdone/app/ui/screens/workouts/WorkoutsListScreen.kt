package com.getgymdone.app.ui.screens.workouts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
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
    val splitId: String = "",
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
            _state.value = WorkoutsState(
                loading = false,
                splitId = splitId.orEmpty(),
                splitName = split?.name.orEmpty(),
                days = rows,
            )
        }
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
fun WorkoutsListScreen(onOpenDay: (String) -> Unit) {
    val vm: WorkoutsViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            }
            if (state.days.isNotEmpty()) {
                EditToggle(editing = editing, onToggle = { editing = !editing })
            }
        }
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
            if (editing) {
                itemsIndexed(state.days, key = { _, day -> day.id }) { idx, day ->
                    EditDayRow(
                        day = day,
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
                item(key = "__add_day") { AddDayButton(onClick = vm::addDay) }
            } else {
                items(state.days, key = { it.id }) { day ->
                    // Rest days have nothing to open or start, so they aren't tappable.
                    DayCard(day = day, onClick = if (day.isRestDay) null else ({ onOpenDay(day.id) }))
                }
            }
        }
    }
}

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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!editing) Icon(Icons.Rounded.Edit, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(if (editing) "DONE" else "EDIT", style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun EditDayRow(
    day: DayRow,
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
                if (day.isRestDay) "Recovery day" else "${day.exerciseCount} exercises",
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun DayCard(day: DayRow, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (day.isRestDay) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
