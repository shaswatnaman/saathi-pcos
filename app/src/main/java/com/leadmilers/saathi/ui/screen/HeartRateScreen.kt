package com.leadmilers.saathi.ui.screen

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.HealthEntry
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

// ── PPG signal state ─────────────────────────────────────────────────────────

private sealed interface PpgState {
    object WaitingForFinger : PpgState
    object Measuring : PpgState
    data class Reading(val bpm: Int) : PpgState
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HeartRateScreen() {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (!cameraPermission.status.isGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Camera access is needed to measure heart rate",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp))
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }
    HeartRateContent()
}

@Composable
private fun HeartRateContent() {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo          = remember { (context.applicationContext as SaathiApp).repository }
    val scope         = rememberCoroutineScope()
    val snackbarHost  = remember { SnackbarHostState() }

    var ppgState  by remember { mutableStateOf<PpgState>(PpgState.WaitingForFinger) }
    var isSaved   by remember { mutableStateOf(false) }

    // Rolling buffer for the live waveform trace (last 100 normalized samples)
    val waveformBuffer = remember { mutableStateListOf<Float>() }

    // PpgAnalyzer is stateful — keep it alive across recompositions
    val analyzer = remember { PpgAnalyzer { sample, state ->
        waveformBuffer.add(sample)
        if (waveformBuffer.size > 120) waveformBuffer.removeAt(0)
        ppgState = state
    }}

    // Start camera + torch
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, analyzer)
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    analysis
                )
                camera.cameraControl.enableTorch(true)
            } catch (e: Exception) {
                Log.e("HeartRateScreen", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
            } catch (_: Exception) {}
            executor.shutdown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF1A0A1A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Title ───────────────────────────────────────────────────
            Text("Heart Rate (PPG)", color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Rear camera · photoplethysmography",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelMedium)

            // ── Instruction ─────────────────────────────────────────────
            InstructionCard(ppgState)

            // ── BPM display with pulse animation ────────────────────────
            BpmDisplay(ppgState)

            // ── Live waveform trace ──────────────────────────────────────
            WaveformTrace(waveformBuffer.toList())

            // ── Save button (only when we have a reading) ───────────────
            if (ppgState is PpgState.Reading) {
                val bpm = (ppgState as PpgState.Reading).bpm
                Button(
                    onClick = {
                        scope.launch {
                            repo.insertHealthEntry(
                                HealthEntry(
                                    moduleId     = "general",
                                    entryType    = "heart_rate_bpm",
                                    numericValue = bpm.toDouble(),
                                    sourceType   = "camera",
                                    timestamp    = System.currentTimeMillis()
                                )
                            )
                            isSaved = true
                            snackbarHost.showSnackbar("Heart rate saved ($bpm BPM) ✓")
                        }
                    },
                    enabled = !isSaved,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSaved) Color(0xFF4CAF50) else Color(0xFFE91E63)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (isSaved) Icons.Default.Favorite else Icons.Default.Save,
                        null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSaved) "Saved!" else "Save reading")
                }
            }

            // ── Disclaimer ──────────────────────────────────────────────
            Text(
                "Wellness trend only · Not a medical-grade measurement · " +
                "PPG accuracy ±5–10 BPM vs. ECG reference (IEEE 2017 meta-analysis)",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InstructionCard(state: PpgState) {
    val (icon, text, tint) = when (state) {
        PpgState.WaitingForFinger ->
            Triple(Icons.Default.FavoriteBorder,
                "Place your fingertip gently over the rear camera and flash",
                Color(0xFFFF9800))
        PpgState.Measuring ->
            Triple(Icons.Default.FavoriteBorder,
                "Hold still — collecting signal…",
                Color(0xFF2196F3))
        is PpgState.Reading ->
            Triple(Icons.Default.Favorite,
                "Reading stable — keep finger in place",
                Color(0xFFE91E63))
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(text, color = tint, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BpmDisplay(state: PpgState) {
    val bpm = if (state is PpgState.Reading) state.bpm else null

    // Pulsing animation tied to BPM
    val beatDuration = if (bpm != null) (60_000f / bpm).toLong() else 1000L
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (beatDuration / 2).toInt().coerceIn(200, 800),
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(Color(0xFF2D0A2D)),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulse ring
        if (bpm != null) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color(0xFFE91E63).copy(alpha = 0.12f))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (bpm != null) {
                Text(
                    "$bpm",
                    color = Color(0xFFE91E63),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(scale)
                )
                Text("BPM", color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge)
            } else {
                Text("—", color = Color.White.copy(alpha = 0.3f),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state == PpgState.WaitingForFinger) "no finger" else "measuring",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun WaveformTrace(samples: List<Float>) {
    if (samples.size < 4) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2D0A2D)),
            contentAlignment = Alignment.Center
        ) {
            Text("Waveform will appear here",
                color = Color.White.copy(alpha = 0.2f),
                style = MaterialTheme.typography.labelSmall)
        }
        return
    }

    val min = samples.min()
    val max = samples.max()
    val range = (max - min).coerceAtLeast(0.001f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D0A2D))
    ) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val step = (w - pad * 2) / (samples.size - 1).coerceAtLeast(1)

        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = pad + i * step
            val y = h - pad - ((v - min) / range) * (h - pad * 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color(0xFFE91E63),
            style = Stroke(width = 2.dp.toPx())
        )
        // Zero-line
        val zeroY = h - pad - ((-min) / range) * (h - pad * 2)
        drawLine(
            color = Color.White.copy(alpha = 0.1f),
            start = Offset(pad, zeroY),
            end   = Offset(w - pad, zeroY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

// ── PPG signal analyzer ──────────────────────────────────────────────────────

private class PpgAnalyzer(
    private val onSample: (normalizedSample: Float, state: PpgState) -> Unit
) : ImageAnalysis.Analyzer {

    private val raw       = ArrayDeque<Float>()
    private val WINDOW    = 150        // ~5 s at 30 fps
    private val MIN_COUNT = 60         // 2 s before we attempt BPM
    private val TORCH_THRESH = 100f    // below this mean → no finger

    override fun analyze(image: ImageProxy) {
        val yPlane = image.planes[0]
        val buf    = yPlane.buffer
        val stride = yPlane.rowStride
        val cx     = image.width  / 2
        val cy     = image.height / 2
        val roi    = 40

        var sum = 0L; var n = 0
        for (y in (cy - roi)..(cy + roi)) {
            for (x in (cx - roi)..(cx + roi)) {
                val idx = y * stride + x
                if (idx in 0 until buf.limit()) {
                    sum += (buf[idx].toInt() and 0xFF); n++
                }
            }
        }
        image.close()

        val mean = if (n > 0) sum.toFloat() / n else 0f

        if (mean < TORCH_THRESH) {
            raw.clear()
            onSample(0f, PpgState.WaitingForFinger)
            return
        }

        raw.addLast(mean)
        if (raw.size > WINDOW) raw.removeFirst()

        if (raw.size < MIN_COUNT) {
            // DC-remove and emit raw for waveform
            val dc = raw.average().toFloat()
            onSample((mean - dc) / (TORCH_THRESH / 2f), PpgState.Measuring)
            return
        }

        // DC removal
        val dc   = raw.average().toFloat()
        val norm = raw.map { it - dc }
        val absMax = norm.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 0.001f

        // Count positive zero-crossings as proxy for peaks
        // Each heartbeat ≈ one positive crossing (rising edge)
        var peaks = 0
        for (i in 1 until norm.size) {
            if (norm[i - 1] < 0f && norm[i] >= 0f) peaks++
        }
        val durationSec = raw.size / 30.0
        val bpm = ((peaks / durationSec) * 60.0).toInt()

        val normalizedLatest = (mean - dc) / absMax
        if (bpm in 40..200) {
            onSample(normalizedLatest, PpgState.Reading(bpm))
        } else {
            onSample(normalizedLatest, PpgState.Measuring)
        }
    }
}
