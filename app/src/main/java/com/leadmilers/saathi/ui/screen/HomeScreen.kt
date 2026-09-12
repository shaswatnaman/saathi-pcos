package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.RiskScorer
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import com.leadmilers.saathi.ui.viewmodel.HomeViewModel
import java.util.*

@Composable
fun HomeScreen(
    onLogTodayClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    getGreeting(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Here's how you're doing.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // ── Cycle card ────────────────────────────────────────────────────────
        CycleCard(
            cycleLogs = state.recentCycleLogs,
            modifier  = Modifier.padding(horizontal = 24.dp),
        )

        // ── Status row ────────────────────────────────────────────────────────
        state.latestRisk?.let { risk ->
            Spacer(Modifier.height(16.dp))
            StatusRow(
                risk    = risk,
                symptom = state.recentSymptomLogs.firstOrNull(),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        // ── Saathi noticed ────────────────────────────────────────────────────
        val insight = buildInsight(state.recentSymptomLogs, state.recentCycleLogs, state.latestRisk)
        Spacer(Modifier.height(32.dp))
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SaathiSectionHeader("Your Saathi noticed")
            Spacer(Modifier.height(14.dp))
            if (insight != null) {
                SaathiInsightCard(
                    observation = insight.first,
                    supporting  = insight.second,
                    accent      = MaterialTheme.colorScheme.primary,
                )
            } else {
                SaathiEmptyState(
                    title      = "Your story starts here.",
                    message    = "Log a few check-ins and Saathi will begin learning your patterns.",
                    actionText = "Start tracking",
                    onAction   = onLogTodayClick,
                )
            }
        }

        // ── Partner note ──────────────────────────────────────────────────────
        val partnerNotes by vm.partnerNotes.collectAsState(initial = emptyList())
        partnerNotes.maxByOrNull { it.timestamp }?.textValue?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(20.dp))
            PartnerNoteRow(note = note, modifier = Modifier.padding(horizontal = 24.dp))
        }

        // ── Health Connect steps ──────────────────────────────────────────────
        if (state.healthConnectAvailable && state.todaySteps != null) {
            Spacer(Modifier.height(20.dp))
            StepsRow(
                steps    = state.todaySteps!!,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        // ── Quick actions ─────────────────────────────────────────────────────
        Spacer(Modifier.height(32.dp))
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SaathiSectionHeader("Quick actions")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SaathiQuickActionTile(
                    icon    = Icons.Outlined.EditNote,
                    label   = "Track symptoms",
                    onClick = onLogTodayClick,
                    modifier = Modifier.weight(1f),
                )
                SaathiQuickActionTile(
                    icon    = Icons.Outlined.PhotoCamera,
                    label   = "Scan skin",
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SaathiQuickActionTile(
                    icon    = Icons.Outlined.Mic,
                    label   = "Voice check-in",
                    onClick = onVoiceClick,
                    modifier = Modifier.weight(1f),
                )
                SaathiQuickActionTile(
                    icon    = Icons.Outlined.Description,
                    label   = "View report",
                    onClick = onReportClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Cycle card ────────────────────────────────────────────────────────────────

@Composable
private fun CycleCard(cycleLogs: List<CycleLog>, modifier: Modifier = Modifier) {
    val latest = cycleLogs.firstOrNull()
    val daysSince = latest?.let {
        ((System.currentTimeMillis() - it.date) / 86_400_000L + 1L).toInt().coerceAtLeast(1)
    }
    val cycleLength = latest?.cycleLength ?: 28
    val progress    = daysSince?.let { it.toFloat() / cycleLength.toFloat() } ?: 0f

    val phaseName = when {
        progress < 0.14f -> "Menstrual phase"
        progress < 0.46f -> "Follicular phase"
        progress < 0.55f -> "Ovulation window"
        else             -> "Luteal phase"
    }

    Surface(
        shape  = MaterialTheme.shapes.extraLarge,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Cycle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            if (daysSince != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Day $daysSince",
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "of $cycleLength",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    phaseName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SaathiCycleBar(progress = progress)
            } else {
                Text(
                    "No cycle logged yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Status row: 3 compact metric chips ────────────────────────────────────────

@Composable
private fun StatusRow(
    risk: RiskAssessment,
    symptom: SymptomLog?,
    modifier: Modifier = Modifier,
) {
    val riskColor = when (RiskScorer.riskLevelFromLabel(risk.riskLevel)) {
        RiskScorer.RiskLevel.LOW      -> SaathiSuccess
        RiskScorer.RiskLevel.MODERATE -> SaathiWarning
        RiskScorer.RiskLevel.HIGH     -> SaathiError
        RiskScorer.RiskLevel.CRITICAL -> SaathiPlum
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricPill(
            label = "Risk",
            value = risk.riskLevel.replaceFirstChar { it.uppercase() },
            color = riskColor,
            modifier = Modifier.weight(1f),
        )
        MetricPill(
            label = "Fatigue",
            value = symptom?.let { "${it.fatigue}/5" } ?: "–",
            color = if ((symptom?.fatigue ?: 0) <= 3) SaathiSuccess else SaathiWarning,
            modifier = Modifier.weight(1f),
        )
        MetricPill(
            label = "Acne",
            value = symptom?.let { if (it.acneScore < 0.3f) "Low" else if (it.acneScore < 0.6f) "Mild" else "Moderate" } ?: "–",
            color = if ((symptom?.acneScore ?: 0f) < 0.4f) SaathiSuccess else SaathiWarning,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape  = MaterialTheme.shapes.medium,
        color  = color.copy(alpha = 0.10f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = color)
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Partner note ──────────────────────────────────────────────────────────────

@Composable
private fun PartnerNoteRow(note: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Favorite,
                contentDescription = null,
                tint   = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("From your partner",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ── Steps row ────────────────────────────────────────────────────────────────

@Composable
private fun StepsRow(steps: Long, modifier: Modifier = Modifier) {
    val progress = (steps / 7500f).coerceIn(0f, 1f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "%,d steps today".format(steps),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            SaathiCycleBar(progress = progress,
                fillColor = SaathiSuccess.copy(alpha = 0.8f))
            Text("Goal: 7,500 steps · supports insulin sensitivity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CircularProgressIndicator(
            progress    = { progress },
            modifier    = Modifier.size(38.dp),
            strokeWidth = 4.dp,
            color       = SaathiSuccess,
            trackColor  = SaathiSuccess.copy(alpha = 0.15f),
        )
    }
}

// ── Insight logic ─────────────────────────────────────────────────────────────

private fun buildInsight(
    symptoms: List<SymptomLog>,
    cycles: List<CycleLog>,
    risk: RiskAssessment?,
): Pair<String, String>? {
    if (symptoms.isEmpty() && risk == null) return null

    // Fatigue trend: last 7 days avg vs prior
    if (symptoms.size >= 6) {
        val recent = symptoms.take(3).map { it.fatigue }.average()
        val prior  = symptoms.drop(3).take(3).map { it.fatigue }.average()
        if (recent > prior + 0.8) {
            return Pair(
                "Your energy has been lower than usual this week.",
                "Based on your recent check-ins — fatigue is trending higher.",
            )
        }
    }

    // Acne trend
    if (symptoms.size >= 4) {
        val recentAcne = symptoms.take(2).map { it.acneScore }.average()
        val priorAcne  = symptoms.drop(2).take(2).map { it.acneScore }.average()
        if (recentAcne > priorAcne + 0.15) {
            return Pair(
                "Skin activity has increased compared to your recent baseline.",
                "Your last 2 scan readings are higher than the 2 before that.",
            )
        }
    }

    // Cycle variability
    if (cycles.size >= 3) {
        val lengths = cycles.take(3).map { it.cycleLength }
        if (lengths.max() - lengths.min() > 7) {
            return Pair(
                "Your cycle length has been more variable lately.",
                "Recent cycles: ${lengths.joinToString(", ")} days — that's a ${lengths.max() - lengths.min()}-day range.",
            )
        }
    }

    // Risk-level insight
    return risk?.let {
        val levelName = it.riskLevel.replaceFirstChar { c -> c.uppercase() }
        Pair(
            "Your current risk level is $levelName.",
            "Score ${it.totalScore}/12 — based on cycle, skin, fatigue, and physical signals.",
        )
    }
}

private fun getGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in  5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else      -> "Good evening"
    }
}
