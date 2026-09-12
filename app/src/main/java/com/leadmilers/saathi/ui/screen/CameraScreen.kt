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
import com.leadmilers.saathi.ml.AcneAnalysisResult
import com.leadmilers.saathi.ml.AcneClassifier
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Camera access is needed to analyze acne", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRequest) { Text("Grant Permission") }
        }
    }
}

@Composable
private fun CameraPreviewContent(context: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { (context.applicationContext as SaathiApp).repository }
    val classifier = remember { AcneClassifier() }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var analysisResult: AcneAnalysisResult? by remember { mutableStateOf(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { classifier.close() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Result overlay
        analysisResult?.let { result ->
            val color = if (result.isAndrogenic) Color(0xFFE53935) else Color(0xFF43A047)
            val label = if (result.isAndrogenic)
                "Androgenic acne detected (${(result.score * 100).toInt()}%)"
            else
                "Clear skin (${(result.confidence * 100).toInt()}% confidence)"

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(color.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color(0xFFB71C1C).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 14.sp)
            }
        }

        // Bottom controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
            } else {
                FilledIconButton(
                    onClick = {
                        val capture = imageCapture ?: return@FilledIconButton
                        isAnalyzing = true
                        analysisResult = null
                        errorMessage = null

                        val executor = Executors.newSingleThreadExecutor()
                        capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(proxy: ImageProxy) {
                                scope.launch {
                                    try {
                                        val bitmap = proxyToBitmap(proxy)
                                        proxy.close()
                                        val cropped = cropFaceRegion(bitmap)
                                        val result = withContext(Dispatchers.Default) {
                                            classifier.analyze(cropped)
                                        }
                                        analysisResult = result
                                        saveToSymptomLog(repository, result)
                                    } catch (e: Exception) {
                                        errorMessage = "Analysis failed: ${e.message}"
                                        Log.e("CameraScreen", "Inference error", e)
                                    } finally {
                                        isAnalyzing = false
                                        executor.shutdown()
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                scope.launch {
                                    errorMessage = "Capture failed: ${exception.message}"
                                    isAnalyzing = false
                                    executor.shutdown()
                                }
                            }
                        })
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capture",
                        tint = Color.Black, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

private fun proxyToBitmap(proxy: ImageProxy): Bitmap {
    val buffer = proxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

// Center-crop the middle 70% of the frame — captures face in selfie mode
private fun cropFaceRegion(src: Bitmap): Bitmap {
    val cropFactor = 0.70f
    val w = (src.width * cropFactor).toInt()
    val h = (src.height * cropFactor).toInt()
    val x = (src.width - w) / 2
    val y = (src.height - h) / 4  // offset upward slightly so face is centered
    return Bitmap.createBitmap(src, x, y, w, h)
}

private suspend fun saveToSymptomLog(
    repository: com.leadmilers.saathi.data.repository.SaathiRepository,
    result: AcneAnalysisResult
) {
    val existing = repository.getLatestSymptomLog()
    val log = existing?.copy(acneScore = result.score)
        ?: SymptomLog(
            date = System.currentTimeMillis(),
            fatigue = 3,
            acneScore = result.score,
            voiceEnergyScore = 0.8f,
            weight = 0f,
            waistHip = 0f
        )
    if (existing != null) repository.updateSymptomLog(log)
    else repository.insertSymptomLog(log)
}
