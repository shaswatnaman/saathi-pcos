package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    onResetClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }

    when (prefs.role) {
        UserRole.COMPANION -> CompanionSettingsScreen(onPrivacyClick = onPrivacyClick, onResetClick = onResetClick)
        UserRole.GYNAC     -> GynacSettingsScreen(onPrivacyClick = onPrivacyClick, onResetClick = onResetClick)
        else               -> SelfSettingsScreen(
            onPrivacyClick         = onPrivacyClick,
            onPairingClick         = onPairingClick,
            onSharingControlsClick = onSharingControlsClick,
            onResetClick           = onResetClick,
        )
    }
}

// ── SELF settings ──────────────────────────────────────────────────────────────

@Composable
private fun SelfSettingsScreen(
    onPrivacyClick: () -> Unit,
    onPairingClick: () -> Unit,
    onSharingControlsClick: () -> Unit,
    onResetClick: () -> Unit,
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
    var showResetConfirm by remember { mutableStateOf(false) }

    var isPaired    by remember { mutableStateOf(prefs.isPaired) }
    var partnerName by remember { mutableStateOf(prefs.partnerName) }
    var lastSyncAt  by remember { mutableStateOf(prefs.lastSyncAt) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsTitle("Settings")

            // ── Demo data ──────────────────────────────────────────────────
            SettingsSectionLabel("Demo")
            SettingCard(
                icon = Icons.Default.Dataset,
                title = "Load Demo Data",
                description = "Insert 30 days of realistic PCOS symptom data. Useful for demos and testing.",
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
                        } finally { isSeeding = false }
                    }
                },
                loading = isSeeding,
            )

            // ── Data ──────────────────────────────────────────────────────
            SettingsSectionLabel("Data")
            SettingCard(
                icon = Icons.Default.DeleteForever,
                title = "Clear All Data",
                description = "Permanently delete all cycle logs, symptom logs, and risk assessments.",
                actionLabel = "Clear",
                enabled = !isSeeding && !isClearing,
                onClick = { showClearConfirm = true },
                loading = isClearing,
                destructive = true,
            )

            // ── Companion ─────────────────────────────────────────────────
            SettingsSectionLabel("Companion")
            if (isPaired) {
                PairedCompanionCard(
                    partnerName = partnerName,
                    lastSyncAt  = lastSyncAt,
                    onSharingControlsClick = onSharingControlsClick,
                    onDisconnect = { showDisconnectConfirm = true },
                )
            } else {
                AddCompanionCard(onClick = onPairingClick)
            }

            // ── Account ───────────────────────────────────────────────────
            SettingsSectionLabel("Account")
            SettingCard(
                icon = Icons.Outlined.RestartAlt,
                title = "Reset & Start Over",
                description = "Clear all settings, health history, and onboarding data. Returns you to role selection.",
                actionLabel = "Reset",
                enabled = !isSeeding && !isClearing,
                onClick = { showResetConfirm = true },
                destructive = true,
            )

            // ── Privacy ───────────────────────────────────────────────────
            SettingsSectionLabel("Privacy")
            PrivacyCard(onClick = onPrivacyClick)

            AboutSection()
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text  = { Text("This will delete all logs and risk assessments. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        isClearing = true
                        try { repo.clearAll(); snackbarHost.showSnackbar("All data cleared.") }
                        finally { isClearing = false }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect companion?") },
            text  = { Text("They will no longer receive your health updates.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    prefs.isPaired = false; prefs.pairedDeviceId = ""
                    prefs.partnerName = ""; prefs.lastSyncAt = 0L
                    isPaired = false; partnerName = ""; lastSyncAt = 0L
                    scope.launch { snackbarHost.showSnackbar("Companion disconnected.") }
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResetConfirm) { ResetConfirmDialog(onConfirm = { scope.launch { try { repo.clearAll() } catch (_: Exception) {} }; prefs.reset(); onResetClick() }, onDismiss = { showResetConfirm = false }) }
}

// ── COMPANION settings ─────────────────────────────────────────────────────────

@Composable
private fun CompanionSettingsScreen(
    onPrivacyClick: () -> Unit,
    onResetClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }
    val scope   = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var nameInput by remember { mutableStateOf(prefs.userName) }
    var nameSaved by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    var isPaired    by remember { mutableStateOf(prefs.isPaired) }
    var partnerName by remember { mutableStateOf(prefs.partnerName) }
    var lastSyncAt  by remember { mutableStateOf(prefs.lastSyncAt) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsTitle("Settings")

            // ── Profile ───────────────────────────────────────────────────
            SettingsSectionLabel("Your Profile")
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; nameSaved = false },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Button(
                        onClick = { prefs.userName = nameInput.trim(); nameSaved = true; scope.launch { snackbarHost.showSnackbar("Name saved.") } },
                        enabled = nameInput.isNotBlank() && !nameSaved,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Save") }
                }
            }

            // ── Connected to ──────────────────────────────────────────────
            SettingsSectionLabel("Connection")
            if (isPaired) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.PersonOutline, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connected to ${if (partnerName.isNotBlank()) partnerName else "your partner"}",
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (lastSyncAt > 0L) {
                                    val df = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                                    Text("Last synced ${df.format(Date(lastSyncAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Waiting for first sync…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showDisconnectConfirm = true }) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LinkOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Text("Not connected to a partner yet.\nAsk them to open Saathi → Settings → Add Companion.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Account ───────────────────────────────────────────────────
            SettingsSectionLabel("Account")
            SettingCard(
                icon = Icons.Outlined.RestartAlt,
                title = "Reset & Start Over",
                description = "Clear all settings and onboarding data. Returns to role selection.",
                actionLabel = "Reset",
                enabled = true,
                onClick = { showResetConfirm = true },
                destructive = true,
            )

            // ── Privacy ───────────────────────────────────────────────────
            SettingsSectionLabel("Privacy")
            PrivacyCard(onClick = onPrivacyClick)

            AboutSection()
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect?") },
            text  = { Text("You'll stop receiving your partner's health updates. You can reconnect any time.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    prefs.isPaired = false; prefs.pairedDeviceId = ""; prefs.pairingToken = ""
                    prefs.partnerName = ""; prefs.lastSyncAt = 0L
                    isPaired = false; partnerName = ""; lastSyncAt = 0L
                    scope.launch { snackbarHost.showSnackbar("Disconnected.") }
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResetConfirm) { ResetConfirmDialog(onConfirm = { prefs.reset(); onResetClick() }, onDismiss = { showResetConfirm = false }) }
}

// ── GYNAC settings ─────────────────────────────────────────────────────────────

@Composable
private fun GynacSettingsScreen(
    onPrivacyClick: () -> Unit,
    onResetClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }
    val scope   = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var nameInput by remember { mutableStateOf(prefs.userName) }
    var nameSaved by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    var isPaired   by remember { mutableStateOf(prefs.isPaired) }
    var lastSyncAt by remember { mutableStateOf(prefs.lastSyncAt) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsTitle("Settings")

            // ── Profile ───────────────────────────────────────────────────
            SettingsSectionLabel("Your Profile")
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; nameSaved = false },
                        label = { Text("Doctor name") },
                        placeholder = { Text("Dr. Surname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Button(
                        onClick = { prefs.userName = nameInput.trim(); nameSaved = true; scope.launch { snackbarHost.showSnackbar("Name saved.") } },
                        enabled = nameInput.isNotBlank() && !nameSaved,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Save") }
                }
            }

            // ── Patient connect ───────────────────────────────────────────
            SettingsSectionLabel("Patient Connection")
            if (isPaired) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.MonitorHeart, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Patient data connected",
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                                if (lastSyncAt > 0L) {
                                    val df = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                                    Text("Last received ${df.format(Date(lastSyncAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Waiting for patient to open app on same Wi-Fi…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showDisconnectConfirm = true }) {
                                Text("Disconnect patient", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Enter patient code",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Ask your patient to open Saathi → Settings → Add Companion, then share their 6-character code with you.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { if (it.length <= 6) codeInput = it.uppercase() },
                            label = { Text("Patient code") },
                            placeholder = { Text("ABC123") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        )
                        Button(
                            onClick = {
                                val token = codeInput.trim().uppercase()
                                if (token.length < 4) { scope.launch { snackbarHost.showSnackbar("Enter the code from the patient's app.") }; return@Button }
                                prefs.pairingToken = token
                                prefs.pairedDeviceId = token
                                prefs.isPaired = true
                                isPaired = true
                                codeInput = ""
                                scope.launch { snackbarHost.showSnackbar("Connected! Open the patient's app on the same Wi-Fi to sync.") }
                            },
                            enabled = codeInput.length >= 4,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Connect") }
                    }
                }
            }

            // ── Account ───────────────────────────────────────────────────
            SettingsSectionLabel("Account")
            SettingCard(
                icon = Icons.Outlined.RestartAlt,
                title = "Reset & Start Over",
                description = "Clear all settings and onboarding data. Returns to role selection.",
                actionLabel = "Reset",
                enabled = true,
                onClick = { showResetConfirm = true },
                destructive = true,
            )

            // ── Privacy ───────────────────────────────────────────────────
            SettingsSectionLabel("Privacy")
            PrivacyCard(onClick = onPrivacyClick)

            AboutSection()
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect patient?") },
            text  = { Text("You'll stop receiving live data from this patient's device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    prefs.isPaired = false; prefs.pairedDeviceId = ""; prefs.pairingToken = ""
                    prefs.lastSyncAt = 0L; prefs.lastSharedSnapshot = ""
                    isPaired = false; lastSyncAt = 0L
                    scope.launch { snackbarHost.showSnackbar("Patient disconnected.") }
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResetConfirm) { ResetConfirmDialog(onConfirm = { prefs.reset(); onResetClick() }, onDismiss = { showResetConfirm = false }) }
}

// ── Shared sub-composables ─────────────────────────────────────────────────────

@Composable
private fun SettingsTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

@Composable
private fun PairedCompanionCard(
    partnerName: String,
    lastSyncAt: Long,
    onSharingControlsClick: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (partnerName.isNotBlank()) partnerName else "Connected partner",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (lastSyncAt > 0L) {
                        val df = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                        Text("Last synced ${df.format(Date(lastSyncAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("What they can see", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onSharingControlsClick, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect companion", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AddCompanionCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add a companion", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("Let a trusted person see what you choose to share.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacyCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Privacy & Architecture", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("How your data stays on-device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutSection() {
    Spacer(Modifier.height(4.dp))
    Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
    Text("Saathi v1.0 — iQOO Hackathon 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    Text("Team leadmilers  •  PCOS cross-signal tracker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("ML model: logistic regression, CV-AUC 0.862 on 541 patients", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Reset everything?") },
        text  = { Text("This clears your name, role, health history, and all app settings. You'll return to role selection.\n\nThis cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Reset", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
    destructive: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                val btnColors = if (destructive)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                else ButtonDefaults.buttonColors()
                Button(onClick = onClick, enabled = enabled, colors = btnColors, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
