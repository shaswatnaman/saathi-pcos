package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Privacy & Architecture",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "How Saathi handles your health data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Architecture diagram
        ArchitectureDiagram()

        // Core guarantees
        Text(
            "Our Guarantees",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        val guarantees = listOf(
            Triple(Icons.Default.PhoneAndroid, "Photos never leave this phone",
                "Camera analysis runs entirely on your device. No image is ever uploaded to any server, including ours."),
            Triple(Icons.Default.CloudOff, "No account, no login, no server",
                "Saathi has no backend. There is no user account, no analytics platform, and no remote database receiving your data."),
            Triple(Icons.Default.Share, "You control every export",
                "Nothing leaves the app unless you explicitly tap 'Generate Report' and choose to share the PDF. Sharing is always opt-in."),
            Triple(Icons.Default.Lock, "Stored only on this device",
                "All symptom logs, cycle data, and risk assessments are stored in a local Room database that lives only on your phone."),
            Triple(Icons.Default.DeleteForever, "Delete everything in one tap",
                "Settings → Clear All Data permanently deletes the entire local database. Nothing is retained anywhere else."),
        )

        guarantees.forEach { (icon, title, body) ->
            PrivacyCard(icon = icon, title = title, body = body)
        }

        // AI explanation note
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "About AI Explanations",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "When the app generates a plain-language explanation of your scan results, it sends a short structured summary (numbers only — no photo, no name, no personal identifiers) to an AI service. The raw photo is never sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ArchitectureDiagram() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Data Flow",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            DiagramNode(Icons.Default.CameraAlt, "Camera / Mic", Color(0xFF1976D2), "Captures")
            DiagramArrow("on-device only")
            DiagramNode(Icons.Default.Memory, "On-Device Processing", Color(0xFF7B1FA2), "Analyzes")
            DiagramArrow("stored locally")
            DiagramNode(Icons.Default.Storage, "Local Database", Color(0xFF388E3C), "Room DB")
            DiagramArrow("you choose to share")
            DiagramNode(Icons.Default.PictureAsPdf, "PDF Report", Color(0xFFE64A19), "Export only")
        }
    }
}

@Composable
private fun DiagramNode(icon: ImageVector, label: String, color: Color, sublabel: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = color)
                Text(sublabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiagramArrow(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text("↓", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacyCard(icon: ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp).padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
