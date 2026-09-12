package com.leadmilers.saathi.ml

import android.graphics.Bitmap

class AcneClassifier {

    private val cloudAnalyzer = CloudFaceAnalyzer()

    suspend fun analyze(bitmap: Bitmap): AcneResult = cloudAnalyzer.analyze(bitmap)

    fun close() = Unit
}
