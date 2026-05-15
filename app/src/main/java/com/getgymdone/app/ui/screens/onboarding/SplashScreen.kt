package com.getgymdone.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getgymdone.app.ui.components.BigCta
import com.getgymdone.app.ui.components.GhostCta
import com.getgymdone.app.ui.theme.AccentLime

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    onSkipToHome: () -> Unit,
    isOnboardingComplete: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize().padding(22.dp)) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Text(
                text = "GET",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "GYM",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "DONE.",
                style = MaterialTheme.typography.displayLarge,
                color = AccentLime,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Calendar-first workout logger.\nKnows what day it is. Knows what to lift.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            BigCta(
                label = if (isOnboardingComplete) "Open app" else "Let's lift",
                onClick = if (isOnboardingComplete) onSkipToHome else onContinue,
            )
            if (isOnboardingComplete) {
                Spacer(Modifier.height(10.dp))
                GhostCta(label = "Reset routine", onClick = onContinue)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "v0.1 · local-only",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
        }
    }
}
