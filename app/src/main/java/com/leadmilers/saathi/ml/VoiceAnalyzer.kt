package com.leadmilers.saathi.ml

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Records 10 s of audio using AudioRecord (raw PCM) and extracts:
 *   - F0 mean / SD / 5th-percentile / 95th-percentile via the YIN pitch detector
 *   - Normalized RMS energy (kept as exploratory label in UI)
 *
 * Sample rate is 16 kHz — adequate for the 80-500 Hz speech F0 range and
 * keeps the YIN τ-max window small (200 samples) for on-device performance.
 *
 * Scientific basis: F0SD and F0MIN shift measurably across menstrual-cycle
 * phases (Ziemer et al., JMIR 2025, PMC11737864). This is NOT a PCOS-risk
 * signal and is not fed into RiskScorer.
 */
class VoiceAnalyzer {

    companion object {
        private const val SAMPLE_RATE    = 16_000           // Hz
        private const val RECORD_MS      = 10_000L          // recording window
        private const val FRAME          = 512              // ~32 ms / frame
        private const val MIN_F0         = 80f              // Hz — lower bound for speech
        private const val MAX_F0         = 500f             // Hz — upper bound for speech
        private const val TAU_MIN        = (SAMPLE_RATE / MAX_F0).toInt()  // 32
        private const val TAU_MAX        = (SAMPLE_RATE / MIN_F0).toInt()  // 200
        private const val YIN_THRESHOLD  = 0.12f
    }

    private var audioRecord: AudioRecord? = null

    // Runs on IO dispatcher; calls onAmplitude(0-1) on Main for live waveform.
    suspend fun record(onAmplitude: suspend (Float) -> Unit): VoiceResult =
        withContext(Dispatchers.IO) {

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, FRAME * 4)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        audioRecord!!.startRecording()

        val frame        = ShortArray(FRAME)
        val f0Values     = mutableListOf<Float>()
        val rmsValues    = mutableListOf<Float>()
        val deadline     = System.currentTimeMillis() + RECORD_MS

        while (System.currentTimeMillis() < deadline) {
            val read = audioRecord!!.read(frame, 0, FRAME)
            if (read <= 0) continue

            val rms = computeRms(frame, read)
            rmsValues += rms
            val normalizedRms = (rms / 16384f).coerceIn(0f, 1f)
            withContext(Dispatchers.Main) { onAmplitude(normalizedRms) }

            val f0 = estimatePitchYin(frame)
            if (f0 != null) f0Values += f0
        }

        audioRecord?.apply { stop(); release() }
        audioRecord = null

        computeResult(f0Values, rmsValues)
    }

    fun stopEarly() {
        try { audioRecord?.apply { stop(); release() } } catch (_: Exception) {}
        audioRecord = null
    }

    // ── YIN pitch estimator ────────────────────────────────────────────────────

    private fun estimatePitchYin(buf: ShortArray): Float? {
        // Need at least 2 * TAU_MAX samples
        if (buf.size < TAU_MAX * 2) return null

        val yin = FloatArray(TAU_MAX + 1)

        // Step 1 + 2: cumulative mean normalized difference function
        yin[0] = 1f
        var runningSum = 0f
        for (tau in 1..TAU_MAX) {
            var diff = 0f
            for (j in 0 until TAU_MAX) {
                val delta = buf[j].toFloat() - buf[j + tau].toFloat()
                diff += delta * delta
            }
            runningSum += diff
            yin[tau] = if (runningSum > 0f) diff * tau / runningSum else 1f
        }

        // Step 3: absolute threshold — first local minimum below YIN_THRESHOLD
        for (tau in TAU_MIN..TAU_MAX) {
            if (yin[tau] < YIN_THRESHOLD) {
                // Step 4: parabolic interpolation
                val s0 = if (tau > 0) yin[tau - 1] else yin[tau]
                val s1 = yin[tau]
                val s2 = if (tau < TAU_MAX) yin[tau + 1] else yin[tau]
                val denom = 2f * (2f * s1 - s2 - s0)
                val betterTau = if (abs(denom) > 1e-6f)
                    tau + (s2 - s0) / denom
                else tau.toFloat()
                val f0 = SAMPLE_RATE / betterTau
                return if (f0 in MIN_F0..MAX_F0) f0 else null
            }
        }
        return null // unvoiced frame
    }

    // ── Statistics helpers ─────────────────────────────────────────────────────

    private fun computeRms(buf: ShortArray, len: Int): Float {
        if (len == 0) return 0f
        var sum = 0.0
        for (i in 0 until len) sum += buf[i].toDouble() * buf[i]
        return sqrt(sum / len).toFloat()
    }

    private fun computeSd(values: List<Float>, mean: Float): Float {
        if (values.size < 2) return 0f
        val variance = values.sumOf { (it - mean).toDouble() * (it - mean) } / values.size
        return sqrt(variance).toFloat()
    }

    private fun computeResult(f0Values: List<Float>, rmsValues: List<Float>): VoiceResult {
        val sorted    = f0Values.sorted()
        val n         = sorted.size
        val f0Mean    = if (n > 0) f0Values.average().toFloat() else 0f
        val f0Sd      = computeSd(f0Values, f0Mean)
        // 5th percentile = index n/20; 95th = index n*19/20
        val f0Min     = if (n >= 5) sorted[n / 20] else sorted.firstOrNull() ?: 0f
        val f0Max     = if (n >= 5) sorted[(n * 19 / 20).coerceAtMost(n - 1)] else sorted.lastOrNull() ?: 0f
        val energy    = if (rmsValues.isEmpty()) 0f
                        else (rmsValues.average().toFloat() / 16384f).coerceIn(0f, 1f)
        return VoiceResult(
            f0Mean       = f0Mean,
            f0Sd         = f0Sd,
            f0Min        = f0Min,
            f0Max        = f0Max,
            voicedFrames = n,
            energyScore  = energy
        )
    }
}
