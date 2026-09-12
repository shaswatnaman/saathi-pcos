package com.leadmilers.saathi.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.HealthEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PartnerScreen() {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as SaathiApp).repository }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val recentSymptoms by repo.recentSymptomLogs(7).collectAsState(initial = emptyList())
    val recentCycles   by repo.recentCycleLogs(3).collectAsState(initial = emptyList())
    val generalEntries by repo.healthEntriesByModule("general").collectAsState(initial = emptyList())
    val partnerNotes   by repo.healthEntriesByModule("partner").collectAsState(initial = emptyList())

    var noteText by remember { mutableStateOf("") }
    var noteSent by remember { mutableStateOf(false) }

    // Derived wellness state from most recent data
    val todayEntries = generalEntries.filter { isToday(it.timestamp) }
    val latestMood    = todayEntries.lastOrNull { it.entryType == "mood" }?.numericValue?.toInt() ?: 3
    val latestFatigue = todayEntries.lastOrNull { it.entryType == "fatigue" }?.numericValue?.toInt() ?: 0
    val latestPain    = todayEntries.lastOrNull { it.entryType == "pain_level" }?.numericValue?.toInt() ?: 0
    val latestSymptom = recentSymptoms.firstOrNull()
    val hasLoggedToday = todayEntries.isNotEmpty() || (latestSymptom?.let { isToday(it.date) } == true)

    // Cycle day estimate
    val latestCycle = recentCycles.firstOrNull()
    val cycleDay = latestCycle?.let {
        val daysSince = ((System.currentTimeMillis() - it.date) / 86_400_000L).toInt()
        ((daysSince % it.cycleLength) + 1).coerceIn(1, it.cycleLength)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7B1FA2))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Favorite, null,
                            tint = Color(0xFFFF80AB), modifier = Modifier.size(22.dp))
                        Text("Partner View", color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Text("How she's doing", color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        if (hasLoggedToday) "She logged today — here's a summary for you."
                        else "No log yet today — check back later.",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!hasLoggedToday) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙", fontSize = 28.sp)
                        Text("Nothing logged yet today. The summary below reflects her most recent data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Today's Wellness Summary ─────────────────────────────────
            Text("Today's Wellness", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WellnessTile("Mood", moodEmoji(latestMood), moodLabel(latestMood),
                    moodColor(latestMood), Modifier.weight(1f))
                WellnessTile("Energy", energyEmoji(latestFatigue), energyLabel(latestFatigue),
                    energyColor(latestFatigue), Modifier.weight(1f))
                WellnessTile("Pain", painEmoji(latestPain), painLabel(latestPain),
                    painColor(latestPain), Modifier.weight(1f))
            }

            // ── Cycle context ────────────────────────────────────────────
            if (cycleDay != null) {
                val (cycleLabel, cycleNote) = cycleContext(cycleDay, latestCycle!!.cycleLength)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF7B1FA2).copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7B1FA2).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$cycleDay", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                color = Color(0xFF7B1FA2))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Day $cycleDay of her cycle — $cycleLabel",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(cycleNote, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Support suggestions ──────────────────────────────────────
            val suggestions = supportSuggestions(latestMood, latestFatigue, latestPain, cycleDay,
                latestCycle?.cycleLength)
            if (suggestions.isNotEmpty()) {
                Text("How you can help", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                suggestions.forEach { (icon, title, body) ->
                    SupportCard(icon, title, body)
                }
            }

            // ── Leave a note ─────────────────────────────────────────────
            Text("Leave a note for her", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)

            // Show most recent partner note if exists
            val lastNote = partnerNotes.maxByOrNull { it.timestamp }
            if (lastNote != null) {
                val fmt = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE91E63).copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Favorite, null,
                                tint = Color(0xFFE91E63), modifier = Modifier.size(14.dp))
                            Text("Last note", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE91E63))
                        }
                        Text(lastNote.textValue ?: "", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text(fmt.format(Date(lastNote.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it; noteSent = false },
                        placeholder = { Text("Write something kind…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                    val btnColor by animateColorAsState(
                        if (noteSent) Color(0xFF4CAF50) else Color(0xFFE91E63),
                        animationSpec = tween(300), label = "btn"
                    )
                    Button(
                        onClick = {
                            val msg = noteText.trim()
                            if (msg.isEmpty()) return@Button
                            scope.launch {
                                repo.insertHealthEntry(
                                    HealthEntry(
                                        moduleId  = "partner",
                                        entryType = "note",
                                        textValue = msg,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                noteSent = true
                                noteText = ""
                                snackbarHost.showSnackbar("Note saved — she'll see it on her home screen 💜")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                        enabled = noteText.isNotBlank()
                    ) {
                        Icon(
                            if (noteSent) Icons.Default.Check else Icons.Default.Send,
                            null, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (noteSent) "Note saved!" else "Send note")
                    }
                }
            }

            // Privacy note
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                    Text(
                        "Partner view is local-only. No data leaves this phone. " +
                        "Clinical details are not shown here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Tile composable ──────────────────────────────────────────────────────────

@Composable
private fun WellnessTile(
    label: String, emoji: String, value: String, color: Color, modifier: Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = color, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SupportCard(icon: ImageVector, title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = Color(0xFF7B1FA2),
                modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Helper functions ─────────────────────────────────────────────────────────

private fun isToday(ts: Long): Boolean {
    val cal = Calendar.getInstance()
    val day = Calendar.getInstance().apply { timeInMillis = ts }
    return cal.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR) &&
           cal.get(Calendar.YEAR) == day.get(Calendar.YEAR)
}

private fun moodEmoji(v: Int) = when (v) { 1 -> "😔"; 2 -> "😕"; 3 -> "😐"; 4 -> "🙂"; else -> "😊" }
private fun moodLabel(v: Int) = when (v) { 1 -> "Very low"; 2 -> "Low"; 3 -> "Okay"; 4 -> "Good"; else -> "Great" }
private fun moodColor(v: Int) = when {
    v <= 2 -> Color(0xFFF44336); v == 3 -> Color(0xFFFF9800); else -> Color(0xFF4CAF50)
}

private fun energyEmoji(fatigue: Int) = when (fatigue) { 0, 1 -> "⚡"; 2, 3 -> "🔋"; else -> "😴" }
private fun energyLabel(fatigue: Int) = when (fatigue) { 0 -> "Full energy"; 1 -> "Good"; 2 -> "Moderate"; 3 -> "Low"; else -> "Exhausted" }
private fun energyColor(fatigue: Int) = when {
    fatigue <= 1 -> Color(0xFF4CAF50); fatigue <= 3 -> Color(0xFFFF9800); else -> Color(0xFFF44336)
}

private fun painEmoji(pain: Int) = when { pain == 0 -> "✨"; pain <= 3 -> "🟡"; pain <= 6 -> "🟠"; else -> "🔴" }
private fun painLabel(pain: Int) = when { pain == 0 -> "No pain"; pain <= 3 -> "Mild"; pain <= 6 -> "Moderate"; else -> "Significant" }
private fun painColor(pain: Int) = when { pain == 0 -> Color(0xFF4CAF50); pain <= 3 -> Color(0xFFFF9800); else -> Color(0xFFF44336) }

private fun cycleContext(day: Int, length: Int): Pair<String, String> = when {
    day <= 5  -> "Menstrual phase" to "She may feel more tired or crampy. Extra warmth and patience go a long way."
    day <= 13 -> "Follicular phase" to "Energy often rises during this phase. A good time for plans together."
    day <= 16 -> "Ovulation window" to "Energy typically peaks around now."
    day <= length - 3 -> "Luteal phase" to "PMS symptoms can appear late in this phase. She might appreciate calm and comfort."
    else -> "Late luteal phase" to "Pre-period days — mood and energy may dip. Small gestures mean a lot."
}

private fun supportSuggestions(
    mood: Int, fatigue: Int, pain: Int, cycleDay: Int?, cycleLength: Int?
): List<Triple<ImageVector, String, String>> {
    val list = mutableListOf<Triple<ImageVector, String, String>>()

    if (fatigue >= 4)
        list += Triple(Icons.Default.LocalCafe, "Bring her a warm drink",
            "High fatigue today — something warm and comforting can make a real difference.")
    if (pain >= 5)
        list += Triple(Icons.Default.Spa, "Ask if she'd like a heat pad",
            "She's logged significant pain today. A heat pad or a gentle check-in can help.")
    if (mood <= 2)
        list += Triple(Icons.Default.Favorite, "Just be there",
            "Mood is low today. Sometimes presence matters more than words.")
    if (cycleDay != null && cycleLength != null && cycleDay > cycleLength - 5)
        list += Triple(Icons.Default.SelfImprovement, "Be extra patient",
            "Pre-period days can bring mood shifts — not personal, just hormonal.")
    if (list.isEmpty())
        list += Triple(Icons.Default.Stars, "She seems to be doing okay",
            "No distress signals today. A kind check-in is still always welcome.")

    return list.take(3)
}
