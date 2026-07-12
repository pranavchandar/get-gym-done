package com.getgymdone.app.ui.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.os.Build
import com.getgymdone.app.notifications.RestTimerScheduler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.getgymdone.app.ui.components.MuscleMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import android.net.Uri
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.ExerciseMedia
import com.getgymdone.app.data.repository.ExerciseMediaRepository
import com.getgymdone.app.data.repository.ExerciseRepository
import com.getgymdone.app.data.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import com.getgymdone.app.data.repository.SplitRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.domain.WeightSuggestion
import com.getgymdone.app.domain.WeightUnit
import com.getgymdone.app.domain.displayStep
import com.getgymdone.app.domain.displayToKg
import com.getgymdone.app.domain.kgToDisplay
import com.getgymdone.app.domain.weightIncreaseSuggestion
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

/** Fallback starting load (kg) for a set with no prior history to prefill from. */
private const val DEFAULT_START_WEIGHT_KG = 20.0

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
    // Added on the fly during this workout; never written back to the split's prescription, so it
    // won't reappear next time.
    val isTemporary: Boolean = false,
    // Progressive-overload nudge derived from past completed sessions; null when nothing has stalled.
    val weightSuggestion: WeightSuggestion? = null,
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
    // Totals track the actual number of set rows (which can grow when the user adds a set),
    // not just the original prescription, so progress stays accurate.
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
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
    private val mediaRepo: ExerciseMediaRepository,
) : ViewModel() {

    /** User-uploaded images/GIFs for an exercise (local only). */
    fun media(exerciseId: String): Flow<List<ExerciseMedia>> = mediaRepo.observe(exerciseId)

    fun addMedia(exerciseId: String, uri: Uri) = viewModelScope.launch { mediaRepo.add(exerciseId, uri) }

    fun removeMedia(mediaId: String) = viewModelScope.launch { mediaRepo.remove(mediaId) }

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

            // Resume an in-progress session for this day if one exists — every set is written to
            // the DB the moment it's logged, so a kill mid-workout leaves a session we can pick
            // back up. Sets already logged are restored as done; the rest start fresh.
            val existing = sessions.getInProgressForDay(workoutDayId)
            val session = existing ?: sessions.startSession(workoutDayId)
            val loggedByExercise = existing
                ?.let { sessions.getSetsForSession(it.id) }
                ?.groupBy { it.exerciseId }
                .orEmpty()

            // Smallest natural plate jump in the user's unit, for progressive-overload advice.
            val incrementKg = unit.displayStep.displayToKg(unit)

            val active = items.mapNotNull { de ->
                val ex = exercises.getById(de.exerciseId) ?: return@mapNotNull null
                val logged = loggedByExercise[de.exerciseId]?.associateBy { it.setNumber }.orEmpty()
                // Per-set memory: each set prefills with what was logged for that same set number
                // last time. Extra sets beyond last time's count fall back to the final set's
                // weight; a brand-new exercise falls back to a sane starting load.
                val prevSets = sessions.getLastSessionSets(de.exerciseId)
                val prevByNumber = prevSets.associateBy { it.setNumber }
                val prevFallback = prevSets.lastOrNull()
                // Rebuild at least the prescribed sets, but also any extra sets the user logged
                // beyond the prescription before the app was killed.
                val setCount = maxOf(de.prescribedSets, logged.keys.maxOrNull() ?: 0)
                val suggestion = weightIncreaseSuggestion(
                    history = sessions.getCompletedProgression(de.exerciseId),
                    repsHigh = de.prescribedRepsHigh,
                    incrementKg = incrementKg,
                )
                ActiveExercise(
                    exercise = ex,
                    prescribedSets = de.prescribedSets,
                    repsLow = de.prescribedRepsLow,
                    repsHigh = de.prescribedRepsHigh,
                    sets = List(setCount) { idx ->
                        val saved = logged[idx + 1]
                        if (saved != null) {
                            SetEntry(weightKg = saved.weightKg, reps = saved.reps, done = true, logId = saved.id)
                        } else {
                            val prev = prevByNumber[idx + 1] ?: prevFallback
                            SetEntry(
                                weightKg = prev?.weightKg ?: DEFAULT_START_WEIGHT_KG,
                                reps = prev?.reps ?: de.prescribedRepsLow,
                            )
                        }
                    },
                    weightSuggestion = suggestion,
                )
            }
            // Jump to the first exercise that still has unlogged sets so a resumed workout opens
            // where the user left off.
            val resumeIndex = active.indexOfFirst { ex -> ex.sets.any { !it.done } }
                .takeIf { it >= 0 } ?: 0

            _state.value = ActiveWorkoutState(
                loading = false,
                dayName = day?.name.orEmpty(),
                dayNumber = day?.dayNumber ?: 0,
                unit = unit,
                sessionId = session.id,
                exercises = active,
                currentIndex = resumeIndex,
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

    /** Apply a weight (display units) to every not-yet-logged set of the current exercise. */
    fun applyWeightToActiveExercise(display: Double) {
        _state.update { st ->
            val exIdx = st.currentIndex
            val ex = st.exercises.getOrNull(exIdx) ?: return@update st
            val kg = display.displayToKg(st.unit).coerceAtLeast(0.0)
            val newSets = ex.sets.map { if (it.done) it else it.copy(weightKg = kg) }
            val newEx = st.exercises.toMutableList().also { it[exIdx] = ex.copy(sets = newSets) }
            st.copy(exercises = newEx)
        }
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

    /**
     * Drop a single set from the current exercise. If it was already logged, its DB row is deleted so
     * history stays clean. Removing every set leaves the exercise with none, which the UI reads as
     * "done" so the Next exercise / Finish button appears.
     */
    fun removeSet(index: Int) {
        val st = _state.value
        val exIdx = st.currentIndex
        val ex = st.exercises.getOrNull(exIdx) ?: return
        val set = ex.sets.getOrNull(index) ?: return
        viewModelScope.launch {
            set.logId?.let { sessions.deleteSet(it) }
            _state.update { s ->
                val e = s.exercises.getOrNull(exIdx) ?: return@update s
                val newSets = e.sets.toMutableList().also { it.removeAt(index) }
                val newEx = s.exercises.toMutableList().also { it[exIdx] = e.copy(sets = newSets) }
                s.copy(exercises = newEx)
            }
        }
    }

    /** Append an extra set to the current exercise, prefilled from its last set. */
    fun addSet() {
        _state.update { st ->
            val exIdx = st.currentIndex
            val ex = st.exercises.getOrNull(exIdx) ?: return@update st
            val template = ex.sets.lastOrNull()
            val newSet = SetEntry(
                weightKg = template?.weightKg ?: DEFAULT_START_WEIGHT_KG,
                reps = template?.reps ?: ex.repsLow,
            )
            val newEx = st.exercises.toMutableList().also { it[exIdx] = ex.copy(sets = ex.sets + newSet) }
            st.copy(exercises = newEx)
        }
    }

    /** Exercise library for the "add exercise" picker. */
    fun exerciseLibrary(): Flow<List<Exercise>> = exercises.observeAll()

    /**
     * Append an exercise to this workout only. Its sets prefill from last-session memory like the
     * prescribed ones, but it's flagged temporary and never saved to the split, so it's gone next
     * time. No-op if the exercise is already in the workout (logging is keyed by exercise + set
     * number, so duplicates would collide).
     */
    fun addTemporaryExercise(exercise: Exercise) {
        if (_state.value.exercises.any { it.exercise.id == exercise.id }) return
        viewModelScope.launch {
            val newEx = buildActiveExercise(exercise)
            _state.update { st ->
                val list = st.exercises + newEx
                st.copy(exercises = list, currentIndex = list.lastIndex)
            }
        }
    }

    /**
     * Swap the exercise at [index] for [exercise], keeping its position in the workout. Sets already
     * logged for the old exercise are deleted (like [removeExercise]); the replacement is per-session
     * only and never touches the split's prescription. No-op if the new exercise is already elsewhere
     * in the workout (logging is keyed by exercise + set number, so duplicates would collide).
     */
    fun replaceExercise(index: Int, exercise: Exercise) {
        val old = _state.value.exercises.getOrNull(index) ?: return
        if (old.exercise.id == exercise.id) return
        if (_state.value.exercises.any { it.exercise.id == exercise.id }) return
        viewModelScope.launch {
            old.sets.mapNotNull { it.logId }.forEach { sessions.deleteSet(it) }
            val newEx = buildActiveExercise(exercise)
            _state.update { st ->
                val list = st.exercises.toMutableList().also { if (index in it.indices) it[index] = newEx }
                st.copy(exercises = list)
            }
        }
    }

    /** Build a fresh per-session [ActiveExercise], prefilling sets from last-session memory. */
    private suspend fun buildActiveExercise(exercise: Exercise): ActiveExercise {
        val prevSets = sessions.getLastSessionSets(exercise.id)
        val prevByNumber = prevSets.associateBy { it.setNumber }
        val prevFallback = prevSets.lastOrNull()
        val setCount = exercise.defaultSets.coerceAtLeast(1)
        val unit = _state.value.unit
        val incrementKg = unit.displayStep.displayToKg(unit)
        val suggestion = weightIncreaseSuggestion(
            history = sessions.getCompletedProgression(exercise.id),
            repsHigh = exercise.defaultRepsHigh,
            incrementKg = incrementKg,
        )
        return ActiveExercise(
            exercise = exercise,
            prescribedSets = exercise.defaultSets,
            repsLow = exercise.defaultRepsLow,
            repsHigh = exercise.defaultRepsHigh,
            sets = List(setCount) { idx ->
                val prev = prevByNumber[idx + 1] ?: prevFallback
                SetEntry(
                    weightKg = prev?.weightKg ?: DEFAULT_START_WEIGHT_KG,
                    reps = prev?.reps ?: exercise.defaultRepsLow,
                )
            },
            isTemporary = true,
            weightSuggestion = suggestion,
        )
    }

    /**
     * Drop an exercise from this workout. Any sets already logged for it in this session are deleted
     * so history stays clean; the split's prescription is untouched, so this is a per-session skip.
     */
    fun removeExercise(index: Int) {
        val ex = _state.value.exercises.getOrNull(index) ?: return
        viewModelScope.launch {
            ex.sets.mapNotNull { it.logId }.forEach { sessions.deleteSet(it) }
            _state.update { st ->
                val list = st.exercises.toMutableList().also { it.removeAt(index) }
                st.copy(
                    exercises = list,
                    currentIndex = st.currentIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0)),
                )
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

    val context = LocalContext.current
    var keypadTarget by remember { mutableStateOf<KeypadField?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var replaceMode by remember { mutableStateOf(false) }

    // Local image/GIF upload for the current exercise. Picked items are copied into app storage;
    // nothing is sent to the cloud. The target exercise is captured at launch time.
    var mediaTargetId by remember { mutableStateOf<String?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        mediaTargetId?.let { id -> uris.forEach { vm.addMedia(id, it) } }
    }

    // Rest timer is anchored to a wall-clock end time rather than a tick counter, so it stays
    // correct after the OS freezes the app in the background. [now] is refreshed on a loop only to
    // animate the dial; the remaining seconds are always derived from (endAt − now).
    var restEndAt by remember { mutableStateOf<Long?>(null) }
    var restTotal by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val restRemaining = restEndAt?.let { (((it - now) + 999) / 1000).coerceAtLeast(0).toInt() } ?: 0

    val startRest: (Int) -> Unit = { seconds ->
        val end = System.currentTimeMillis() + seconds * 1000L
        restTotal = seconds
        now = System.currentTimeMillis()
        restEndAt = end
        RestTimerScheduler.schedule(context, end)
    }
    val stopRest: () -> Unit = {
        restEndAt = null
        restTotal = 0
        RestTimerScheduler.cancel(context)
    }
    // Shift the running timer by ±delta and reschedule the background alert.
    val adjustRest: (Int) -> Unit = { delta ->
        val newRemaining = (restRemaining + delta).coerceAtLeast(0)
        if (newRemaining <= 0) {
            stopRest()
        } else {
            val end = System.currentTimeMillis() + newRemaining * 1000L
            restTotal = (restTotal + delta).coerceAtLeast(newRemaining)
            now = System.currentTimeMillis()
            restEndAt = end
            RestTimerScheduler.schedule(context, end)
        }
    }

    // Ask for notification permission (Android 13+) so the background rest alert can be posted.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Drive the on-screen dial from wall-clock time; clears itself (and the alarm) when rest ends.
    LaunchedEffect(restEndAt) {
        val end = restEndAt ?: return@LaunchedEffect
        while (System.currentTimeMillis() < end) {
            now = System.currentTimeMillis()
            delay(200)
        }
        now = System.currentTimeMillis()
        restEndAt = null
        restTotal = 0
        RestTimerScheduler.cancel(context)
    }
    // Leaving the workout screen cancels any pending rest alert.
    DisposableEffect(Unit) {
        onDispose { RestTimerScheduler.cancel(context) }
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
                onAddExercise = {
                    replaceMode = false
                    pickerOpen = true
                },
                onReplaceExercise = {
                    replaceMode = true
                    pickerOpen = true
                },
                onRemoveExercise = {
                    val name = current.exercise.name
                    vm.removeExercise(state.currentIndex)
                    toast = "Removed $name"
                },
                canRemove = state.exercises.size > 1,
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
                item("hero") {
                    val mediaFlow = remember(current.exercise.id) { vm.media(current.exercise.id) }
                    val mediaList by mediaFlow.collectAsState(initial = emptyList())
                    ExerciseHero(
                        ex = current,
                        index = state.currentIndex,
                        media = mediaList,
                        onAddMedia = {
                            mediaTargetId = current.exercise.id
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRemoveMedia = { vm.removeMedia(it) },
                    )
                }
                item("targets") { TargetsCard(current.exercise) }
                if (current.exercise.formCues.isNotEmpty()) {
                    item("cues") { FormCuesCard(current.exercise.formCues) }
                }
                current.weightSuggestion?.let { sug ->
                    // Hide once the remaining sets have been bumped to (or past) the suggested load.
                    val needed = current.sets.any { !it.done && it.weightKg < sug.suggestedWeightKg - 1e-3 }
                    if (needed) {
                        item("suggestion") {
                            WeightSuggestionCard(
                                suggestion = sug,
                                unit = state.unit,
                                onApply = { vm.applyWeightToActiveExercise(sug.suggestedWeightKg.kgToDisplay(state.unit)) },
                            )
                        }
                    }
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
                        onRemove = { vm.removeSet(idx) },
                    )
                }
                if (current.sets.all { it.done }) {
                    item("add-set") { AddSetButton(onClick = vm::addSet) }
                }
            }

            BottomBar(
                state = state,
                onCompleteSet = {
                    val setNo = state.activeSetIndex + 1
                    vm.completeActiveSet()
                    if (!(state.isLastExercise && state.activeSetIndex == current.sets.lastIndex)) {
                        startRest(state.restDurationSeconds)
                        toast = "Set $setNo logged · ${state.restDurationSeconds}s rest"
                    } else {
                        toast = "Set $setNo logged"
                    }
                },
                onNextExercise = {
                    stopRest()
                    vm.nextExercise()
                },
                onFinish = { vm.finish(onComplete) },
                onUndo = vm::undoLastSet,
            )
        }

        if (pickerOpen) {
            val libraryFlow = remember { vm.exerciseLibrary() }
            val library by libraryFlow.collectAsState(initial = emptyList())
            AddExercisePicker(
                replace = replaceMode,
                library = library,
                alreadyInWorkout = state.exercises.mapTo(HashSet()) { it.exercise.id },
                onPick = { ex ->
                    if (replaceMode) {
                        val old = current.exercise.name
                        vm.replaceExercise(state.currentIndex, ex)
                        toast = "Replaced $old with ${ex.name}"
                    } else {
                        vm.addTemporaryExercise(ex)
                        toast = "Added ${ex.name}"
                    }
                    pickerOpen = false
                },
                onClose = { pickerOpen = false },
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
            visible = restEndAt != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
        ) {
            RestTimerOverlay(
                remaining = restRemaining,
                total = restTotal,
                onMinus = {
                    vm.adjustRestDuration(-REST_STEP_SECONDS)
                    adjustRest(-REST_STEP_SECONDS)
                },
                onPlus = {
                    vm.adjustRestDuration(REST_STEP_SECONDS)
                    adjustRest(REST_STEP_SECONDS)
                },
                onSkip = stopRest,
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
private fun TopBar(
    title: String,
    position: String,
    onClose: () -> Unit,
    onAddExercise: () -> Unit,
    onReplaceExercise: () -> Unit,
    onRemoveExercise: () -> Unit,
    canRemove: Boolean,
) {
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
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Add exercise") },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onAddExercise()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Replace exercise") },
                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onReplaceExercise()
                    },
                )
                if (canRemove) {
                    DropdownMenuItem(
                        text = { Text("Remove this exercise") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemoveExercise()
                        },
                    )
                }
            }
        }
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
private fun ExerciseHero(
    ex: ActiveExercise,
    index: Int,
    media: List<ExerciseMedia>,
    onAddMedia: () -> Unit,
    onRemoveMedia: (String) -> Unit,
) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    Column {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f)
                .clip(shape),
        ) {
            if (media.isEmpty()) {
                StripedPlaceholder(
                    label = "add photos / GIFs",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val pagerState = rememberPagerState(pageCount = { media.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = File(media[page].filePath),
                        contentDescription = "Exercise media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewerIndex = page },
                    )
                }
                // Page indicator dots.
                if (media.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(media.size) { i ->
                            val active = i == pagerState.currentPage
                            Box(
                                Modifier
                                    .size(if (active) 8.dp else 6.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                    ),
                            )
                        }
                    }
                }
                // Delete the currently-shown item.
                HeroCircleButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Remove photo",
                    onClick = { media.getOrNull(pagerState.currentPage)?.let { onRemoveMedia(it.id) } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
            // Add more media.
            HeroCircleButton(
                icon = Icons.Rounded.AddPhotoAlternate,
                contentDescription = "Add photo or GIF",
                onClick = onAddMedia,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
        viewerIndex?.let { idx ->
            media.getOrNull(idx)?.let { item ->
                Dialog(
                    onDismissRequest = { viewerIndex = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .clickable { viewerIndex = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = File(item.filePath),
                            contentDescription = "Exercise media",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        HeroCircleButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Close",
                            onClick = { viewerIndex = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (ex.isTemporary) "EXERCISE ${index + 1} · ADDED" else "EXERCISE ${index + 1}",
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

@Composable
private fun HeroCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
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

// ── Progressive-overload nudge ───────────────────────────────────────────────

@Composable
private fun WeightSuggestionCard(
    suggestion: WeightSuggestion,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val current = formatWeight(suggestion.currentWeightKg.kgToDisplay(unit))
    val next = formatWeight(suggestion.suggestedWeightKg.kgToDisplay(unit))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("TIME TO ADD WEIGHT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "You hit $current ${unit.label} × ${suggestion.reps} the last 2 sessions — try $next ${unit.label}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                .clickable(onClick = onApply)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("BUMP TO $next ${unit.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
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
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        set.done -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    val bg = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    val weightStep = unit.displayStep

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
            Spacer(Modifier.size(10.dp))
            Icon(
                Icons.Rounded.RemoveCircleOutline,
                contentDescription = "Remove set",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .clickable(onClick = onRemove),
            )
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
private fun AddSetButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+ ADD SET",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
    // The prefilled value is shown but treated as a placeholder: the first key press (or quick-add)
    // clears it and starts fresh, so you don't have to backspace the old number first.
    var pristine by remember { mutableStateOf(true) }
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
                        pristine = false
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
                            buffer = applyKey(if (pristine) "" else buffer, key, allowDecimal)
                            pristine = false
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

// ── Add-exercise picker (temporary, this workout only) ───────────────────────

@Composable
private fun AddExercisePicker(
    library: List<Exercise>,
    alreadyInWorkout: Set<String>,
    onPick: (Exercise) -> Unit,
    onClose: () -> Unit,
    replace: Boolean = false,
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
            .padding(horizontal = 18.dp)
            .padding(top = 48.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (replace) "REPLACE EXERCISE" else "ADD EXERCISE", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }
        Text(
            if (replace) "Swaps the current exercise for this workout — won't change your routine."
            else "Just for this workout — won't change your routine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SearchField(value = query, onValueChange = { query = it })
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val grouped = filtered.groupBy { it.primaryMuscle }.toSortedMap()
            grouped.forEach { (muscle, exs) ->
                item(key = "h_$muscle") {
                    Text(
                        muscle.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items = exs, key = { it.id }) { ex ->
                    PickerRow(ex, ex.id in alreadyInWorkout) { onPick(ex) }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No exercises match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(exercise: Exercise, alreadyInWorkout: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = !alreadyInWorkout, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (alreadyInWorkout) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${exercise.equipment} · ${exercise.defaultSets}×${exercise.defaultRepsLow}-${exercise.defaultRepsHigh}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (alreadyInWorkout) {
            Text("IN WORKOUT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                "Search by name or muscle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
