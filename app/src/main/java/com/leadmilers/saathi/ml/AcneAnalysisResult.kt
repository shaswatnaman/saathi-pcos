package com.leadmilers.saathi.ml

data class AcneAnalysisResult(
    val score: Float,
    val isAndrogenic: Boolean,
    val confidence: Float
)
