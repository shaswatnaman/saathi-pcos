package com.leadmilers.saathi.ui.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.ml.AcneClassifier
import com.leadmilers.saathi.ml.AcneResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (!cameraPermission.status.isGranted) {
        PermissionRequest(onRequest = { cameraPermission.launchPermissionRequest() })
        return
    }

    CameraPreviewContent(context)
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Camera access is needed to analyze acne",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onRequest) { Text("Grant Permission") }
        }
    }
}

@Composable
private fun CameraPreviewContent(context: Context) {
    val lifecycleOwner  = LocalLifecycleOwner.current
    val scope           = rememberCoroutineScope()
    val repository      = remember { (context.applicationContext as SaathiApp).repository }
    val classifier      = remember { AcneClassifier() }

    var imageCapture: ImageCapture?  by remember { mutableStateOf(null) }
    var acneResult: AcneResult?      by remember { mutableStateOf(null) }
    var isAnalyzing                   by remember { mutableStateOf(false) }
    var capturedThumbnail: Bitmap?   by remember { mutableStateOf(null) }
    var currentLux by remember { mutableFloatStateOf(1000f) }

    DisposableEffect(Unit) { onDispose { classifier.close() } }

    // Register ambient light sensor to gate capture quality
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { currentLux = e.values[0] }
            override fun onAccuracyChanged(s: Sensor, a: Int) = Unit
        }
        lightSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ───────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    ProcessCameraProvider.getInstance(ctx).addListener({
                        val provider = ProcessCameraProvider.getInstance(ctx).get()
                        val preview  = Preview.Builder().build()
                            .also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val capture  = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview, capture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Ambient-light warning (< 80 lux = dim indoor / twilight) ────────
        if (currentLux < 80f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xFFE65100).copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "⚠️  Low light (${currentLux.toInt()} lx) — move to brighter surroundings for reliable analysis",
                    color    = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        // ── Result / feedback overlay ────────────────────────────────────────
        acneResult?.let { result ->
            ResultOverlay(
                result   = result,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
            )
        }

        // ── Thumbnail while scanning ─────────────────────────────────────────
        if (isAnalyzing && capturedThumbnail != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Image(
                    bitmap       = capturedThumbnail!!.asImageBitmap(),
                    contentDescription = "Captured photo",
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color    = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // ── Capture / retake button ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Retake button — visible after result or during scan
            if (acneResult != null || isAnalyzing) {
                FilledIconButton(
                    onClick = {
                        acneResult = null
                        capturedThumbnail = null
                    },
                    modifier = Modifier.size(52.dp),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.25f)
                    )
                ) {
                    Icon(Icons.Default.Refresh, "Retake",
                        tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            // Main capture button — always enabled so user can retake mid-scan
            FilledIconButton(
                onClick = {
                    val capture = imageCapture ?: return@FilledIconButton
                    isAnalyzing = true
                    acneResult  = null
                    capturedThumbnail = null

                    val executor = Executors.newSingleThreadExecutor()
                    capture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(proxy: ImageProxy) {
                                scope.launch {
                                    try {
                                        val bitmap = proxyToBitmap(proxy)
                                        proxy.close()
                                        capturedThumbnail = bitmap
                                        val result = classifier.analyze(bitmap)
                                        acneResult = result
                                        if (result is AcneResult.ValidAnalysis) {
                                            saveToSymptomLog(repository, result)
                                        }
                                    } catch (e: Exception) {
                                        acneResult = AcneResult.InferenceError
                                        Log.e("CameraScreen", "Capture processing error", e)
                                    } finally {
                                        isAnalyzing = false
                                        executor.shutdown()
                                    }
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                scope.launch {
                                    acneResult  = AcneResult.InferenceError
                                    isAnalyzing = false
                                    executor.shutdown()
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.size(72.dp),
                shape    = CircleShape,
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isAnalyzing) Color.White.copy(alpha = 0.5f) else Color.White
                )
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(Icons.Default.Camera, "Capture",
                        tint = Color.Black, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

// ── Result overlay composable ─────────────────────────────────────────────────

@Composable
private fun ResultOverlay(result: AcneResult, modifier: Modifier) {
    when (result) {

        // ── Guidance-only states ──────────────────────────────────────────────
        is AcneResult.NoFace ->
            SimpleResultCard(modifier, Color(0xFF546E7A),
                "👤", "Can't see your face",
                "Point the front camera directly at your face and try again.")

        is AcneResult.MultipleFaces ->
            SimpleResultCard(modifier, Color(0xFF5D4037),
                "👥", "More than one face visible",
                "Make sure you're the only person in frame, then retake.")

        is AcneResult.PoorImageQuality ->
            SimpleResultCard(modifier, Color(0xFFE65100),
                "📷", when (result.reason) {
                    AcneResult.Quality.TOO_BLURRY         -> "Photo came out blurry"
                    AcneResult.Quality.TOO_DARK           -> "It's too dark here"
                    AcneResult.Quality.TOO_BRIGHT         -> "Too much light — try shade"
                    AcneResult.Quality.TOO_LOW_RESOLUTION -> "Move a little closer"
                }, result.reason.guidance)

        is AcneResult.InsufficientCoverage ->
            SimpleResultCard(modifier, Color(0xFF37474F),
                "🔍", "Move a bit closer",
                "Your face needs to fill more of the frame for a reliable reading.")

        is AcneResult.LowConfidence ->
            SimpleResultCard(modifier, Color(0xFF4A148C),
                "💡", "Hard to read in this lighting",
                "Try standing near a window or a bright lamp and take another photo.")

        is AcneResult.InferenceError ->
            SimpleResultCard(modifier, Color(0xFF795548),
                "🔄", "Couldn't complete the scan",
                "Check your internet connection and try again.")

        // ── Valid analysis — friendly card ────────────────────────────────────
        is AcneResult.ValidAnalysis -> FriendlyResultCard(result, modifier)
    }
}

@Composable
private fun SimpleResultCard(
    modifier: Modifier,
    color: Color,
    emoji: String,
    title: String,
    body: String
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(color.copy(alpha = 0.88f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("$emoji  $title", color = Color.White,
            fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(body, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
    }
}

@Composable
private fun FriendlyResultCard(result: AcneResult.ValidAnalysis, modifier: Modifier) {
    val pct = (result.score * 100).toInt()

    // Friendly headline
    val (emoji, headline, skinNote) = when {
        pct < 15 -> Triple("✨", "Your skin looks clear today",
            "No significant inflammation visible right now.")
        pct < 35 -> Triple("🌿", "Mild skin activity noticed",
            "A small amount of inflammation detected — nothing unusual.")
        pct < 60 -> Triple("🌡️", "Some inflammation detected",
            "Moderate skin activity visible. Worth keeping an eye on.")
        else     -> Triple("💊", "Noticeable skin flare-up",
            "Significant inflammation visible today. Consider tracking this over a few days.")
    }

    // Region in plain language
    val regionText = when {
        result.acneRegions.isEmpty() || result.acneRegions == listOf("none") -> ""
        result.acneRegions.size == 1 -> "Mainly around your ${result.acneRegions.first()}."
        else -> "Concentrated around your ${result.acneRegions.dropLast(1).joinToString(", ")} and ${result.acneRegions.last()}."
    }

    // Hirsutism in plain language
    val hairNote = when {
        result.hirsutismScore > 0.5f -> "Some facial hair growth also noticed — this can be related to hormonal changes."
        result.hirsutismScore > 0.2f -> "Slight facial hair visible."
        else -> ""
    }

    // Card color
    val bgColor = when {
        pct < 15 -> Color(0xFF1B5E20)
        pct < 35 -> Color(0xFF33691E)
        pct < 60 -> Color(0xFFE65100)
        else     -> Color(0xFFB71C1C)
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(bgColor.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$emoji  $headline",
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

        Text(skinNote, color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp)

        if (regionText.isNotEmpty())
            Text(regionText, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)

        if (hairNote.isNotEmpty())
            Text(hairNote, color = Color.White.copy(alpha = 0.80f), fontSize = 12.sp)

        // Guidance from AI (already plain language)
        if (result.guidance.isNotEmpty())
            Text(result.guidance, color = Color.White.copy(alpha = 0.80f), fontSize = 12.sp)

        // Subtle save confirmation
        Text("✓ Saved to your skin log today",
            color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)

        Text("Skin tracking only · not a medical diagnosis",
            color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
    }
}

// ── Bitmap helpers ────────────────────────────────────────────────────────────

private fun proxyToBitmap(proxy: ImageProxy): Bitmap {
    val buffer = proxy.planes[0].buffer
    val bytes  = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bmp    = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

// ── DB persistence (only called for ValidAnalysis) ────────────────────────────

private suspend fun saveToSymptomLog(
    repository: com.leadmilers.saathi.data.repository.SaathiRepository,
    result: AcneResult.ValidAnalysis
) {
    val existing = repository.getLatestSymptomLog()
    val log = existing?.copy(acneScore = result.score)
        ?: SymptomLog(
            date             = System.currentTimeMillis(),
            fatigue          = 3,
            acneScore        = result.score,
            voiceEnergyScore = 0.8f,
            weight           = 0f,
            waistHip         = 0f
        )
    if (existing != null) repository.updateSymptomLog(log)
    else repository.insertSymptomLog(log)
}
