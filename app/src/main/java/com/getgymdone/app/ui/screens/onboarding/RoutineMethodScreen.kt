package com.getgymdone.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.repository.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineMethodViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
) : ViewModel() {
    fun confirmCurated(splitId: String) {
        viewModelScope.launch {
            prefs.update { it.copy(activeSplitId = splitId, onboardingComplete = true) }
        }
    }
}

@Composable
fun RoutineMethodScreen(
    splitId: String,
    onCurate: () -> Unit,
    onBuildMyOwn: () -> Unit,
) {
    val vm: RoutineMethodViewModel = hiltViewModel()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Text(
            text = "HOW DO YOU",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "WANT IT?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Curate from your PDF, or assemble exercises day-by-day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        MethodCard(
            title = "Curate for me",
            body = "Load the 26 exercises from your PDF split. Ready in one tap.",
            badge = "Recommended",
            onClick = {
                vm.confirmCurated(splitId)
                onCurate()
            },
        )
        Spacer(Modifier.height(14.dp))
        MethodCard(
            title = "Build my own",
            body = "Pick your own exercises per day. Coming in v1.1.",
            badge = "Soon",
            enabled = false,
            onClick = onBuildMyOwn,
        )
    }
}

@Composable
private fun MethodCard(
    title: String,
    body: String,
    badge: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(18.dp),
    ) {
        Text(
            text = badge.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
