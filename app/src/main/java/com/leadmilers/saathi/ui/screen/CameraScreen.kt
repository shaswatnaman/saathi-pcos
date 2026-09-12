package com.leadmilers.saathi.ui.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var acneResult: AcneResult?     by remember { mutableStateOf(null) }
    var isAnalyzing                  by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { classifier.close() } }

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

        // ── Result / feedback overlay ────────────────────────────────────────
        acneResult?.let { result ->
            ResultOverlay(
                result   = result,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
            )
        }

        // ── Capture button ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            if (isAnalyzing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color    = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "Analyzing…",
                        color    = Color.White,
                        fontSize = 13.sp
                    )
                }
            } else {
                FilledIconButton(
                    onClick = {
                        val capture = imageCapture ?: return@FilledIconButton
                        isAnalyzing = true
                        acneResult  = null

                        val executor = Executors.newSingleThreadExecutor()
                        capture.takePicture(
                            executor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(proxy: ImageProxy) {
                                    scope.launch {
                                        try {
                                            val bitmap = proxyToBitmap(proxy)
                                            proxy.close()
                                            val result = withContext(Dispatchers.Default) {
                                                classifier.analyze(bitmap)
                                            }
                                            acneResult = result
                                            // Only persist to DB on a valid analysis
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
                        containerColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Capture",
                        tint               = Color.Black,
                        modifier           = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// ── Result overlay composable ─────────────────────────────────────────────────

@Composable
private fun ResultOverlay(result: AcneResult, modifier: Modifier) {
    val (bgColor, headline, subline) = when (result) {

        // ── Abstention states ─────────────────────────────────────────────
        // NONE of these should ever read as "clear skin".

        is AcneResult.NoFace ->
            Triple(
                Color(0xFF546E7A),
                "No face detected",
                "Point the camera at your face"
            )

        is AcneResult.MultipleFaces ->
            Triple(
                Color(0xFF5D4037),
                "Multiple faces detected (${result.count})",
                "Only one face should be visible"
            )

        is AcneResult.PoorImageQuality ->
            Triple(
                Color(0xFFE65100),
                when (result.reason) {
                    AcneResult.Quality.TOO_BLURRY          -> "Image too blurry"
                    AcneResult.Quality.TOO_DARK            -> "Image too dark"
                    AcneResult.Quality.TOO_BRIGHT          -> "Image overexposed"
                    AcneResult.Quality.TOO_LOW_RESOLUTION  -> "Move closer"
                },
                result.reason.guidance
            )

        is AcneResult.InsufficientCoverage ->
            Triple(
                Color(0xFF37474F),
                "Face not fully visible",
                "Move closer — ${"%.0f".format(result.skinCoverage * 100)}% skin coverage (need ≥ 33%)"
            )

        is AcneResult.LowConfidence ->
            Triple(
                Color(0xFF4A148C),
                "Unable to analyze reliably",
                "Try better lighting or move the camera closer"
            )

        is AcneResult.InferenceError ->
            Triple(
                Color(0xFFB71C1C),
                "Analysis error",
                "Please try again"
            )

        // ── Valid analysis ────────────────────────────────────────────────

        is AcneResult.ValidAnalysis -> {
            val pct = (result.score * 100).toInt()
            val conf = (result.confidence * 100).toInt()
            if (result.hasSignificantAcne) {
                Triple(
                    Color(0xFFB71C1C),
                    "Acne-like lesions detected ($pct%)",
                    "Confidence $conf% • Consult a dermatologist for evaluation"
                )
            } else {
                Triple(
                    Color(0xFF2E7D32),
                    "No significant lesions in analyzed areas",
                    "Score $pct% • Confidence $conf% • Not a clinical diagnosis"
                )
            }
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(bgColor.copy(alpha = 0.90f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            headline,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 15.sp
        )
        Text(
            subline,
            color    = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
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
