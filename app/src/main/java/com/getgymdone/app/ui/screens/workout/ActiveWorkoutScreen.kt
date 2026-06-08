package com.getgymdone.app.ui.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.getgymdone.app.ui.components.MuscleMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.data.repository.SessionRepository
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.WeightUnit
import com.getgymdone.app.domain.displayToKg
import com.getgymdone.app.domain.kgToDisplay
import com.getgymdone.app.ui.components.StripedPlaceholder
import com.getgymdone.app.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val REST_MIN_SECONDS = 30
private const val REST_MAX_SECONDS = 600
private const val REST_STEP_SECONDS = 30

data class SetEntry(
    val weightKg: Double,
    val reps: Int,
    val done: Boolean = false,
    val logId: String? = null,
)

data class ActiveExercise(
    val exercise: Exercise,
    val prescribedSets: Int,
    val repsLow: Int,
    val repsHigh: Int,
    val sets: List<SetEntry>,
)

data class ActiveWorkoutState(
    val loading: Boolean = true,
    val dayName: String = "",
    val dayNumber: Int = 0,
    val unit: WeightUnit = WeightUnit.Kg,
    val sessionId: String? = null,
    val exercises: List<ActiveExercise> = emptyList(),
    val currentIndex: Int = 0,
    val isRestDay: Boolean = false,
    val restDurationSeconds: Int = 90,
) {
    val current: ActiveExercise? get() = exercises.getOrNull(currentIndex)
    val totalSets: Int get() = exercises.sumOf { it.prescribedSets }
    val doneSets: Int get() = exercises.sumOf { ex -> ex.sets.count { it.done } }
    val isLastExercise: Boolean get() = currentIndex == exercises.lastIndex
    /** Index of the next set to log in the current exercise, or -1 when the exercise is done. */
    val activeSetIndex: Int get() = current?.sets?.indexOfFirst { !it.done } ?: -1
    val allDone: Boolean get() = doneSets >= totalSets && totalSets > 0
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val splits: SplitRepository,
    private val exercises: ExerciseRepository,
    private val sessions: SessionRepository,
    private val prefs: UserPrefsRepository,
) : ViewModel() {

    private val workoutDayId = handle.toRoute<Route.ActiveWorkout>().workoutDayId
    private val _state = MutableStateFlow(ActiveWorkoutState())
    val state: StateFlow<ActiveWorkoutState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val day = splits.getDayById(workoutDayId)
            val p = prefs.get()
            val unit = WeightUnit.fromStored(p.units)
            val restDuration = p.restSeconds.coerceIn(REST_MIN_SECONDS, REST_MAX_SECONDS)
            if (day?.isRestDay == true) {
                // Rest days are not trainable — never open a session for them.
                _state.value = ActiveWorkoutState(
                    loading = false,
                    dayName = day.name,
                    dayNumber = day.dayNumber,
                    unit = unit,
                    isRestDay = true,
                    restDurationSeconds = restDuration,
                )
                return@launch
            }
            val items = splits.getDayExercises(workoutDayId).sortedBy { it.orderIndex }
            val active = items.mapNotNull { de ->
                val ex = exercises.getById(de.exerciseId) ?: return@mapNotNull null
                val last = sessions.getLastSet(de.exerciseId)
                val startWeight = last?.weightKg ?: 20.0
                val startReps = last?.reps ?: de.prescribedRepsLow
                ActiveExercise(
                    exercise = ex,
                    prescribedSets = de.prescribedSets,
                    repsLow = de.prescribedRepsLow,
                    repsHigh = de.prescribedRepsHigh,
                    sets = List(de.prescribedSets) { SetEntry(weightKg = startWeight, reps = startReps) },
                )
            }
            val session = sessions.startSession(workoutDayId)
            _state.value = ActiveWorkoutState(
                loading = false,
                dayName = day?.name.orEmpty(),
                dayNumber = day?.dayNumber ?: 0,
                unit = unit,
                sessionId = session.id,
                exercises = active,
                currentIndex = 0,
                restDurationSeconds = restDuration,
            )
        }
    }

    /** Adjust the rest-timer duration (±30s) and remember it for future workouts. */
    fun adjustRestDuration(deltaSeconds: Int) {
        val updated = (_state.value.restDurationSeconds + deltaSeconds)
            .coerceIn(REST_MIN_SECONDS, REST_MAX_SECONDS)
        if (updated == _state.value.restDurationSeconds) return
        _state.update { it.copy(restDurationSeconds = updated) }
        viewModelScope.launch { prefs.update { it.copy(restSeconds = updated) } }
    }

    private fun mutateActiveSet(transform: (SetEntry) -> SetEntry) {
        _state.update { st ->
            val exIdx = st.currentIndex
            val ex = st.exercises.getOrNull(exIdx) ?: return@update st
            val setIdx = ex.sets.indexOfFirst { !it.done }
            if (setIdx < 0) return@update st
            val newSets = ex.sets.toMutableList().also { it[setIdx] = transform(it[setIdx]) }
            val newEx = st.exercises.toMutableList().also { it[exIdx] = ex.copy(sets = newSets) }
            st.copy(exercises = newEx)
        }
    }

    fun adjustWeight(displayDelta: Double) = mutateActiveSet {
        it.copy(weightKg = (it.weightKg + displayDelta.displayToKg(currentUnit())).coerceAtLeast(0.0))
    }

    fun setWeightDisplay(display: Double) = mutateActiveSet {
        it.copy(weightKg = display.displayToKg(currentUnit()).coerceAtLeast(0.0))
    }

    fun adjustReps(delta: Int) = mutateActiveSet { it.copy(reps = (it.reps + delta).coerceIn(0, 99)) }
    fun setReps(reps: Int) = mutateActiveSet { it.copy(reps = reps.coerceIn(0, 99)) }

    private fun currentUnit() = _state.value.unit

    fun completeActiveSet() {
        val st = _state.value
        val exIdx = st.currentIndex
        val ex = st.exercises.getOrNull(exIdx) ?: return
        val setIdx = ex.sets.indexOfFirst { !it.done }
        if (setIdx < 0) return
        val entry = ex.sets[setIdx]
        val sessionId = st.sessionId ?: return
        viewModelScope.launch {
            val logId = sessions.logSet(sessionId, ex.exercise.id, setIdx + 1, entry.weightKg, entry.reps)
            _state.update { s ->
                val e = s.exercises.getOrNull(exIdx) ?: return@update s
                val newSets = e.sets.toMutableList()
                    .also { it[setIdx] = entry.copy(done = true, logId = logId) }
                val newEx = s.exercises.toMutableList().also { it[exIdx] = e.copy(sets = newSets) }
                s.copy(exercises = newEx)
            }
        }
    }

    fun undoLastSet() {
        val st = _state.value
        val exIdx = st.currentIndex
        val ex = st.exercises.getOrNull(exIdx) ?: return
        val setIdx = ex.sets.indexOfLast { it.done }
        if (setIdx < 0) return
        val entry = ex.sets[setIdx]
        viewModelScope.launch {
            entry.logId?.let { sessions.deleteSet(it) }
            _state.update { s ->
                val e = s.exercises.getOrNull(exIdx) ?: return@update s
                val newSets = e.sets.toMutableList()
                    .also { it[setIdx] = entry.copy(done = false, logId = null) }
                val newEx = s.exercises.toMutableList().also { it[exIdx] = e.copy(sets = newSets) }
                s.copy(exercises = newEx)
            }
        }
    }

    fun nextExercise() {
        _state.update { if (it.currentIndex < it.exercises.lastIndex) it.copy(currentIndex = it.currentIndex + 1) else it }
    }

    fun goToExercise(index: Int) {
        _state.update { if (index in it.exercises.indices) it.copy(currentIndex = index) else it }
    }

    fun finish(onDone: (String) -> Unit) {
        val id = _state.value.sessionId ?: return
        viewModelScope.launch {
            sessions.completeSession(id)
            onDone(id)
        }
    }
}

