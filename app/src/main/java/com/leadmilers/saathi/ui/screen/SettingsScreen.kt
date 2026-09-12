package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.demo.DemoDataSeeder
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as SaathiApp).repository }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var isSeeding by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(8.dp))

            // ── Demo data ──────────────────────────────────────────────────
            Text("Demo", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()

            SettingCard(
                icon = Icons.Default.Dataset,
                title = "Load Demo Data",
                description = "Insert 30 days of realistic PCOS symptom data with escalating risk. " +
                              "Useful for hackathon demos and UI testing.",
                actionLabel = if (isSeeding) "Loading…" else "Load",
                enabled = !isSeeding && !isClearing,
                onClick = {
                    scope.launch {
                        isSeeding = true
                        try {
                            DemoDataSeeder.seed(repo)
                            snackbarHost.showSnackbar("30 days of demo data loaded!")
                        } catch (e: Exception) {
                            snackbarHost.showSnackbar("Failed: ${e.message}")
                        } finally {
                            isSeeding = false
                        }
                    }
                },
                loading = isSeeding
            )

            // ── Data management ────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("Data", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()

            SettingCard(
                icon = Icons.Default.DeleteForever,
                title = "Clear All Data",
                description = "Permanently delete all cycle logs, symptom logs, and risk assessments.",
                actionLabel = "Clear",
                enabled = !isSeeding && !isClearing,
                onClick = { showClearConfirm = true },
                loading = isClearing,
                destructive = true
            )

            // ── App info ───────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("About", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("Saathi v1.0 — iQOO Hackathon 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            Text("Team leadmilers  •  PCOS cross-signal tracker",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ML model: logistic regression, CV-AUC 0.862 on 541 patients",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text = { Text("This will delete all 30 days of logs and risk assessments. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        isClearing = true
                        try {
                            repo.clearAll()
                            snackbarHost.showSnackbar("All data cleared.")
                        } finally {
                            isClearing = false
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
    destructive: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                val btnColors = if (destructive)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer)
                else ButtonDefaults.buttonColors()
                Button(onClick = onClick, enabled = enabled,
                    colors = btnColors,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
