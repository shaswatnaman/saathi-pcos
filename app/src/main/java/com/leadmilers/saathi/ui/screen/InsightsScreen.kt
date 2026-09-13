package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

private enum class TimeRange(val label: String, val days: Int) {
    SEVEN("7d", 7), THIRTY("30d", 30), NINETY("90d", 90)
}

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val repo    = remember { (context.applicationContext as SaathiApp).repository }

    var topTab  by remember { mutableIntStateOf(0) }
    val topTabs = listOf("Charts", "Cycles", "Symptoms", "Risk")

    var range   by remember { mutableStateOf(TimeRange.THIRTY) }
    val cutoff  = remember(range) { System.currentTimeMillis() - range.days * 86_400_000L }

    val symptomsFlow = remember(cutoff) { repo.recentSymptomLogs(90).map { it.filter { s -> s.date >= cutoff } } }
    val symptoms by symptomsFlow.collectAsState(initial = emptyList())

    val cyclesFlow = remember(cutoff) { repo.recentCycleLogs(12).map { it.filter { c -> c.date >= cutoff } } }
    val cycles by cyclesFlow.collectAsState(initial = emptyList())

    val risksFlow = remember(cutoff) { repo.recentRiskAssessments(90).map { it.filter { r -> r.date >= cutoff } } }
    val risks by risksFlow.collectAsState(initial = emptyList())

    // For log tabs we want ALL data, not time-filtered
    val allSymptoms by repo.recentSymptomLogs(200).collectAsState(initial = emptyList())
    val allCycles   by repo.recentCycleLogs(100).collectAsState(initial = emptyList())
    val allRisks    by repo.recentRiskAssessments(200).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(20.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Insights", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Your patterns over time.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(16.dp))

        TabRow(selectedTabIndex = topTab, containerColor = MaterialTheme.colorScheme.background) {
            topTabs.forEachIndexed { i, title ->
                Tab(selected = topTab == i, onClick = { topTab = i },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) })
            }
        }

        when (topTab) {
            0 -> ChartsTab(range, { range = it }, symptoms, cycles, risks)
            1 -> CycleLogsTab(allCycles)
            2 -> SymptomLogsTab(allSymptoms)
            3 -> RiskLogsTab(allRisks)
        }
    }
}

// ── Charts tab (existing content) ────────────────────────────────────────────

