package com.leadmilers.saathi.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.media.FaceDetector
import android.util.Log
import kotlin.math.sqrt

/**
 * On-device acne / skin analysis pipeline.
 *
 * Architecture (every gate must pass before the next runs):
 *   Image dimensions  →  Blur / brightness  →  Skin coverage  →
 *   Face detection  →  Coverage floor  →  Confidence floor  →
 *   Acne analysis on skin ROI  →  [AcneResult]
 *
 * INVARIANT: failure at any gate returns a named abstention state.
 *            The "no significant lesions" result is only reachable through
 *            all gates with sufficient confidence. It is never a fallback.
 *
 * Model notes:
 *   No TFLite model is used (AGP 9.4 enforces unique namespace; TFLite 2.14
 *   violates it with no suppressible flag). Inference uses HSV feature
 *   engineering calibrated against PCOS acne characteristics:
 *     • Redness ratio  — inflammatory papules / pustules (H 0–20°, elevated S)
 *     • PIH density    — post-inflammatory hyperpigmentation (low V, moderate S)
 *     • Texture var    — surface irregularity from lesions (std-dev of V channel)
 *   These are evaluated only within skin-tone pixels inside the face region.
 *   This approach is explicitly an exploratory heuristic, not a validated
 *   clinical instrument. A TFLite lesion-detection model (e.g. EfficientDet-Lite)
 *   should replace it once the TFLite namespace issue is resolved in a future
 *   AGP / LiteRT version.
 *
 * Thresholds documented here; none are scattered in call sites.
 */
class AcneClassifier {

