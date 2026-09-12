package com.leadmilers.saathi.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class AcneClassifier(context: Context) {

    private val interpreter: Interpreter
    private val inputBuffer: ByteBuffer
    private var nnApiDelegate: NnApiDelegate? = null

    companion object {
        private const val TAG = "AcneClassifier"
        private const val MODEL_FILE = "acne_classifier.tflite"
        private const val IMG_SIZE = 224
        private const val THRESHOLD = 0.5f
    }

    init {
        val model = loadModel(context)
        inputBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3)
            .apply { order(ByteOrder.nativeOrder()) }

        interpreter = try {
            val delegate = NnApiDelegate()
            nnApiDelegate = delegate
            Interpreter(model, Interpreter.Options().addDelegate(delegate))
        } catch (e: Exception) {
            Log.w(TAG, "NPU unavailable, falling back to CPU: ${e.message}")
            nnApiDelegate = null
            Interpreter(model, Interpreter.Options().setNumThreads(4))
        }
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    fun classify(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        fillBuffer(scaled)
        if (scaled != bitmap) scaled.recycle()

        val output = Array(1) { FloatArray(1) }
        interpreter.run(inputBuffer, output)
        return output[0][0]
    }

    fun analyze(bitmap: Bitmap): AcneAnalysisResult {
        val score = classify(bitmap)
        val isAndrogenic = score > THRESHOLD
        val confidence = if (isAndrogenic) score else 1f - score
        return AcneAnalysisResult(score, isAndrogenic, confidence)
    }

    private fun fillBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE)
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
    }

    fun close() {
        interpreter.close()
        nnApiDelegate?.close()
    }
}
