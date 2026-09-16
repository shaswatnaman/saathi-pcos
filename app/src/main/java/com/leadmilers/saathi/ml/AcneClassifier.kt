package com.leadmilers.saathi.ml

import android.content.Context
import android.graphics.Bitmap

class AcneClassifier(context: Context) {

    val analyzer = OnDeviceFaceAnalyzer(context)

    val isModelReady: Boolean get() = analyzer.isModelReady

    suspend fun downloadModelIfNeeded(
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) = analyzer.downloadModelIfNeeded(onProgress)

    suspend fun ensureLoaded() = analyzer.ensureLoaded()

    suspend fun analyze(bitmap: Bitmap): AcneResult = analyzer.analyze(bitmap)

    fun close() = analyzer.release()
}