@Composable
fun ActiveWorkoutScreen(
    workoutDayId: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ActiveWorkoutViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    var keypadTarget by remember { mutableStateOf<KeypadField?>(null) }
    var restSeconds by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Rest countdown — ticks once per second while > 0.
    LaunchedEffect(restSeconds) {
        if (restSeconds > 0) {
            delay(1000)
            restSeconds -= 1
        }
    }
    // Auto-dismiss the set-logged toast.
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("LOADING…", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val current = state.current
    if (current == null) {
        // Day has no exercises — let the user bail cleanly.
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (state.isRestDay) "REST DAY" else "NOTHING TO DO", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.isRestDay) "Recovery day — no training. Rest up." else "This day has no exercises.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            GhostButton("Back", onBack)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
        ) {
            TopBar(
                title = state.dayName,
                position = "${state.currentIndex + 1} / ${state.exercises.size}",
                onClose = onBack,
            )
            ProgressBar(fraction = if (state.totalSets == 0) 0f else state.doneSets.toFloat() / state.totalSets)
            ExerciseDots(
                exercises = state.exercises,
                current = state.currentIndex,
                onPick = vm::goToExercise,
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item("hero") { ExerciseHero(current, state.currentIndex) }
                item("targets") { TargetsCard(current.exercise) }
                if (current.exercise.formCues.isNotEmpty()) {
                    item("cues") { FormCuesCard(current.exercise.formCues) }
                }
                item("sets-head") { SetsHeader(current) }
                items(current.sets.withIndex().toList(), key = { it.index }) { (idx, set) ->
                    SetRow(
                        setNumber = idx + 1,
                        set = set,
                        unit = state.unit,
                        isActive = idx == state.activeSetIndex,
                        onWeightTap = { keypadTarget = KeypadField.Weight },
                        onRepsTap = { keypadTarget = KeypadField.Reps },
                        onWeightStep = vm::adjustWeight,
                        onRepsStep = vm::adjustReps,
                    )
                }
            }

            BottomBar(
                state = state,
                onCompleteSet = {
                    val setNo = state.activeSetIndex + 1
                    vm.completeActiveSet()
                    if (!(state.isLastExercise && state.activeSetIndex == current.sets.lastIndex)) {
                        restSeconds = state.restDurationSeconds
                        toast = "Set $setNo logged · ${state.restDurationSeconds}s rest"
                    } else {
                        toast = "Set $setNo logged"
                    }
                },
                onNextExercise = {
                    restSeconds = 0
                    vm.nextExercise()
                },
                onFinish = { vm.finish(onComplete) },
                onUndo = vm::undoLastSet,
            )
        }

        keypadTarget?.let { field ->
            val activeSet = current.sets.getOrNull(state.activeSetIndex)
            KeypadSheet(
                field = field,
                unit = state.unit,
                initial = when (field) {
                    KeypadField.Weight -> activeSet?.weightKg?.kgToDisplay(state.unit) ?: 0.0
                    KeypadField.Reps -> (activeSet?.reps ?: 0).toDouble()
                },
                onSave = { value ->
                    when (field) {
                        KeypadField.Weight -> vm.setWeightDisplay(value)
                        KeypadField.Reps -> vm.setReps(value.toInt())
                    }
                    keypadTarget = null
                },
                onDismiss = { keypadTarget = null },
            )
        }

        AnimatedVisibility(
            visible = restSeconds > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
        ) {
            RestTimerOverlay(
                remaining = restSeconds,
                total = state.restDurationSeconds,
                onMinus = {
                    vm.adjustRestDuration(-REST_STEP_SECONDS)
                    restSeconds = (restSeconds - REST_STEP_SECONDS).coerceAtLeast(0)
                },
                onPlus = {
                    vm.adjustRestDuration(REST_STEP_SECONDS)
                    restSeconds += REST_STEP_SECONDS
                },
                onSkip = { restSeconds = 0 },
            )
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) {
            Toast(text = toast.orEmpty())
        }
    }
}

