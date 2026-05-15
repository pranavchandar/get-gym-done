package com.getgymdone.app.ui.screens.workouts

import androidx.compose.runtime.Composable
import com.getgymdone.app.ui.screens.PlaceholderScreen

@Composable
fun WorkoutsListScreen(onBack: () -> Unit) =
    PlaceholderScreen(
        title = "Workouts",
        note = "Lists all days of the active split with completion counts and exercise summaries. Lands in Week 5.",
        onBack = onBack,
    )
