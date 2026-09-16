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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// ── Scan flow state machine ───────────────────────────────────────────────────

private enum class ScanState {
    IDLE,       // camera live, button ready
    COUNTDOWN,  // 3-2-1 before burst
    SCANNING,   // capturing 3 frames
    ANALYZING,  // running CNN
    RESULTS,    // results screen shown
    ERROR
}

// ── Entry point ───────────────────────────────────────────────────────────────

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
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Camera access needed for face scan",
                 style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onRequest) { Text("Grant Permission") }
        }
    }
}

// ── Main camera composable ────────────────────────────────────────────────────

@Composable
private fun CameraPreviewContent(context: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()
    val repository     = remember { (context.applicationContext as SaathiApp).repository }
    val classifier     = remember { AcneClassifier(context) }

    // Model setup
    var modelReady       by remember { mutableStateOf(classifier.isModelReady) }
    var modelDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb     by remember { mutableIntStateOf(0) }
    var modelError       by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!classifier.isModelReady) {
            modelDownloading = true
            runCatching {
                classifier.downloadModelIfNeeded { done, total ->
                    downloadedMb    = (done / 1_000_000L).toInt()
                    downloadProgress = done.toFloat() / total.coerceAtLeast(1)
                }
            }.onFailure { modelError = it.message ?: "Download failed" }
            modelDownloading = false
        }
        runCatching { classifier.ensureLoaded() }
        modelReady = true
    }

    if (!modelReady) {
        FaceModelSetupScreen(
            downloading  = modelDownloading,
            progress     = downloadProgress,
            downloadedMb = downloadedMb,
            error        = modelError,
            onRetry      = {
                scope.launch {
                    modelError = null; modelDownloading = true
                    runCatching {
                        classifier.downloadModelIfNeeded { d, t ->
                            downloadedMb     = (d / 1_000_000L).toInt()
                            downloadProgress = d.toFloat() / t.coerceAtLeast(1)
                        }
                    }.onFailure { modelError = it.message }
                    modelDownloading = false
                    runCatching { classifier.ensureLoaded() }
                    modelReady = classifier.isModelReady
                }
            }
        )
        return
    }

    // Scan state
    var scanState     by remember { mutableStateOf(ScanState.IDLE) }
    var countdown     by remember { mutableIntStateOf(3) }
    var framesDone    by remember { mutableIntStateOf(0) }
    var analysisResult by remember { mutableStateOf<AcneResult?>(null) }
    var currentLux    by remember { mutableFloatStateOf(1000f) }
    var imageCapture  by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(Unit) { onDispose { classifier.close() } }

    // Light sensor
    DisposableEffect(context) {
        val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val l      = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { currentLux = e.values[0] }
            override fun onAccuracyChanged(s: Sensor, a: Int) = Unit
        }
        sensor?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }

    // Helper: take one photo and return bitmap
    suspend fun takeOneBitmap(): Bitmap? {
        val cap = imageCapture ?: return null
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val exec = Executors.newSingleThreadExecutor()
            cap.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(proxy: ImageProxy) {
                    val bmp = proxyToBitmap(proxy)
                    proxy.close()
                    exec.shutdown()
                    cont.resumeWith(Result.success(bmp))
                }
                override fun onError(e: ImageCaptureException) {
                    exec.shutdown()
                    cont.resumeWith(Result.success(null))
                }
            })
        }
    }

    // Burst: 3 frames → average ValidAnalysis
    fun startScan() {
        scope.launch {
            // Countdown
            scanState = ScanState.COUNTDOWN
            for (i in 3 downTo 1) {
                countdown = i
                delay(800)
            }

            // Capture 3 frames
            scanState  = ScanState.SCANNING
            framesDone = 0
            val results = mutableListOf<AcneResult.ValidAnalysis>()
            repeat(3) {
                val bmp = takeOneBitmap()
                if (bmp != null) {
                    val r = classifier.analyze(bmp)
                    if (r is AcneResult.ValidAnalysis) results.add(r)
                }
                framesDone++
                if (it < 2) delay(400)
            }

            // Analyze / average
            scanState = ScanState.ANALYZING
            delay(200)

            if (results.isEmpty()) {
                // Fall back to last non-null result
                analysisResult = AcneResult.NoFace
                scanState = ScanState.ERROR
                return@launch
            }

            // Average all feature scores across frames for stability
            val avgScore     = results.map { it.score }.average().toFloat()
            val avgHirsutism = results.map { it.hirsutismScore }.average().toFloat()
            val avgFeatures  = results.flatMap { it.features.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, vs) -> vs.average().toFloat() }
            val avgConfidence = results.map { it.confidence }.average().toFloat()
            val avgCoverage   = results.map { it.skinCoverage }.average().toFloat()
            val regions       = results.flatMap { it.acneRegions }.distinct()

            val merged = results.last().copy(
                score          = avgScore,
                hirsutismScore = avgHirsutism,
                features       = avgFeatures,
                confidence     = avgConfidence,
                skinCoverage   = avgCoverage,
                acneRegions    = regions,
            )

            analysisResult = merged
            saveToSymptomLog(repository, merged)
            scanState = ScanState.RESULTS
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ───────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    ProcessCameraProvider.getInstance(ctx).addListener({
                        runCatching {
                            val provider = ProcessCameraProvider.getInstance(ctx).get()
                            val preview  = Preview.Builder().build()
                                .also { it.setSurfaceProvider(pv.surfaceProvider) }
                            val cap = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()
                            imageCapture = cap
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview, cap
                            )
                        }.onFailure { Log.e("CameraScreen", "Camera bind failed", it) }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Face oval guide (shown until results) ────────────────────────────
        if (scanState != ScanState.RESULTS) {
            FaceOvalGuide(
                isScanning = scanState == ScanState.SCANNING || scanState == ScanState.ANALYZING,
                modifier   = Modifier.fillMaxSize()
            )
        }

        // ── Lux warning ──────────────────────────────────────────────────────
        if (currentLux < 80f && scanState == ScanState.IDLE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 20.dp, end = 20.dp)
                    .background(Color(0xFFE65100).copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Low light · move near a window for accurate results",
                    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }

        // ── Countdown overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = scanState == ScanState.COUNTDOWN,
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val scale by animateFloatAsState(
                targetValue = if (scanState == ScanState.COUNTDOWN) 1f else 0.5f,
                animationSpec = tween(300), label = "countdown"
            )
            Text(
                text     = "$countdown",
                color    = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            )
        }

        // ── Scanning progress ─────────────────────────────────────────────────
        if (scanState == ScanState.SCANNING) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Hold still · frame $framesDone of 3",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(
                    progress = { framesDone / 3f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color    = Color(0xFFE98B8B),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }

        // ── Analyzing spinner ─────────────────────────────────────────────────
        if (scanState == ScanState.ANALYZING) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFFE98B8B), modifier = Modifier.size(40.dp))
                Text("Analyzing…", color = Color.White, fontSize = 14.sp)
            }
        }

        // ── Capture button (IDLE only) ────────────────────────────────────────
        if (scanState == ScanState.IDLE) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Align your face in the oval · tap to scan",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                FilledIconButton(
                    onClick  = { startScan() },
                    modifier = Modifier.size(72.dp),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Camera, "Scan",
                        tint = Color.Black, modifier = Modifier.size(36.dp))
                }
            }
        }

        // ── Results full-screen overlay ───────────────────────────────────────
        AnimatedVisibility(
            visible  = scanState == ScanState.RESULTS,
            enter    = slideInVertically(initialOffsetY = { it }),
            exit     = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            val result = analysisResult
            if (result is AcneResult.ValidAnalysis) {
                ScanResultsScreen(
                    result  = result,
                    onRetake = {
                        analysisResult = null
                        scanState      = ScanState.IDLE
                    }
                )
            }
        }

        // ── Error state ───────────────────────────────────────────────────────
        if (scanState == ScanState.ERROR) {
            var retryCountdown by remember { mutableStateOf(3) }
            LaunchedEffect(Unit) {
                while (retryCountdown > 0) {
                    delay(1_000)
                    retryCountdown--
                }
                scanState = ScanState.IDLE
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .padding(horizontal = 24.dp)
                    .background(Color(0xFFB71C1C).copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Couldn't find your face", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Move to better light and center your face in the oval.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                OutlinedButton(
                    onClick = { scanState = ScanState.IDLE },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                ) {
                    Text("Try again (${retryCountdown}s)")
                }
            }
        }
    }
}

