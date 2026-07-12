package com.getgymdone.app.ui.screens.onboarding

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.repository.CustomDayDraft
import com.getgymdone.app.data.repository.CustomExerciseDraft
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.ui.components.BigCta
import com.getgymdone.app.ui.components.CustomExerciseForm
import com.getgymdone.app.ui.components.CustomExerciseInput
import com.getgymdone.app.ui.components.GhostCta
import com.getgymdone.app.ui.components.PillChip
import com.getgymdone.app.ui.components.PillStyle
import com.getgymdone.app.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MIN_DAYS = 2
private const val MAX_DAYS = 7

// ── Mutable per-day draft used by the builder UI (separate from the repository's
// CustomDayDraft so we can edit prescriptions inline). ─────────────────────────
data class ExerciseDraft(
    val exerciseId: String,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
)

data class DayDraft(
    val name: String,
    val exercises: List<ExerciseDraft>,
)

@HiltViewModel
class CustomizeRoutineViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val splits: SplitRepository,
    private val exerciseRepo: ExerciseRepository,
    private val prefs: UserPrefsRepository,
) : ViewModel() {

    private val seedSplitId: String? = handle.toRoute<Route.CustomizeRoutine>().seedSplitId

    private val _library = MutableStateFlow<List<Exercise>>(emptyList())
    val library: StateFlow<List<Exercise>> = _library.asStateFlow()

    private val _initialDraft = MutableStateFlow<InitialDraft?>(null)
    val initialDraft: StateFlow<InitialDraft?> = _initialDraft.asStateFlow()

    init {
        viewModelScope.launch {
            // One-shot read is enough for the picker; we don't need live updates while editing.
            _library.value = exerciseRepo.observeAll().first()
            _initialDraft.value = loadInitial()
        }
    }

    private suspend fun loadInitial(): InitialDraft {
        val seed = seedSplitId?.let { splits.getById(it) }
        if (seed == null) {
            return InitialDraft(
                name = "My Routine",
                days = List(3) { DayDraft(name = "Day ${it + 1}", exercises = emptyList()) },
            )
        }
        val workoutDays = splits.getDays(seed.id)
        val days = workoutDays.map { wd ->
            val items = splits.getDayExercises(wd.id).map {
                ExerciseDraft(
                    exerciseId = it.exerciseId,
                    sets = it.prescribedSets,
                    repsLow = it.prescribedRepsLow,
                    repsHigh = it.prescribedRepsHigh,
                )
            }
            DayDraft(name = wd.name, exercises = items)
        }
        return InitialDraft(name = "${seed.name} (mine)", days = days)
    }

    fun save(name: String, days: List<DayDraft>, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val drafts = days.map { d ->
                CustomDayDraft(
                    name = d.name.ifBlank { "Day" },
                    muscleGroups = inferMuscleGroups(d.exercises),
                    exercises = d.exercises.map {
                        CustomExerciseDraft(it.exerciseId, it.sets, it.repsLow, it.repsHigh)
                    },
                )
            }
            val id = splits.createCustom(name.ifBlank { "My Routine" }, drafts)
            prefs.update { it.copy(activeSplitId = id, onboardingComplete = true) }
            onSaved(id)
        }
    }

    fun addCustomExercise(input: CustomExerciseInput, onCreated: (Exercise) -> Unit) {
        viewModelScope.launch {
            val ex = exerciseRepo.addCustom(
                name = input.name,
                primaryMuscle = input.primaryMuscle,
                secondaryMuscles = input.secondaryMuscles,
                equipment = input.equipment,
                formCues = input.formCues,
                sets = input.sets,
                repsLow = input.repsLow,
                repsHigh = input.repsHigh,
            )
            _library.value = exerciseRepo.all()
            onCreated(ex)
        }
    }

    private fun inferMuscleGroups(exercises: List<ExerciseDraft>): List<String> {
        val byId = _library.value.associateBy { it.id }
        return exercises.mapNotNull { byId[it.exerciseId]?.primaryMuscle }.distinct()
    }
}

data class InitialDraft(val name: String, val days: List<DayDraft>)

@Composable
fun CustomizeRoutineScreen(
    seedSplitId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: CustomizeRoutineViewModel = hiltViewModel()
    val initial by vm.initialDraft.collectAsState()
    val library by vm.library.collectAsState()

    val draft = initial
    if (draft == null) {
        // Brief loading state while initial draft is computed.
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LOADING…",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BuilderContent(
        initial = draft,
        library = library,
        seededFromPreset = seedSplitId != null,
        onCommit = { name, days -> vm.save(name, days) { onDone() } },
        onCreateCustom = { input, onCreated -> vm.addCustomExercise(input, onCreated) },
        onBack = onBack,
    )
}

