package com.getgymdone.app.ui.screens.profile

import androidx.compose.runtime.Composable
import com.getgymdone.app.ui.screens.PlaceholderScreen

@Composable
fun ProfileScreen(onBack: () -> Unit) =
    PlaceholderScreen(
        title = "Profile",
        note = "Progression chart per exercise, bodyweight, consistency heatmap, recent PRs. Vico charts wired in Week 6.",
        onBack = onBack,
    )
