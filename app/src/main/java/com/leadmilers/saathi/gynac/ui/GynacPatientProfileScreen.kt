package com.leadmilers.saathi.gynac.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.companion.SyncPacket
import com.leadmilers.saathi.gynac.*
import com.leadmilers.saathi.prefs.UserPrefs
import com.leadmilers.saathi.report.GynacReportGenerator
import com.leadmilers.saathi.ui.theme.SaathiError
import com.leadmilers.saathi.ui.theme.SaathiSuccess
import com.leadmilers.saathi.ui.theme.SaathiWarning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GynacPatientProfileScreen(
    patientId: String,
    onBack:    () -> Unit,
) {
    val context = LocalContext.current
    val app     = context.applicationContext as SaathiApp
    val patient = remember(patientId) {
        if (patientId == "live_patient") app.currentLivePatient
        else GynacDemoData.patients.find { it.id == patientId }
    } ?: return

    val scope       = rememberCoroutineScope()
    val snackbar    = remember { SnackbarHostState() }
    var pdfBusy     by remember { mutableStateOf(false) }
    val doctorName  = remember { app.userPrefs.userName.let { if (it.startsWith("Dr.", true)) it else "Dr. $it" } }

    val syncPacket: SyncPacket? = remember(patientId) {
        if (patientId == "live_patient") app.currentLiveSyncPacket else null
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (syncPacket != null)
        listOf("Overview", "Timeline", "Logs", "Medications", "Notes")
    else
        listOf("Overview", "Timeline", "Medications", "Notes")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${patient.age}y · ${patient.diagnosis} · ${patient.patientCode}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    StatusBadge(status = patient.status)
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (!pdfBusy) {
                                pdfBusy = true
                                scope.launch {
                                    try {
                                        val path = GynacReportGenerator.generate(context, patient, doctorName)
                                        snackbar.showSnackbar("Report saved to Downloads")
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(path), "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, "Open PDF"))
                                        }
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar("Failed: ${e.message}")
                                    } finally {
                                        pdfBusy = false
                                    }
                                }
                            }
                        },
                    ) {
                        if (pdfBusy)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF",
                                tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewTab(patient)
                1 -> TimelineTab(patient)
                2 -> if (syncPacket != null) LogsTab(syncPacket) else MedicationsTab(patient)
                3 -> if (syncPacket != null) MedicationsTab(patient) else NotesTab(patient)
                4 -> NotesTab(patient)
            }
        }
    }
}

// ── Overview ──────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(patient: GynacPatient) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Visit brief
        item {
            VisitBriefCard(patient)
        }
        // Alerts
        if (patient.alerts.isNotEmpty()) {
            item {
                SectionLabel("Active Alerts")
            }
            items(patient.alerts) { alert ->
                AlertRow(alert)
            }
        }
        // Health metrics
        item {
            SectionLabel("Health Snapshot")
            Spacer(Modifier.height(8.dp))
            MetricsGrid(patient)
        }
    }
}

