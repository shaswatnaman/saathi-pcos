package com.leadmilers.saathi.ml

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

class VoiceAnalyzer(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BASELINE_RMS = "baseline_rms"
        private const val RECORD_DURATION_MS = 10_000L
        private const val SAMPLE_INTERVAL_MS = 100L
        private const val SAMPLE_COUNT = (RECORD_DURATION_MS / SAMPLE_INTERVAL_MS).toInt()
    }

    private var recorder: MediaRecorder? = null
    private val amplitudeSamples = mutableListOf<Float>()

    val hasBaseline: Boolean get() = prefs.contains(KEY_BASELINE_RMS)

    // Called from UI coroutine; returns live amplitude 0-1 via callback
    suspend fun record(
        onAmplitude: (Float) -> Unit
    ): VoiceResult = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.mp4")
        amplitudeSamples.clear()

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }

        recorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        repeat(SAMPLE_COUNT) {
            delay(SAMPLE_INTERVAL_MS)
            val amplitude = recorder?.maxAmplitude?.toFloat() ?: 0f
            amplitudeSamples += amplitude
            // Normalize raw amplitude (0-32767) to 0-1 for live UI feedback
            withContext(Dispatchers.Main) { onAmplitude((amplitude / 32767f).coerceIn(0f, 1f)) }
        }

        recorder?.apply { stop(); release() }
        recorder = null

        val rms = computeRms(amplitudeSamples)
        val result = normalizeAndStore(rms, outputFile.absolutePath)
        result
    }

    fun stopEarly(): Unit {
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        recorder = null
    }

    private fun computeRms(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val meanSquare = samples.map { it * it }.average().toFloat()
        return sqrt(meanSquare)
    }

    private fun normalizeAndStore(rms: Float, path: String): VoiceResult {
        val baseline = prefs.getFloat(KEY_BASELINE_RMS, -1f)
        return if (baseline < 0f) {
            // First recording only — lock in as the personal baseline
            prefs.edit().putFloat(KEY_BASELINE_RMS, rms).apply()
            VoiceResult(energyScore = 1.0f, recordingPath = path, isBaseline = true)
        } else {
            // Score = fraction of baseline energy; louder than baseline clamps to 1.0
            val score = (rms / baseline).coerceIn(0f, 1f)
            VoiceResult(energyScore = score, recordingPath = path, isBaseline = false)
        }
    }

    fun resetBaseline() {
        prefs.edit().remove(KEY_BASELINE_RMS).apply()
    }
}
