package com.getgymdone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/** Data collected from the user when creating a custom exercise. */
data class CustomExerciseInput(
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val formCues: List<String>,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
)

private val EQUIPMENT = listOf(
    "Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight",
    "Smith Machine", "Kettlebell", "Plate", "Band", "Other",
)

/**
 * Full-screen form for creating a custom exercise. The muscle group is chosen from the existing
 * categories ([muscleOptions]) so the new exercise files into the right group in the picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseForm(
    muscleOptions: List<String>,
    onSubmit: (CustomExerciseInput) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    val secondary = remember { mutableStateListOf<String>() }
    var equipment by remember { mutableStateOf("Dumbbell") }
    var cuesText by remember { mutableStateOf("") }
    var sets by remember { mutableIntStateOf(3) }
    var repsLow by remember { mutableIntStateOf(8) }
    var repsHigh by remember { mutableIntStateOf(12) }

    val valid = name.isNotBlank() && muscle != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NEW EXERCISE", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Name")
        Spacer(Modifier.height(6.dp))
        NameField(value = name, onValueChange = { name = it })

        Spacer(Modifier.height(18.dp))
        FieldLabel("Primary muscle")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            muscleOptions.forEach { m ->
                SelectChip(
                    label = m,
                    selected = m == muscle,
                    onClick = { muscle = m; secondary.remove(m) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Secondary muscles (optional)")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            muscleOptions.filter { it != muscle }.forEach { m ->
                SelectChip(
                    label = m,
                    selected = m in secondary,
                    onClick = { if (m in secondary) secondary.remove(m) else secondary.add(m) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Equipment")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EQUIPMENT.forEach { e ->
                SelectChip(label = e, selected = e == equipment, onClick = { equipment = e })
            }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Form cues / tips (one per line)")
        Spacer(Modifier.height(6.dp))
        CuesField(value = cuesText, onValueChange = { cuesText = it })

        Spacer(Modifier.height(18.dp))
        FieldLabel("Default sets & reps")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("Sets", "$sets", onMinus = { sets = (sets - 1).coerceAtLeast(1) }, onPlus = { sets = (sets + 1).coerceAtMost(10) }, modifier = Modifier.weight(1f))
            Stepper("Rep low", "$repsLow", onMinus = { repsLow = (repsLow - 1).coerceAtLeast(1) }, onPlus = { repsLow = (repsLow + 1).coerceAtMost(repsHigh) }, modifier = Modifier.weight(1f))
            Stepper("Rep high", "$repsHigh", onMinus = { repsHigh = (repsHigh - 1).coerceAtLeast(repsLow) }, onPlus = { repsHigh = (repsHigh + 1).coerceAtMost(50) }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape)
                .clickable(enabled = valid) {
                    onSubmit(
                        CustomExerciseInput(
                            name = name.trim(),
                            primaryMuscle = muscle!!,
                            secondaryMuscles = secondary.toList(),
                            equipment = equipment,
                            formCues = cuesText.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                            sets = sets,
                            repsLow = repsLow,
                            repsHigh = repsHigh,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "ADD EXERCISE",
                style = MaterialTheme.typography.headlineMedium,
                color = if (valid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text("e.g. Incline Hammer Press", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CuesField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                "e.g.\nShoulder blades retracted\nElbows tucked ~45°\nFull lockout",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StepBtn("−", onMinus)
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
            StepBtn("+", onPlus)
        }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
