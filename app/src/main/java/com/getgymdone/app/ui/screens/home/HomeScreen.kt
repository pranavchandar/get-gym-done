package com.getgymdone.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.getgymdone.app.data.db.entities.WorkoutDay
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.nextDayNumber
import com.getgymdone.app.ui.components.BigCta
import com.getgymdone.app.ui.components.GhostCta
import com.getgymdone.app.ui.components.PillChip
import com.getgymdone.app.ui.components.PillStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val splitName: String = "",
    val days: List<WorkoutDay> = emptyList(),
    val nextDay: WorkoutDay? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
    private val splits: SplitRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = prefs.get()
            val splitId = p.activeSplitId ?: return@launch
            val split = splits.getById(splitId) ?: return@launch
            val days = splits.getDays(splitId)
            val lastSession = sessions.getLastCompleted()
            val lastDayNumber = lastSession?.let { s -> days.firstOrNull { it.id == s.workoutDayId }?.dayNumber }
            val target = nextDayNumber(lastDayNumber, split.dayCount)
            val nextDay = days.firstOrNull { it.dayNumber == target } ?: days.firstOrNull()
            _state.value = HomeState(
                loading = false,
                splitName = split.name,
                days = days,
                nextDay = nextDay,
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenDay: (String) -> Unit,
    onStartToday: (String) -> Unit,
    onOpenWorkouts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("READY TO", style = MaterialTheme.typography.displaySmall)
                Text("GET IT.", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            }
            PillChip(text = state.splitName.ifEmpty { "—" }, style = PillStyle.Outline)
        }

        Spacer(Modifier.height(24.dp))

        state.nextDay?.let { day ->
            UpNextCard(
                day = day,
                onStart = { onStartToday(day.id) },
                onOpen = { onOpenDay(day.id) },
            )
        }

        Spacer(Modifier.height(20.dp))

        if (state.days.isNotEmpty()) {
            Text(
                "THIS WEEK".uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(state.days, key = { it.id }) { day ->
                    WeekDayRow(
                        day = day,
                        isNext = day.id == state.nextDay?.id,
                        onClick = { onOpenDay(day.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GhostCta(label = "Workouts", onClick = onOpenWorkouts, modifier = Modifier.weight(1f))
            GhostCta(label = "Profile", onClick = onOpenProfile, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GhostCta(label = "Settings", onClick = onOpenSettings, modifier = Modifier.weight(1f))
            GhostCta(label = "Debug data", onClick = onOpenDebug, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun UpNextCard(day: WorkoutDay, onStart: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onOpen)
            .padding(20.dp),
    ) {
        Text(
            text = "UP NEXT · DAY ${day.dayNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = day.name.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        if (day.muscleGroups.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = day.muscleGroups.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        BigCta(label = "Start workout", onClick = onStart)
    }
}

@Composable
private fun WeekDayRow(day: WorkoutDay, isNext: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(
                1.dp,
                if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "D${day.dayNumber} · ${day.name.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (day.muscleGroups.isNotEmpty()) {
                Text(
                    text = day.muscleGroups.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isNext) PillChip(text = "Next", style = PillStyle.Solid)
    }
}
