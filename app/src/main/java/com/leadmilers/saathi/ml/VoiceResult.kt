package com.leadmilers.saathi.ml

data class VoiceResult(
    val energyScore: Float,
    val recordingPath: String,
    val isBaseline: Boolean = false
)
