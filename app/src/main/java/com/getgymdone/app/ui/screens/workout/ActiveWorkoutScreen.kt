package com.getgymdone.app.ui.screens.workout

import androidx.compose.runtime.Composable
import com.getgymdone.app.ui.screens.PlaceholderScreen

@Composable
fun ActiveWorkoutScreen(
    workoutDayId: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
) = PlaceholderScreen(
    title = "Active workout",
    note = "The hero screen — Stacked layout per spec. Lands in Week 4: GIF placeholder, name, muscle map, cues, set rows, big Complete-Set CTA, rest timer.",
    onBack = onBack,
)
