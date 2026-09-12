package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.office.OfficeBridge
import com.leadmilers.saathi.report.ReportGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OfficeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val sdkAvailable = remember { OfficeBridge.isAvailable(context) }

    // Last generated PDF path kept in state so all 3 actions share it
    var pdfPath by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Clinical summary for doctor clipboard
    suspend fun buildSummary(): String {
        val repo = (context.applicationContext as SaathiApp).repository
        val risk: RiskAssessment? = repo.latestRiskAssessment.first()
        val symptom = repo.getLatestSymptomLog()

        val f0Sd   = repo.getLatestHealthEntry("voice", "f0_sd")
        val f0Min  = repo.getLatestHealthEntry("voice", "f0_min")
        val f0Mean = repo.getLatestHealthEntry("voice", "f0_mean")
        val whtr   = repo.getLatestHealthEntry("general", "whtr")
        val waist  = repo.getLatestHealthEntry("general", "waist_cm")
        val height = repo.getLatestHealthEntry("general", "height_cm")

        val sb = StringBuilder()
        sb.appendLine("SAATHI — CLINICAL SCREENING SUMMARY")
        sb.appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("─────────────────────────────────────")

        if (risk != null) {
            sb.appendLine("PCOS RISK ASSESSMENT")
            sb.appendLine("  Risk level : ${risk.riskLevel}")
            sb.appendLine("  Total score: ${risk.totalScore}/12")
            sb.appendLine("  Cycle irregularity       : ${risk.cycleScore} pts")
            sb.appendLine("  Androgenic acne (CV model): ${risk.acneScore} pts")
            sb.appendLine("  Fatigue / voice energy    : ${risk.fatigueScore} pts")
            sb.appendLine("  Physical symptoms         : ${risk.physicalScore} pts")
        } else {
            sb.appendLine("PCOS RISK ASSESSMENT: No assessment recorded yet.")
        }

        sb.appendLine()
        sb.appendLine("ACOUSTIC BIOMARKERS (on-device YIN pitch, 16kHz)")
        if (f0Sd != null) {
            sb.appendLine("  F0 SD  (cycle-phase primary)  : ${"%.2f".format(f0Sd.numericValue)} Hz")
            sb.appendLine("  F0 min (5th-pct, secondary)   : ${"%.1f".format(f0Min?.numericValue ?: 0.0)} Hz")
            sb.appendLine("  F0 mean (baseline anchor)     : ${"%.1f".format(f0Mean?.numericValue ?: 0.0)} Hz")
            sb.appendLine("  Ref: Ziemer et al., JMIR Formative Research 2025 (PMC11737864)")
        } else {
            sb.appendLine("  No voice recording on file.")
        }

        sb.appendLine()
        sb.appendLine("BODY COMPOSITION")
        if (whtr != null) {
            sb.appendLine("  Waist circumference: ${"%.1f".format(waist?.numericValue ?: 0.0)} cm")
            sb.appendLine("  Height             : ${"%.1f".format(height?.numericValue ?: 0.0)} cm")
            sb.appendLine("  WHtR               : ${"%.3f".format(whtr.numericValue)} (insulin resistance proxy)")
            val whtrVal = whtr.numericValue ?: 0.0
            val band = when { whtrVal < 0.43 -> "Low" ; whtrVal < 0.53 -> "Healthy" ; whtrVal < 0.58 -> "Increased" ; else -> "High" }
            sb.appendLine("  Risk band          : $band (ref: ≤0.43 low · 0.43–0.53 healthy · 0.53–0.58 ↑ · >0.58 high)")
        } else {
            sb.appendLine("  No body measurements recorded.")
        }

        symptom?.let {
            sb.appendLine()
            sb.appendLine("SYMPTOM FLAGS (latest entry)")
            sb.appendLine("  Fatigue (0–5)  : ${it.fatigue}")
            sb.appendLine("  Acanthosis nigricans: ${if (it.skinDarkening) "Reported" else "Not reported"}")
            sb.appendLine("  Unexplained weight gain: ${if (it.weightGain) "Reported" else "Not reported"}")
            sb.appendLine("  Hair thinning/hirsutism: ${if (it.hairIssues) "Reported" else "Not reported"}")
            if (it.weight > 0f) sb.appendLine("  Body weight: ${"%.1f".format(it.weight)} kg")
        }

        sb.appendLine()
        sb.appendLine("─────────────────────────────────────")
        sb.appendLine("NOT A CLINICAL DIAGNOSIS. For reference only.")
        sb.appendLine("Source: Saathi on-device PCOS screening tracker (leadmilers)")
        return sb.toString()
    }

    // Ensure the PDF is generated before mirror/transfer actions
    suspend fun ensurePdf(): String? {
        pdfPath?.let { return it }
        isGenerating = true
        return try {
            val repo = (context.applicationContext as SaathiApp).repository
            val risks    = repo.recentRiskAssessments(30).first()
            val symptoms = repo.recentSymptomLogs(30).first()
            val entries  = repo.getRecentHealthEntries("voice", 10) +
                           repo.getRecentHealthEntries("general", 20)
            ReportGenerator.generate(context, risks, symptoms, healthEntries = entries).also { pdfPath = it }
        } catch (e: Exception) {
            snackbarHost.showSnackbar("Could not generate report: ${e.message}")
            null
        } finally {
            isGenerating = false
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                "iQOO Office Kit",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Send your Saathi report to your laptop wirelessly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // SDK availability banner
            if (!sdkAvailable) {
                SdkUnavailableBanner()
            }

            Spacer(Modifier.height(8.dp))

            // Mirror Report
            OfficeActionCard(
                icon = Icons.Default.Laptop,
                title = "Mirror Report",
                description = "Display the PDF on your laptop screen via screen mirror.",
                enabled = !isGenerating,
                onClick = {
                    scope.launch {
                        val path = ensurePdf() ?: return@launch
                        val ok = OfficeBridge.mirrorReport(context, path)
                        snackbarHost.showSnackbar(
                            if (ok) "Mirroring report to laptop…" else "Mirror failed — is your laptop connected?"
                        )
                    }
                }
            )

            // Copy Summary
            OfficeActionCard(
                icon = Icons.Default.ContentCopy,
                title = "Copy Summary",
                description = "Push risk summary text to your laptop clipboard.",
                enabled = !isGenerating,
                onClick = {
                    scope.launch {
                        val summary = buildSummary()
                        val ok = OfficeBridge.syncClipboard(context, summary)
                        snackbarHost.showSnackbar(
                            if (ok) "Summary copied to laptop clipboard." else "Clipboard sync failed — is your laptop connected?"
                        )
                    }
                }
            )

            // Transfer File
            OfficeActionCard(
                icon = Icons.Default.Send,
                title = "Transfer File",
                description = "Save the PDF to your laptop's Downloads folder.",
                enabled = !isGenerating,
                onClick = {
                    scope.launch {
                        val path = ensurePdf() ?: return@launch
                        val ok = OfficeBridge.transferFile(context, path)
                        snackbarHost.showSnackbar(
                            if (ok) "Report transferred to laptop Downloads." else "Transfer failed — is your laptop connected?"
                        )
                    }
                }
            )

            if (isGenerating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Generating report…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OfficeActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun SdkUnavailableBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚠",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "iQOO Office Kit not detected on this device. " +
                "Actions will be simulated (no-op) on non-iQOO hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
