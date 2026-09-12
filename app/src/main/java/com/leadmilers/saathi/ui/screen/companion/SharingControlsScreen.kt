package com.leadmilers.saathi.ui.screen.companion

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
import com.leadmilers.saathi.prefs.UserPrefs
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingControlsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }

    var shareCycle        by remember { mutableStateOf(prefs.shareCycle) }
    var sharePeriod       by remember { mutableStateOf(prefs.sharePeriod) }
    var shareSymptoms     by remember { mutableStateOf(prefs.shareSymptoms) }
    var shareMood         by remember { mutableStateOf(prefs.shareMood) }
    var shareEnergy       by remember { mutableStateOf(prefs.shareEnergy) }
    var shareInsights     by remember { mutableStateOf(prefs.shareInsights) }
    var sharePrivateNotes by remember { mutableStateOf(prefs.sharePrivateNotes) }

    // Persist on every toggle
    fun save() {
        prefs.shareCycle        = shareCycle
        prefs.sharePeriod       = sharePeriod
        prefs.shareSymptoms     = shareSymptoms
        prefs.shareMood         = shareMood
        prefs.shareEnergy       = shareEnergy
        prefs.shareInsights     = shareInsights
        prefs.sharePrivateNotes = sharePrivateNotes
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("What they can see") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back",
                            modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                "You decide what your partner sees.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            SaathiSectionHeader("Health data")
            Spacer(Modifier.height(14.dp))

            Surface(
                shape  = MaterialTheme.shapes.large,
                color  = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SharingToggleRow("Cycle phase & day", shareCycle) {
                        shareCycle = it; save()
                    }
                    SaathiDivider(Modifier.padding(horizontal = 16.dp))
                    SharingToggleRow("Period & flow", sharePeriod) {
                        sharePeriod = it; save()
                    }
                    SaathiDivider(Modifier.padding(horizontal = 16.dp))
                    SharingToggleRow("Physical symptoms", shareSymptoms) {
                        shareSymptoms = it; save()
                    }
                    SaathiDivider(Modifier.padding(horizontal = 16.dp))
                    SharingToggleRow("Mood", shareMood) {
                        shareMood = it; save()
                    }
                    SaathiDivider(Modifier.padding(horizontal = 16.dp))
                    SharingToggleRow("Energy level", shareEnergy) {
                        shareEnergy = it; save()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SaathiSectionHeader("Keep private")
            Spacer(Modifier.height(14.dp))

            Surface(
                shape  = MaterialTheme.shapes.large,
                color  = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SharingToggleRow("PCOS insights", shareInsights) {
                        shareInsights = it; save()
                    }
                    SaathiDivider(Modifier.padding(horizontal = 16.dp))
                    SharingToggleRow("Private notes", sharePrivateNotes) {
                        sharePrivateNotes = it; save()
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(
                        "Changes apply the next time your devices sync. Everything is stored on your device only — nothing goes to a server.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SharingToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked  = value,
            onCheckedChange = onChange,
            colors   = SwitchDefaults.colors(
                checkedThumbColor  = MaterialTheme.colorScheme.surface,
                checkedTrackColor  = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        )
    }
}