    companion object {
        private const val TAG = "AcneClassifier"

        // ── Sampling sizes ───────────────────────────────────────────────────
        private const val QUALITY_SIZE   = 64    // blur / brightness check
        private const val SKIN_SIZE      = 128   // skin coverage + acne features
        private const val FACE_DET_SIZE  = 256   // face detector input

        // ── Gate thresholds ──────────────────────────────────────────────────
        // MIN_DIMENSION: below this → image is too small to contain useful detail
        private const val MIN_DIMENSION      = 120

        // MIN_SHARPNESS: Laplacian variance below this → too blurry to analyze
        // Calibration: typical sharp selfie = 300–2000; moderate blur = 80–300; severe = < 80
        private const val MIN_SHARPNESS      = 80f

        // Brightness (mean V channel, 0–1)
        private const val MIN_BRIGHTNESS     = 0.15f   // very dark / night
        private const val MAX_BRIGHTNESS     = 0.93f   // overexposed

        // Skin-tone coverage gates (fraction of frame pixels in skin-HSV range)
        // < NO_FACE_COVERAGE  → definitely no face (random object / scene)
        // < ANALYSIS_COVERAGE → face present but too small for reliable analysis
        private const val NO_FACE_COVERAGE   = 0.18f
        private const val ANALYSIS_COVERAGE  = 0.33f

        // MIN_CONFIDENCE: combined quality × coverage below this → abstain
        private const val MIN_CONFIDENCE     = 0.38f

        // ACNE_THRESHOLD: score above this → hasSignificantAcne = true
        // Not to be confused with confidence — this is the feature score threshold.
        private const val ACNE_THRESHOLD     = 0.45f

        // Max faces to detect (FaceDetector parameter)
        private const val MAX_FACES          = 4

        // Skin-tone HSV ranges (Fitzpatrick I – VI inclusive)
        // Intentionally broad to act as a face proxy across all skin tones.
        private const val SKIN_H_MAX         = 50f   // warm hues only
        private const val SKIN_S_MIN         = 0.05f
        private const val SKIN_S_MAX         = 0.88f
        private const val SKIN_V_MIN         = 0.09f // allows dark skin tones

        // Acne feature HSV parameters (skin-ROI only)
        private const val RED_H_MAX          = 20f   // redness window
        private const val RED_S_MIN          = 0.25f
        private const val RED_V_MIN          = 0.22f
        private const val PIH_V_MIN          = 0.08f // PIH dark spots
        private const val PIH_V_MAX          = 0.42f
        private const val PIH_S_MIN          = 0.10f
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Analyze [bitmap] for acne-like lesions.
     *
     * Returns an [AcneResult]. Only [AcneResult.ValidAnalysis] produces an
     * acne assessment. All other states are abstentions — never "clear skin".
     */
    fun analyze(bitmap: Bitmap): AcneResult {
        return try {
            pipeline(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected inference error", e)
            AcneResult.InferenceError
        }
    }

    /** No resources to release. */
    fun close() = Unit

    // ── Pipeline ─────────────────────────────────────────────────────────────

    private fun pipeline(bitmap: Bitmap): AcneResult {

        // Gate 1 — Image dimensions
        if (bitmap.width < MIN_DIMENSION || bitmap.height < MIN_DIMENSION)
            return AcneResult.PoorImageQuality(AcneResult.Quality.TOO_LOW_RESOLUTION)

        // Gate 2 — Blur
        val sharpness = computeSharpness(bitmap)
        if (sharpness < MIN_SHARPNESS)
            return AcneResult.PoorImageQuality(AcneResult.Quality.TOO_BLURRY)

        // Gate 3 — Brightness
        val brightness = computeBrightness(bitmap)
        when {
            brightness < MIN_BRIGHTNESS -> return AcneResult.PoorImageQuality(AcneResult.Quality.TOO_DARK)
            brightness > MAX_BRIGHTNESS -> return AcneResult.PoorImageQuality(AcneResult.Quality.TOO_BRIGHT)
        }

        // Gate 4 — Skin coverage (face proxy, works across skin tones)
        val skinCoverage = computeSkinCoverage(bitmap)
        if (skinCoverage < NO_FACE_COVERAGE)
            return AcneResult.NoFace

        // Gate 5 — Face detector (AOSP FaceDetector, no external dependency)
        val faceCount = detectFaces(bitmap)
        if (faceCount > 1)
            return AcneResult.MultipleFaces(faceCount)
        // faceCount == 0 with low skin coverage → no face; with high coverage → continue
        if (faceCount == 0 && skinCoverage < ANALYSIS_COVERAGE)
            return AcneResult.NoFace

        // Gate 6 — Coverage floor for reliable analysis
        if (skinCoverage < ANALYSIS_COVERAGE)
            return AcneResult.InsufficientCoverage(skinCoverage)

        // Gate 7 — Combined analysis confidence
        val qualityScore = computeQualityScore(sharpness, brightness)
        val confidence = skinCoverage * qualityScore
        if (confidence < MIN_CONFIDENCE)
            return AcneResult.LowConfidence

        // ── Acne analysis on skin pixels only ───────────────────────────────
        val metrics = analyzeAcneInSkinROI(bitmap)
        if (metrics.skinPixels == 0)
            return AcneResult.InsufficientCoverage(0f)

        val raw = metrics.rednessRatio * 0.50f +
                  metrics.pihRatio    * 0.30f +
                  metrics.textureVar  * 0.80f
        val acneScore = raw.coerceIn(0f, 1f)

        return AcneResult.ValidAnalysis(
            score              = acneScore,
            hasSignificantAcne = acneScore > ACNE_THRESHOLD,
            confidence         = confidence,
            skinCoverage       = skinCoverage
        )
    }

    // ── Gate helpers ─────────────────────────────────────────────────────────

    /**
     * Laplacian variance — measures image sharpness.
     * High variance = sharp edges present. Low = blurry.
     */
    private fun computeSharpness(bitmap: Bitmap): Float {
        val side = QUALITY_SIZE
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()

        // Approximate grayscale (green channel weighted more, avoids div)
        val gray = IntArray(side * side) { i ->
            val p = pixels[i]
            (Color.red(p) + 2 * Color.green(p) + Color.blue(p)) / 4
        }

        var lapSum = 0L
        var lapSumSq = 0L
        var count = 0
        for (y in 1 until side - 1) {
            for (x in 1 until side - 1) {
                val lap = 4 * gray[y * side + x] -
                    gray[(y - 1) * side + x] -
                    gray[(y + 1) * side + x] -
                    gray[y * side + (x - 1)] -
                    gray[y * side + (x + 1)]
                lapSum   += lap
                lapSumSq += lap.toLong() * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = lapSum.toDouble() / count
        return (lapSumSq.toDouble() / count - mean * mean).toFloat().coerceAtLeast(0f)
    }

    /**
     * Mean brightness of the image (mean V channel in HSV, 0–1).
     */
    private fun computeBrightness(bitmap: Bitmap): Float {
        val side = QUALITY_SIZE / 2
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()

        val hsv = FloatArray(3)
        var totalV = 0f
        for (p in pixels) {
            Color.colorToHSV(p, hsv)
            totalV += hsv[2]
        }
        return totalV / pixels.size
    }

    /**
     * Fraction of pixels in the broad skin-tone HSV range.
     * Used as a face proxy — sufficient coverage implies a face is present.
     * Covers Fitzpatrick I–VI: H 0–50°, moderate S, V above noise floor.
     */
    private fun computeSkinCoverage(bitmap: Bitmap): Float {
        val side = SKIN_SIZE
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()

        var skinCount = 0
        val hsv = FloatArray(3)
        for (p in pixels) {
            Color.colorToHSV(p, hsv)
            if (isSkinTone(hsv)) skinCount++
        }
        return skinCount.toFloat() / pixels.size
    }

    /**
     * AOSP face detector — no external dependency, works offline.
     * Returns face count, or -1 if the detector threw (treat as unknown).
     * Deprecated since API 28 but still functional on all Android versions.
     */
    @Suppress("DEPRECATION")
    private fun detectFaces(bitmap: Bitmap): Int {
        return try {
            // FaceDetector requires even width and RGB_565 config
            val w = if (FACE_DET_SIZE % 2 == 0) FACE_DET_SIZE else FACE_DET_SIZE - 1
            val scaled = Bitmap.createScaledBitmap(bitmap, w, FACE_DET_SIZE, false)
            val rgb565 = scaled.copy(Bitmap.Config.RGB_565, false)
            if (scaled !== bitmap) scaled.recycle()

            val detector = FaceDetector(rgb565.width, rgb565.height, MAX_FACES)
            @Suppress("UNCHECKED_CAST")
            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES) as Array<FaceDetector.Face>
            val count = detector.findFaces(rgb565, faces)
            rgb565.recycle()
            count
        } catch (e: Exception) {
            Log.w(TAG, "FaceDetector unavailable: ${e.message}")
            -1  // unknown — don't gate on this alone
        }
    }

    /**
     * Combines sharpness and brightness into a [0, 1] quality score.
     * Used as a multiplier in the confidence calculation.
     */
    private fun computeQualityScore(sharpness: Float, brightness: Float): Float {
        // Sharpness: normalize 80–500 → 0–1 (saturates at 500)
        val sharpNorm = ((sharpness - MIN_SHARPNESS) / (500f - MIN_SHARPNESS)).coerceIn(0f, 1f)
        // Brightness: penalise extremes; ideal is ~0.40–0.70
        val midpoint = 0.55f
        val brightScore = (1f - (kotlin.math.abs(brightness - midpoint) / midpoint)).coerceIn(0f, 1f)
        return (sharpNorm * 0.6f + brightScore * 0.4f)
    }

    // ── Acne feature extraction ───────────────────────────────────────────────

    private data class AcneMetrics(
        val rednessRatio: Float,
        val pihRatio: Float,
        val textureVar: Float,
        val skinPixels: Int
    )

    /**
     * Extracts acne-related features from skin-tone pixels only.
     *
     * Features:
     *   rednessRatio — fraction of skin pixels showing inflammatory redness (H 0–20°)
     *   pihRatio     — fraction showing post-inflammatory dark spots (low V, moderate S)
     *   textureVar   — std-dev of V channel within skin region (surface irregularity)
     *
     * Non-skin pixels (background, hair, eyes, lips) are excluded before analysis.
     * This prevents background color from influencing the acne score.
     */
    private fun analyzeAcneInSkinROI(bitmap: Bitmap): AcneMetrics {
        val side = SKIN_SIZE
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()

        var skinCount = 0
        var redCount  = 0
        var pihCount  = 0
        val vValues   = FloatArray(pixels.size)  // pre-alloc, fill up to skinCount
        var vIdx      = 0
        val hsv       = FloatArray(3)

        for (p in pixels) {
            Color.colorToHSV(p, hsv)
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]

            if (!isSkinTone(hsv)) continue
            skinCount++
            vValues[vIdx++] = v

            // Inflammatory redness: warm hue, elevated saturation
            if (h <= RED_H_MAX && s >= RED_S_MIN && v >= RED_V_MIN) redCount++

            // PIH / dark spots: darker than normal skin, some saturation
            if (v in PIH_V_MIN..PIH_V_MAX && s >= PIH_S_MIN) pihCount++
        }

        if (skinCount == 0) return AcneMetrics(0f, 0f, 0f, 0)

        val rednessRatio = redCount.toFloat() / skinCount
        val pihRatio     = pihCount.toFloat() / skinCount

        // Texture variance (std-dev of V within skin region)
        val effectiveV = vValues.copyOf(vIdx)
        val meanV      = effectiveV.average().toFloat()
        val textureVar = if (vIdx > 1) {
            sqrt(effectiveV.fold(0f) { acc, v -> acc + (v - meanV) * (v - meanV) } / vIdx)
        } else 0f

        return AcneMetrics(rednessRatio, pihRatio, textureVar, skinCount)
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Skin-tone HSV predicate — broad enough to cover Fitzpatrick I–VI.
     * H: 0–50° (warm hues), S: 0.05–0.88, V: 0.09–1.0.
     */
    private fun isSkinTone(hsv: FloatArray): Boolean {
        val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
        return h <= SKIN_H_MAX && s in SKIN_S_MIN..SKIN_S_MAX && v >= SKIN_V_MIN
    }
}
