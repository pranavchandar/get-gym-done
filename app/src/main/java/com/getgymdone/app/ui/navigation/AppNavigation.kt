package com.getgymdone.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.ui.screens.MainShell
import com.getgymdone.app.ui.screens.day.DayOverviewScreen
import com.getgymdone.app.ui.screens.debug.DebugDataScreen
import com.getgymdone.app.ui.screens.onboarding.CustomizeRoutineScreen
import com.getgymdone.app.ui.screens.onboarding.PickSplitScreen
import com.getgymdone.app.ui.screens.onboarding.RoutineMethodScreen
import com.getgymdone.app.ui.screens.onboarding.SplashScreen
import com.getgymdone.app.ui.screens.workout.ActiveWorkoutScreen
import com.getgymdone.app.ui.screens.workout.WorkoutCompleteScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    prefs: UserPrefsRepository,
) : ViewModel() {
    val onboardingComplete: StateFlow<Boolean?> = prefs.observe()
        .map { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun AppNavigation() {
    val startVm: StartDestinationViewModel = hiltViewModel()
    val onboardingComplete by startVm.onboardingComplete.collectAsState()

    // Hold off rendering until prefs load: NavHost can't change its start route after first
    // composition, so a null (still-loading) flag must resolve before we pick a start. Once
    // onboarding is complete the splash is bypassed and we open straight on Home; the splash
    // only ever shows on the first run, as part of onboarding.
    val complete = onboardingComplete ?: return

    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (complete) Route.Home else Route.Splash,
    ) {
        composable<Route.Splash> {
            SplashScreen(
                onContinue = {
                    nav.navigate(Route.PickSplit) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onSkipToHome = {
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                isOnboardingComplete = onboardingComplete == true,
            )
        }

        composable<Route.PickSplit> {
            PickSplitScreen(
                onPicked = { splitId -> nav.navigate(Route.RoutineMethod(splitId)) },
                onPickCustom = { nav.navigate(Route.CustomizeRoutine(seedSplitId = null)) },
                onUseExisting = {
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onBack = { if (!nav.popBackStack()) nav.navigate(Route.Splash) },
            )
        }

        composable<Route.RoutineMethod> { entry ->
            val args: Route.RoutineMethod = entry.toRoute()
            RoutineMethodScreen(
                splitId = args.splitId,
                onCurate = {
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onBuildMyOwn = { nav.navigate(Route.CustomizeRoutine(seedSplitId = args.splitId)) },
            )
        }

        composable<Route.CustomizeRoutine> { entry ->
            val args: Route.CustomizeRoutine = entry.toRoute()
            CustomizeRoutineScreen(
                seedSplitId = args.seedSplitId,
                onDone = {
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable<Route.Home> {
            MainShell(
                onOpenDay = { dayId -> nav.navigate(Route.DayOverview(dayId)) },
                onStartToday = { dayId -> nav.navigate(Route.ActiveWorkout(dayId)) },
                onResetRoutine = {
                    // Clear Home from the stack so PickSplit becomes the new root. The completion
                    // path (RoutineMethod → Home) then rebuilds the tab shell cleanly.
                    nav.navigate(Route.PickSplit) {
                        popUpTo(Route.Home) { inclusive = true }
                    }
                },
                onOpenDebug = { nav.navigate(Route.DebugData) },
            )
        }

        composable<Route.DayOverview> { entry ->
            val args: Route.DayOverview = entry.toRoute()
            DayOverviewScreen(
                workoutDayId = args.workoutDayId,
                onStart = { dayId -> nav.navigate(Route.ActiveWorkout(dayId)) },
                onBack = { nav.popBackStack() },
            )
        }

        composable<Route.ActiveWorkout> { entry ->
            val args: Route.ActiveWorkout = entry.toRoute()
            ActiveWorkoutScreen(
                workoutDayId = args.workoutDayId,
                onComplete = { sessionId ->
                    nav.navigate(Route.WorkoutComplete(sessionId)) {
                        popUpTo(Route.Home)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable<Route.WorkoutComplete> { entry ->
            val args: Route.WorkoutComplete = entry.toRoute()
            WorkoutCompleteScreen(
                sessionId = args.sessionId,
                onDone = {
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Home) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.DebugData> {
            DebugDataScreen(onBack = { nav.popBackStack() })
        }
    }
}
