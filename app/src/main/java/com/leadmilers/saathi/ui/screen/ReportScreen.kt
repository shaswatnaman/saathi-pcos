package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.report.ReportGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ReportScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as SaathiApp).repository }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var isGenerating by remember { mutableStateOf(false) }
    var lastPath by remember { mutableStateOf<String?>(null) }
    var doctorMode by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .padding(top = 32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Women's Health Summary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Generate a multi-page PDF with your 30-day risk trend, " +
                "symptom log, and wellness appendix. Saved to Downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Feature 1.2: Doctor mode toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (doctorMode)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        if (doctorMode) Icons.Default.MedicalServices else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (doctorMode)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (doctorMode) "Doctor mode" else "Patient mode",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (doctorMode)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (doctorMode)
                                "Clinical detail: AUC scores, probability, structured appendix"
                            else
                                "Plain language: friendly summaries, no clinical jargon",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (doctorMode)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = doctorMode,
                        onCheckedChange = { doctorMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isGenerating = true
                        try {
                            val risks    = repo.recentRiskAssessments(30).first()
                            val symptoms = repo.recentSymptomLogs(30).first()
                            val entries  = repo.recentHealthEntriesByModule("general", 40).first()
                            if (risks.isEmpty()) {
                                snackbarHost.showSnackbar("No data yet — load demo data or log a cycle first.")
                                return@launch
                            }
                            val path = ReportGenerator.generate(
                                context, risks, symptoms,
                                doctorMode = doctorMode,
                                healthEntries = entries
                            )
                            lastPath = path
                            snackbarHost.showSnackbar("Report saved to Downloads ✓")
                        } catch (e: Exception) {
                            snackbarHost.showSnackbar("Error: ${e.message}")
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Generating…")
                } else {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (doctorMode) "Generate Clinical PDF" else "Generate PDF Report")
                }
            }

            lastPath?.let { path ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Last generated:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            path.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
