package com.leadmilers.saathi.ml

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

class VoiceAnalyzer(private val context: Context) {

    companion object {
        private const val RECORD_DURATION_MS = 10_000L
        private const val SAMPLE_INTERVAL_MS = 100L
        private const val SAMPLE_COUNT = (RECORD_DURATION_MS / SAMPLE_INTERVAL_MS).toInt()

        // maxAmplitude range 0-32767. A clear speaking voice at ~30 cm
        // registers ~10 000-14 000 RMS. Values above this clamp to 1.0.
        private const val REFERENCE_RMS = 12_000f
    }

    private var recorder: MediaRecorder? = null
    private val amplitudeSamples = mutableListOf<Float>()

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
        val score = (rms / REFERENCE_RMS).coerceIn(0f, 1f)
        VoiceResult(energyScore = score, recordingPath = outputFile.absolutePath, isBaseline = false)
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
}
