package com.getgymdone.app.ui.screens.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.WorkoutDay
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.ui.components.BigCta
import com.getgymdone.app.ui.components.GhostCta
import com.getgymdone.app.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DayOverviewState(
    val day: WorkoutDay? = null,
    val exercises: List<Pair<Exercise, Triple<Int, Int, Int>>> = emptyList(),
)

@HiltViewModel
class DayOverviewViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val splits: SplitRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val workoutDayId = handle.toRoute<Route.DayOverview>().workoutDayId
    private val _state = MutableStateFlow(DayOverviewState())
    val state: StateFlow<DayOverviewState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val day = splits.getDayExercises(workoutDayId).let { items ->
                val ex = items.mapNotNull { de ->
                    val e = exercises.getById(de.exerciseId) ?: return@mapNotNull null
                    e to Triple(de.prescribedSets, de.prescribedRepsLow, de.prescribedRepsHigh)
                }
                ex
            }
            _state.value = DayOverviewState(exercises = day)
        }
    }
}

@Composable
fun DayOverviewScreen(
    workoutDayId: String,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: DayOverviewViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Text("TODAY'S", style = MaterialTheme.typography.displaySmall)
        Text("LIFTS.", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.exercises) { (ex, presc) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        ex.name.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "${presc.first} × ${presc.second}-${presc.third} · ${ex.primaryMuscle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BigCta(label = "Start workout", onClick = onStart)
        Spacer(Modifier.height(8.dp))
        GhostCta(label = "Back", onClick = onBack)
    }
}
