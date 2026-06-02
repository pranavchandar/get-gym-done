package com.getgymdone.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.db.entities.UserPrefs
import com.getgymdone.app.data.repository.ExportRepository
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.ui.components.GhostCta
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
    private val exportRepo: ExportRepository,
) : ViewModel() {
    val prefsFlow: StateFlow<UserPrefs> = prefs.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPrefs())

    fun setTheme(theme: String) = viewModelScope.launch {
        prefs.update { it.copy(theme = theme) }
    }
    fun setUnits(units: String) = viewModelScope.launch {
        prefs.update { it.copy(units = units) }
    }
    fun export(uri: android.net.Uri) = viewModelScope.launch { exportRepo.exportTo(uri) }
    fun importBackup(uri: android.net.Uri) = viewModelScope.launch { exportRepo.importFrom(uri) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onResetRoutine: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val p by vm.prefsFlow.collectAsState()

    val createDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::export) }

    val openDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importBackup) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(20.dp))

        SettingsGroup("Appearance") {
            ChoiceRow("Theme", p.theme, listOf("system", "light", "dark"), vm::setTheme)
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Workout") {
            ChoiceRow("Units", p.units, listOf("kg", "lbs"), vm::setUnits)
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Routine") {
            Text(
                text = "Pick a different split. Your workout logs stay put.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            GhostCta(label = "Reset routine", onClick = onResetRoutine)
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Data") {
            GhostCta(
                label = "Export to JSON",
                onClick = { createDoc.launch("gymdone-backup.json") },
            )
            Spacer(Modifier.height(8.dp))
            GhostCta(
                label = "Import from JSON",
                onClick = { openDoc.launch(arrayOf("application/json", "*/*")) },
            )
        }

        Spacer(Modifier.weight(1f))
        GhostCta(label = "Back", onClick = onBack)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { opt ->
                val isSel = opt == selected
                val shape = RoundedCornerShape(100.dp)
                Text(
                    text = opt.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(shape)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape,
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
