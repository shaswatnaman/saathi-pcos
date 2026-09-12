package com.leadmilers.saathi.ui.screen

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.HealthEntry
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.VoiceAnalyzer
import com.leadmilers.saathi.ml.VoiceResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (!audioPermission.status.isGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text("Microphone access needed for voice signature analysis",
                    style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { audioPermission.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    VoiceRecorderContent()
}

@Composable
private fun VoiceRecorderContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { (context.applicationContext as SaathiApp).repository }
    val analyzer = remember { VoiceAnalyzer() }

    var isRecording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VoiceResult?>(null) }
    var liveAmplitudes by remember { mutableStateOf(List(30) { 0f }) }
    var secondsLeft by remember { mutableStateOf(10) }
    var statusMessage by remember { mutableStateOf("Tap to record 10 seconds of your natural speaking voice") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Voice Signature",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Tracks acoustic features that shift across your cycle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Science context card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What this measures", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Pitch variability (F0 SD) and lower-pitch boundary (F0 min) shift measurably " +
                    "across menstrual cycle phases due to estrogen/progesterone effects on laryngeal " +
                    "tissue. Useful for cross-checking your self-reported cycle phase — especially " +
                    "helpful with irregular cycles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Validated: Ziemer et al., JMIR Formative Research 2025",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Waveform
        Waveform(amplitudes = liveAmplitudes, isRecording = isRecording)

        // Timer
        if (isRecording) {
            Text("$secondsLeft s", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }

        // Result
        result?.let { r ->
            VoiceResultCard(r)
        }

        // Status
        Text(statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Record button
        FilledIconButton(
            onClick = {
                if (isRecording) return@FilledIconButton
                isRecording = true
                result = null
                secondsLeft = 10
                liveAmplitudes = List(30) { 0f }
                statusMessage = "Recording… speak naturally for 10 seconds"

                scope.launch {
                    var frameCount = 0
                    try {
                        val voiceResult = analyzer.record { amplitude ->
                            frameCount++
                            // ~32 ms/frame at 16kHz/512 samples → ~31 frames/s
                            secondsLeft = (10 - frameCount / 31).coerceAtLeast(0)
                            liveAmplitudes = liveAmplitudes.drop(1) + amplitude
                        }
                        result = voiceResult
                        statusMessage = if (voiceResult.voicedFrames >= 5) {
                            "Done! Your voice was captured clearly."
                        } else {
                            "Couldn't pick up your voice well — try speaking a bit louder in a quieter spot."
                        }
                        if (voiceResult.voicedFrames >= 5) {
                            saveVoiceResult(repository, voiceResult)
                        }
                    } catch (e: Exception) {
                        statusMessage = "Recording failed: ${e.message}"
                    } finally {
                        isRecording = false
                        secondsLeft = 10
                    }
                }
            },
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isRecording) "Recording" else "Record",
                modifier = Modifier.size(40.dp)
            )
        }

        // Disclaimer
        Text(
            "Experimental · for cycle-phase corroboration only · not a diagnostic measurement · " +
            "accuracy comparable to published research requires multiple recordings over time",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun VoiceResultCard(result: VoiceResult) {
    val hasData = result.voicedFrames >= 5

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎙️", fontSize = 20.sp)
                Text("Today's Voice Reading",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }

            if (!hasData) {
                Text("We couldn't catch enough of your voice. Try speaking naturally for the full 10 seconds in a quieter spot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                return@Column
            }

            // Primary: F0 SD — voice "expressiveness" across cycle
            FriendlyVoiceRow(
                emoji = "〰️",
                title = "How much your voice varied",
                subtitle = "This shifts across your cycle — useful for spotting where you are",
                value = "${"%.1f".format(result.f0Sd)} Hz",
                valueColor = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Secondary: F0 Min — lowest pitch
            FriendlyVoiceRow(
                emoji = "🔉",
                title = "Your lowest note today",
                subtitle = "Tends to be a bit higher in the second half of your cycle",
                value = "${"%.0f".format(result.f0Min)} Hz",
                valueColor = Color(0xFF6A1B9A)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // F0 Mean — stable baseline
            FriendlyVoiceRow(
                emoji = "📍",
                title = "Your average pitch (baseline)",
                subtitle = "This stays mostly the same — used to compare your other readings to",
                value = "${"%.0f".format(result.f0Mean)} Hz",
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Save note
            Text("✓ Saved to your voice log on this device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))

            Text("All processing happens on your phone · nothing is sent to any server",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun FriendlyVoiceRow(
    emoji: String,
    title: String,
    subtitle: String,
    value: String,
    valueColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 16.sp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor)
    }
}


@Composable
private fun Waveform(amplitudes: List<Float>, isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        amplitudes.forEachIndexed { i, amp ->
            val height = if (isRecording) ((amp * pulse * 70f) + 6f).coerceIn(6f, 70f) else 6f
            val animatedHeight by animateFloatAsState(
                targetValue = height,
                animationSpec = tween(80),
                label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

private suspend fun saveVoiceResult(
    repository: com.leadmilers.saathi.data.repository.SaathiRepository,
    result: VoiceResult
) {
    val now = System.currentTimeMillis()

    // Save F0 metrics as HealthEntry rows (cycle-phase signals, not risk inputs)
    repository.insertHealthEntries(listOf(
        HealthEntry(moduleId = "voice", entryType = "f0_sd",
            numericValue = result.f0Sd.toDouble(), sourceType = "microphone", timestamp = now),
        HealthEntry(moduleId = "voice", entryType = "f0_min",
            numericValue = result.f0Min.toDouble(), sourceType = "microphone", timestamp = now),
        HealthEntry(moduleId = "voice", entryType = "f0_mean",
            numericValue = result.f0Mean.toDouble(), sourceType = "microphone", timestamp = now),
        HealthEntry(moduleId = "voice", entryType = "voice_energy_experimental",
            numericValue = result.energyScore.toDouble(), sourceType = "microphone", timestamp = now)
    ))

    // Keep SymptomLog.voiceEnergyScore populated for backward compat with RiskScorer
    val existing = repository.getLatestSymptomLog()
    val log = existing?.copy(voiceEnergyScore = result.energyScore)
        ?: SymptomLog(
            date = now,
            fatigue = 3,
            acneScore = 0f,
            voiceEnergyScore = result.energyScore,
            weight = 0f,
            waistHip = 0f
        )
    if (existing != null) repository.updateSymptomLog(log)
    else repository.insertSymptomLog(log)
}