@Composable
private fun BuilderContent(
    initial: InitialDraft,
    library: List<Exercise>,
    seededFromPreset: Boolean,
    onCommit: (String, List<DayDraft>) -> Unit,
    onCreateCustom: (CustomExerciseInput, (Exercise) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var nameField by remember { mutableStateOf(TextFieldValue(initial.name)) }
    val days: SnapshotStateList<DayDraft> = remember {
        mutableStateListOf<DayDraft>().apply { addAll(initial.days) }
    }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    var pickerOpenForDay by remember { mutableStateOf<Int?>(null) }
    var creatingCustomForDay by remember { mutableStateOf<Int?>(null) }

    fun addExerciseToDay(dayIndex: Int, ex: Exercise) {
        val current = days[dayIndex]
        days[dayIndex] = current.copy(
            exercises = current.exercises + ExerciseDraft(
                exerciseId = ex.id,
                sets = ex.defaultSets,
                repsLow = ex.defaultRepsLow,
                repsHigh = ex.defaultRepsHigh,
            ),
        )
    }

    // Custom-exercise form short-circuits everything while active.
    val creatingForDay = creatingCustomForDay
    if (creatingForDay != null) {
        CustomExerciseForm(
            muscleOptions = library.map { it.primaryMuscle }.distinct().sorted(),
            onSubmit = { input ->
                onCreateCustom(input) { ex ->
                    addExerciseToDay(creatingForDay, ex)
                    creatingCustomForDay = null
                    pickerOpenForDay = null
                }
            },
            onCancel = { creatingCustomForDay = null },
        )
        return
    }

    // Picker overlay short-circuits the main builder UI when active.
    val openPickerForDay = pickerOpenForDay
    if (openPickerForDay != null) {
        ExercisePickerSheet(
            library = library,
            alreadyAdded = days[openPickerForDay].exercises.map { it.exerciseId }.toSet(),
            onPick = { exercise ->
                val ex = library.firstOrNull { it.id == exercise.id } ?: return@ExercisePickerSheet
                addExerciseToDay(openPickerForDay, ex)
                pickerOpenForDay = null
            },
            onCreateCustom = { creatingCustomForDay = openPickerForDay },
            onClose = { pickerOpenForDay = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Text("BUILD YOUR", style = MaterialTheme.typography.displaySmall)
        Text("ROUTINE.", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (seededFromPreset)
                "Adjust the preset to your taste, then lock it in."
            else
                "Name it, pick your day count, then add exercises.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        FieldLabel("Routine name")
        Spacer(Modifier.height(6.dp))
        BorderedTextField(
            value = nameField,
            onValueChange = { nameField = it },
            placeholder = "e.g. My Routine",
        )

        Spacer(Modifier.height(16.dp))

        FieldLabel("Day count")
        Spacer(Modifier.height(6.dp))
        DayCountStepper(
            value = days.size,
            onChange = { target ->
                if (target in MIN_DAYS..MAX_DAYS) {
                    when {
                        target > days.size -> repeat(target - days.size) {
                            val n = days.size + 1
                            days.add(DayDraft(name = "Day $n", exercises = emptyList()))
                        }
                        target < days.size -> repeat(days.size - target) {
                            days.removeAt(days.size - 1)
                        }
                    }
                    if (selectedDay >= days.size) selectedDay = days.size - 1
                }
            },
        )

        Spacer(Modifier.height(20.dp))

        // Day tabs — horizontal scroll so 7-day routines don't overflow.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEachIndexed { idx, _ ->
                DayTab(
                    label = "D${idx + 1}",
                    selected = idx == selectedDay,
                    onClick = { selectedDay = idx },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Selected-day editor.
        val current = days[selectedDay]
        DayEditor(
            dayIndex = selectedDay,
            day = current,
            library = library,
            onRenameDay = { newName ->
                days[selectedDay] = current.copy(name = newName)
            },
            onUpdateExercise = { index, transform ->
                val updated = current.exercises.toMutableList()
                updated[index] = transform(updated[index])
                days[selectedDay] = current.copy(exercises = updated)
            },
            onRemoveExercise = { index ->
                val updated = current.exercises.toMutableList()
                updated.removeAt(index)
                days[selectedDay] = current.copy(exercises = updated)
            },
            onOpenPicker = { pickerOpenForDay = selectedDay },
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.height(12.dp))

        BigCta(
            label = "Lock it in",
            enabled = days.any { it.exercises.isNotEmpty() },
            onClick = {
                scope.launch {
                    onCommit(nameField.text, days.toList())
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        GhostCta(label = "Back", onClick = onBack)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BorderedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DayCountStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepperButton(label = "−", enabled = value > MIN_DAYS) { onChange(value - 1) }
        Text(
            text = "$value days",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        StepperButton(label = "+", enabled = value < MAX_DAYS) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape,
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayEditor(
    dayIndex: Int,
    day: DayDraft,
    library: List<Exercise>,
    onRenameDay: (String) -> Unit,
    onUpdateExercise: (Int, (ExerciseDraft) -> ExerciseDraft) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keying on dayIndex (not the data class) keeps the cursor stable during typing —
    // the day object is replaced each keystroke via copy(), but the index is stable.
    var nameField by remember(dayIndex) { mutableStateOf(TextFieldValue(day.name)) }
    val byId = remember(library) { library.associateBy { it.id } }

    Column(modifier = modifier) {
        FieldLabel("Day name")
        Spacer(Modifier.height(6.dp))
        BorderedTextField(
            value = nameField,
            onValueChange = {
                nameField = it
                onRenameDay(it.text)
            },
            placeholder = "e.g. Push A",
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        ) {
            items(
                items = day.exercises.withIndex().toList(),
                key = { (idx, ex) -> "${idx}_${ex.exerciseId}" },
            ) { (idx, ex) ->
                val exMeta = byId[ex.exerciseId]
                ExerciseRow(
                    name = exMeta?.name ?: ex.exerciseId,
                    muscle = exMeta?.primaryMuscle ?: "—",
                    sets = ex.sets,
                    repsLow = ex.repsLow,
                    repsHigh = ex.repsHigh,
                    onSetsChange = { delta ->
                        onUpdateExercise(idx) { it.copy(sets = (it.sets + delta).coerceIn(1, 10)) }
                    },
                    onRepsLowChange = { delta ->
                        onUpdateExercise(idx) {
                            val newLow = (it.repsLow + delta).coerceIn(1, it.repsHigh)
                            it.copy(repsLow = newLow)
                        }
                    },
                    onRepsHighChange = { delta ->
                        onUpdateExercise(idx) {
                            val newHigh = (it.repsHigh + delta).coerceIn(it.repsLow, 30)
                            it.copy(repsHigh = newHigh)
                        }
                    },
                    onRemove = { onRemoveExercise(idx) },
                )
            }
            item(key = "add_exercise") {
                AddExerciseButton(onClick = onOpenPicker)
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    name: String,
    muscle: String,
    sets: Int,
    repsLow: Int,
    repsHigh: Int,
    onSetsChange: (Int) -> Unit,
    onRepsLowChange: (Int) -> Unit,
    onRepsHighChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = muscle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Remove",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrescriptionStepper(
                label = "Sets",
                value = "$sets",
                onMinus = { onSetsChange(-1) },
                onPlus = { onSetsChange(+1) },
                modifier = Modifier.weight(1f),
            )
            PrescriptionStepper(
                label = "Rep low",
                value = "$repsLow",
                onMinus = { onRepsLowChange(-1) },
                onPlus = { onRepsLowChange(+1) },
                modifier = Modifier.weight(1f),
            )
            PrescriptionStepper(
                label = "Rep high",
                value = "$repsHigh",
                onMinus = { onRepsHighChange(-1) },
                onPlus = { onRepsHighChange(+1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PrescriptionStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniStepperButton("−", onMinus)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            MiniStepperButton("+", onPlus)
        }
    }
}

@Composable
private fun MiniStepperButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
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
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ExercisePickerSheet(
    library: List<Exercise>,
    alreadyAdded: Set<String>,
    onPick: (Exercise) -> Unit,
    onCreateCustom: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val filtered = remember(library, query.text) {
        val q = query.text.trim().lowercase()
        if (q.isEmpty()) library
        else library.filter {
            it.name.lowercase().contains(q) || it.primaryMuscle.lowercase().contains(q)
        }
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
        BorderedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search by name or muscle",
        )
        Spacer(Modifier.height(10.dp))
        AddExerciseButton(onClick = onCreateCustom, label = "+ CREATE CUSTOM EXERCISE")
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Group alphabetically by primary muscle for a stable, predictable order.
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
                    PickerRow(
                        exercise = ex,
                        alreadyAdded = ex.id in alreadyAdded,
                        onPick = onPick,
                    )
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
private fun PickerRow(exercise: Exercise, alreadyAdded: Boolean, onPick: (Exercise) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { onPick(exercise) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
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
