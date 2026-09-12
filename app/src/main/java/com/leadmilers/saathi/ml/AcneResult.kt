package com.leadmilers.saathi.ml

/**
 * Every state the acne pipeline can produce.
 *
 * INVARIANT: none of the abstention states may ever be shown to the user
 * as "clear skin". Only [ValidAnalysis] with [hasSignificantAcne]=false
 * is allowed to say "no significant lesions detected".
 */
sealed class AcneResult {

    // ── Abstention states ────────────────────────────────────────────────────
    // These must NEVER be interpreted as "clear skin".

    /** No skin-tone region large enough to contain a face. */
    data object NoFace : AcneResult()

    /** Face detector found more than one face. */
    data class MultipleFaces(val count: Int) : AcneResult()

    /** Image rejected before face detection due to a measurable quality failure. */
    data class PoorImageQuality(val reason: Quality) : AcneResult()

    /** A face was partially detected but the skin coverage is too small for
     *  reliable analysis (< 35 % of the frame). */
    data class InsufficientCoverage(val skinCoverage: Float) : AcneResult()

    /** All quality gates passed but the combined analysis confidence is below
     *  the calibrated floor — abstaining rather than guessing. */
    data object LowConfidence : AcneResult()

    /** An unexpected exception occurred at any stage. */
    data object InferenceError : AcneResult()

    // ── Valid result ─────────────────────────────────────────────────────────
    // Only reachable after every gate passes.

    /**
     * @param score         0–1 acne severity (HSV feature composite).
     * @param hasSignificantAcne  true when [score] > threshold AND confidence is
     *   sufficient. false means "no significant lesions detected in analyzed
     *   areas" — NOT a clinical "clear skin" declaration.
     * @param confidence    Calibrated analysis confidence 0–1 (skinCoverage × qualityScore).
     * @param skinCoverage  Fraction of image pixels classified as skin-tone.
     */
    data class ValidAnalysis(
        val score: Float,
        val hasSignificantAcne: Boolean,
        val confidence: Float,
        val skinCoverage: Float
    ) : AcneResult()

    // ── Quality failure enum ─────────────────────────────────────────────────

    enum class Quality(val guidance: String) {
        TOO_BLURRY("Hold still — image is too blurry"),
        TOO_DARK("Move to better lighting"),
        TOO_BRIGHT("Avoid direct light — image is overexposed"),
        TOO_LOW_RESOLUTION("Move closer to the camera")
    }
}
