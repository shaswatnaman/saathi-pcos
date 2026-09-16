package com.leadmilers.saathi.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.*

class OnDeviceFaceAnalyzer(private val context: Context) {

    /** CNN acne classifier — loaded lazily, falls back to heuristic if unavailable. */
    private val cnn = AcneCnnClassifier(context)

    companion object {
        const val MODEL_FILENAME = "face_landmarker.task"
        const val MODEL_SIZE_BYTES = 7_000_000L
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/" +
            "face_landmarker/float16/1/face_landmarker.task"

        // ── MediaPipe 478-point canonical landmark indices ──────────────────
        // Forehead
        private val IDX_FOREHEAD = listOf(10, 67, 109, 103, 104, 54, 68, 338, 337, 297, 299, 333)
        // T-zone (forehead + nose bridge + nose tip)
        private val IDX_TZONE    = listOf(10, 67, 109, 6, 168, 197, 195, 5, 4, 1, 2)
        // Cheeks
        private val IDX_LCHEEK   = listOf(116, 123, 132, 234, 93, 215)
        private val IDX_RCHEEK   = listOf(345, 352, 361, 454, 323, 435)
        // Under-eye (dark circles / puffiness)
        private val IDX_LEYE_LO  = listOf(145, 153, 154, 155, 163)
        private val IDX_REYE_LO  = listOf(374, 380, 381, 382, 390)
        // Eye upper lids (for aperture)
        private val IDX_LEYE_UP  = listOf(159, 160, 161, 246)
        private val IDX_REYE_UP  = listOf(386, 387, 388, 466)
        // Eyebrows — upper and lower rows
        private val IDX_LEBROW_U = listOf(46, 53, 52, 65, 55, 70)
        private val IDX_LEBROW_L = listOf(105, 66, 107, 63, 68)
        private val IDX_REBROW_U = listOf(276, 283, 282, 295, 285, 300)
        private val IDX_REBROW_L = listOf(334, 296, 336, 293, 298)
        // Jaw / chin (skin darkening zone)
        private val IDX_JAW      = listOf(152, 148, 176, 149, 150, 136, 172, 58, 132)
        // Lips
        private val IDX_LIP_UP   = listOf(0, 267, 269, 270, 37, 39, 40, 185)
        private val IDX_LIP_LO   = listOf(17, 84, 181, 91, 314, 405, 321, 375)
        // Symmetry reference pairs [left, right]
        private val SYMMETRY_PAIRS = listOf(
            33 to 263,   // outer eye corners
            133 to 362,  // inner eye corners
            234 to 454,  // cheek extremes
            172 to 397,  // jaw extremes
            55 to 285,   // inner eyebrow
            46 to 276    // outer eyebrow
        )
        // Hairline (top of face mesh)
        private val IDX_HAIRLINE = listOf(10, 338, 297, 332, 284, 251, 389, 356)
        // Bridge of nose
        private val IDX_NOSE_BRIDGE = listOf(6, 197, 195, 5, 4)
    }

    val modelFile: File get() = File(context.filesDir, MODEL_FILENAME)
    val isModelReady: Boolean get() = modelFile.exists() && modelFile.length() > 1_000_000L

    private var landmarker: FaceLandmarker? = null

