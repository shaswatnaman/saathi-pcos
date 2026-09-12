package com.leadmilers.saathi.ml

/**
 * Acoustic features extracted from a voice recording.
 *
 * Primary cycle-phase signals: f0Sd and f0Min.
 * Reference: Ziemer et al. (2025) JMIR Formative Research (PMC11737864) —
 * 9% lower F0SD and 8.8% higher F0MIN in luteal vs. follicular phase.
 *
 * energyScore is kept for backward compat with RiskScorer but relabeled
 * "experimental/exploratory" in the UI. It is NOT used as a PCOS-risk input.
 */
data class VoiceResult(
    val f0Mean: Float,       // Hz — per-user normalization anchor (no significant phase effect)
    val f0Sd: Float,         // Hz — primary cycle-phase signal
    val f0Min: Float,        // Hz — secondary cycle-phase signal (5th-percentile F0)
    val f0Max: Float,        // Hz — exploratory (no significant phase effect in literature)
    val voicedFrames: Int,   // frames with detected pitch — quality indicator
    val energyScore: Float,  // 0-1 normalized RMS — kept exploratory, not diagnostic
    val recordingPath: String = "",
    val isBaseline: Boolean = false
)
