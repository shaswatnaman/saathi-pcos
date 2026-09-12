package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.HealthEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GeneralSymptomScreen(onHeartRateClick: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as SaathiApp).repository }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var pain by remember { mutableFloatStateOf(0f) }
    var mood by remember { mutableFloatStateOf(3f) }
    var fatigue by remember { mutableFloatStateOf(0f) }
    var bleeding by remember { mutableStateOf("none") }
    var isSaving by remember { mutableStateOf(false) }
    var savedToday by remember { mutableStateOf(false) }

    val recentEntries by repo.healthEntriesByModule("general").collectAsState(initial = emptyList())
    val todayEntries = recentEntries.filter {
        val cal = Calendar.getInstance()
        val entryDay = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        cal.get(Calendar.DAY_OF_YEAR) == entryDay.get(Calendar.DAY_OF_YEAR) &&
        cal.get(Calendar.YEAR) == entryDay.get(Calendar.YEAR)
    }
    LaunchedEffect(todayEntries) {
        savedToday = todayEntries.isNotEmpty()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EditNote, null,
                    tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
                Column {
                    Text("General Symptoms", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Daily wellness log — not condition-specific",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (savedToday) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Text("Today's log saved. You can update it below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50))
                    }
                }
            }

            // Pain slider
            SliderField(
                label = "Pain Level",
                value = pain,
                onValueChange = { pain = it },
                range = 0f..10f,
                steps = 9,
                lowLabel = "None",
                highLabel = "Severe",
                color = Color(0xFFD32F2F),
                icon = Icons.Default.SentimentVeryDissatisfied,
                displayValue = pain.toInt().toString()
            )

            // Mood slider
            SliderField(
                label = "Mood",
                value = mood,
                onValueChange = { mood = it },
                range = 1f..5f,
                steps = 3,
                lowLabel = "Very low",
                highLabel = "Great",
                color = Color(0xFF1976D2),
                icon = Icons.Default.Mood,
                displayValue = moodLabel(mood.toInt())
            )

            // Fatigue slider
            SliderField(
                label = "Fatigue",
                value = fatigue,
                onValueChange = { fatigue = it },
                range = 0f..5f,
                steps = 4,
                lowLabel = "None",
                highLabel = "Exhausted",
                color = Color(0xFFE64A19),
                icon = Icons.Default.BatteryAlert,
                displayValue = "${fatigue.toInt()}/5"
            )

            // Bleeding pattern
            BleedingSelector(selected = bleeding, onSelect = { bleeding = it })

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        val now = System.currentTimeMillis()
                        val entries = listOf(
                            HealthEntry(moduleId = "general", entryType = "pain_level",
                                numericValue = pain.toDouble(), sourceType = "self_report", timestamp = now),
                            HealthEntry(moduleId = "general", entryType = "mood",
                                numericValue = mood.toDouble(), sourceType = "self_report", timestamp = now),
                            HealthEntry(moduleId = "general", entryType = "fatigue",
                                numericValue = fatigue.toDouble(), sourceType = "self_report", timestamp = now),
                            HealthEntry(moduleId = "general", entryType = "bleeding_pattern",
                                textValue = bleeding, sourceType = "self_report", timestamp = now)
                        )
                        repo.insertHealthEntries(entries)
                        isSaving = false
                        savedToday = true
                        snackbarHost.showSnackbar("Today's symptoms saved.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Today's Log")
                }
            }

            // Heart rate (PPG) shortcut
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE91E63).copy(alpha = 0.08f)),
                onClick = onHeartRateClick
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("❤️", style = MaterialTheme.typography.titleLarge)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Measure Heart Rate", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE91E63))
                        Text("Rear camera PPG — place finger over lens · wellness trend",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFE91E63))
                }
            }

            // Recent entries
            if (recentEntries.isNotEmpty()) {
                RecentGeneralEntries(recentEntries.take(20))
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SliderField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    lowLabel: String,
    highLabel: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    displayValue: String
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Text(label, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
                    Text(displayValue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color
                )
            )
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lowLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(highLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BleedingSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("none", "spotting", "light", "heavy")
    val labels = listOf("None", "Spotting", "Light", "Heavy")
    val colors = listOf(Color(0xFF9E9E9E), Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFFD32F2F))

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Water, null,
                    tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                Text("Bleeding Pattern", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { i, opt ->
                    val isSelected = selected == opt
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) colors[i].copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { onSelect(opt) }
                    ) {
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                labels[i],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) colors[i]
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentGeneralEntries(entries: List<HealthEntry>) {
    val grouped = entries.groupBy {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.timestamp))
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent Logs", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall)
            grouped.entries.take(5).forEach { (day, dayEntries) ->
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(day, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp))
                    dayEntries.forEach { e ->
                        val label = when (e.entryType) {
                            "pain_level" -> "Pain ${e.numericValue?.toInt()}"
                            "mood" -> "Mood ${moodLabel(e.numericValue?.toInt() ?: 3)}"
                            "fatigue" -> "Fatigue ${e.numericValue?.toInt()}"
                            "bleeding_pattern" -> e.textValue?.replaceFirstChar { it.uppercase() } ?: ""
                            else -> e.entryType
                        }
                        if (label.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(label,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun moodLabel(v: Int) = when (v) {
    1 -> "Very Low"; 2 -> "Low"; 3 -> "Okay"; 4 -> "Good"; else -> "Great"
}
