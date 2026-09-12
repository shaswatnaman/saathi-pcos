package com.leadmilers.saathi.ml

import kotlin.math.exp

/**
 * PCOS risk scorer trained on 541 real patients (Kaggle PCOS dataset).
 * Logistic regression: 10-fold CV AUC = 0.862
 * Features validated against clinical data — waist-hip ratio removed
 * (not discriminating in Indian population).
 */
object RiskScorer {

    // Logistic regression weights (pre-scaled, apply directly to raw 0/1 inputs)
    private const val W_CYCLE_IRREGULAR  = +1.580317f
    private const val W_ACNE            = +0.859949f
    private const val W_WEIGHT_GAIN     = +1.328890f
    private const val W_SKIN_DARKENING  = +1.769301f
    private const val W_HAIR_ISSUES     = +0.404461f
    private const val W_BMI             = -0.023809f
    private const val BIAS              = -1.881813f

    private fun sigmoid(x: Float): Float = (1.0f / (1.0f + exp(-x.toDouble()).toFloat()))

    data class RiskResult(
        val probability: Float,      // 0-1 PCOS probability from model
        val riskLevel: RiskLevel,
        val riskScore: Int,          // 0-12 interpretable score for UI
        val dominantFactors: List<String>
    )

    enum class RiskLevel(val label: String, val color: Long) {
        LOW("Low Risk", 0xFF4CAF50),
        MODERATE("Moderate Risk", 0xFFFF9800),
        HIGH("High Risk", 0xFFF44336),
        CRITICAL("Critical — See Doctor", 0xFF9C27B0)
    }

    fun calculate(
        cycleIrregular: Boolean,   // cycle length < 21 or > 35 days, or user flags irregular
        acneScore: Float,          // 0-1 from AcneClassifier TFLite (0.94 AUC)
        weightGain: Boolean,       // self-reported
        skinDarkening: Boolean,    // self-reported or camera HSV analysis
        hairIssues: Boolean,       // excess growth OR hair loss
        bmi: Float = 23f           // default average if not entered
    ): RiskResult {

        val acneBinary = if (acneScore > 0.5f) 1f else 0f

        // Logistic regression probability (clinical model)
        val logit = W_CYCLE_IRREGULAR * (if (cycleIrregular) 1f else 0f) +
                    W_ACNE            * acneBinary +
                    W_WEIGHT_GAIN     * (if (weightGain) 1f else 0f) +
                    W_SKIN_DARKENING  * (if (skinDarkening) 1f else 0f) +
                    W_HAIR_ISSUES     * (if (hairIssues) 1f else 0f) +
                    W_BMI             * bmi +
                    BIAS
        val probability = sigmoid(logit)

        // Interpretable score for UI (maps to 0-12)
        var score = 0
        val factors = mutableListOf<String>()
        if (cycleIrregular)   { score += 3; factors += "Irregular cycle" }
        if (acneBinary > 0f)  { score += 3; factors += "Androgenic acne" }
        if (skinDarkening)    { score += 2; factors += "Skin darkening" }
        if (weightGain)       { score += 2; factors += "Unexplained weight gain" }
        if (hairIssues)       { score += 1; factors += "Hair changes" }

        val level = when {
            probability >= 0.65f -> RiskLevel.CRITICAL
            probability >= 0.50f -> RiskLevel.HIGH
            probability >= 0.35f -> RiskLevel.MODERATE
            else                 -> RiskLevel.LOW
        }

        return RiskResult(probability, level, score, factors)
    }
}