@Composable
private fun Toast(text: String) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

// ── Chrome ─────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(title: String, position: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(position, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val animated by animateFloatAsState(fraction, animationSpec = tween(300), label = "progress")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ExerciseDots(exercises: List<ActiveExercise>, current: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        exercises.forEachIndexed { i, ex ->
            val done = ex.sets.isNotEmpty() && ex.sets.all { it.done }
            val color = when {
                done -> MaterialTheme.colorScheme.primary
                i == current -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(color)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { onPick(i) },
            )
        }
    }
}

@Composable
private fun ExerciseHero(ex: ActiveExercise, index: Int) {
    Column {
        StripedPlaceholder(
            label = "exercise gif · ${ex.exercise.illustrationFilename}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EXERCISE ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(ex.exercise.name.uppercase(), style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${ex.prescribedSets}×${ex.repsLow}-${ex.repsHigh}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("PRESCRIPTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetsCard(exercise: Exercise) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MuscleMap(active = listOf(exercise.primaryMuscle) + exercise.secondaryMuscles, width = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text("TARGETS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MuscleTag(exercise.primaryMuscle, solid = true)
                exercise.secondaryMuscles.forEach { MuscleTag(it, solid = false) }
            }
        }
    }
}

@Composable
private fun MuscleTag(muscle: String, solid: Boolean) {
    val shape = RoundedCornerShape(100.dp)
    val mod = if (solid) {
        Modifier.background(MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    }
    Text(
        muscle.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (solid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(shape).then(mod).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun FormCuesCard(cues: List<String>) {
    Column {
        Text("FORM CUES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(14.dp),
        ) {
            cues.forEachIndexed { i, cue ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("%02d".format(i + 1), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(cue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SetsHeader(ex: ActiveExercise) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("SETS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${ex.sets.count { it.done }} / ${ex.sets.size} done",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Set rows ───────────────────────────────────────────────────────────────

@Composable
private fun SetRow(
    setNumber: Int,
    set: SetEntry,
    unit: WeightUnit,
    isActive: Boolean,
    onWeightTap: () -> Unit,
    onRepsTap: () -> Unit,
    onWeightStep: (Double) -> Unit,
    onRepsStep: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        set.done -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    val bg = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    val weightStep = if (unit == WeightUnit.Kg) 2.5 else 5.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SET $setNumber",
                style = MaterialTheme.typography.titleMedium,
                color = if (set.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (set.done) {
                Text(
                    "${formatWeight(set.weightKg.kgToDisplay(unit))} ${unit.label} · ${set.reps} reps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            } else if (!isActive) {
                Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isActive) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueStepper(
                    label = unit.label,
                    value = formatWeight(set.weightKg.kgToDisplay(unit)),
                    onMinus = { onWeightStep(-weightStep) },
                    onPlus = { onWeightStep(weightStep) },
                    onTapValue = onWeightTap,
                    modifier = Modifier.weight(1f),
                )
                ValueStepper(
                    label = "reps",
                    value = "${set.reps}",
                    onMinus = { onRepsStep(-1) },
                    onPlus = { onRepsStep(1) },
                    onTapValue = onRepsTap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ValueStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onTapValue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", onMinus)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTapValue)
                    .padding(vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepButton("+", onPlus)
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ── Bottom bar (rest timer + CTA) ────────────────────────────────────────────

@Composable
private fun BottomBar(
    state: ActiveWorkoutState,
    onCompleteSet: () -> Unit,
    onNextExercise: () -> Unit,
    onFinish: () -> Unit,
    onUndo: () -> Unit,
) {
    val current = state.current ?: return
    val exerciseDone = state.activeSetIndex < 0
    val anyDoneInExercise = current.sets.any { it.done }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp, top = 6.dp),
    ) {
        if (anyDoneInExercise) {
            Text(
                "Undo last set",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onUndo)
                    .padding(8.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        when {
            !exerciseDone -> CtaButton("Complete set ${state.activeSetIndex + 1}", onCompleteSet)
            !state.isLastExercise -> CtaButton("Next exercise →", onNextExercise)
            else -> CtaButton("Finish workout", onFinish)
        }
    }
}

@Composable
private fun RestTimerOverlay(
    remaining: Int,
    total: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSkip: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RestAdjustButton("−30s", onMinus)
            CircularRestDial(remaining = remaining, total = total)
            RestAdjustButton("+30s", onPlus)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "SKIP",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CircularRestDial(remaining: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (remaining.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, animationSpec = tween(300), label = "rest")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawArc(
                color = progress,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text("REST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RestAdjustButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CtaButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ── Keypad ───────────────────────────────────────────────────────────────────

enum class KeypadField { Weight, Reps }

@Composable
private fun KeypadSheet(
    field: KeypadField,
    unit: WeightUnit,
    initial: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var buffer by remember {
        mutableStateOf(if (field == KeypadField.Reps) initial.toInt().toString() else formatWeight(initial))
    }
    val allowDecimal = field == KeypadField.Weight
    val quickAdds = if (field == KeypadField.Weight) {
        if (unit == WeightUnit.Kg) listOf(2.5, 5.0, 10.0) else listOf(5.0, 10.0, 25.0)
    } else listOf(1.0, 2.0, 5.0)

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
            Text(
                if (field == KeypadField.Weight) "WEIGHT (${unit.label})" else "REPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buffer.ifEmpty { "0" },
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickAdds.forEach { add ->
                    QuickAddChip("+${formatWeight(add)}") {
                        val base = buffer.toDoubleOrNull() ?: 0.0
                        buffer = if (allowDecimal) formatWeight(base + add) else (base + add).toInt().toString()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (allowDecimal) "." else "", "0", "⌫")
            keys.chunked(3).forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowKeys.forEach { key ->
                        KeypadKey(key, Modifier.weight(1f)) {
                            buffer = applyKey(buffer, key, allowDecimal)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            CtaButton("Save") { onSave(buffer.toDoubleOrNull() ?: 0.0) }
        }
    }
}

private fun applyKey(buffer: String, key: String, allowDecimal: Boolean): String = when (key) {
    "" -> buffer
    "⌫" -> buffer.dropLast(1)
    "." -> if (!allowDecimal || buffer.contains(".")) buffer else (buffer.ifEmpty { "0" }) + "."
    else -> {
        val next = (if (buffer == "0") "" else buffer) + key
        if (next.length > 6) buffer else next
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(if (label.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant, shape)
            .clickable(enabled = label.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun QuickAddChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
