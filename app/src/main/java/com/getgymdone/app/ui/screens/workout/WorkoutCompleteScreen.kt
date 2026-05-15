package com.getgymdone.app.ui.screens.workout

import androidx.compose.runtime.Composable
import com.getgymdone.app.ui.screens.PlaceholderScreen

@Composable
fun WorkoutCompleteScreen(
    sessionId: String,
    onDone: () -> Unit,
) = PlaceholderScreen(
    title = "Complete",
    note = "Celebration screen with PR + volume delta callouts. Lands once the active workout screen logs real sets.",
    onBack = onDone,
)