    suspend fun downloadModelIfNeeded(
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        if (isModelReady) return@withContext
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val response = client.newCall(Request.Builder().url(MODEL_URL).build()).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val body = response.body ?: throw Exception("Empty response")
        val total = body.contentLength().coerceAtLeast(MODEL_SIZE_BYTES)
        val tmp = File(context.filesDir, "$MODEL_FILENAME.tmp")
        body.byteStream().use { input ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(32 * 1024)
                var written = 0L; var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    if (!isActive) { tmp.delete(); throw Exception("Cancelled") }
                    out.write(buf, 0, read); written += read
                    onProgress(written, total)
                }
            }
        }
        tmp.renameTo(modelFile)
    }

    suspend fun ensureLoaded() = withContext(Dispatchers.Default) {
        cnn.load()
        if (landmarker != null) return@withContext
        val opts = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.4f)
            .setMinFacePresenceConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .build()
        landmarker = FaceLandmarker.createFromOptions(context, opts)
    }

    // ── Main entry point ─────────────────────────────────────────────────────

    suspend fun analyze(bitmap: Bitmap): AcneResult = withContext(Dispatchers.Default) {
        val lm = landmarker ?: return@withContext AcneResult.InferenceError

        val qualityCheck = checkImageQuality(bitmap)
        if (qualityCheck != null) return@withContext qualityCheck

        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = runCatching { lm.detect(mpImage) }.getOrNull()
            ?: return@withContext AcneResult.InferenceError

        if (result.faceLandmarks().isEmpty()) return@withContext AcneResult.NoFace
        if (result.faceLandmarks().size > 1)
            return@withContext AcneResult.MultipleFaces(result.faceLandmarks().size)

        val lms = result.faceLandmarks()[0]
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Face bounding box from landmarks
        val xs = lms.map { it.x() * w }
        val ys = lms.map { it.y() * h }
        val left   = (xs.minOrNull() ?: 0f).toInt().coerceAtLeast(0)
        val top    = (ys.minOrNull() ?: 0f).toInt().coerceAtLeast(0)
        val right  = (xs.maxOrNull() ?: w).toInt().coerceAtMost(bitmap.width - 1)
        val bottom = (ys.maxOrNull() ?: h).toInt().coerceAtMost(bitmap.height - 1)
        val faceW  = right - left
        val faceH  = bottom - top

        if (faceW < 60 || faceH < 60) return@withContext AcneResult.InsufficientCoverage(0.05f)
        val coverage = (faceW.toFloat() * faceH) / (bitmap.width.toFloat() * bitmap.height)
        if (coverage < 0.04f) return@withContext AcneResult.InsufficientCoverage(coverage)

        // ── Run all feature analyses ─────────────────────────────────────────
        val faceBmp   = Bitmap.createBitmap(bitmap, left, top, faceW, faceH)
        val acneScore = cnnOrHeuristic(faceBmp, lms, bitmap, faceW)
        val regions   = detectAcneRegions(lms, bitmap, faceW)

        val cheekL    = meanL(lms, IDX_LCHEEK + IDX_RCHEEK, bitmap, patchPx = 12)

        // Hirsutism — chin + jaw dark pixels on skin
        val chinTop   = top + (faceH * 0.75f).toInt()
        val chinH     = bottom - chinTop
        val hirsutism = if (chinH > 10)
            computeHirsutism(Bitmap.createBitmap(bitmap, left, chinTop, faceW, chinH)) else 0f

        // Oiliness — T-zone brightness vs cheeks
        val tzoneL  = meanL(lms, IDX_TZONE, bitmap, patchPx = 14)
        val oiliness = ((tzoneL - cheekL + 20f) / 60f).coerceIn(0f, 1f)

        // Skin darkening — jaw/neck vs cheeks (acanthosis nigricans: jaw darker)
        val jawL      = meanL(lms, IDX_JAW, bitmap, patchPx = 12)
        val skinDark  = ((cheekL - jawL) / 50f).coerceIn(0f, 1f)

        // Dark circles — under-eye L vs cheek L
        val underEyeL = meanL(lms, IDX_LEYE_LO + IDX_REYE_LO, bitmap, patchPx = 8)
        val darkCirc  = ((cheekL - underEyeL) / 55f).coerceIn(0f, 1f)

        // Eye puffiness — lower vs upper eyelid landmark Y distance
        val puff = eyePuffiness(lms, bitmap.height)

        // Skin uniformity — low variance = uniform (good); high variance = uneven
        val uniformity = skinUniformity(lms, bitmap)

        // Eyebrow thinning — vertical span of eyebrow landmarks
        val browThin = eyebrowThinning(lms, bitmap.height, faceH)

        // Facial symmetry — how closely mirrored left/right landmarks are
        val symmetry = facialSymmetry(lms, w, h)

        // Lip colour — compare lip L vs cheek baseline (darkened lips = hormonal)
        val lipL     = meanL(lms, IDX_LIP_UP + IDX_LIP_LO, bitmap, patchPx = 6)
        val lipDark  = ((cheekL - lipL) / 60f).coerceIn(0f, 1f)

        val confidence = (coverage * 3f).coerceIn(0.5f, 1.0f)

        val features = mapOf(
            "oiliness"        to oiliness,
            "skin_darkening"  to skinDark,
            "dark_circles"    to darkCirc,
            "eye_puffiness"   to puff,
            "skin_uniformity" to uniformity,
            "eyebrow_thinning" to browThin,
            "symmetry"        to symmetry,
            "lip_darkening"   to lipDark,
        )

        AcneResult.ValidAnalysis(
            score              = acneScore,
            hasSignificantAcne = acneScore > (if (cnn.isReady) 0.40f else 0.35f),
            confidence         = confidence,
            skinCoverage       = coverage,
            hirsutismScore     = hirsutism,
            acneRegions        = regions,
            observations       = buildObservation(acneScore, hirsutism, features),
            guidance           = buildGuidance(acneScore, features),
            features           = features,
        )
    }

    // ── Image quality pre-check ───────────────────────────────────────────────

    private fun checkImageQuality(bitmap: Bitmap): AcneResult? {
        val step = ((bitmap.width * bitmap.height) / 200).coerceAtLeast(1)
        var lum = 0.0; var count = 0
        for (i in 0 until bitmap.width * bitmap.height step step) {
            val x = i % bitmap.width; val y = i / bitmap.width
            val p = bitmap.getPixel(x, y)
            lum += Color.red(p) * 0.299 + Color.green(p) * 0.587 + Color.blue(p) * 0.114
            count++
        }
        val avg = lum / count
        return when {
            avg < 30  -> AcneResult.PoorImageQuality(AcneResult.Quality.TOO_DARK)
            avg > 235 -> AcneResult.PoorImageQuality(AcneResult.Quality.TOO_BRIGHT)
            else      -> null
        }
    }

    // ── Colour helpers ────────────────────────────────────────────────────────

    private fun isSkin(r: Int, g: Int, b: Int): Boolean =
        r > 95 && g > 40 && b > 20 && r > g && r > b &&
        (r - g) > 15 && (r - b) in 10..135

    /** Perceived luminance (0–255) */
    private fun lumOf(r: Int, g: Int, b: Int): Float = r * 0.299f + g * 0.587f + b * 0.114f

    /**
     * Sample a square patch around each landmark and return mean L (luminance 0–255).
     * Only skin-tone pixels are included.
     */
    private fun meanL(
        lms: List<NormalizedLandmark>,
        indices: List<Int>,
        bitmap: Bitmap,
        patchPx: Int,
    ): Float {
        var sum = 0.0; var count = 0
        for (idx in indices) {
            if (idx >= lms.size) continue
            val cx = (lms[idx].x() * bitmap.width).toInt()
            val cy = (lms[idx].y() * bitmap.height).toInt()
            for (dy in -patchPx..patchPx) for (dx in -patchPx..patchPx) {
                val px = (cx + dx).coerceIn(0, bitmap.width - 1)
                val py = (cy + dy).coerceIn(0, bitmap.height - 1)
                val p = bitmap.getPixel(px, py)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                if (isSkin(r, g, b)) { sum += lumOf(r, g, b); count++ }
            }
        }
        return if (count > 0) (sum / count).toFloat() else 150f
    }

    // ── CNN + heuristic composite ─────────────────────────────────────────────

    /**
     * Score acne severity 0–1.
     *
     * When the TFLite CNN model is loaded, sample 5 landmark-anchored patches
     * (forehead, cheeks ×2, chin, nose) and average their CNN probabilities.
     * This gives F1 ≈ 0.85 on the validation set vs heuristic F1 ≈ 0.71.
     *
     * Falls back to the redness+Sobel heuristic when the model file is absent
     * (e.g. first launch before assets are copied).
     */
    private fun cnnOrHeuristic(
        faceBmp: Bitmap,
        lms: List<NormalizedLandmark>,
        bitmap: Bitmap,
        faceW: Int,
    ): Float {
        if (!cnn.isReady) return computeAcneScore(faceBmp)

        // Match training patch size (~44px average bounding box from the dataset)
        val patchPx = (faceW * 0.09f).toInt().coerceIn(40, 80)
        val half    = patchPx / 2
        val W = bitmap.width;  val H = bitmap.height

        // 24 anatomically fixed landmark indices that cover all acne-prone zones.
        // Each index maps to a stable face point — same landmark = same skin spot
        // every scan regardless of head position, so results are deterministic.
        val acneAnchors = intArrayOf(
            // Forehead (5 points)
            10, 67, 109, 338, 299,
            // Left cheek (4 points)
            116, 123, 50, 187,
            // Right cheek (4 points)
            345, 352, 280, 411,
            // Nose (3 points)
            4, 5, 197,
            // Chin / jaw (4 points)
            152, 175, 148, 377,
            // Around mouth (4 points)
            14, 17, 92, 322,
        )

        val scores = ArrayList<Float>(acneAnchors.size)
        for (idx in acneAnchors) {
            if (idx >= lms.size) continue
            val cx = (lms[idx].x() * W).toInt()
            val cy = (lms[idx].y() * H).toInt()
            val x1 = (cx - half).coerceAtLeast(0)
            val y1 = (cy - half).coerceAtLeast(0)
            val x2 = (x1 + patchPx).coerceAtMost(W)
            val y2 = (y1 + patchPx).coerceAtMost(H)
            if (x2 <= x1 || y2 <= y1) continue
            val crop = Bitmap.createBitmap(bitmap, x1, y1, x2 - x1, y2 - y1)
            scores.add(cnn.score(crop))
        }

        if (scores.isEmpty()) return computeAcneScore(faceBmp)

        // 75th-percentile: if ≥25% of face regions score high, acne is flagged.
        // This is insensitive to a few patches shifting near landmark edges.
        scores.sort()
        val p75 = scores[((scores.size - 1) * 0.75f).toInt()]
        return p75.coerceIn(0f, 1f)
    }

    // ── Heuristic acne score (fallback) ───────────────────────────────────────

    private fun computeAcneScore(bmp: Bitmap): Float {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val gray = FloatArray(w * h)
        var rednessSum = 0.0; var skinCount = 0
        for (i in px.indices) {
            val r = Color.red(px[i]); val g = Color.green(px[i]); val b = Color.blue(px[i])
            gray[i] = lumOf(r, g, b)
            if (isSkin(r, g, b)) {
                rednessSum += (r - g).coerceAtLeast(0) / 255.0; skinCount++
            }
        }
        var gradSum = 0.0; var gradCount = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val dx = gray[y * w + x + 1] - gray[y * w + x - 1]
            val dy = gray[(y + 1) * w + x] - gray[(y - 1) * w + x]
            gradSum += sqrt((dx * dx + dy * dy).toDouble()); gradCount++
        }
        val redness = if (skinCount > 0) (rednessSum / skinCount).toFloat() else 0f
        val texture = if (gradCount > 0) (gradSum / gradCount / 80.0).toFloat().coerceIn(0f, 1f) else 0f
        return (redness * 0.55f + texture * 0.45f).coerceIn(0f, 1f)
    }

    // ── Hirsutism ─────────────────────────────────────────────────────────────

    private fun computeHirsutism(bmp: Bitmap): Float {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        var dark = 0; var skin = 0
        for (p in px) {
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            if (isSkin(r, g, b)) { skin++; if (lumOf(r, g, b) < 90) dark++ }
        }
        return if (skin > 50) (dark.toFloat() / skin).coerceIn(0f, 1f) else 0f
    }

    // ── Eye puffiness ─────────────────────────────────────────────────────────
    // Compares upper/lower eyelid Y distance vs expected (wider = puffier lower lid)

    private fun eyePuffiness(lms: List<NormalizedLandmark>, imgH: Int): Float {
        fun aperture(upIdx: List<Int>, loIdx: List<Int>): Float {
            val upY = upIdx.filter { it < lms.size }.map { lms[it].y() * imgH }.average().toFloat()
            val loY = loIdx.filter { it < lms.size }.map { lms[it].y() * imgH }.average().toFloat()
            return (loY - upY).coerceAtLeast(0f)
        }
        val leftAp  = aperture(IDX_LEYE_UP, IDX_LEYE_LO)
        val rightAp = aperture(IDX_REYE_UP, IDX_REYE_LO)
        val avgAp   = (leftAp + rightAp) / 2f
        // Typical eye aperture ~8-12% of face height; puffiness pushes lower lid down
        // Normalise: < 5% = very puffy, > 12% = open/normal
        return (1f - (avgAp / imgH / 0.07f)).coerceIn(0f, 1f)
    }

    // ── Skin uniformity ───────────────────────────────────────────────────────
    // Sample patches from forehead, cheeks, chin; measure luminance std-dev

    private fun skinUniformity(lms: List<NormalizedLandmark>, bitmap: Bitmap): Float {
        val regions = listOf(IDX_FOREHEAD, IDX_LCHEEK, IDX_RCHEEK, IDX_JAW)
        val means = regions.map { meanL(lms, it, bitmap, patchPx = 10) }
        val overall = means.average().toFloat()
        val variance = means.map { (it - overall).pow(2) }.average().toFloat()
        val stdDev = sqrt(variance.toDouble()).toFloat()
        // stdDev < 8 = very uniform; > 30 = uneven
        return (1f - (stdDev / 30f)).coerceIn(0f, 1f)
    }

    // ── Eyebrow thinning ──────────────────────────────────────────────────────

    private fun eyebrowThinning(
        lms: List<NormalizedLandmark>,
        imgH: Int,
        faceH: Int,
    ): Float {
        fun browHeight(upper: List<Int>, lower: List<Int>): Float {
            val upY = upper.filter { it < lms.size }.map { lms[it].y() * imgH }.average()
            val loY = lower.filter { it < lms.size }.map { lms[it].y() * imgH }.average()
            return (loY - upY).toFloat().coerceAtLeast(0f)
        }
        val lBrow = browHeight(IDX_LEBROW_U, IDX_LEBROW_L)
        val rBrow = browHeight(IDX_REBROW_U, IDX_REBROW_L)
        val avgHeight = (lBrow + rBrow) / 2f
        // Typical eyebrow height is ~2-5% of face height
        // Higher thinning score = thinner brows
        return (1f - (avgHeight / faceH / 0.04f)).coerceIn(0f, 1f)
    }

    // ── Facial symmetry ───────────────────────────────────────────────────────

    private fun facialSymmetry(lms: List<NormalizedLandmark>, w: Float, h: Float): Float {
        // Find vertical midline (average x of nose landmarks)
        val noseX = listOf(1, 4, 5, 6).filter { it < lms.size }
            .map { lms[it].x() * w }.average().toFloat()

        var totalDev = 0f; var pairs = 0
        for ((li, ri) in SYMMETRY_PAIRS) {
            if (li >= lms.size || ri >= lms.size) continue
            val lx = lms[li].x() * w; val ly = lms[li].y() * h
            val rx = lms[ri].x() * w; val ry = lms[ri].y() * h
            // Mirror left landmark across midline
            val lxMirrored = 2 * noseX - lx
            val dist = sqrt(((lxMirrored - rx).pow(2) + (ly - ry).pow(2)).toDouble()).toFloat()
            totalDev += dist / w   // normalise by image width
            pairs++
        }
        val avgDev = if (pairs > 0) totalDev / pairs else 0f
        return (1f - (avgDev / 0.06f)).coerceIn(0f, 1f)
    }

    // ── Per-region acne detection ─────────────────────────────────────────────

    private fun detectAcneRegions(
        lms: List<NormalizedLandmark>,
        bitmap: Bitmap,
        faceW: Int,
    ): List<String> {
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        val regions = mutableListOf<String>()
        val threshold = 0.13f
        val radius = (faceW * 0.07f).toInt().coerceAtLeast(6)

        fun redness(indices: List<Int>): Float {
            var sum = 0.0; var count = 0
            for (idx in indices) {
                if (idx >= lms.size) continue
                val cx = (lms[idx].x() * w).toInt()
                val cy = (lms[idx].y() * h).toInt()
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val px = (cx + dx).coerceIn(0, bitmap.width - 1)
                    val py = (cy + dy).coerceIn(0, bitmap.height - 1)
                    val p = bitmap.getPixel(px, py)
                    val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                    if (isSkin(r, g, b)) { sum += (r - g).coerceAtLeast(0) / 255.0; count++ }
                }
            }
            return if (count > 0) (sum / count).toFloat() else 0f
        }

        if (redness(IDX_FOREHEAD)           > threshold) regions.add("forehead")
        if (redness(IDX_LCHEEK + IDX_RCHEEK) > threshold) regions.add("cheeks")
        if (redness(listOf(152, 175))        > threshold) regions.add("chin")
        if (redness(IDX_JAW)                 > threshold) regions.add("jawline")
        if (redness(IDX_NOSE_BRIDGE)         > threshold) regions.add("nose")

        return regions
    }

    // ── Text generation ───────────────────────────────────────────────────────

    private fun buildObservation(
        acne: Float,
        hirsutism: Float,
        f: Map<String, Float>,
    ): String {
        val parts = mutableListOf<String>()
        parts += when {
            acne < 0.35f -> "No visible inflammatory lesions detected"
            acne < 0.55f -> "Mild visible skin texture irregularities"
            acne < 0.75f -> "Moderate visible redness and texture changes"
            else         -> "Significant visible redness and skin texture changes"
        }
        if (hirsutism > 0.15f) parts += "visible terminal hair in the chin/jaw area"
        if ((f["dark_circles"] ?: 0f) > 0.35f) parts += "visible darkening under the eyes"
        if ((f["skin_darkening"] ?: 0f) > 0.35f) parts += "visible skin darkening in the jaw/neck area"
        if ((f["oiliness"] ?: 0f) > 0.55f) parts += "elevated T-zone brightness indicating possible oiliness"
        return parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
    }

    private fun buildGuidance(acne: Float, f: Map<String, Float>): String = when {
        acne > 0.65f -> "Consider discussing this pattern with a dermatologist alongside your cycle log."
        (f["skin_darkening"] ?: 0f) > 0.4f ->
            "Jaw/neck skin darkening can be related to hormonal changes — worth noting with your doctor."
        (f["oiliness"] ?: 0f) > 0.55f ->
            "An oil-control cleanser for the T-zone and non-comedogenic moisturiser may help."
        (f["dark_circles"] ?: 0f) > 0.4f ->
            "Dark circles often correlate with sleep quality — check your sleep log for this period."
        else -> "Keep logging consistently — patterns across your cycle give the most useful picture."
    }

    fun release() { landmarker?.close(); landmarker = null; cnn.close() }
}
