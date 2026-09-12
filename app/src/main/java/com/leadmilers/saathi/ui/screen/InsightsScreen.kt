package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.RiskScorer
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

private enum class TimeRange(val label: String, val days: Int) {
    SEVEN("7d", 7), THIRTY("30d", 30), NINETY("90d", 90)
}

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val repo    = remember { (context.applicationContext as SaathiApp).repository }

    var range by remember { mutableStateOf(TimeRange.THIRTY) }

    val cutoff = remember(range) { System.currentTimeMillis() - range.days * 86_400_000L }

    val symptoms by repo.recentSymptomLogs(90).map { list ->
        list.filter { it.date >= cutoff }
    }.collectAsState(initial = emptyList())

    val cycles by repo.recentCycleLogs(12).map { list ->
        list.filter { it.date >= cutoff }
    }.collectAsState(initial = emptyList())

    val risks by repo.recentRiskAssessments(90).map { list ->
        list.filter { it.date >= cutoff }
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(20.dp))

        // Header
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Insights",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your patterns over time.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Time range chips
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeRange.entries.forEach { r ->
                SaathiChip(
                    text     = r.label,
                    selected = range == r,
                    onClick  = { range = r },
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (symptoms.isEmpty() && cycles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                SaathiEmptyState(
                    title   = "No data for this period.",
                    message = "Log a few check-ins to start seeing your patterns here.",
                )
            }
        } else {
            // Energy trend section
            if (symptoms.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Energy & Fatigue")
                    Spacer(Modifier.height(14.dp))
                    FatigueTrendCard(symptoms)
                }
                Spacer(Modifier.height(24.dp))
            }

            // Skin trend
            val skinLogs = symptoms.filter { it.acneScore > 0f }
            if (skinLogs.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Skin Activity")
                    Spacer(Modifier.height(14.dp))
                    SkinTrendCard(skinLogs)
                }
                Spacer(Modifier.height(24.dp))
            }

            // Cycle pattern
            if (cycles.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Cycle Pattern")
                    Spacer(Modifier.height(14.dp))
                    CyclePatternCard(cycles)
                }
                Spacer(Modifier.height(24.dp))
            }

            // Risk trend
            if (risks.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Risk Trajectory")
                    Spacer(Modifier.height(14.dp))
                    RiskTrendCard(risks.map { it })
                }
                Spacer(Modifier.height(24.dp))
            }

            // Symptom flag summary
            if (symptoms.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Physical Symptoms")
                    Spacer(Modifier.height(14.dp))
                    SymptomFlagCard(symptoms)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun FatigueTrendCard(logs: List<SymptomLog>) {
    val sorted  = logs.sortedBy { it.date }
    val avg     = sorted.map { it.fatigue }.average()
    val recent  = sorted.takeLast(3).map { it.fatigue }.average()
    val prior   = if (sorted.size >= 6) sorted.dropLast(3).takeLast(3).map { it.fatigue }.average() else avg

    val trend = when {
        recent > prior + 0.5 -> "Higher lately"
        recent < prior - 0.5 -> "Lower lately"
        else                  -> "Steady"
    }
    val trendColor = when {
        recent > prior + 0.5 -> SaathiWarning
        recent < prior - 0.5 -> SaathiSuccess
        else                  -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Average fatigue", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("%.1f / 5".format(avg),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = trendColor.copy(alpha = 0.12f),
                ) {
                    Text(trend,
                        style = MaterialTheme.typography.labelMedium,
                        color = trendColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            SparkLine(
                values = sorted.map { it.fatigue.toFloat() },
                color  = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${logs.size} check-ins in this period",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkinTrendCard(logs: List<SymptomLog>) {
    val sorted   = logs.sortedBy { it.date }
    val avgScore = sorted.map { it.acneScore }.average()
    val label = when {
        avgScore < 0.20 -> "Generally clear"
        avgScore < 0.45 -> "Mild activity"
        avgScore < 0.65 -> "Moderate activity"
        else             -> "Higher activity"
    }

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Avg skin activity", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(label,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface)
                }
                Text("%.0f%%".format(avgScore * 100),
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.secondary)
            }
            SparkLine(
                values = sorted.map { it.acneScore },
                color  = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "${logs.size} skin scans · Camera AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CyclePatternCard(cycles: List<CycleLog>) {
    val sorted  = cycles.sortedByDescending { it.date }
    val lengths = sorted.map { it.cycleLength }
    val avg     = lengths.average()
    val variance = lengths.maxOrNull()!! - lengths.minOrNull()!!

    val regularity = when {
        variance <= 2 -> "Very regular"
        variance <= 5 -> "Fairly regular"
        variance <= 8 -> "Some variability"
        else          -> "Irregular"
    }
    val regularityColor = when {
        variance <= 2 -> SaathiSuccess
        variance <= 5 -> SaathiSuccess
        variance <= 8 -> SaathiWarning
        else          -> SaathiError
    }

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Avg cycle length", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("%.0f days".format(avg),
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = regularityColor.copy(alpha = 0.12f),
                ) {
                    Text(regularity,
                        style = MaterialTheme.typography.labelMedium,
                        color = regularityColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            if (variance > 0) {
                Text(
                    "Range: ${lengths.minOrNull()} – ${lengths.maxOrNull()} days across ${cycles.size} cycles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RiskTrendCard(risks: List<com.leadmilers.saathi.data.entity.RiskAssessment>) {
    val sorted = risks.sortedBy { it.date }
    val recent = sorted.lastOrNull()
    val prior  = if (sorted.size >= 2) sorted[sorted.size - 2] else null

    val direction = when {
        prior == null                    -> "First reading"
        recent!!.totalScore < prior.totalScore - 1 -> "Improving"
        recent.totalScore > prior.totalScore + 1 -> "Watch closely"
        else                             -> "Stable"
    }
    val directionColor = when (direction) {
        "Improving"    -> SaathiSuccess
        "Watch closely"-> SaathiWarning
        else           -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Current risk score", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("${recent?.totalScore ?: "–"} / 12",
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = directionColor.copy(alpha = 0.12f),
                ) {
                    Text(direction,
                        style = MaterialTheme.typography.labelMedium,
                        color = directionColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            SparkLine(
                values = sorted.map { it.totalScore.toFloat() },
                color  = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Composite of cycle, skin, fatigue, and physical signals",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SymptomFlagCard(logs: List<SymptomLog>) {
    val skinDarkeningDays = logs.count { it.skinDarkening }
    val weightGainDays    = logs.count { it.weightGain }
    val hairIssuesDays    = logs.count { it.hairIssues }
    val total             = logs.size

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SymptomFlagRow("Skin darkening", skinDarkeningDays, total)
            SaathiDivider()
            SymptomFlagRow("Weight changes noticed", weightGainDays, total)
            SaathiDivider()
            SymptomFlagRow("Hair concerns", hairIssuesDays, total)
            Spacer(Modifier.height(4.dp))
            Text(
                "Logged on $total days in this period",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SymptomFlagRow(label: String, count: Int, total: Int) {
    val pct   = if (total > 0) count * 100 / total else 0
    val color = if (pct >= 30) SaathiWarning else SaathiSuccess
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            if (count > 0) "$count / $total days" else "Not flagged",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Minimal canvas-free spark line using linear progress bars
@Composable
private fun SparkLine(values: List<Float>, color: androidx.compose.ui.graphics.Color) {
    if (values.size < 2) return
    val max = values.maxOrNull()?.coerceAtLeast(0.01f) ?: return
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            val frac = (v / max).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(frac)
                    .background(
                        color  = color.copy(alpha = 0.55f),
                        shape  = MaterialTheme.shapes.extraSmall,
                    )
            )
        }
    }
}
