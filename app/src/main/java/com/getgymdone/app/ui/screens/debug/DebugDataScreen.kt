package com.getgymdone.app.ui.screens.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.ui.components.GhostCta
import com.getgymdone.app.ui.components.PillChip
import com.getgymdone.app.ui.components.PillStyle
import com.getgymdone.app.ui.components.StripedPlaceholder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DebugDataViewModel @Inject constructor(
    repo: ExerciseRepository,
) : ViewModel() {
    val exercises: StateFlow<List<Exercise>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun DebugDataScreen(onBack: () -> Unit) {
    val vm: DebugDataViewModel = hiltViewModel()
    val items by vm.exercises.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Text("DB SEED", style = MaterialTheme.typography.displaySmall)
        Text("${items.size} EXERCISES", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(items, key = { it.id }) { ex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StripedPlaceholder(
                        label = "GIF",
                        modifier = Modifier.size(width = 56.dp, height = 56.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ex.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${ex.primaryMuscle} · ${ex.equipment}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PillChip(text = "${ex.defaultSets}×${ex.defaultRepsLow}-${ex.defaultRepsHigh}", style = PillStyle.Outline)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GhostCta(label = "Back", onClick = onBack)
    }
}
