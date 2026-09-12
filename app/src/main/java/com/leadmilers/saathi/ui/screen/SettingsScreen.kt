package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Color
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
import com.leadmilers.saathi.prefs.UserRole
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    onPrivacyClick: () -> Unit = {},
    onPairingClick: () -> Unit = {},
    onSharingControlsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo    = remember { (context.applicationContext as SaathiApp).repository }
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }
    val scope   = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var isSeeding by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    // Live-read pairing state so the section refreshes after pairing
    var isPaired     by remember { mutableStateOf(prefs.isPaired) }
    var partnerName  by remember { mutableStateOf(prefs.partnerName) }
    var lastSyncAt   by remember { mutableStateOf(prefs.lastSyncAt) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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

            // ── Companion ─────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("Companion", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()

            if (isPaired) {
                // Connected state: show partner info + action rows
                Surface(
                    shape    = MaterialTheme.shapes.large,
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Partner name + last sync
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.PersonOutline, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (partnerName.isNotBlank()) partnerName else "Connected partner",
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurface,
                                )
                                if (lastSyncAt > 0L) {
                                    val df = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                                    Text("Last synced ${df.format(Date(lastSyncAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        // Sharing controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("What they can see", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = onSharingControlsClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        // Disconnect
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showDisconnectConfirm = true }) {
                                Text("Disconnect companion",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                // Not paired: show Add Companion card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.large,
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    onClick  = onPairingClick,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null,
                            tint   = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Add a companion", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("Let a trusted person see what you choose to share.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Privacy ────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text("Privacy", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = onPrivacyClick
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Privacy & Architecture", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        Text("How your data stays on-device", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

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

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect companion?") },
            text  = { Text("They will no longer receive your health updates. You can add a companion again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    prefs.isPaired       = false
                    prefs.pairedDeviceId = ""
                    prefs.partnerName    = ""
                    prefs.lastSyncAt     = 0L
                    isPaired    = false
                    partnerName = ""
                    lastSyncAt  = 0L
                    scope.launch { snackbarHost.showSnackbar("Companion disconnected.") }
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
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
