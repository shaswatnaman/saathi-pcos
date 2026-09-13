package com.leadmilers.saathi.gynac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.companion.SyncPacket
import com.leadmilers.saathi.companion.SyncStatus
import com.leadmilers.saathi.gynac.*
import com.leadmilers.saathi.ui.theme.SaathiPlum
import com.leadmilers.saathi.ui.theme.SaathiPlumDark
import com.leadmilers.saathi.ui.theme.SaathiWarning
import com.leadmilers.saathi.ui.theme.SaathiError
import com.leadmilers.saathi.ui.theme.SaathiSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GynacHomeScreen(
    onPatientClick:  (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val app     = context.applicationContext as SaathiApp
    val prefs   = remember { app.userPrefs }
    val nearby  = remember { app.lanSyncManager }
    val doctor  = GynacDemoData.doctor

    // Live patient packet received via LAN sync
    var livePacket by remember {
        mutableStateOf(
            if (prefs.lastSharedSnapshot.isNotBlank()) SyncPacket.fromJson(prefs.lastSharedSnapshot) else null
        )
    }
    val nearbyState by nearby.state.collectAsState()

    // Build a GynacPatient from the live sync packet and store in app so profile screen can use it
    val livePatient = remember(livePacket) {
        livePacket?.let { p -> buildLivePatient(p) }.also { app.currentLivePatient = it }
    }

    // All patients: live synced patient first, then demo patients
    val patients = remember(livePatient) {
        buildList {
            livePatient?.let { add(it) }
            addAll(GynacDemoData.patients)
        }
    }

    // Always start LAN discovery — no pairing prerequisite for demo
    LaunchedEffect(Unit) {
        nearby.configure(
            localName   = prefs.userName.ifBlank { "Doctor" },
            pairedToken = prefs.pairingToken,
            onPacket    = { p ->
                CoroutineScope(Dispatchers.Main).launch {
                    prefs.lastSharedSnapshot = p.toJson()
                    prefs.lastSyncAt = System.currentTimeMillis()
                    app.currentLiveSyncPacket = p
                    livePacket = p
                }
            },
        )
        nearby.discover()
    }

    DisposableEffect(Unit) {
        onDispose { nearby.stopAll() }
    }

    var searchQuery    by remember { mutableStateOf("") }
    var activeFilter   by remember { mutableStateOf<GynacStatus?>(null) }

    val filtered = remember(searchQuery, activeFilter, patients) {
        patients.filter { p ->
            (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true)) &&
            (activeFilter == null || p.status == activeFilter)
        }
    }
    val attention = remember(patients) { patients.filter { it.status == GynacStatus.NEEDS_REVIEW || it.status == GynacStatus.FOLLOW_UP } }

    val displayName = prefs.userName.let {
        if (it.startsWith("Dr.", ignoreCase = true)) it else "Dr. $it"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayName,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            doctor.clinic,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Stats row ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                StatsRow(patients = patients)
            }

            // ── Live patient sync banner ──────────────────────────────────
            item {
                LivePatientBanner(
                        packet     = livePacket,
                        syncStatus = nearbyState.status,
                        onRetry    = {
                            CoroutineScope(Dispatchers.Main).launch {
                                nearby.stopAll()
                                nearby.configure(
                                    localName   = prefs.userName.ifBlank { "Doctor" },
                                    pairedToken = prefs.pairingToken,
                                    onPacket    = { p ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            prefs.lastSharedSnapshot = p.toJson()
                                            prefs.lastSyncAt = System.currentTimeMillis()
                                            livePacket = p
                                        }
                                    },
                                )
                                nearby.discover()
                            }
                        },
                        onDisconnect = {
                            nearby.stopAll()
                            onSettingsClick()
                        },
                    )
                }

            // ── Needs attention ──────────────────────────────────────────
            if (attention.isNotEmpty()) {
                item {
                    SectionHeader("Needs Your Attention", "${attention.size}")
                }
                items(attention) { p ->
                    AttentionCard(patient = p, onClick = { onPatientClick(p.id) })
                }
            }

            // ── Search + filter chips ────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("All Patients", "${filtered.size}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder   = { Text("Search patients…") },
                    leadingIcon   = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape         = MaterialTheme.shapes.extraLarge,
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                FilterChipsRow(activeFilter = activeFilter, onFilter = { activeFilter = if (activeFilter == it) null else it })
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            // ── Patient list ─────────────────────────────────────────────
            items(filtered) { p ->
                PatientRow(patient = p, onClick = { onPatientClick(p.id) })
                HorizontalDivider(
                    modifier  = Modifier.padding(start = 72.dp),
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
            }
        }
    }
}

