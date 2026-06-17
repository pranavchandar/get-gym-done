package com.getgymdone.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.getgymdone.app.ui.components.BottomTabBar
import com.getgymdone.app.ui.components.MainTab
import com.getgymdone.app.ui.screens.home.HomeScreen
import com.getgymdone.app.ui.screens.profile.ProfileScreen
import com.getgymdone.app.ui.screens.settings.SettingsScreen
import com.getgymdone.app.ui.screens.workouts.WorkoutsListScreen

/**
 * The persistent tabbed home. Today / Workouts / Profile / Settings live here behind a single
 * bottom bar; deeper screens (day overview, active workout, complete) are pushed on top by the
 * NavHost and hide the bar. Tab selection survives config changes but resets on process death,
 * which is the right default — you always want to land on Today.
 */
@Composable
fun MainShell(
    onOpenDay: (String) -> Unit,
    onStartToday: (String) -> Unit,
    onResetRoutine: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenSocial: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Today) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomTabBar(selected = tab, onSelect = { tab = it }) },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = inner.calculateBottomPadding()),
        ) {
            Crossfade(targetState = tab, animationSpec = tween(200), label = "tab") { current ->
                when (current) {
                    MainTab.Today -> HomeScreen(
                        onOpenDay = onOpenDay,
                        onStartToday = onStartToday,
                        onResetRoutine = onResetRoutine,
                    )
                    MainTab.Workouts -> WorkoutsListScreen(onOpenDay = onOpenDay)
                    MainTab.Profile -> ProfileScreen()
                    MainTab.Settings -> SettingsScreen(
                        onResetRoutine = onResetRoutine,
                        onOpenDebug = onOpenDebug,
                        onOpenSocial = onOpenSocial,
                    )
                }
            }
        }
    }
}
