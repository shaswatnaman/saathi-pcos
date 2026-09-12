package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.HealthModule

@Composable
fun ModulesScreen(onModuleClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as SaathiApp).repository }
    val modules by repo.allModules.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Health Modules",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Saathi is a multi-condition platform. Each module tracks a different health condition using on-device sensor signals and self-report.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(modules) { module ->
                ModuleCard(module = module, onClick = {
                    if (module.isActive) onModuleClick(module.id)
                })
            }
            item {
                // Platform pitch card at bottom
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Open Platform Architecture",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "New modules share the same sensor pipelines (camera CV, voice energy, cycle analysis) and data schema — each condition gets independent tracking without rebuilding the stack.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun ModuleCard(module: HealthModule, onClick: () -> Unit) {
    val (icon, accent) = moduleVisuals(module.id)
    val containerColor = if (module.isActive)
        accent.copy(alpha = 0.10f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (module.isActive) accent.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (module.isActive) accent
                               else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        module.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (module.isActive) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    StatusBadge(module)
                }
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (module.isActive) 1f else 0.5f
                    )
                )
            }

            if (module.isActive) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = accent
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(module: HealthModule) {
    val (label, color) = when {
        module.isComingSoon -> "Coming Soon" to Color(0xFF9E9E9E)
        module.isActive -> "Active" to Color(0xFF4CAF50)
        else -> "Inactive" to Color(0xFF9E9E9E)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun moduleVisuals(id: String): Pair<ImageVector, Color> = when (id) {
    "pcos"           -> Icons.Default.MonitorHeart to Color(0xFF7B1FA2)
    "general"        -> Icons.Default.EditNote to Color(0xFF1976D2)
    "endometriosis"  -> Icons.Default.Healing to Color(0xFFD32F2F)
    "thyroid"        -> Icons.Default.Biotech to Color(0xFF388E3C)
    "perimenopause"  -> Icons.Default.Thermostat to Color(0xFFE64A19)
    else             -> Icons.Default.HealthAndSafety to Color(0xFF455A64)
}
