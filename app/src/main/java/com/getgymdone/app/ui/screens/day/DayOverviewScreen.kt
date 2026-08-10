package com.getgymdone.app.ui.screens.day

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.ExerciseMedia
import com.getgymdone.app.data.db.entities.WorkoutDay
import com.getgymdone.app.data.repository.DayExerciseEdit
import com.getgymdone.app.data.repository.ExerciseMediaRepository
import com.getgymdone.app.data.repository.ExerciseNoteRepository
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.ui.components.BigCta
import com.getgymdone.app.ui.components.CustomExerciseForm
import com.getgymdone.app.ui.components.CustomExerciseInput
import com.getgymdone.app.ui.components.ExerciseNotesSection
import com.getgymdone.app.ui.components.MediaViewer
import com.getgymdone.app.ui.components.MediaViewerItem
import com.getgymdone.app.ui.components.PillChip
import com.getgymdone.app.ui.components.PillStyle
import com.getgymdone.app.ui.components.StripedPlaceholder
import com.getgymdone.app.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DayExerciseRow(val exercise: Exercise, val sets: Int, val repsLow: Int, val repsHigh: Int)

data class DayBrief(val id: String, val dayNumber: Int, val name: String, val exerciseCount: Int, val isRestDay: Boolean)

data class DayOverviewState(
    val loading: Boolean = true,
    val currentDayId: String = "",
    val day: WorkoutDay? = null,
    val splitDayCount: Int = 0,
    val exercises: List<DayExerciseRow> = emptyList(),
    val days: List<DayBrief> = emptyList(),
) {
    val warmup: List<String> get() = day?.let { warmupFor(it) }.orEmpty()
}

private fun warmupFor(day: WorkoutDay): List<String> {
    val focus = day.muscleGroups.firstOrNull() ?: day.name
    return listOf(
        "5 min easy bike or rower",
        "Dynamic stretch — $focus, 2 min",
        "Band activation — 2×15",
        "Light ramp-up sets — 1×10",
    )
}

@HiltViewModel
class DayOverviewViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val splits: SplitRepository,
    private val exercises: ExerciseRepository,
    private val prefs: UserPrefsRepository,
    private val mediaRepo: ExerciseMediaRepository,
    private val noteRepo: ExerciseNoteRepository,
) : ViewModel() {
    /** User-uploaded images/GIFs for an exercise (local only) — same source as the workout hero. */
    fun media(exerciseId: String): Flow<List<ExerciseMedia>> = mediaRepo.observe(exerciseId)

    /** The user's own note for an exercise, live from Room. */
    fun note(exerciseId: String): Flow<String> = noteRepo.observe(exerciseId)

    fun saveNote(exerciseId: String, text: String) = viewModelScope.launch {
        noteRepo.save(exerciseId, text)
    }

    private val initialDayId = handle.toRoute<Route.DayOverview>().workoutDayId
    private val _state = MutableStateFlow(DayOverviewState())
    val state: StateFlow<DayOverviewState> = _state.asStateFlow()

    private val _library = MutableStateFlow<List<Exercise>>(emptyList())
    val library: StateFlow<List<Exercise>> = _library.asStateFlow()

    init {
        viewModelScope.launch {
            _library.value = exercises.all()
            val splitId = prefs.get().activeSplitId
            val allDays = splitId?.let { splits.getDays(it) }.orEmpty()
            val briefs = allDays.map { d ->
                DayBrief(d.id, d.dayNumber, d.name, splits.getDayExercises(d.id).size, d.isRestDay)
            }
            _state.value = _state.value.copy(splitDayCount = allDays.size, days = briefs)
            load(initialDayId)
        }
    }

    fun switchTo(dayId: String) = viewModelScope.launch { load(dayId) }

    /** Create a user-defined exercise, refresh the picker library, and hand it back to the caller. */
    fun addCustomExercise(input: CustomExerciseInput, onCreated: (Exercise) -> Unit) = viewModelScope.launch {
        val ex = exercises.addCustom(
            name = input.name,
            primaryMuscle = input.primaryMuscle,
            secondaryMuscles = input.secondaryMuscles,
            equipment = input.equipment,
            formCues = input.formCues,
            sets = input.sets,
            repsLow = input.repsLow,
            repsHigh = input.repsHigh,
        )
        _library.value = exercises.all()
        onCreated(ex)
    }

    /** Persist the edited day name + exercise list, then refresh the view and day counts. */
    fun saveExercises(name: String, rows: List<DayExerciseRow>) = viewModelScope.launch {
        val dayId = _state.value.currentDayId
        splits.renameDay(dayId, name)
        splits.setDayExercises(
            dayId,
            rows.map { DayExerciseEdit(it.exercise.id, it.sets, it.repsLow, it.repsHigh) },
        )
        val cleanName = name.ifBlank { "Day" }
        val updatedDays = _state.value.days.map {
            if (it.id == dayId) it.copy(name = cleanName, exerciseCount = rows.size) else it
        }
        _state.value = _state.value.copy(days = updatedDays)
        load(dayId)
    }

    private suspend fun load(dayId: String) {
        val day = splits.getDayById(dayId)
        val items = splits.getDayExercises(dayId).sortedBy { it.orderIndex }
        val rows = items.mapNotNull { de ->
            val e = exercises.getById(de.exerciseId) ?: return@mapNotNull null
            DayExerciseRow(e, de.prescribedSets, de.prescribedRepsLow, de.prescribedRepsHigh)
        }
        _state.value = _state.value.copy(loading = false, currentDayId = dayId, day = day, exercises = rows)
    }
}