// ── Stats row ──────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(patients: List<GynacPatient>) {
    val active     = patients.count { it.status == GynacStatus.STABLE || it.status == GynacStatus.MONITORING }
    val followUps  = patients.count { it.status == GynacStatus.FOLLOW_UP }
    val reviews    = patients.count { it.status == GynacStatus.NEEDS_REVIEW }
    val monitoring = patients.count { it.status == GynacStatus.MONITORING }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(value = "${patients.size}", label = "Total",     color = MaterialTheme.colorScheme.primary,          modifier = Modifier.weight(1f))
        StatTile(value = "$followUps",       label = "Follow-up", color = MaterialTheme.colorScheme.secondary,         modifier = Modifier.weight(1f))
        StatTile(value = "$monitoring",      label = "Monitoring",color = MaterialTheme.colorScheme.tertiary,           modifier = Modifier.weight(1f))
        StatTile(value = "$reviews",         label = "Review",    color = SaathiError,                                 modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape  = MaterialTheme.shapes.medium,
        color  = color.copy(alpha = 0.08f),
        modifier = modifier,
    ) {
        Column(
            modifier             = Modifier.padding(vertical = 10.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: String) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Filter chips ──────────────────────────────────────────────────────────────

@Composable
private fun FilterChipsRow(activeFilter: GynacStatus?, onFilter: (GynacStatus) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(GynacStatus.values()) { status ->
            val selected = activeFilter == status
            FilterChip(
                selected = selected,
                onClick  = { onFilter(status) },
                label    = { Text(status.displayName(), style = MaterialTheme.typography.labelMedium) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor    = status.chipColor(),
                    selectedLabelColor        = Color.White,
                ),
            )
        }
    }
}

private fun GynacStatus.displayName() = when (this) {
    GynacStatus.STABLE       -> "Stable"
    GynacStatus.MONITORING   -> "Monitoring"
    GynacStatus.FOLLOW_UP    -> "Follow-up"
    GynacStatus.NEEDS_REVIEW -> "Needs Review"
}

@Composable
private fun GynacStatus.chipColor(): Color = when (this) {
    GynacStatus.STABLE       -> MaterialTheme.colorScheme.tertiary
    GynacStatus.MONITORING   -> MaterialTheme.colorScheme.secondary
    GynacStatus.FOLLOW_UP    -> SaathiWarning
    GynacStatus.NEEDS_REVIEW -> SaathiError
}

// ── Attention card ─────────────────────────────────────────────────────────────

@Composable
private fun AttentionCard(patient: GynacPatient, onClick: () -> Unit) {
    val accentColor = when (patient.status) {
        GynacStatus.NEEDS_REVIEW -> SaathiError
        GynacStatus.FOLLOW_UP    -> SaathiWarning
        else                     -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick  = onClick,
        shape    = MaterialTheme.shapes.large,
        color    = accentColor.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialsAvatar(name = patient.name, color = accentColor)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(patient.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    StatusBadge(status = patient.status)
                }
                patient.alerts.firstOrNull()?.let { alert ->
                    Text(alert.message, style = MaterialTheme.typography.bodySmall, color = accentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${patient.age}y · ${patient.diagnosis} · Last visit ${patient.lastVisitDays}d ago",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Patient row ────────────────────────────────────────────────────────────────

@Composable
private fun PatientRow(patient: GynacPatient, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialsAvatar(name = patient.name, color = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(patient.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                StatusBadge(status = patient.status)
            }
            Text("${patient.age}y · ${patient.primaryMedication}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniSparkBar(values = patient.cycleHistory.map { it.toFloat() }, color = MaterialTheme.colorScheme.primary)
            Text("${patient.lastVisitDays}d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Shared sub-composables ─────────────────────────────────────────────────────

@Composable
fun InitialsAvatar(name: String, color: Color, size: Int = 40) {
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier         = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, fontSize = (size * 0.35f).sp)
    }
}

@Composable
fun StatusBadge(status: GynacStatus) {
    val (text, color) = when (status) {
        GynacStatus.STABLE       -> "Stable" to MaterialTheme.colorScheme.tertiary
        GynacStatus.MONITORING   -> "Monitoring" to MaterialTheme.colorScheme.secondary
        GynacStatus.FOLLOW_UP    -> "Follow-up" to SaathiWarning
        GynacStatus.NEEDS_REVIEW -> "Review" to SaathiError
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Live patient sync banner ───────────────────────────────────────────────────

@Composable
private fun LivePatientBanner(
    packet:       SyncPacket?,
    syncStatus:   SyncStatus,
    onRetry:      () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isError = syncStatus == SyncStatus.ERROR
    val (dotColor, statusText) = when (syncStatus) {
        SyncStatus.DISCOVERING, SyncStatus.CONNECTING, SyncStatus.SYNCING ->
            SaathiWarning to "Connecting to patient…"
        SyncStatus.SYNCED ->
            SaathiSuccess to "Live · synced just now"
        SyncStatus.ERROR ->
            SaathiError to "Tap to retry"
        else ->
            MaterialTheme.colorScheme.onSurfaceVariant to "Waiting for patient on same Wi-Fi"
    }

    Surface(
        shape    = MaterialTheme.shapes.large,
        color    = if (isError)
            SaathiError.copy(alpha = 0.06f)
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (isError) Modifier.clickable(onClick = onRetry) else Modifier),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isError) Icons.Outlined.WifiOff else Icons.Outlined.MonitorHeart,
                    contentDescription = null,
                    tint     = if (isError) SaathiError else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (isError) "Sync Failed" else "Live Patient Data",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isError) SaathiError else MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(dotColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = dotColor)
                }
            }

            if (isError) {
                Text(
                    "Couldn't connect to the patient's device. Make sure both devices are on the same Wi-Fi network and the patient's Saathi app is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = onRetry,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = SaathiError),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick  = onDisconnect,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Connection settings", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (packet != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    packet.cycleDay?.let { day ->
                        LiveChip(label = "Cycle day $day", sub = packet.phaseName)
                    }
                    packet.fatigueLevel?.let { f ->
                        LiveChip(label = "Fatigue $f/5", sub = if (f >= 4) "High — note" else null)
                    }
                    if (packet.isInPeriod == true) {
                        LiveChip(label = "On period", sub = null, accent = SaathiError)
                    }
                }
                if (packet.skinDarkening == true || packet.hairIssues == true || packet.weightGain == true) {
                    val flags = buildList {
                        if (packet.skinDarkening == true) add("Skin darkening")
                        if (packet.hairIssues == true) add("Hair thinning")
                        if (packet.weightGain == true) add("Weight change")
                    }
                    Text("Active symptoms: ${flags.joinToString(" · ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    "No data received yet. Open the patient's Saathi app while on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveChip(label: String, sub: String?, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.10f)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.7f)) }
        }
    }
}

/** Converts a LAN sync packet into a GynacPatient so the gynac can tap into it like any patient. */
private fun buildLivePatient(p: SyncPacket): GynacPatient {
    val cycleLen    = p.cycleLength ?: (p.cycleHistory.firstOrNull()?.cycleLength ?: 28)
    val isIrregular = cycleLen > 35 || cycleLen < 21
    val fatigue     = p.fatigueLevel ?: (p.symptomHistory.firstOrNull()?.fatigue ?: 3)
    val skinDark    = p.skinDarkening ?: (p.symptomHistory.firstOrNull()?.skinDarkening == true)
    val hairIssue   = p.hairIssues ?: (p.symptomHistory.firstOrNull()?.hairIssues == true)
    val weightGain  = p.weightGain ?: (p.symptomHistory.firstOrNull()?.weightGain == true)
    val acne        = if (skinDark) "Moderate" else "Mild"

    val status = when {
        isIrregular && (skinDark || hairIssue) -> GynacStatus.NEEDS_REVIEW
        isIrregular || weightGain -> GynacStatus.FOLLOW_UP
        else -> GynacStatus.MONITORING
    }

    // Build cycle history (last 6 lengths)
    val cycleLengths = p.cycleHistory.take(6).map { it.cycleLength }.let {
        if (it.isEmpty()) listOf(cycleLen) else it
    }
    val avgCycleLen = if (cycleLengths.isEmpty()) cycleLen else cycleLengths.average().toInt()

    // Determine cycle trend from last 6 cycles
    val cycleTrend = when {
        cycleLengths.size < 2 -> if (isIrregular) HealthTrend.WORSENING else HealthTrend.STABLE
        else -> {
            val first = cycleLengths.last(); val last = cycleLengths.first()
            when {
                last > first + 3 -> HealthTrend.WORSENING
                last < first - 3 -> HealthTrend.IMPROVING
                else -> HealthTrend.STABLE
            }
        }
    }

    // Build acne / fatigue history from symptom logs
    val acneHistory = p.symptomHistory.take(30).map { it.acneScore / 10f }.let {
        if (it.isEmpty()) listOf(if (skinDark) 0.6f else 0.2f) else it
    }
    val acneTrend = when {
        acneHistory.size < 2 -> HealthTrend.STABLE
        acneHistory.first() > acneHistory.last() + 0.1f -> HealthTrend.WORSENING
        acneHistory.first() < acneHistory.last() - 0.1f -> HealthTrend.IMPROVING
        else -> HealthTrend.STABLE
    }

    val fatigueValues = p.symptomHistory.take(30).map { it.fatigue.toFloat() }
    val fatigueAvg = if (fatigueValues.isEmpty()) fatigue.toFloat() else fatigueValues.average().toFloat()
    val fatigueTrend = when {
        fatigueValues.size < 2 -> if (fatigue >= 4) HealthTrend.WORSENING else HealthTrend.STABLE
        fatigueValues.first() > fatigueValues.last() + 0.5f -> HealthTrend.WORSENING
        fatigueValues.first() < fatigueValues.last() - 0.5f -> HealthTrend.IMPROVING
        else -> HealthTrend.STABLE
    }

    // Latest risk score for data completeness estimate
    val latestRisk = p.riskHistory.firstOrNull()
    val dataCompleteness = when {
        p.symptomHistory.size >= 20 -> 85
        p.symptomHistory.size >= 10 -> 65
        p.symptomHistory.isNotEmpty() -> 45
        else -> 20
    }

    val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    val alerts = buildList {
        if (isIrregular) add(GynacAlert("Cycle length $cycleLen days — irregular", AlertSev.WARNING, 0))
        if (skinDark) add(GynacAlert("Skin darkening reported", AlertSev.WARNING, 0))
        if (hairIssue) add(GynacAlert("Hair changes reported", AlertSev.INFO, 0))
        if (fatigue >= 4) add(GynacAlert("Fatigue score $fatigue/5 — high", AlertSev.INFO, 0))
        latestRisk?.let {
            if (it.totalScore >= 70) add(GynacAlert("Risk score ${it.totalScore}/100 — ${it.riskLevel}", AlertSev.URGENT, 0))
            else if (it.totalScore >= 50) add(GynacAlert("Risk score ${it.totalScore}/100 — ${it.riskLevel}", AlertSev.WARNING, 0))
        }
    }

    val notes = buildList {
        add(GynacNote(
            date    = today,
            type    = NoteType.PATIENT_NOTE,
            content = buildString {
                p.phaseName?.let { append("Phase: $it. ") }
                p.cycleDay?.let { append("Day $it of cycle. ") }
                if (p.isInPeriod == true) append("On period (${p.flowIntensity ?: "?"} flow). ")
                p.mood?.let { append("Mood: $it. ") }
                append("Fatigue: $fatigue/5. ")
                append("Entries synced: cycles=${p.cycleHistory.size}, symptoms=${p.symptomHistory.size}, risk=${p.riskHistory.size}.")
            },
            source  = DataSource.PATIENT,
        ))
        if (p.riskHistory.isNotEmpty()) {
            add(GynacNote(
                date    = today,
                type    = NoteType.ASSESSMENT,
                content = "Risk history (${p.riskHistory.size} assessments). Latest: ${p.riskHistory.first().riskLevel} (score ${p.riskHistory.first().totalScore}).",
                source  = DataSource.SYSTEM,
            ))
        }
    }

    return GynacPatient(
        id                = "live_patient",
        name              = "Live Patient",
        age               = 0,
        diagnosis         = "PCOS (Live Sync)",
        patientCode       = p.senderDeviceId.take(8).uppercase(),
        lastVisitDays     = 0,
        nextFollowUpDays  = null,
        status            = status,
        avgCycleLength    = avgCycleLen,
        recentCycleLength = cycleLen,
        cycleTrend        = cycleTrend,
        cycleHistory      = cycleLengths,
        acneSeverity      = acne,
        acneTrend         = acneTrend,
        acneHistory       = acneHistory,
        fatigueAvg        = fatigueAvg,
        fatigueTrend      = fatigueTrend,
        painAvg           = 0f,
        sleepAvg          = 0f,
        sleepTrend        = HealthTrend.STABLE,
        sleepHistory      = emptyList(),
        primaryMedication = "—",
        primaryMedDose    = "",
        medications       = emptyList(),
        clinicalNotes     = notes,
        alerts            = alerts,
        dataCompleteness  = dataCompleteness,
        lastUpdateHours   = 0,
    )
}

@Composable
fun MiniSparkBar(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val max = values.maxOrNull() ?: 1f
    Row(
        modifier              = modifier.width(36.dp).height(16.dp),
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        values.takeLast(6).forEach { v ->
            val fraction = (v / max).coerceIn(0.1f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color.copy(alpha = 0.6f)),
            )
        }
    }
}