@Composable
private fun ChartsTab(
    range: TimeRange,
    onRange: (TimeRange) -> Unit,
    symptoms: List<SymptomLog>,
    cycles: List<CycleLog>,
    risks: List<RiskAssessment>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeRange.entries.forEach { r ->
                SaathiChip(text = r.label, selected = range == r, onClick = { onRange(r) })
            }
        }
        Spacer(Modifier.height(24.dp))

        if (symptoms.isEmpty() && cycles.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SaathiEmptyState(title = "No data for this period.", message = "Log a few check-ins to start seeing your patterns here.")
            }
        } else {
            if (symptoms.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Energy & Fatigue"); Spacer(Modifier.height(14.dp))
                    FatigueTrendCard(symptoms)
                }
                Spacer(Modifier.height(24.dp))
            }
            val skinLogs = symptoms.filter { it.acneScore > 0f }
            if (skinLogs.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Skin Activity"); Spacer(Modifier.height(14.dp))
                    SkinTrendCard(skinLogs)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (skinLogs.size >= 3 && cycles.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Acne Calibration"); Spacer(Modifier.height(14.dp))
                    AcneCalibrationCard(skinLogs, cycles)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (cycles.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Cycle Pattern"); Spacer(Modifier.height(14.dp))
                    CyclePatternCard(cycles)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (risks.size >= 2) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Risk Trajectory"); Spacer(Modifier.height(14.dp))
                    RiskTrendCard(risks)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (symptoms.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaathiSectionHeader("Physical Symptoms"); Spacer(Modifier.height(14.dp))
                    SymptomFlagCard(symptoms)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

// ── Cycle Logs tab ────────────────────────────────────────────────────────────

@Composable
private fun CycleLogsTab(cycles: List<CycleLog>) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    if (cycles.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            SaathiEmptyState(title = "No cycle logs yet.", message = "Start tracking your cycle to see entries here.")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${cycles.size} entries", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        items(cycles) { c ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(fmt.format(Date(c.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${c.cycleLength} days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val irreg = c.cycleLength > 35 || c.cycleLength < 21
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowChip(c.flowIntensity)
                        if (irreg) IrregularChip()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Symptom Logs tab ──────────────────────────────────────────────────────────

@Composable
private fun SymptomLogsTab(symptoms: List<SymptomLog>) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    if (symptoms.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            SaathiEmptyState(title = "No symptom logs yet.", message = "Log your daily symptoms to see them here.")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${symptoms.size} entries", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        items(symptoms) { s ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(s.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FatigueBar(s.fatigue)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        if (s.acneScore > 0f) InsightChip("Acne ${"%.0f".format(s.acneScore * 100)}%")
                        if (s.skinDarkening) InsightChip("Skin darkening", warn = true)
                        if (s.hairIssues)    InsightChip("Hair issues", warn = true)
                        if (s.weightGain)    InsightChip("Weight gain", warn = true)
                    }
                    if (s.weight > 0f || s.waistHip > 0f) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (s.weight > 0f)   Text("Weight: ${"%.1f".format(s.weight)} kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (s.waistHip > 0f) Text("Waist-Hip: ${"%.2f".format(s.waistHip)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Risk Logs tab ─────────────────────────────────────────────────────────────

@Composable
private fun RiskLogsTab(risks: List<RiskAssessment>) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    if (risks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            SaathiEmptyState(title = "No risk assessments yet.", message = "Complete a health check-in to see your risk scores here.")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${risks.size} assessments", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        items(risks) { r ->
            val riskColor = when {
                r.totalScore >= 70 -> SaathiError
                r.totalScore >= 50 -> SaathiWarning
                else               -> SaathiSuccess
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(r.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${r.totalScore}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = riskColor)
                    }
                    Surface(shape = MaterialTheme.shapes.small, color = riskColor.copy(alpha = 0.12f)) {
                        Text(r.riskLevel, style = MaterialTheme.typography.labelSmall, color = riskColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScoreItem("Cycle", r.cycleScore)
                        ScoreItem("Skin", r.acneScore)
                        ScoreItem("Fatigue", r.fatigueScore)
                        ScoreItem("Physical", r.physicalScore)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Small helpers ──────────────────────────────────────────────────────────────

@Composable
private fun ScoreItem(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$score", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FatigueBar(level: Int) {
    val color = if (level >= 4) SaathiWarning else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Fatigue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (1..5).forEach { i ->
                Surface(shape = MaterialTheme.shapes.extraSmall,
                    color = if (i <= level) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.size(width = 10.dp, height = 16.dp)) {}
            }
        }
    }
}

@Composable
private fun FlowChip(flow: String) {
    if (flow.isBlank()) return
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
        Text(flow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun IrregularChip() {
    Surface(shape = MaterialTheme.shapes.small, color = SaathiWarning.copy(alpha = 0.12f)) {
        Text("Irregular", style = MaterialTheme.typography.labelSmall, color = SaathiWarning,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun InsightChip(label: String, warn: Boolean = false) {
    val bg = if (warn) SaathiWarning.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    val fg = if (warn) SaathiWarning else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.small, color = bg) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ── Chart cards (unchanged) ────────────────────────────────────────────────────

@Composable
private fun FatigueTrendCard(logs: List<SymptomLog>) {
    val sorted  = logs.sortedBy { it.date }
    val avg     = sorted.map { it.fatigue }.average()
    val recent  = sorted.takeLast(3).map { it.fatigue }.average()
    val prior   = if (sorted.size >= 6) sorted.dropLast(3).takeLast(3).map { it.fatigue }.average() else avg
    val trend = when {
        recent > prior + 0.5 -> "Higher lately"; recent < prior - 0.5 -> "Lower lately"; else -> "Steady"
    }
    val trendColor = when {
        recent > prior + 0.5 -> SaathiWarning; recent < prior - 0.5 -> SaathiSuccess; else -> MaterialTheme.colorScheme.primary
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Average fatigue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("%.1f / 5".format(avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = MaterialTheme.shapes.small, color = trendColor.copy(alpha = 0.12f)) {
                    Text(trend, style = MaterialTheme.typography.labelMedium, color = trendColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            SparkLine(values = sorted.map { it.fatigue.toFloat() }, color = MaterialTheme.colorScheme.primary)
            Text("${logs.size} check-ins in this period", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SkinTrendCard(logs: List<SymptomLog>) {
    val sorted   = logs.sortedBy { it.date }
    val avgScore = sorted.map { it.acneScore }.average()
    val label = when { avgScore < 0.20 -> "Generally clear"; avgScore < 0.45 -> "Mild activity"; avgScore < 0.65 -> "Moderate activity"; else -> "Higher activity" }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Avg skin activity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Text("%.0f%%".format(avgScore * 100), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            }
            SparkLine(values = sorted.map { it.acneScore }, color = MaterialTheme.colorScheme.secondary)
            Text("${logs.size} skin scans · Camera AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CyclePatternCard(cycles: List<CycleLog>) {
    val sorted  = cycles.sortedByDescending { it.date }
    val lengths = sorted.map { it.cycleLength }
    val avg     = lengths.average()
    val variance = lengths.maxOrNull()!! - lengths.minOrNull()!!
    val regularity = when { variance <= 2 -> "Very regular"; variance <= 5 -> "Fairly regular"; variance <= 8 -> "Some variability"; else -> "Irregular" }
    val regularityColor = when { variance <= 5 -> SaathiSuccess; variance <= 8 -> SaathiWarning; else -> SaathiError }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Avg cycle length", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("%.0f days".format(avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = MaterialTheme.shapes.small, color = regularityColor.copy(alpha = 0.12f)) {
                    Text(regularity, style = MaterialTheme.typography.labelMedium, color = regularityColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            if (variance > 0) Text("Range: ${lengths.minOrNull()} – ${lengths.maxOrNull()} days across ${cycles.size} cycles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RiskTrendCard(risks: List<RiskAssessment>) {
    val sorted = risks.sortedBy { it.date }
    val recent = sorted.lastOrNull()
    val prior  = if (sorted.size >= 2) sorted[sorted.size - 2] else null
    val direction = when {
        prior == null -> "First reading"; recent!!.totalScore < prior.totalScore - 1 -> "Improving"
        recent.totalScore > prior.totalScore + 1 -> "Watch closely"; else -> "Stable"
    }
    val directionColor = when (direction) { "Improving" -> SaathiSuccess; "Watch closely" -> SaathiWarning; else -> MaterialTheme.colorScheme.primary }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Current risk score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("${recent?.totalScore ?: "–"} / 100", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = MaterialTheme.shapes.small, color = directionColor.copy(alpha = 0.12f)) {
                    Text(direction, style = MaterialTheme.typography.labelMedium, color = directionColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            SparkLine(values = sorted.map { it.totalScore.toFloat() }, color = MaterialTheme.colorScheme.primary)
            Text("Composite of cycle, skin, fatigue, and physical signals", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SymptomFlagCard(logs: List<SymptomLog>) {
    val skinDarkeningDays = logs.count { it.skinDarkening }
    val weightGainDays    = logs.count { it.weightGain }
    val hairIssuesDays    = logs.count { it.hairIssues }
    val total             = logs.size
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SymptomFlagRow("Skin darkening", skinDarkeningDays, total)
            SaathiDivider()
            SymptomFlagRow("Weight changes noticed", weightGainDays, total)
            SaathiDivider()
            SymptomFlagRow("Hair concerns", hairIssuesDays, total)
            Spacer(Modifier.height(4.dp))
            Text("Logged on $total days in this period", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SymptomFlagRow(label: String, count: Int, total: Int) {
    val pct   = if (total > 0) count * 100 / total else 0
    val color = if (pct >= 30) SaathiWarning else SaathiSuccess
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            if (count > 0) "$count / $total days" else "Not flagged",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SparkLine(values: List<Float>, color: androidx.compose.ui.graphics.Color) {
    if (values.size < 2) return
    val areaColor = color.copy(alpha = 0.12f)
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        val max = values.maxOrNull()?.coerceAtLeast(0.01f) ?: return@Canvas
        val min = values.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(0.01f)
        val w = size.width; val h = size.height
        val step = w / (values.size - 1)
        fun xOf(i: Int) = i * step
        fun yOf(v: Float) = h - ((v - min) / range) * h * 0.85f - h * 0.05f
        val area = Path().apply {
            moveTo(xOf(0), h); lineTo(xOf(0), yOf(values[0]))
            for (i in 1 until values.size) { val cx = (xOf(i - 1) + xOf(i)) / 2f; cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i])) }
            lineTo(xOf(values.size - 1), h); close()
        }
        drawPath(area, color = areaColor)
        val line = Path().apply {
            moveTo(xOf(0), yOf(values[0]))
            for (i in 1 until values.size) { val cx = (xOf(i - 1) + xOf(i)) / 2f; cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i])) }
        }
        drawPath(line, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = color, radius = 4f, center = Offset(xOf(values.size - 1), yOf(values.last())))
    }
}

@Composable
private fun AcneCalibrationCard(skinLogs: List<SymptomLog>, cycles: List<CycleLog>) {
    val buckets = FloatArray(28) { 0f }; val counts = IntArray(28) { 0 }
    val sortedCycles = cycles.sortedBy { it.date }
    skinLogs.forEach { log ->
        val cycleStart = sortedCycles.lastOrNull { it.date <= log.date }?.date ?: return@forEach
        val dayIndex   = ((log.date - cycleStart) / 86_400_000L).toInt().coerceIn(0, 27)
        buckets[dayIndex] += log.acneScore; counts[dayIndex]++
    }
    val avgs   = FloatArray(28) { i -> if (counts[i] > 0) buckets[i] / counts[i] else 0f }
    val peak   = avgs.maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
    val areaFill  = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
    val lineStroke = MaterialTheme.colorScheme.secondary
    val phaseColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Acne severity by cycle day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                val w = size.width; val h = size.height; val step = w / 27f
                fun xOf(d: Int) = d * step
                fun yOf(v: Float) = h - (v / peak) * h * 0.85f - h * 0.05f
                val area = Path().apply {
                    moveTo(xOf(0), h); lineTo(xOf(0), yOf(avgs[0]))
                    for (d in 1..27) { val cx = (xOf(d - 1) + xOf(d)) / 2f; cubicTo(cx, yOf(avgs[d - 1]), cx, yOf(avgs[d]), xOf(d), yOf(avgs[d])) }
                    lineTo(xOf(27), h); close()
                }
                drawPath(area, color = areaFill)
                val line = Path().apply {
                    moveTo(xOf(0), yOf(avgs[0]))
                    for (d in 1..27) { val cx = (xOf(d - 1) + xOf(d)) / 2f; cubicTo(cx, yOf(avgs[d - 1]), cx, yOf(avgs[d]), xOf(d), yOf(avgs[d])) }
                }
                drawPath(line, color = lineStroke, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                listOf(0, 7, 14, 21).forEach { d -> drawLine(color = phaseColor.copy(alpha = 0.25f), start = Offset(xOf(d), 0f), end = Offset(xOf(d), h), strokeWidth = 1f) }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Menstrual", "Follicular", "Ovulation", "Luteal").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
            val peakDay = avgs.indices.maxByOrNull { avgs[it] } ?: 0
            if (avgs[peakDay] > 0.05f) Text("Peak acne on cycle day ${peakDay + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text("Not enough data yet — keep logging skin scans", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
