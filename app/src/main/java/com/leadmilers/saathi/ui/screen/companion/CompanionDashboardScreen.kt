package com.leadmilers.saathi.ui.screen.companion

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.R
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.companion.NearbyManager
import com.leadmilers.saathi.companion.SyncPacket
import com.leadmilers.saathi.companion.SyncStatus
import com.leadmilers.saathi.prefs.UserPrefs
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CompanionDashboardScreen(
    onSettingsClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
) {
    val context  = LocalContext.current
    val prefs    = remember { (context.applicationContext as SaathiApp).userPrefs }
    val nearby   = remember { (context.applicationContext as SaathiApp).nearbyManager }
    val nearbyState by nearby.state.collectAsState()
    val scope    = rememberCoroutineScope()

    // The last snapshot is stored in prefs as JSON; we parse it here
    var packet by remember {
        mutableStateOf(
            if (prefs.lastSharedSnapshot.isNotBlank())
                SyncPacket.fromJson(prefs.lastSharedSnapshot)
            else null
        )
    }

    // When nearby delivers a new packet, persist and display it
    LaunchedEffect(nearbyState.lastPacket) {
        nearbyState.lastPacket?.let {
            prefs.lastSharedSnapshot = it.toJson()
            packet = it
        }
    }

    // Auto-start discovery when this screen opens (companion pulls data)
    LaunchedEffect(Unit) {
        if (prefs.isPaired) {
            nearby.configure(
                localName      = prefs.userName,
                pairedDeviceId = prefs.pairedDeviceId,
                onPacket       = { p ->
                    CoroutineScope(Dispatchers.Main).launch {
                        prefs.lastSharedSnapshot = p.toJson()
                        packet = p
                    }
                },
            )
            nearby.discover()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(20.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    getGreeting(prefs.userName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "How ${if (prefs.partnerName.isNotBlank()) prefs.partnerName else "your partner"} is doing",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Sync status bar
        SyncStatusRow(
            status    = nearbyState.status,
            lastSync  = prefs.lastSyncAt,
            onSyncNow = onSyncClick,
            modifier  = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(24.dp))

        if (!prefs.isPaired) {
            NotPairedState(modifier = Modifier.padding(horizontal = 24.dp))
        } else if (packet == null) {
            WaitingForDataState(modifier = Modifier.padding(horizontal = 24.dp))
        } else {
            // Partner data cards
            PartnerDataSection(packet = packet!!, prefs = prefs)
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Sync status row ────────────────────────────────────────────────────────────

@Composable
private fun SyncStatusRow(
    status: SyncStatus,
    lastSync: Long,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dot, label, dotColor) = when (status) {
        SyncStatus.IDLE, SyncStatus.DISCOVERING, SyncStatus.ADVERTISING ->
            Triple("○", "Partner nearby status unavailable", MaterialTheme.colorScheme.onSurfaceVariant)
        SyncStatus.CONNECTING, SyncStatus.SYNCING ->
            Triple("↻", "Syncing…", MaterialTheme.colorScheme.primary)
        SyncStatus.SYNCED ->
            Triple("●", "Connected · Synced just now", SaathiSuccess)
        SyncStatus.ERROR ->
            Triple("!", "Couldn't sync", SaathiError)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dot, style = MaterialTheme.typography.labelLarge, color = dotColor)
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lastSync > 0L && status != SyncStatus.SYNCED) {
                    val df = SimpleDateFormat("h:mm a", Locale.getDefault())
                    Text("Last synced ${df.format(Date(lastSync))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = onSyncNow) {
            Text("Sync now", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── Partner data cards ─────────────────────────────────────────────────────────

@Composable
private fun PartnerDataSection(packet: SyncPacket, prefs: UserPrefs) {
    val partnerDisplayName = if (prefs.partnerName.isNotBlank()) prefs.partnerName else "Your partner"

    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SaathiSectionHeader("Today")

        // Mood
        packet.mood?.let { mood ->
            CompanionInfoCard(
                icon    = Icons.Outlined.SentimentSatisfied,
                label   = "Feeling",
                value   = mood,
                tint    = MaterialTheme.colorScheme.primary,
            )
        }

        // Energy
        packet.fatigueLevel?.let { f ->
            CompanionInfoCard(
                icon    = Icons.Outlined.BatteryChargingFull,
                label   = "Energy",
                value   = energyLabel(f),
                tint    = SaathiSuccess,
                subtext = "Fatigue ${f}/5",
            )
        }

        // Cycle
        if (packet.cycleDay != null) {
            CompanionInfoCard(
                icon    = Icons.Outlined.CalendarMonth,
                label   = "Cycle",
                value   = "Day ${packet.cycleDay}",
                tint    = MaterialTheme.colorScheme.secondary,
                subtext = packet.phaseName,
            )
        }

        // Period notice
        if (packet.isInPeriod == true) {
            Surface(
                shape  = MaterialTheme.shapes.large,
                color  = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        "$partnerDisplayName is on their period right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SaathiDivider()
        Spacer(Modifier.height(8.dp))

        // Support suggestion
        val suggestion = buildSupportSuggestion(packet)
        if (suggestion != null) {
            SaathiSectionHeader("You can support them")
            Spacer(Modifier.height(8.dp))
            SaathiInsightCard(
                observation = suggestion.first,
                supporting  = suggestion.second,
                accent      = MaterialTheme.colorScheme.primary,
            )
        }

        // Last updated timestamp
        if (packet.timestamp > 0L) {
            val df = SimpleDateFormat("h:mm a", Locale.getDefault())
            val isToday = run {
                val cal1 = Calendar.getInstance().apply { timeInMillis = packet.timestamp }
                val cal2 = Calendar.getInstance()
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
            }
            Text(
                "Last updated · ${if (isToday) "Today" else "Recently"} ${df.format(Date(packet.timestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompanionInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    subtext: String? = null,
) {
    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                subtext?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Empty states ───────────────────────────────────────────────────────────────

@Composable
private fun NotPairedState(modifier: Modifier = Modifier) {
    SaathiEmptyState(
        title      = "Not connected yet.",
        message    = "Go to Settings → Companion to connect with your partner.",
        modifier   = modifier,
    )
}

@Composable
private fun WaitingForDataState(modifier: Modifier = Modifier) {
    SaathiEmptyState(
        title   = "Waiting for your partner.",
        message = "Once they open Saathi nearby, their shared information will appear here.",
        modifier = modifier,
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun getGreeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    return if (name.isNotBlank()) "$greet, $name" else greet
}

private fun energyLabel(fatigue: Int): String = when (fatigue) {
    1, 2 -> "High energy"
    3    -> "Moderate"
    4    -> "Tired"
    else -> "Exhausted"
}

private fun buildSupportSuggestion(packet: SyncPacket): Pair<String, String>? {
    if ((packet.fatigueLevel ?: 0) >= 4) {
        return Pair(
            "Rest might be helpful today.",
            "Energy is low — a quiet evening or small act of care can go a long way.",
        )
    }
    if (packet.isInPeriod == true) {
        return Pair(
            "Warmth and comfort go a long way right now.",
            "They're on their period. Small gestures — warmth, food, space — matter.",
        )
    }
    packet.phaseName?.let { phase ->
        if (phase == "Luteal phase") {
            return Pair(
                "They may feel a bit more sensitive this week.",
                "The luteal phase can bring lower energy and heightened emotions. Patience helps.",
            )
        }
    }
    return null
}