private enum class DayTab(val label: String) { Exercises("Exercises"), Warmup("Warmup") }

@Composable
fun DayOverviewScreen(
    workoutDayId: String,
    onStart: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: DayOverviewViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val library by vm.library.collectAsState()
    var tab by remember { mutableStateOf(DayTab.Exercises) }
    var switchOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }

    val day = state.day
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconSquare(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.days.size > 1) {
                        IconSquare(Icons.Rounded.MoreHoriz, "Switch day", onClick = { switchOpen = true })
                    }
                    if (day?.isRestDay == false) {
                        IconSquare(Icons.Rounded.Edit, "Edit exercises", onClick = { editOpen = true })
                    }
                }
            }

            if (day?.isRestDay == true) {
                RestDayBody(
                    dayNumber = day.dayNumber,
                    totalDays = state.splitDayCount,
                    modifier = Modifier.weight(1f),
                )
            } else {
            // Header
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Text(
                    text = day?.let { "DAY ${it.dayNumber} OF ${state.splitDayCount}" }.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text((day?.name ?: "Day").uppercase(), style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip("${state.exercises.size} exercises", PillStyle.Outline)
                    PillChip("~${state.exercises.size * 11} min", PillStyle.Outline)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Tab selector
            SegmentedTabs(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 22.dp),
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            ) {
                when (tab) {
                    DayTab.Exercises -> {
                        items(state.exercises.withIndex().toList(), key = { it.index }) { (idx, row) ->
                            // key() by exercise: the list slot is keyed by position, so switching
                            // days must dispose the note editor rather than hand its draft over.
                            key(row.exercise.id) {
                                val mediaFlow = remember(row.exercise.id) { vm.media(row.exercise.id) }
                                val media by mediaFlow.collectAsState(initial = emptyList())
                                val noteFlow = remember(row.exercise.id) { vm.note(row.exercise.id) }
                                val note by noteFlow.collectAsState(initial = "")
                                ExerciseCard(
                                    index = idx + 1,
                                    name = row.exercise.name,
                                    muscle = row.exercise.primaryMuscle,
                                    prescription = "${row.sets}×${row.repsLow}-${row.repsHigh}",
                                    media = media,
                                    note = note,
                                    onSaveNote = { text -> vm.saveNote(row.exercise.id, text) },
                                )
                            }
                        }
                    }
                    DayTab.Warmup -> {
                        item("warmup-head") { WarmupHeader() }
                        items(state.warmup.withIndex().toList(), key = { "w${it.index}" }) { (idx, step) ->
                            WarmupRow(idx + 1, step)
                        }
                        item("warmup-note") {
                            Text(
                                "Skip warmup at your own risk.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp, bottom = 18.dp),
            ) {
                BigCta(
                    label = if (tab == DayTab.Warmup) "Start warmup →" else "Start workout →",
                    onClick = { onStart(state.currentDayId) },
                )
            }
            }
        }

        if (switchOpen) {
            SwitchDaySheet(
                days = state.days,
                currentId = state.currentDayId,
                onPick = { id -> vm.switchTo(id); tab = DayTab.Exercises; switchOpen = false },
                onDismiss = { switchOpen = false },
            )
        }

        if (editOpen && day != null) {
            EditExercisesSheet(
                dayName = day.name,
                initial = state.exercises,
                library = library,
                onSave = { name, rows -> vm.saveExercises(name, rows); editOpen = false },
                onCreateCustom = { input, onCreated -> vm.addCustomExercise(input, onCreated) },
                onCancel = { editOpen = false },
            )
        }
    }
}

/**
 * Full-screen editor for a day's exercise list: reorder (up/down), remove, and add from the
 * library. Edits are held locally and only committed when the user taps Save.
 */
@Composable
private fun EditExercisesSheet(
    dayName: String,
    initial: List<DayExerciseRow>,
    library: List<Exercise>,
    onSave: (String, List<DayExerciseRow>) -> Unit,
    onCreateCustom: (CustomExerciseInput, (Exercise) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    val rows = remember { androidx.compose.runtime.mutableStateListOf<DayExerciseRow>().apply { addAll(initial) } }
    var name by remember { mutableStateOf(dayName) }
    var pickerOpen by remember { mutableStateOf(false) }
    var creatingCustom by remember { mutableStateOf(false) }

    if (creatingCustom) {
        CustomExerciseForm(
            muscleOptions = library.map { it.primaryMuscle }.distinct().sorted(),
            onSubmit = { input ->
                onCreateCustom(input) { ex ->
                    rows.add(DayExerciseRow(ex, ex.defaultSets, ex.defaultRepsLow, ex.defaultRepsHigh))
                    creatingCustom = false
                    pickerOpen = false
                }
            },
            onCancel = { creatingCustom = false },
        )
        return
    }

    if (pickerOpen) {
        EditPickerSheet(
            library = library,
            alreadyAdded = rows.map { it.exercise.id }.toSet(),
            onPick = { ex ->
                rows.add(DayExerciseRow(ex, ex.defaultSets, ex.defaultRepsLow, ex.defaultRepsHigh))
                pickerOpen = false
            },
            onCreateCustom = { creatingCustom = true },
            onClose = { pickerOpen = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 44.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onCancel),
            )
            Text(
                text = "Save",
                style = MaterialTheme.typography.labelLarge,
                color = if (rows.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = rows.isNotEmpty()) { onSave(name, rows.toList()) },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text("DAY NAME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            val fieldShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(MaterialTheme.colorScheme.surface, fieldShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, fieldShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (name.isEmpty()) {
                    Text("Day name", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            items(rows.withIndex().toList(), key = { (_, r) -> r.exercise.id }) { (idx, row) ->
                EditExerciseCard(
                    index = idx + 1,
                    name = row.exercise.name,
                    muscle = row.exercise.primaryMuscle,
                    prescription = "${row.sets}×${row.repsLow}-${row.repsHigh}",
                    canMoveUp = idx > 0,
                    canMoveDown = idx < rows.size - 1,
                    onMoveUp = { if (idx > 0) { val r = rows.removeAt(idx); rows.add(idx - 1, r) } },
                    onMoveDown = { if (idx < rows.size - 1) { val r = rows.removeAt(idx); rows.add(idx + 1, r) } },
                    onRemove = { rows.removeAt(idx) },
                )
            }
            item(key = "add") {
                AddExerciseButton(onClick = { pickerOpen = true })
            }
        }
    }
}

@Composable
private fun EditExerciseCard(
    index: Int,
    name: String,
    muscle: String,
    prescription: String,
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
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            MiniIconButton(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = canMoveUp, onClick = onMoveUp)
            Spacer(Modifier.height(4.dp))
            MiniIconButton(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = canMoveDown, onClick = onMoveDown)
        }
        Spacer(Modifier.width(10.dp))
        Text("$index", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("$muscle · $prescription", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        MiniIconButton(Icons.Rounded.Close, "Remove", enabled = true, onClick = onRemove)
    }
}

@Composable
private fun MiniIconButton(
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
private fun AddExerciseButton(onClick: () -> Unit, label: String = "+ ADD EXERCISE") {
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
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EditPickerSheet(
    library: List<Exercise>,
    alreadyAdded: Set<String>,
    onPick: (Exercise) -> Unit,
    onCreateCustom: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(library, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) library
        else library.filter { it.name.lowercase().contains(q) || it.primaryMuscle.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PICK AN EXERCISE", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }
        Spacer(Modifier.height(12.dp))
        SearchField(value = query, onValueChange = { query = it })
        Spacer(Modifier.height(10.dp))
        AddExerciseButton(onClick = onCreateCustom, label = "+ CREATE CUSTOM EXERCISE")
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val grouped = filtered.groupBy { it.primaryMuscle }.toSortedMap()
            grouped.forEach { (muscle, exs) ->
                item(key = "header_$muscle") {
                    Text(
                        text = muscle.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items = exs, key = { it.id }) { ex ->
                    EditPickerRow(exercise = ex, alreadyAdded = ex.id in alreadyAdded, onPick = onPick)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "No exercises match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Search by name or muscle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditPickerRow(exercise: Exercise, alreadyAdded: Boolean, onPick: (Exercise) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = !alreadyAdded) { onPick(exercise) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${exercise.equipment} · ${exercise.defaultSets}×${exercise.defaultRepsLow}-${exercise.defaultRepsHigh}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (alreadyAdded) {
            PillChip(text = "Added", style = PillStyle.Solid)
        }
    }
}

@Composable
private fun RestDayBody(dayNumber: Int, totalDays: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "DAY $dayNumber OF $totalDays",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("REST DAY", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "No training today. Recovery is when the work pays off — eat well, sleep, and let the muscles rebuild. Light walks or mobility are fine.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SegmentedTabs(selected: DayTab, onSelect: (DayTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayTab.entries.forEach { t ->
            val active = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) MaterialTheme.colorScheme.background else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One exercise in the day plan: the prescription row, its uploaded media (tap to open the same
 * full-screen [MediaViewer] the workout screen uses) and its notes — reachable here so notes can be
 * written while planning, not only mid-workout.
 */
@Composable
private fun ExerciseCard(
    index: Int,
    name: String,
    muscle: String,
    prescription: String,
    media: List<ExerciseMedia>,
    note: String,
    onSaveNote: (String) -> Unit,
) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$index", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
            val thumbShape = RoundedCornerShape(10.dp)
            val first = media.firstOrNull()
            if (first == null) {
                StripedPlaceholder(label = "gif", modifier = Modifier.size(48.dp).clip(thumbShape))
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(thumbShape)
                        .clickable { viewerIndex = 0 },
                ) {
                    AsyncImage(
                        model = File(first.filePath),
                        contentDescription = "Exercise media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (media.size > 1) {
                        val badgeShape = RoundedCornerShape(6.dp)
                        Text(
                            "+${media.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(badgeShape)
                                .background(Color.Black.copy(alpha = 0.45f), badgeShape)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(muscle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(prescription, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.height(10.dp))
        // Flat: this card is already outlined, so the notes section drops its own frame.
        ExerciseNotesSection(note = note, onSave = onSaveNote, flat = true)
    }

    viewerIndex?.let { idx ->
        val viewerItems = remember(media) { media.map { MediaViewerItem(it.id, it.filePath) } }
        MediaViewer(
            items = viewerItems,
            initialIndex = idx,
            onDismiss = { viewerIndex = null },
        )
    }
}

@Composable
private fun WarmupHeader() {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column {
            Text("RECOMMENDED WARMUP", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text("~8 min · primes the muscles you'll hit today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WarmupRow(index: Int, step: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$index", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SwitchDaySheet(days: List<DayBrief>, currentId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .padding(20.dp)
                .padding(bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            Text("OVERRIDE TODAY", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text("Feeling like a different day? Switch it up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            days.forEach { d ->
                val isCurrent = d.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .clickable { onPick(d.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${d.dayNumber}", style = MaterialTheme.typography.headlineSmall, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (d.isRestDay) "REST" else d.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Text(if (d.isRestDay) "Recovery day" else "${d.exerciseCount} exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isCurrent) {
                        Icon(Icons.Rounded.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}
