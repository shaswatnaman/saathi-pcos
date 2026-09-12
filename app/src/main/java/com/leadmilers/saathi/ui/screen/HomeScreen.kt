package com.leadmilers.saathi.ui.screen

import android.graphics.Color as GColor
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.RiskScorer
import com.leadmilers.saathi.office.OfficeBridge
import com.leadmilers.saathi.report.ReportGenerator
import com.leadmilers.saathi.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class SignalConfidence(val label: String, val color: Color) {
    VALIDATED("Validated", Color(0xFF4CAF50)),
    EXPLORATORY("Exploratory", Color(0xFFFF9800)),
    EXPERIMENTAL("Experimental", Color(0xFF9E9E9E))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLogTodayClick: () -> Unit = {}) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val isOfficeAvailable = remember { OfficeBridge.isAvailable(context) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onLogTodayClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Today")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Saathi",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "PCOS Cross-Signal Tracker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOfficeAvailable) OfficeStatusChip()
                }

                RiskLevelCard(state.latestRisk)

                val latest = state.recentSymptomLogs.firstOrNull()
                MetricGrid(latest, state.recentCycleLogs.firstOrNull()?.cycleLength)

                if (state.recentSymptomLogs.size >= 2) {
                    SparklineCard(state.recentSymptomLogs, state.latestRisk)
                }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val repo = (context.applicationContext as SaathiApp).repository
                                val risks = repo.recentRiskAssessments(30).first()
                                val symptoms = repo.recentSymptomLogs(30).first()
                                val pdfPath = ReportGenerator.generate(context, risks, symptoms)
                                if (isOfficeAvailable) {
                                    val msg = OfficeBridge.sendHealthReport(
                                        context, pdfPath, risks.firstOrNull(), symptoms.firstOrNull()
                                    )
                                    snackbarHost.showSnackbar(msg)
                                } else {
                                    snackbarHost.showSnackbar("Report saved to Downloads")
                                }
                            } catch (e: Exception) {
                                snackbarHost.showSnackbar("Report failed: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOfficeAvailable)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isOfficeAvailable)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        if (isOfficeAvailable) Icons.Default.Laptop else Icons.Default.Assessment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isOfficeAvailable) "Generate & Send to Doctor" else "Generate PDF Report")
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun OfficeStatusChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Laptop,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "iQOO Office",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RiskLevelCard(risk: RiskAssessment?) {
    val level = risk?.riskLevel ?: "No data yet"
    val score = risk?.totalScore ?: 0
    val riskLevel = RiskScorer.riskLevelFromLabel(level)
    val color = riskLevelColor(riskLevel)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Current Risk Level", color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge)
            Text(level.uppercase(), color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 28.sp)
            if (risk != null) {
                LinearProgressIndicator(
                    progress = { score / 12f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Text("Score: $score / 12  •  Probability: ${(getProbability(risk) * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "Not a medical diagnosis — consult a gynaecologist for evaluation",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private data class MetricInfo(
    val label: String,
    val value: String,
    val isGood: Boolean,
    val confidence: SignalConfidence
)

@Composable
private fun MetricGrid(symptom: SymptomLog?, cycleLength: Int?) {
    val metrics = listOf(
        MetricInfo("Cycle", cycleLength?.let { "${it}d" } ?: "—",
            cycleLength?.let { it in 21..35 } ?: true, SignalConfidence.VALIDATED),
        MetricInfo("Acne", symptom?.let { "%.0f%%".format(it.acneScore * 100) } ?: "—",
            (symptom?.acneScore ?: 0f) < 0.5f, SignalConfidence.EXPLORATORY),
        MetricInfo("Voice", symptom?.let { "%.0f%%".format(it.voiceEnergyScore * 100) } ?: "—",
            (symptom?.voiceEnergyScore ?: 1f) >= 0.6f, SignalConfidence.EXPERIMENTAL),
        MetricInfo("Fatigue", symptom?.let { "${it.fatigue}/5" } ?: "—",
            (symptom?.fatigue ?: 0) <= 3, SignalConfidence.VALIDATED)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        metrics.forEach { metric ->
            MetricTile(metric, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(metric: MetricInfo, modifier: Modifier) {
    val color = if (metric.isGood) Color(0xFF4CAF50) else Color(0xFFF44336)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(metric.value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(metric.label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                metric.confidence.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = metric.confidence.color.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun SparklineCard(logs: List<SymptomLog>, latestRisk: RiskAssessment?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("30-Day Trend", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val acneColor = Color(0xFFE91E63).toArgb()
            val voiceColor = Color(0xFF2196F3).toArgb()
            val fatigueColor = Color(0xFFFF9800).toArgb()

            AndroidView(
                factory = { ctx ->
                    LineChart(ctx).apply {
                        description.isEnabled = false
                        legend.isEnabled = false
                        axisRight.isEnabled = false
                        setTouchEnabled(false)
                        setBackgroundColor(GColor.TRANSPARENT)
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                            textSize = 9f
                            granularity = 1f
                        }
                        axisLeft.apply {
                            axisMinimum = 0f; axisMaximum = 1f
                            setLabelCount(3, true)
                            textSize = 9f
                        }
                    }
                },
                update = { chart ->
                    val sorted = logs.reversed()
                    val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
                    val labels = sorted.map { fmt.format(Date(it.date)) }

                    fun mkSet(vals: List<Float>, color: Int, label: String) =
                        LineDataSet(vals.mapIndexed { i, v -> Entry(i.toFloat(), v) }, label).apply {
                            this.color = color; setDrawCircles(false); lineWidth = 1.8f
                            setDrawValues(false); mode = LineDataSet.Mode.CUBIC_BEZIER
                            fillAlpha = 50; setDrawFilled(true); fillColor = color
                        }

                    chart.data = LineData(
                        mkSet(sorted.map { it.acneScore }, acneColor, "Acne"),
                        mkSet(sorted.map { it.voiceEnergyScore }, voiceColor, "Voice"),
                        mkSet(sorted.map { it.fatigue / 5f }, fatigueColor, "Fatigue")
                    )
                    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    chart.xAxis.setLabelCount(minOf(7, labels.size), false)
                    chart.invalidate()
                },
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendDot("Acne", Color(0xFFE91E63))
                LegendDot("Voice", Color(0xFF2196F3))
                LegendDot("Fatigue", Color(0xFFFF9800))
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun riskLevelColor(level: RiskScorer.RiskLevel) = when (level) {
    RiskScorer.RiskLevel.LOW      -> Color(0xFF4CAF50)
    RiskScorer.RiskLevel.MODERATE -> Color(0xFFFF9800)
    RiskScorer.RiskLevel.HIGH     -> Color(0xFFF44336)
    RiskScorer.RiskLevel.CRITICAL -> Color(0xFF9C27B0)
}

private fun getProbability(risk: RiskAssessment): Float {
    val result = RiskScorer.riskLevelFromLabel(risk.riskLevel)
    return when (result) {
        RiskScorer.RiskLevel.CRITICAL -> 0.75f
        RiskScorer.RiskLevel.HIGH     -> 0.57f
        RiskScorer.RiskLevel.MODERATE -> 0.42f
        RiskScorer.RiskLevel.LOW      -> 0.20f
    }
}

