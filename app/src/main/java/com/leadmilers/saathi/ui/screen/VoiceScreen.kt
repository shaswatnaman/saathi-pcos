package com.leadmilers.saathi.ui.screen

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.VoiceAnalyzer
import com.leadmilers.saathi.ml.VoiceResult
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (!audioPermission.status.isGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Microphone access needed for voice energy analysis")
                Button(onClick = { audioPermission.launchPermissionRequest() }) { Text("Grant Permission") }
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
    val analyzer = remember { VoiceAnalyzer(context) }

    var isRecording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VoiceResult?>(null) }
    var liveAmplitudes by remember { mutableStateOf(List(30) { 0f }) }
    var secondsLeft by remember { mutableStateOf(10) }
    var statusMessage by remember { mutableStateOf("Tap to record 10 seconds of your voice") }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Voice Energy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tracks vocal fatigue as a hormonal proxy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Waveform
        Waveform(amplitudes = liveAmplitudes, isRecording = isRecording)

        // Timer during recording
        if (isRecording) {
            Text("$secondsLeft s", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }

        // Result card
        result?.let { r ->
            ResultCard(r, analyzer.hasBaseline)
        }

        // Status
        Text(statusMessage, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Record button
        FilledIconButton(
            onClick = {
                if (isRecording) return@FilledIconButton
                isRecording = true
                result = null
                secondsLeft = 10
                liveAmplitudes = List(30) { 0f }
                statusMessage = "Recording… speak naturally"

                scope.launch {
                    var tick = 0
                    try {
                        val voiceResult = analyzer.record { amplitude ->
                            tick++
                            secondsLeft = 10 - (tick * 100 / 1000).coerceAtMost(10)
                            liveAmplitudes = (liveAmplitudes.drop(1) + amplitude)
                        }
                        result = voiceResult
                        statusMessage = if (voiceResult.isBaseline)
                            "Baseline set! Future recordings compared to this."
                        else
                            "Score saved to today's log."
                        saveToSymptomLog(repository, voiceResult)
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

        Spacer(Modifier.height(8.dp))
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
        modifier = Modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        amplitudes.forEachIndexed { i, amp ->
            val height = if (isRecording) {
                ((amp * pulse * 80f) + 6f).coerceIn(6f, 90f)
            } else {
                6f
            }
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

@Composable
private fun ResultCard(result: VoiceResult, hasBaseline: Boolean) {
    val scorePercent = (result.energyScore * 100).roundToInt()
    val color = when {
        result.energyScore >= 0.75f -> Color(0xFF43A047)
        result.energyScore >= 0.50f -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }
    val label = when {
        result.isBaseline -> "Baseline established"
        result.energyScore >= 0.75f -> "Good energy — normal"
        result.energyScore >= 0.50f -> "Moderate fatigue"
        else -> "Low energy — possible fatigue"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Voice Energy Score", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$scorePercent%", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

private suspend fun saveToSymptomLog(
    repository: com.leadmilers.saathi.data.repository.SaathiRepository,
    result: VoiceResult
) {
    val existing = repository.getLatestSymptomLog()
    val log = existing?.copy(voiceEnergyScore = result.energyScore)
        ?: SymptomLog(
            date = System.currentTimeMillis(),
            fatigue = 3,
            acneScore = 0f,
            voiceEnergyScore = result.energyScore,
            weight = 0f,
            waistHip = 0f
        )
    if (existing != null) repository.updateSymptomLog(log)
    else repository.insertSymptomLog(log)
}