@Composable
private fun VisitBriefCard(patient: GynacPatient) {
    val nextVisit = patient.nextFollowUpDays
    val urgency   = when (patient.status) {
        GynacStatus.NEEDS_REVIEW -> SaathiError
        GynacStatus.FOLLOW_UP    -> SaathiWarning
        else                     -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape    = MaterialTheme.shapes.extraLarge,
        color    = urgency.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EventNote, contentDescription = null, tint = urgency, modifier = Modifier.size(18.dp))
                Text("Visit Brief", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = urgency)
            }
            Text("Last visit: ${patient.lastVisitDays} days ago",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (nextVisit != null) {
                Text(
                    if (nextVisit <= 0) "Follow-up overdue" else "Next follow-up in $nextVisit days",
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (nextVisit <= 3) SaathiError else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text("Primary: ${patient.primaryMedication} ${patient.primaryMedDose}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertRow(alert: GynacAlert) {
    val color = when (alert.severity) {
        AlertSev.URGENT  -> SaathiError
        AlertSev.WARNING -> SaathiWarning
        AlertSev.INFO    -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            when (alert.severity) {
                AlertSev.URGENT  -> Icons.Outlined.Error
                AlertSev.WARNING -> Icons.Outlined.Warning
                AlertSev.INFO    -> Icons.Outlined.Info
            },
            contentDescription = null,
            tint     = color,
            modifier = Modifier.size(16.dp),
        )
        Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("${alert.daysAgo}d ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricsGrid(patient: GynacPatient) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile("Cycle", "${patient.recentCycleLength}d", patient.cycleTrend, modifier = Modifier.weight(1f))
        MetricTile("Sleep",  "${patient.sleepAvg}h",         patient.sleepTrend,  modifier = Modifier.weight(1f))
        MetricTile("Acne",   patient.acneSeverity,            patient.acneTrend,   modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(label: String, value: String, trend: HealthTrend, modifier: Modifier = Modifier) {
    val (trendIcon, trendColor) = when (trend) {
        HealthTrend.IMPROVING -> Icons.Outlined.TrendingUp   to MaterialTheme.colorScheme.tertiary
        HealthTrend.WORSENING -> Icons.Outlined.TrendingDown to SaathiError
        HealthTrend.STABLE    -> Icons.Outlined.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
        HealthTrend.VARIABLE  -> Icons.Outlined.TrendingFlat to SaathiWarning
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(trendIcon, contentDescription = null, tint = trendColor, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Timeline ──────────────────────────────────────────────────────────────────

@Composable
private fun TimelineTab(patient: GynacPatient) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel("Cycle Length — last ${patient.cycleHistory.size} cycles")
            Spacer(Modifier.height(8.dp))
            TrackChart(
                values = patient.cycleHistory.map { it.toFloat() },
                label  = "days",
                color  = MaterialTheme.colorScheme.primary,
                minVal = 20f, maxVal = 40f,
                annotations = listOf(28f to "Normal"),
            )
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Sleep Quality — last 30 days")
            Spacer(Modifier.height(8.dp))
            TrackChart(
                values = patient.sleepHistory,
                label  = "hrs",
                color  = MaterialTheme.colorScheme.tertiary,
                minVal = 3f, maxVal = 10f,
                annotations = listOf(7f to "Target"),
            )
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Acne Severity — last 30 days")
            Spacer(Modifier.height(8.dp))
            TrackChart(
                values = patient.acneHistory,
                label  = "score",
                color  = MaterialTheme.colorScheme.secondary,
                minVal = 0f, maxVal = 1f,
                annotations = listOf(0.5f to "Moderate"),
            )
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Multi-Signal Overview")
            Spacer(Modifier.height(8.dp))
            MultiSignalChart(patient)
        }
    }
}

@Composable
private fun TrackChart(
    values:      List<Float>,
    label:       String,
    color:       Color,
    minVal:      Float,
    maxVal:      Float,
    annotations: List<Pair<Float, String>> = emptyList(),
) {
    if (values.size < 2) return
    val areaColor  = color.copy(alpha = 0.12f)
    val gridColor  = color.copy(alpha = 0.15f)
    val annotColor = color.copy(alpha = 0.55f)
    val avg        = values.average().toFloat()

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("avg %.1f".format(avg), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                val w = size.width
                val h = size.height
                val range = (maxVal - minVal).coerceAtLeast(0.01f)
                val step  = w / (values.size - 1).coerceAtLeast(1)

                fun xOf(i: Int) = i * step
                fun yOf(v: Float) = h - ((v - minVal) / range) * h * 0.85f - h * 0.05f

                // annotation lines
                annotations.forEach { (av, _) ->
                    val ay = yOf(av)
                    drawLine(color = annotColor, start = Offset(0f, ay), end = Offset(w, ay), strokeWidth = 1f, pathEffect = null)
                }

                // area fill
                val area = Path().apply {
                    moveTo(xOf(0), h)
                    lineTo(xOf(0), yOf(values[0]))
                    for (i in 1 until values.size) {
                        val cx = (xOf(i - 1) + xOf(i)) / 2f
                        cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i]))
                    }
                    lineTo(xOf(values.size - 1), h)
                    close()
                }
                drawPath(area, color = areaColor)

                // line
                val line = Path().apply {
                    moveTo(xOf(0), yOf(values[0]))
                    for (i in 1 until values.size) {
                        val cx = (xOf(i - 1) + xOf(i)) / 2f
                        cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i]))
                    }
                }
                drawPath(line, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // dots at first and last
                drawCircle(color = color, radius = 4f, center = Offset(xOf(0), yOf(values.first())))
                drawCircle(color = color, radius = 5f, center = Offset(xOf(values.size - 1), yOf(values.last())))
            }

            // annotation labels
            if (annotations.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    annotations.forEach { (_, lbl) ->
                        Text("— $lbl", style = MaterialTheme.typography.labelSmall, color = annotColor)
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSignalChart(patient: GynacPatient) {
    val sleepN  = patient.sleepHistory.takeLast(10)
    val acneN   = patient.acneHistory.takeLast(10)
    val count   = minOf(sleepN.size, acneN.size).coerceAtLeast(2)
    if (count < 2) return

    val sleepColor = MaterialTheme.colorScheme.tertiary
    val acneColor  = MaterialTheme.colorScheme.secondary

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sleep vs Acne (last 10 days)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                val w = size.width
                val h = size.height
                val step = w / (count - 1).coerceAtLeast(1)

                fun xOf(i: Int) = i * step.toFloat()

                // sleep: scale 3-10
                fun sleepY(v: Float) = h - ((v - 3f) / 7f) * h * 0.85f - h * 0.05f
                // acne: scale 0-1
                fun acneY(v: Float)  = h - (v / 1f) * h * 0.85f - h * 0.05f

                listOf(
                    sleepN.take(count) to Pair(sleepColor, ::sleepY),
                    acneN.take(count)  to Pair(acneColor,  ::acneY),
                ).forEach { (vals, colorFn) ->
                    val (c, yFn) = colorFn
                    val area = Path().apply {
                        moveTo(xOf(0), h)
                        lineTo(xOf(0), yFn(vals[0]))
                        for (i in 1 until count) {
                            val cx = (xOf(i - 1) + xOf(i)) / 2f
                            cubicTo(cx, yFn(vals[i - 1]), cx, yFn(vals[i]), xOf(i), yFn(vals[i]))
                        }
                        lineTo(xOf(count - 1), h)
                        close()
                    }
                    drawPath(area, color = c.copy(alpha = 0.10f))

                    val line = Path().apply {
                        moveTo(xOf(0), yFn(vals[0]))
                        for (i in 1 until count) {
                            val cx = (xOf(i - 1) + xOf(i)) / 2f
                            cubicTo(cx, yFn(vals[i - 1]), cx, yFn(vals[i]), xOf(i), yFn(vals[i]))
                        }
                    }
                    drawPath(line, color = c, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp, 3.dp).background(sleepColor, MaterialTheme.shapes.small))
                    Text("Sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp, 3.dp).background(acneColor, MaterialTheme.shapes.small))
                    Text("Acne", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Medications ───────────────────────────────────────────────────────────────

@Composable
private fun MedicationsTab(patient: GynacPatient) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val active   = patient.medications.filter { it.active }
        val inactive = patient.medications.filter { !it.active }

        if (active.isNotEmpty()) {
            item { SectionLabel("Active") }
            items(active) { med -> MedicationCard(med) }
        }
        if (inactive.isNotEmpty()) {
            item { Spacer(Modifier.height(4.dp)); SectionLabel("Past") }
            items(inactive) { med -> MedicationCard(med) }
        }
    }
}

@Composable
private fun MedicationCard(med: GynacMedication) {
    Surface(
        shape    = MaterialTheme.shapes.large,
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Medication,
                contentDescription = null,
                tint     = if (med.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${med.name} ${med.dose}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("${med.frequency} · from ${med.startDate}${med.endDate?.let { " to $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (med.notes.isNotBlank()) {
                    Text(med.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (med.active) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Notes ──────────────────────────────────────────────────────────────────────

@Composable
private fun NotesTab(patient: GynacPatient) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionLabel("Clinical Notes") }
        items(patient.clinicalNotes) { note ->
            NoteCard(note)
        }
    }
}

@Composable
private fun NoteCard(note: GynacNote) {
    val (icon, color) = when (note.type) {
        NoteType.CONSULTATION -> Icons.Outlined.Chat          to MaterialTheme.colorScheme.primary
        NoteType.ASSESSMENT   -> Icons.Outlined.Assignment    to MaterialTheme.colorScheme.secondary
        NoteType.PLAN         -> Icons.Outlined.Checklist     to MaterialTheme.colorScheme.tertiary
        NoteType.PATIENT_NOTE -> Icons.Outlined.Person        to MaterialTheme.colorScheme.onSurfaceVariant
        NoteType.FOLLOWUP     -> Icons.Outlined.EventRepeat   to SaathiWarning
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(note.type.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
                    Text("· ${note.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(note.source.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ── Logs Tab ──────────────────────────────────────────────────────────────────

@Composable
private fun LogsTab(packet: SyncPacket) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var logsTab by remember { mutableIntStateOf(0) }
    val logTabs = listOf("Cycles (${packet.cycleHistory.size})", "Symptoms (${packet.symptomHistory.size})", "Risk (${packet.riskHistory.size})")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = logsTab, edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.surface) {
            logTabs.forEachIndexed { i, title ->
                Tab(selected = logsTab == i, onClick = { logsTab = i },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall) })
            }
        }

        when (logsTab) {
            0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (packet.cycleHistory.isEmpty()) {
                    item { Text("No cycle logs synced.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(packet.cycleHistory) { e ->
                        LogCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fmt.format(Date(e.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${e.cycleLength} days", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LogChip("Flow: ${e.flowIntensity.ifBlank { "—" }}")
                                val irreg = e.cycleLength > 35 || e.cycleLength < 21
                                if (irreg) LogChip("Irregular", warn = true)
                            }
                        }
                    }
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (packet.symptomHistory.isEmpty()) {
                    item { Text("No symptom logs synced.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(packet.symptomHistory) { e ->
                        LogCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fmt.format(Date(e.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Fatigue ${e.fatigue}/5", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                    color = if (e.fatigue >= 4) SaathiWarning else MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                LogChip("Acne ${"%.0f".format(e.acneScore)}%")
                                if (e.skinDarkening) LogChip("Skin darkening", warn = true)
                                if (e.hairIssues)    LogChip("Hair issues", warn = true)
                                if (e.weightGain)    LogChip("Weight gain", warn = true)
                            }
                            if (e.weight > 0f) {
                                Spacer(Modifier.height(4.dp))
                                Text("Weight: ${"%.1f".format(e.weight)} kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (packet.riskHistory.isEmpty()) {
                    item { Text("No risk assessments synced.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(packet.riskHistory) { e ->
                        val riskColor = when {
                            e.totalScore >= 70 -> SaathiError
                            e.totalScore >= 50 -> SaathiWarning
                            else               -> SaathiSuccess
                        }
                        LogCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(fmt.format(Date(e.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${e.totalScore}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = riskColor)
                            }
                            Spacer(Modifier.height(4.dp))
                            LogChip(e.riskLevel, warn = e.totalScore >= 50)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun LogChip(label: String, warn: Boolean = false) {
    val bg  = if (warn) SaathiWarning.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    val fg  = if (warn) SaathiWarning else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.small, color = bg) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ── Shared ─────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 2.dp))
}
