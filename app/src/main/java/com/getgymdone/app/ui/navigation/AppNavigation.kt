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
import com.getgymdone.app.ui.screens.day.DayOverviewScreen
import com.getgymdone.app.ui.screens.debug.DebugDataScreen
import com.getgymdone.app.ui.screens.home.HomeScreen
import com.getgymdone.app.ui.screens.onboarding.CustomizeRoutineScreen
import com.getgymdone.app.ui.screens.onboarding.PickSplitScreen
import com.getgymdone.app.ui.screens.onboarding.RoutineMethodScreen
import com.getgymdone.app.ui.screens.onboarding.SplashScreen
import com.getgymdone.app.ui.screens.profile.ProfileScreen
import com.getgymdone.app.ui.screens.settings.SettingsScreen
import com.getgymdone.app.ui.screens.workout.ActiveWorkoutScreen
import com.getgymdone.app.ui.screens.workout.WorkoutCompleteScreen
import com.getgymdone.app.ui.screens.workouts.WorkoutsListScreen
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
    val nav = rememberNavController()
    val startVm: StartDestinationViewModel = hiltViewModel()
    val onboardingComplete by startVm.onboardingComplete.collectAsState()

    NavHost(
        navController = nav,
        startDestination = Route.Splash,
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
                onBuildMyOwn = { nav.navigate(Route.CustomizeRoutine(args.splitId)) },
            )
        }

        composable<Route.CustomizeRoutine> { entry ->
            val args: Route.CustomizeRoutine = entry.toRoute()
            CustomizeRoutineScreen(
                splitId = args.splitId,
                onDone = {
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Home> {
            HomeScreen(
                onOpenDay = { dayId -> nav.navigate(Route.DayOverview(dayId)) },
                onStartToday = { dayId -> nav.navigate(Route.ActiveWorkout(dayId)) },
                onOpenWorkouts = { nav.navigate(Route.WorkoutsList) },
                onOpenProfile = { nav.navigate(Route.Profile) },
                onOpenSettings = { nav.navigate(Route.Settings) },
                onOpenDebug = { nav.navigate(Route.DebugData) },
            )
        }

        composable<Route.WorkoutsList> {
            WorkoutsListScreen(onBack = { nav.popBackStack() })
        }
        composable<Route.Profile> {
            ProfileScreen(onBack = { nav.popBackStack() })
        }
        composable<Route.Settings> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable<Route.DayOverview> { entry ->
            val args: Route.DayOverview = entry.toRoute()
            DayOverviewScreen(
                workoutDayId = args.workoutDayId,
                onStart = { nav.navigate(Route.ActiveWorkout(args.workoutDayId)) },
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
