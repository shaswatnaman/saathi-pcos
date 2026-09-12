package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.HealthEntry
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.RiskScorer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as SaathiApp).repository }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    // Form state
    var cycleLength by remember { mutableStateOf("28") }
    var flowIntensity by remember { mutableStateOf("Medium") }
    var flowFeelsDifferent by remember { mutableStateOf(false) }
    var fatigue by remember { mutableIntStateOf(2) }
    var weight by remember { mutableStateOf("") }
    var waistCm by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var skinDarkening by remember { mutableStateOf(false) }
    var weightGain by remember { mutableStateOf(false) }
    var hairIssues by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val flowOptions = listOf("Light", "Medium", "Heavy")
    var flowExpanded by remember { mutableStateOf(false) }

    // WHtR — computed live from waist / height
    val whtr = run {
        val w = waistCm.toFloatOrNull()
        val h = heightCm.toFloatOrNull()
        if (w != null && h != null && h > 0f) w / h else null
    }
    val whtrRisk = when {
        whtr == null -> null
        whtr < 0.43f -> "Low"
        whtr < 0.53f -> "Healthy"
        whtr < 0.58f -> "Increased"
        else -> "High"
    }
    val whtrColor = when (whtrRisk) {
        "Low"       -> Color(0xFF1976D2)
        "Healthy"   -> Color(0xFF388E3C)
        "Increased" -> Color(0xFFF57C00)
        "High"      -> Color(0xFFD32F2F)
        else        -> Color.Unspecified
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Daily Log", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Record today's cycle, symptoms, and body measurements.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ── Cycle section ──────────────────────────────────────────────
            SectionHeader("Cycle")
            OutlinedTextField(
                value = cycleLength,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 3) cycleLength = it },
                label = { Text("Cycle length (days)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenuBox(
                expanded = flowExpanded,
                onExpandedChange = { flowExpanded = it }
            ) {
                OutlinedTextField(
                    value = flowIntensity,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Flow intensity") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(flowExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = flowExpanded, onDismissRequest = { flowExpanded = false }) {
                    flowOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = { flowIntensity = opt; flowExpanded = false }
                        )
                    }
                }
            }
            LabeledSwitch("This flow felt different from usual", flowFeelsDifferent) { flowFeelsDifferent = it }

            // ── Body measurements section ──────────────────────────────────
            SectionHeader("Body measurements")
            Text("Optional — used to compute waist-to-height ratio (WHtR), a more sensitive marker of insulin resistance than BMI for PCOS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = waistCm,
                    onValueChange = { if (it.matches(Regex("""^\d{0,3}(\.\d{0,1})?$"""))) waistCm = it },
                    label = { Text("Waist (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = { Text("at navel") }
                )
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = { if (it.matches(Regex("""^\d{0,3}(\.\d{0,1})?$"""))) heightCm = it },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = { Text("standing") }
                )
            }

            // WHtR live indicator
            if (whtr != null && whtrRisk != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = whtrColor.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Waist-to-height ratio (WHtR)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Insulin resistance risk: $whtrRisk",
                                style = MaterialTheme.typography.bodySmall,
                                color = whtrColor, fontWeight = FontWeight.SemiBold)
                            Text("≤0.43 low · 0.43–0.53 healthy · 0.53–0.58 increased · >0.58 high",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Text("${"%.3f".format(whtr)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = whtrColor)
                    }
                }
            }

            // ── Symptoms section ───────────────────────────────────────────
            SectionHeader("Symptoms")
            OutlinedTextField(
                value = weight,
                onValueChange = { if (it.matches(Regex("""^\d{0,3}(\.\d{0,1})?$"""))) weight = it },
                label = { Text("Weight (kg) — optional") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Fatigue level: $fatigue / 5", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = fatigue.toFloat(),
                onValueChange = { fatigue = it.toInt() },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
            LabeledSwitch("Skin darkening (acanthosis)", skinDarkening) { skinDarkening = it }
            LabeledSwitch("Unexplained weight gain", weightGain) { weightGain = it }
            LabeledSwitch("Hair thinning / excess growth", hairIssues) { hairIssues = it }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val len  = cycleLength.toIntOrNull() ?: 28
                    val wt   = weight.toFloatOrNull() ?: 0f
                    val whr  = whtr ?: 0f
                    isSaving = true
                    scope.launch {
                        try {
                            val now = System.currentTimeMillis()
                            val cycle = CycleLog(
                                date = now, cycleLength = len,
                                flowIntensity = flowIntensity
                            )
                            val cycleId = repo.insertCycleLog(cycle)

                            val symptom = SymptomLog(
                                date = now, fatigue = fatigue,
                                acneScore = 0f,        // filled by CameraScreen
                                voiceEnergyScore = 1f, // filled by VoiceScreen
                                weight = wt,
                                waistHip = whr,        // stores WHtR (waist÷height)
                                skinDarkening = skinDarkening,
                                weightGain = weightGain,
                                hairIssues = hairIssues
                            )
                            repo.insertSymptomLog(symptom)

                            // Persist body measurements as HealthEntry for correlation chart
                            val entries = buildList {
                                if (wt > 0f) add(HealthEntry(moduleId = "general", entryType = "weight_kg",
                                    numericValue = wt.toDouble(), sourceType = "self_report", timestamp = now))
                                val w = waistCm.toFloatOrNull()
                                val h = heightCm.toFloatOrNull()
                                if (w != null && w > 0f) add(HealthEntry(moduleId = "general", entryType = "waist_cm",
                                    numericValue = w.toDouble(), sourceType = "self_report", timestamp = now))
                                if (h != null && h > 0f) add(HealthEntry(moduleId = "general", entryType = "height_cm",
                                    numericValue = h.toDouble(), sourceType = "self_report", timestamp = now))
                                if (whr > 0f) add(HealthEntry(moduleId = "general", entryType = "whtr",
                                    numericValue = whr.toDouble(), sourceType = "self_report", timestamp = now))
                                if (flowFeelsDifferent) add(HealthEntry(moduleId = "general",
                                    entryType = "flow_felt_different", textValue = flowIntensity,
                                    sourceType = "self_report", timestamp = now))
                            }
                            if (entries.isNotEmpty()) repo.insertHealthEntries(entries)

                            val risk = RiskScorer.calculateRisk(cycle.copy(id = cycleId), symptom)
                            repo.insertRiskAssessment(risk)

                            snackbarHost.showSnackbar("Saved! Use Camera & Voice tabs to add scores.")
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Today's Log")
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
