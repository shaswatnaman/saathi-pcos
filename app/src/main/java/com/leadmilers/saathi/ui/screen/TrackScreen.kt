package com.leadmilers.saathi.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.HealthEntry
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.RiskScorer
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.launch

private enum class TrackCategory(val label: String) {
    CYCLE("Cycle"), BODY("Body"), ENERGY("Energy"), MIND("Mind"), MORE("More")
}

@Composable
fun TrackScreen(
    onCameraClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onDetailedLogClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo    = remember { (context.applicationContext as SaathiApp).repository }
    val scope   = rememberCoroutineScope()
    val snack   = remember { SnackbarHostState() }

    var selected by remember { mutableStateOf<TrackCategory?>(null) }

    // Cycle
    var flowIntensity by remember { mutableStateOf<String?>(null) }
    var flowDifferent by remember { mutableStateOf(false) }

    // Body
    var skinDarkening  by remember { mutableStateOf(false) }
    var weightGain     by remember { mutableStateOf(false) }
    var hairIssues     by remember { mutableStateOf(false) }

    // Energy
    var fatigueLevel by remember { mutableIntStateOf(3) }

    // Mind
    var moodChoice by remember { mutableStateOf<String?>(null) }

    var saving by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            AnimatedVisibility(
                visible = selected != null && selected != TrackCategory.MORE,
                enter   = slideInVertically { it },
                exit    = slideOutVertically { it },
            ) {
                Surface(shadowElevation = 8.dp) {
                    SaathiPrimaryButton(
                        text     = "Save check-in",
                        loading  = saving,
                        onClick  = {
                            scope.launch {
                                saving = true
                                try {
                                    val now  = System.currentTimeMillis()
                                    val flow = flowIntensity ?: "Medium"
                                    val cycle = CycleLog(date = now, cycleLength = 28, flowIntensity = flow)
                                    val cycleId = repo.insertCycleLog(cycle)
                                    val symptom = SymptomLog(
                                        date             = now,
                                        fatigue          = fatigueLevel,
                                        acneScore        = 0f,
                                        voiceEnergyScore = 1f,
                                        weight           = 0f,
                                        waistHip         = 0f,
                                        skinDarkening    = skinDarkening,
                                        weightGain       = weightGain,
                                        hairIssues       = hairIssues,
                                    )
                                    repo.insertSymptomLog(symptom)
                                    if (moodChoice != null) {
                                        repo.insertHealthEntry(HealthEntry(
                                            moduleId    = "general",
                                            entryType   = "mood",
                                            textValue   = moodChoice,
                                            sourceType  = "self_report",
                                            timestamp   = now,
                                        ))
                                    }
                                    if (flowDifferent) {
                                        repo.insertHealthEntry(HealthEntry(
                                            moduleId    = "general",
                                            entryType   = "flow_felt_different",
                                            textValue   = flow,
                                            sourceType  = "self_report",
                                            timestamp   = now,
                                        ))
                                    }
                                    val risk = RiskScorer.calculateRisk(cycle.copy(id = cycleId), symptom)
                                    repo.insertRiskAssessment(risk)
                                    snack.showSnackbar("Saved.")
                                    selected = null
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(20.dp))

            // Header
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "Track",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "What would you like to check in on?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(28.dp))

            // Category chips
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackCategory.entries.forEach { cat ->
                    SaathiChip(
                        text     = cat.label,
                        selected = selected == cat,
                        onClick  = {
                            selected = if (selected == cat) null else cat
                            if (cat == TrackCategory.MORE) onDetailedLogClick()
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Contextual inputs
            AnimatedVisibility(
                visible = selected == TrackCategory.CYCLE,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                CycleSection(
                    flow       = flowIntensity,
                    onFlow     = { flowIntensity = it },
                    different  = flowDifferent,
                    onDifferent = { flowDifferent = it },
                    modifier   = Modifier.padding(horizontal = 24.dp),
                )
            }

            AnimatedVisibility(
                visible = selected == TrackCategory.BODY,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                BodySection(
                    skinDarkening  = skinDarkening, onSkinDarkening  = { skinDarkening  = it },
                    weightGain     = weightGain,    onWeightGain     = { weightGain     = it },
                    hairIssues     = hairIssues,    onHairIssues     = { hairIssues     = it },
                    onCameraClick  = onCameraClick,
                    modifier       = Modifier.padding(horizontal = 24.dp),
                )
            }

            AnimatedVisibility(
                visible = selected == TrackCategory.ENERGY,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                EnergySection(
                    fatigue   = fatigueLevel,
                    onFatigue = { fatigueLevel = it },
                    onVoiceClick = onVoiceClick,
                    modifier  = Modifier.padding(horizontal = 24.dp),
                )
            }

            AnimatedVisibility(
                visible = selected == TrackCategory.MIND,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                MindSection(
                    mood     = moodChoice,
                    onMood   = { moodChoice = it },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun CycleSection(
    flow: String?,
    onFlow: (String) -> Unit,
    different: Boolean,
    onDifferent: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SaathiSubheading("Cycle")
        Text("Flow intensity", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Light", "Medium", "Heavy", "Spotting").forEach { opt ->
                SaathiChip(text = opt, selected = flow == opt, onClick = { onFlow(opt) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("This flow felt different from usual",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Switch(checked = different, onCheckedChange = onDifferent)
        }
        SaathiDivider()
    }
}

@Composable
private fun BodySection(
    skinDarkening: Boolean, onSkinDarkening: (Boolean) -> Unit,
    weightGain: Boolean,    onWeightGain: (Boolean) -> Unit,
    hairIssues: Boolean,    onHairIssues: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SaathiSubheading("Body")
        ToggleRow("Skin darkening noticed", skinDarkening, onSkinDarkening)
        ToggleRow("Unexplained weight change", weightGain, onWeightGain)
        ToggleRow("Hair thinning or excess growth", hairIssues, onHairIssues)
        Spacer(Modifier.height(4.dp))
        SaathiSecondaryButton(
            text    = "Scan skin with camera",
            onClick = onCameraClick,
            modifier = Modifier.fillMaxWidth(),
        )
        SaathiDivider()
    }
}

@Composable
private fun EnergySection(
    fatigue: Int,
    onFatigue: (Int) -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SaathiSubheading("Energy")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Fatigue level today", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text("$fatigue / 5", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value          = fatigue.toFloat(),
            onValueChange  = { onFatigue(it.toInt()) },
            valueRange     = 1f..5f,
            steps          = 3,
            modifier       = Modifier.fillMaxWidth(),
            colors         = SliderDefaults.colors(
                thumbColor        = MaterialTheme.colorScheme.primary,
                activeTrackColor  = MaterialTheme.colorScheme.primary,
            )
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Full energy", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Exhausted", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        SaathiSecondaryButton(
            text    = "Record voice check-in",
            onClick = onVoiceClick,
            modifier = Modifier.fillMaxWidth(),
        )
        SaathiDivider()
    }
}

@Composable
private fun MindSection(
    mood: String?,
    onMood: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SaathiSubheading("Mind")
        Text("How are you feeling?", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Great", "Good", "Okay", "Low").forEach { m ->
                SaathiChip(text = m, selected = mood == m, onClick = { onMood(m) })
            }
        }
        SaathiDivider()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
