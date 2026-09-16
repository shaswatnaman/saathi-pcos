package com.leadmilers.saathi.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class LocalAiManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: LocalAiManager? = null

        const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
        const val MODEL_SIZE_BYTES = 1_400_000_000L  // ~1.4 GB
        // Publicly hosted MediaPipe model — no auth required
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/llm_inference/" +
            "gemma-2-2b-it-gpu-int4/float16/1/gemma-2-2b-it-gpu-int4.bin"

        fun getInstance(context: Context): LocalAiManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalAiManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    // Use external app files dir so the model can be pushed via ADB without root
    val modelFile: File get() = File(context.getExternalFilesDir(null), MODEL_FILENAME)

    // Model is ready when the file exists and is at least 500 MB (partial downloads won't count)
    val isModelReady: Boolean
        get() = modelFile.exists() && modelFile.length() > 500_000_000L

    private var llm: LlmInference? = null

    suspend fun ensureLoaded() = withContext(Dispatchers.Default) {
        if (llm != null) return@withContext
        if (!isModelReady) throw IllegalStateException("Model not downloaded")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    // Streams tokens as they are generated; caller collects on any dispatcher
    fun generateAsFlow(prompt: String): Flow<String> = callbackFlow {
        val inference = checkNotNull(llm) { "Call ensureLoaded() before generating" }
        inference.generateResponseAsync(prompt) { partial, done ->
            partial?.let { trySend(it) }
            if (done) close()
        }
        awaitClose()
    }.flowOn(Dispatchers.Default)

    fun release() {
        llm?.close()
        llm = null
    }
}
