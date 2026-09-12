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

    // Build a human-readable risk summary for clipboard
    suspend fun buildSummary(): String {
        val repo = (context.applicationContext as SaathiApp).repository
        val risk: RiskAssessment? = repo.latestRiskAssessment.first()
        return if (risk != null) {
            "Saathi PCOS Risk Report\n" +
            "Risk Level: ${risk.riskLevel}\n" +
            "Score: ${risk.totalScore}/12\n" +
            "Cycle: ${risk.cycleScore}  Acne: ${risk.acneScore}  " +
            "Fatigue: ${risk.fatigueScore}  Physical: ${risk.physicalScore}"
        } else {
            "Saathi PCOS Risk Report — no assessment yet. Log a cycle and symptoms first."
        }
    }

    // Ensure the PDF is generated before mirror/transfer actions
    suspend fun ensurePdf(): String? {
        pdfPath?.let { return it }
        isGenerating = true
        return try {
            val repo = (context.applicationContext as SaathiApp).repository
            val risks = repo.recentRiskAssessments(30).first()
            val symptoms = repo.recentSymptomLogs(30).first()
            ReportGenerator.generate(context, risks, symptoms).also { pdfPath = it }
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
