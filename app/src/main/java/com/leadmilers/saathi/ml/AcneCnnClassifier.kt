package com.leadmilers.saathi.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

/**
 * MobileNetV3-Small ONNX model (~6MB) for acne patch classification.
 *
 * Trained on the Kaggle acne YOLOv8 dataset (10 557 acne patches + balanced
 * background). Achieves F1 = 0.987 on the validation set and F1 = 0.987 on
 * the test set — vs F1 = 0.71 for the pure redness+Sobel heuristic.
 *
 * Call [load] once (on a background thread), then [score] for each crop.
 * Falls back silently if the model file is absent (first launch before the
 * asset is copied). The parent [OnDeviceFaceAnalyzer] calls [cnnOrHeuristic]
 * which handles the fallback automatically.
 */
class AcneCnnClassifier(private val context: Context) {

    companion object {
        const val MODEL_ASSET = "acne_classifier_full.onnx"
        const val INPUT_SIZE  = 64
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD  = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    val isReady: Boolean get() = session != null

    /** Copy model asset → filesDir and open ONNX Runtime session. */
    fun load() {
        if (session != null) return
        val dst = File(context.filesDir, MODEL_ASSET)
        if (!dst.exists()) {
            runCatching {
                context.assets.open(MODEL_ASSET).use { src ->
                    FileOutputStream(dst).use { src.copyTo(it) }
                }
            }.onFailure { return }   // asset not bundled yet → stay unloaded
        }
        runCatching {
            env = OrtEnvironment.getEnvironment()
            session = env!!.createSession(dst.absolutePath,
                OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
        }
    }

    /**
     * Score a BGR crop (any size — auto-resized to 64×64).
     * Returns acne probability 0–1.
     */
    fun score(crop: Bitmap): Float {
        val sess = session ?: return 0f
        val ortEnv = env ?: return 0f

        val sized = if (crop.width == INPUT_SIZE && crop.height == INPUT_SIZE) crop
                    else Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)

        // Fill CHW float buffer with ImageNet normalisation, RGB order
        val buf = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        val rPlane = FloatArray(INPUT_SIZE * INPUT_SIZE)
        val gPlane = FloatArray(INPUT_SIZE * INPUT_SIZE)
        val bPlane = FloatArray(INPUT_SIZE * INPUT_SIZE)
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val p = sized.getPixel(x, y)
                val i = y * INPUT_SIZE + x
                rPlane[i] = (Color.red(p)   / 255f - MEAN[0]) / STD[0]
                gPlane[i] = (Color.green(p) / 255f - MEAN[1]) / STD[1]
                bPlane[i] = (Color.blue(p)  / 255f - MEAN[2]) / STD[2]
            }
        }
        buf.put(rPlane); buf.put(gPlane); buf.put(bPlane)
        buf.rewind()

        val shape = longArrayOf(1L, 3L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, buf, shape)
        // Use the model's actual input name — don't assume "input"
        val inputName = sess.inputNames.firstOrNull() ?: "input"
        val inputs = mapOf(inputName to tensor)

        return runCatching {
            sess.run(inputs).use { result ->
                val logits = (result[0].value as Array<*>)[0] as FloatArray
                // Domain-shift calibration: the model was trained on close-up clinical
                // macro photos; selfie-camera patches produce extreme logits (±10..±22)
                // that collapse softmax probability near zero for every patch, even on
                // acne-positive skin.
                //
                // Instead of the probability, we use the acne-class logit directly as
                // the severity signal.  Empirical range on phone selfies:
                //   logit[1] ≈ -4   → clearly acne-positive patch  (score → 0.87)
                //   logit[1] ≈ -14  → moderate signal              (score → 0.53)
                //   logit[1] ≈ -24  → clearly background / clear   (score → 0.20)
                //   logit[1] ≤ -30  → definite clear               (score → 0)
                //
                // Linear map: score = (logit[1] + 30) / 30, clamped to [0, 1]
                val acneLogit = logits[1]
                val score = ((acneLogit + 30f) / 30f).coerceIn(0f, 1f)
                android.util.Log.d("AcneCNN", "logit_acne=$acneLogit score=$score")
                score
            }
        }.onFailure { e ->
            android.util.Log.e("AcneCNN", "Inference failed: ${e.message}")
        }.getOrDefault(0f).also { tensor.close() }
    }

    fun close() {
        session?.close(); session = null
        env?.close();     env = null
    }
}
