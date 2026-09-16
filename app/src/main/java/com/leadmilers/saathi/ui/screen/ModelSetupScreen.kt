package com.leadmilers.saathi.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.R
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.ai.LocalAiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

@Composable
fun ModelSetupScreen(onModelReady: () -> Unit) {
    val context  = LocalContext.current
    val aiManager = remember { (context.applicationContext as SaathiApp).localAiManager }
    val scope    = rememberCoroutineScope()

    var state    by remember { mutableStateOf(DownloadState.IDLE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf("") }
    var downloadedMb by remember { mutableStateOf(0) }
    var totalMb  by remember { mutableStateOf((LocalAiManager.MODEL_SIZE_BYTES / 1_000_000L).toInt()) }

    // Auto-start download
    LaunchedEffect(Unit) {
        state = DownloadState.DOWNLOADING
        try {
            downloadModel(
                url       = LocalAiManager.MODEL_URL,
                dest      = aiManager.modelFile,
                onProgress = { done, total ->
                    downloadedMb = (done / 1_000_000L).toInt()
                    totalMb      = (total / 1_000_000L).toInt().coerceAtLeast(1)
                    progress     = done.toFloat() / total.coerceAtLeast(1)
                },
            )
            state = DownloadState.DONE
            onModelReady()
        } catch (e: Exception) {
            state    = DownloadState.ERROR
            errorMsg = e.message ?: "Unknown error"
            aiManager.modelFile.delete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.saathi_logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Setting up Saathi AI",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Downloading the Gemma 2B model (~1.5 GB) so Saathi can answer your questions entirely on your device — no internet needed after this.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        when (state) {
            DownloadState.DOWNLOADING -> {
                LinearProgressIndicator(
                    progress      = { progress },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color         = MaterialTheme.colorScheme.primary,
                    trackColor    = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "$downloadedMb MB / $totalMb MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This only happens once. Keep the app open.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            DownloadState.ERROR -> {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.WifiOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Download failed: $errorMsg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        state    = DownloadState.DOWNLOADING
                        progress = 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
            }

            DownloadState.DONE -> {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text("Loading model…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DownloadState.IDLE -> {}
        }
    }
}

private suspend fun downloadModel(
    url: String,
    dest: File,
    onProgress: (done: Long, total: Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val request  = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()

    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

    val body        = response.body ?: throw Exception("Empty response body")
    val totalBytes  = body.contentLength().coerceAtLeast(LocalAiManager.MODEL_SIZE_BYTES)
    val tmpFile     = File(dest.parent, dest.name + ".tmp")

    body.byteStream().use { input ->
        FileOutputStream(tmpFile).use { output ->
            val buf      = ByteArray(32 * 1024)
            var written  = 0L
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                if (!isActive) {
                    tmpFile.delete()
                    throw Exception("Cancelled")
                }
                output.write(buf, 0, read)
                written += read
                onProgress(written, totalBytes)
            }
        }
    }

    tmpFile.renameTo(dest)
}