// ── Face oval guide ────────────────────────────────────────────────────────────

@Composable
private fun FaceOvalGuide(isScanning: Boolean, modifier: Modifier) {
    val borderAnim by animateFloatAsState(
        targetValue    = if (isScanning) 1f else 0.55f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "oval"
    )
    val borderColor = if (isScanning) Color(0xFFE98B8B) else Color.White
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            // Dim everything outside the oval
            val ovalW = size.width * 0.72f
            val ovalH = size.height * 0.58f
            val cx    = size.width / 2f
            val cy    = size.height * 0.44f
            val rect  = Rect(Offset(cx - ovalW / 2, cy - ovalH / 2), Size(ovalW, ovalH))
            // Dark scrim
            val path = Path().apply {
                addRect(Rect(Offset.Zero, size))
                addOval(rect)
                fillType = PathFillType.EvenOdd
            }
            drawPath(path, Color.Black.copy(alpha = 0.55f))
            // Oval border
            drawOval(
                color   = borderColor.copy(alpha = borderAnim),
                topLeft = rect.topLeft,
                size    = rect.size,
                style   = Stroke(width = 2.5.dp.toPx())
            )
        }
    )
}

// ── Full-screen results ────────────────────────────────────────────────────────

private val PlumDark   = Color(0xFF1A0D22)
private val PlumMid    = Color(0xFF5B3A68)
private val PlumLight  = Color(0xFF9C6EA8)
private val Coral      = Color(0xFFE98B8B)
private val OnSurface  = Color(0xFFF4EEF7)
private val OnSurfaceDim = Color(0xFFB89EC2)

