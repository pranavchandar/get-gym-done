package com.getgymdone.app.ui.screens.workout

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.getgymdone.app.data.repository.MetricsRepository
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompleteState(
    val loading: Boolean = true,
    val dayNumber: Int = 0,
    val dayName: String = "",
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val prCount: Int = 0,
    val volumeDelta: String = "—",
)

@HiltViewModel
class WorkoutCompleteViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val splits: SplitRepository,
    private val metrics: MetricsRepository,
) : ViewModel() {

    private val sessionId = handle.toRoute<Route.WorkoutComplete>().sessionId
    private val _state = MutableStateFlow(CompleteState())
    val state: StateFlow<CompleteState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessions.getById(sessionId)
            val sets = sessions.getSetsForSession(sessionId)
            val day = session?.let { splits.getDayById(it.workoutDayId) }

            val byExercise = sets.groupBy { it.exerciseId }
            // PR = this session's top weight beats every prior session for that lift.
            val prCount = byExercise.count { (exId, exSets) ->
                val thisMax = exSets.maxOf { it.weightKg }
                val priorMax = sessions.getProgression(exId)
                    .filter { it.sessionId != sessionId }
                    .maxOfOrNull { it.weightKg } ?: 0.0
                thisMax > priorMax
            }

            val thisVolume = sets.sumOf { it.weightKg * it.reps }
            val volumeDelta = computeVolumeDelta(session?.workoutDayId, session?.completedAt, thisVolume)

            _state.value = CompleteState(
                loading = false,
                dayNumber = day?.dayNumber ?: 0,
                dayName = day?.name.orEmpty(),
                exerciseCount = byExercise.size,
                setCount = sets.size,
                prCount = prCount,
                volumeDelta = volumeDelta,
            )
        }
    }

    private suspend fun computeVolumeDelta(workoutDayId: String?, completedAt: Long?, thisVolume: Double): String {
        if (workoutDayId == null || completedAt == null) return "—"
        val previous = metrics.completedSessions()
            .filter { it.workoutDayId == workoutDayId && it.id != sessionId && (it.completedAt ?: 0) < completedAt }
            .maxByOrNull { it.completedAt ?: 0 } ?: return "NEW"
        val prevVolume = sessions.getSetsForSession(previous.id).sumOf { it.weightKg * it.reps }
        if (prevVolume <= 0.0) return "NEW"
        val pct = ((thisVolume - prevVolume) / prevVolume * 100).toInt()
        return if (pct >= 0) "+$pct%" else "$pct%"
    }
}

@Composable
fun WorkoutCompleteScreen(
    sessionId: String,
    onDone: () -> Unit,
) {
    val vm: WorkoutCompleteViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "DAY ${state.dayNumber} DONE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            state.dayName.uppercase(),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "LOCKED IN.",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "${state.exerciseCount} exercises · ${state.setCount} sets · logged.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPill("PRs", "${state.prCount}")
            StatPill("Vol", state.volumeDelta)
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "BACK HOME →",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
