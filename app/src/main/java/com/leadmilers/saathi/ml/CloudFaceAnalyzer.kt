package com.leadmilers.saathi.ml

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.leadmilers.saathi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class CloudFaceAnalyzer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(bitmap: Bitmap): AcneResult = withContext(Dispatchers.IO) {
        try {
            val base64 = bitmapToBase64(bitmap)
            val raw = callOpenRouter(base64)
            parseResponse(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Cloud analysis failed: ${e.message}", e)
            AcneResult.InferenceError
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun callOpenRouter(base64Image: String): String {
        val prompt = """
You are a dermatology screening assistant. Analyze this face photo and return ONLY a valid JSON object — no markdown fences, no explanation, just the JSON.

Required JSON structure:
{
  "face_detected": <true|false>,
  "face_count": <integer 0-5>,
  "image_quality": <"good"|"too_blurry"|"too_dark"|"too_bright"|"too_low_resolution">,
  "acne_score": <float 0.0-1.0>,
  "acne_severity": <"none"|"mild"|"moderate"|"severe">,
  "acne_regions": <array of strings from: "jawline","chin","cheeks","forehead","nose","none">,
  "acne_types": <array of strings from: "inflammatory","comedonal","pih","nodular","none">,
  "hirsutism_score": <float 0.0-1.0, visible terminal hair on upper lip/chin/jaw>,
  "skin_confidence": <float 0.0-1.0>,
  "observations": "<1-2 sentence objective observation starting with 'Visible' or 'No visible' or 'Unable to assess'>",
  "guidance": "<1 sentence actionable guidance for the user>"
}

Rules:
- If no face visible: face_detected=false, face_count=0, all scores=0.0
- If multiple faces: face_detected=true, face_count=<N>, all scores=0.0
- acne_score: 0.0=none, 0.3=mild, 0.6=moderate, 0.9=severe
- hirsutism_score: 0.0=no visible hair, 0.5=noticeable, 1.0=prominent
- skin_confidence: reflects image quality and visibility of skin features
- Never use the word "PCOS". Never provide a diagnosis. Describe only visible observations.
- observations must be clinically neutral and objective
        """.trimIndent()

        val contentArray = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64Image")
                })
            })
            put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", "google/gemini-2.5-pro")
            put("max_tokens", 8192)
            put("temperature", 1.0)   // required for thinking-capable models on OpenRouter
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                }
            ))
        }.toString()

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "com.leadmilers.saathi")
            .addHeader("X-Title", "Saathi PCOS Tracker")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        if (!response.isSuccessful) {
            Log.e(TAG, "API ${response.code}: $responseBody")
            throw Exception("API error ${response.code}")
        }
        return responseBody
    }

    private fun parseResponse(responseJson: String): AcneResult {
        return try {
            val message = JSONObject(responseJson)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")

            // Prefer non-thinking content field; fall back to "content"
            val raw = (if (message.has("content") && !message.isNull("content"))
                message.getString("content") else "").trim()

            // Extract the first complete {...} JSON object — tolerant of thinking
            // tokens, markdown fences, or any surrounding prose
            val start = raw.indexOf('{')
            val end   = raw.lastIndexOf('}')
            if (start < 0 || end < 0 || end <= start) {
                Log.e(TAG, "No JSON object found in response: ${raw.take(200)}")
                return AcneResult.InferenceError
            }
            val jsonStr = raw.substring(start, end + 1)
            val j = JSONObject(jsonStr)

            val faceDetected = j.optBoolean("face_detected", false)
            val faceCount = j.optInt("face_count", 0)

            if (!faceDetected) {
                return if (faceCount > 1) AcneResult.MultipleFaces(faceCount)
                else AcneResult.NoFace
            }
            if (faceCount > 1) return AcneResult.MultipleFaces(faceCount)

            val quality = j.optString("image_quality", "good")
            if (quality != "good") {
                return AcneResult.PoorImageQuality(when (quality) {
                    "too_blurry"          -> AcneResult.Quality.TOO_BLURRY
                    "too_dark"            -> AcneResult.Quality.TOO_DARK
                    "too_bright"          -> AcneResult.Quality.TOO_BRIGHT
                    else                  -> AcneResult.Quality.TOO_LOW_RESOLUTION
                })
            }

            val confidence = j.optDouble("skin_confidence", 0.5).toFloat().coerceIn(0f, 1f)
            if (confidence < 0.25f) return AcneResult.LowConfidence

            val acneScore    = j.optDouble("acne_score", 0.0).toFloat().coerceIn(0f, 1f)
            val severity     = j.optString("acne_severity", "none")
            val hirsutism    = j.optDouble("hirsutism_score", 0.0).toFloat().coerceIn(0f, 1f)
            val observations = j.optString("observations", "")
            val guidance     = j.optString("guidance", "")

            val regions = mutableListOf<String>().apply {
                val arr = j.optJSONArray("acne_regions")
                if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
            }.filter { it != "none" }

            AcneResult.ValidAnalysis(
                score              = acneScore,
                hasSignificantAcne = severity in listOf("moderate", "severe"),
                confidence         = confidence,
                skinCoverage       = confidence,
                hirsutismScore     = hirsutism,
                acneRegions        = regions,
                observations       = observations,
                guidance           = guidance
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            AcneResult.InferenceError
        }
    }

    companion object {
        private const val TAG = "CloudFaceAnalyzer"
    }
}