@Composable
private fun ScanResultsScreen(
    result: AcneResult.ValidAnalysis,
    onRetake: () -> Unit,
) {
    val pct = (result.score * 100).toInt().coerceIn(0, 100)

    val (severityLabel, severityColor, severityDesc) = when {
        pct < 20 -> Triple("Clear",    Color(0xFF43A047), "No significant lesions detected in analyzed regions.")
        pct < 40 -> Triple("Mild",     Color(0xFF8BC34A), "Minor skin activity visible — mild inflammatory signals.")
        pct < 60 -> Triple("Moderate", Color(0xFFFFA726), "Moderate inflammation present across multiple regions.")
        pct < 80 -> Triple("Elevated", Color(0xFFEF6C00), "Significant inflammatory activity across the face.")
        else     -> Triple("Severe",   Color(0xFFE53935), "High-level inflammation — multiple active regions.")
    }

    val confidencePct = (result.confidence * 100).toInt().coerceIn(0, 100)
    val coveragePct   = (result.skinCoverage * 100).toInt().coerceIn(0, 100)

    Box(
        Modifier
            .fillMaxSize()
            .background(PlumDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(PlumMid.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .padding(top = 52.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Skin Analysis Report",
                        color      = OnSurfaceDim,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp)
                    Text("Face Scan",
                        color      = OnSurface,
                        fontSize   = 24.sp,
                        fontWeight = FontWeight.Bold)
                }
            }

            // ── Overall score gauge ───────────────────────────────────────────
            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = PlumMid.copy(alpha = 0.35f)),
                border   = BorderStroke(1.dp, PlumLight.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Inflammation Score",
                                color    = OnSurfaceDim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.8.sp)
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("$pct",
                                    color      = OnSurface,
                                    fontSize   = 52.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 52.sp)
                                Text("/ 100",
                                    color    = OnSurfaceDim,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(bottom = 10.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(severityColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                .border(1.dp, severityColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(severityLabel,
                                color      = severityColor,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }

                    // Score bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(result.score.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF43A047), Color(0xFFFFA726), Color(0xFFE53935))
                                    )
                                )
                        )
                    }

                    Text(severityDesc, color = OnSurfaceDim, fontSize = 12.sp)

                    // Confidence + coverage row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetaChip("Confidence", "$confidencePct%", Modifier.weight(1f))
                        MetaChip("Face coverage", "$coveragePct%", Modifier.weight(1f))
                        MetaChip("Frames", "3 avg", Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Feature breakdown ─────────────────────────────────────────────
            SectionHeader("Skin Feature Analysis")

            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = PlumMid.copy(alpha = 0.25f)),
                border   = BorderStroke(1.dp, PlumLight.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FeatureBar("Acne / Inflammation", result.score,
                        higherIsBad = true)
                    if (result.hirsutismScore > 0.05f)
                        FeatureBar("Facial Hair (hirsutism)", result.hirsutismScore,
                            higherIsBad = true)
                    result.features["oiliness"]?.let {
                        FeatureBar("T-zone Oiliness", it, higherIsBad = true)
                    }
                    result.features["skin_darkening"]?.let {
                        FeatureBar("Jaw / Neck Darkening", it, higherIsBad = true)
                    }
                    result.features["dark_circles"]?.let {
                        FeatureBar("Under-eye Dark Circles", it, higherIsBad = true)
                    }
                    result.features["eye_puffiness"]?.let {
                        FeatureBar("Eye Puffiness", it, higherIsBad = true)
                    }
                    result.features["skin_uniformity"]?.let {
                        FeatureBar("Skin Evenness", it, higherIsBad = false)
                    }
                    result.features["eyebrow_thinning"]?.let {
                        FeatureBar("Eyebrow Fullness", 1f - it, higherIsBad = false)
                    }
                    result.features["symmetry"]?.let {
                        FeatureBar("Facial Symmetry", it, higherIsBad = false)
                    }
                    result.features["lip_darkening"]?.let {
                        FeatureBar("Lip Colour Balance", 1f - it, higherIsBad = false)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Affected regions ──────────────────────────────────────────────
            if (result.acneRegions.isNotEmpty()) {
                SectionHeader("Affected Regions")
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = PlumMid.copy(alpha = 0.25f)),
                    border   = BorderStroke(1.dp, PlumLight.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        result.acneRegions.forEach { region ->
                            Box(
                                modifier = Modifier
                                    .background(Coral.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Coral.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(region.replaceFirstChar { it.uppercase() },
                                    color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Observations ──────────────────────────────────────────────────
            if (result.observations.isNotEmpty()) {
                SectionHeader("Observations")
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = PlumMid.copy(alpha = 0.25f)),
                    border   = BorderStroke(1.dp, PlumLight.copy(alpha = 0.2f))
                ) {
                    Text(
                        result.observations,
                        color    = OnSurface.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Guidance ──────────────────────────────────────────────────────
            if (result.guidance.isNotEmpty()) {
                SectionHeader("Guidance")
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFF1B3A2A)),
                    border   = BorderStroke(1.dp, Color(0xFF43A047).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("💡", fontSize = 18.sp)
                        Text(result.guidance,
                            color    = Color(0xFFA5D6A7),
                            fontSize = 13.sp,
                            lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Medical disclaimer ────────────────────────────────────────────
            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                )
            ) {
                Text(
                    "This analysis is generated by an on-device computer vision model and is for " +
                    "informational purposes only. It does not constitute medical advice, diagnosis, " +
                    "or treatment. Results may vary based on lighting, camera, and skin tone. " +
                    "Consult a qualified dermatologist for clinical assessment.",
                    color    = OnSurfaceDim.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, PlumDark))
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, PlumLight.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Refresh, null,
                    tint = PlumLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retake", color = PlumLight, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick  = onRetake,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PlumMid)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text.uppercase(),
        color    = OnSurfaceDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun MetaChip(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value,
            color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label,
            color = OnSurfaceDim, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeatureBar(name: String, score: Float, higherIsBad: Boolean) {
    val pct = (score * 100).roundToInt().coerceIn(0, 100)
    val barColor = when {
        higherIsBad -> when {
            score < 0.30f -> Color(0xFF43A047)
            score < 0.55f -> Color(0xFFFFA726)
            else          -> Color(0xFFE53935)
        }
        else -> when {
            score > 0.70f -> Color(0xFF43A047)
            score > 0.45f -> Color(0xFFFFA726)
            else          -> Color(0xFFE53935)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(name,
            color    = OnSurface.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.width(160.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(barColor)
            )
        }
        Text("$pct%",
            color    = barColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End)
    }
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color     = Color.White.copy(alpha = 0.06f)
    )
}

// ── Unchanged helpers ──────────────────────────────────────────────────────────

@Composable
private fun FaceModelSetupScreen(
    downloading: Boolean,
    progress: Float,
    downloadedMb: Int,
    error: String?,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Setting up Face Scan", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Downloading face analysis model (~7 MB). This happens once.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                textAlign = TextAlign.Center)
            if (downloading) {
                LinearProgressIndicator(
                    progress    = { progress },
                    modifier    = Modifier.fillMaxWidth().height(6.dp),
                    color       = Color.White,
                    trackColor  = Color.White.copy(alpha = 0.2f),
                )
                Text("$downloadedMb MB", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            if (error != null) {
                Text("Error: $error", color = Color(0xFFFF8A80), fontSize = 12.sp,
                    textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Retry") }
            } else if (!downloading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                Text("Loading model…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }
}

private fun proxyToBitmap(proxy: ImageProxy): Bitmap {
    val buffer = proxy.planes[0].buffer
    val bytes  = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val bmp    = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

private suspend fun saveToSymptomLog(
    repository: com.leadmilers.saathi.data.repository.SaathiRepository,
    result: AcneResult.ValidAnalysis,
) {
    val existing = repository.getLatestSymptomLog()
    val log = existing?.copy(acneScore = result.score)
        ?: SymptomLog(
            date             = System.currentTimeMillis(),
            fatigue          = 3,
            acneScore        = result.score,
            voiceEnergyScore = 0.8f,
            weight           = 0f,
            waistHip         = 0f,
        )
    if (existing != null) repository.updateSymptomLog(log)
    else repository.insertSymptomLog(log)
}
