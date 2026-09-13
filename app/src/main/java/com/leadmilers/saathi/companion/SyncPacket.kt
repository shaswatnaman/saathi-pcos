package com.leadmilers.saathi.companion

import org.json.JSONArray
import org.json.JSONObject

data class SimpleCycleEntry(val date: Long, val cycleLength: Int, val flowIntensity: String)
data class SimpleSymptomEntry(
    val date: Long,
    val fatigue: Int,
    val acneScore: Float,
    val skinDarkening: Boolean,
    val hairIssues: Boolean,
    val weightGain: Boolean,
    val weight: Float,
)
data class SimpleRiskEntry(val date: Long, val totalScore: Int, val riskLevel: String)

data class SyncPacket(
    val senderDeviceId: String,
    val timestamp: Long,
    // Latest cycle snapshot
    val cycleDay: Int? = null,
    val cycleLength: Int? = null,
    val phaseName: String? = null,
    val isInPeriod: Boolean? = null,
    val flowIntensity: String? = null,
    // Latest symptom snapshot
    val mood: String? = null,
    val fatigueLevel: Int? = null,
    val skinDarkening: Boolean? = null,
    val hairIssues: Boolean? = null,
    val weightGain: Boolean? = null,
    // Messages & insights
    val partnerMessage: String? = null,
    val insightText: String? = null,
    // Full history (last 30 entries each)
    val cycleHistory: List<SimpleCycleEntry> = emptyList(),
    val symptomHistory: List<SimpleSymptomEntry> = emptyList(),
    val riskHistory: List<SimpleRiskEntry> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("senderDeviceId", senderDeviceId)
        put("timestamp", timestamp)
        cycleDay?.let { put("cycleDay", it) }
        cycleLength?.let { put("cycleLength", it) }
        phaseName?.let { put("phaseName", it) }
        isInPeriod?.let { put("isInPeriod", it) }
        flowIntensity?.let { put("flowIntensity", it) }
        mood?.let { put("mood", it) }
        fatigueLevel?.let { put("fatigueLevel", it) }
        skinDarkening?.let { put("skinDarkening", it) }
        hairIssues?.let { put("hairIssues", it) }
        weightGain?.let { put("weightGain", it) }
        partnerMessage?.let { put("partnerMessage", it) }
        insightText?.let { put("insightText", it) }
        put("cycleHistory", JSONArray().also { arr ->
            cycleHistory.forEach { e ->
                arr.put(JSONObject().apply {
                    put("date", e.date); put("cycleLength", e.cycleLength); put("flowIntensity", e.flowIntensity)
                })
            }
        })
        put("symptomHistory", JSONArray().also { arr ->
            symptomHistory.forEach { e ->
                arr.put(JSONObject().apply {
                    put("date", e.date); put("fatigue", e.fatigue); put("acneScore", e.acneScore)
                    put("skinDarkening", e.skinDarkening); put("hairIssues", e.hairIssues)
                    put("weightGain", e.weightGain); put("weight", e.weight)
                })
            }
        })
        put("riskHistory", JSONArray().also { arr ->
            riskHistory.forEach { e ->
                arr.put(JSONObject().apply {
                    put("date", e.date); put("totalScore", e.totalScore); put("riskLevel", e.riskLevel)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): SyncPacket? = runCatching {
            val o = JSONObject(json)
            SyncPacket(
                senderDeviceId = o.getString("senderDeviceId"),
                timestamp      = o.getLong("timestamp"),
                cycleDay       = o.optInt("cycleDay").takeIf { o.has("cycleDay") },
                cycleLength    = o.optInt("cycleLength").takeIf { o.has("cycleLength") },
                phaseName      = o.optString("phaseName").ifEmpty { null },
                isInPeriod     = if (o.has("isInPeriod")) o.getBoolean("isInPeriod") else null,
                flowIntensity  = o.optString("flowIntensity").ifEmpty { null },
                mood           = o.optString("mood").ifEmpty { null },
                fatigueLevel   = o.optInt("fatigueLevel").takeIf { o.has("fatigueLevel") },
                skinDarkening  = if (o.has("skinDarkening")) o.getBoolean("skinDarkening") else null,
                hairIssues     = if (o.has("hairIssues")) o.getBoolean("hairIssues") else null,
                weightGain     = if (o.has("weightGain")) o.getBoolean("weightGain") else null,
                partnerMessage = o.optString("partnerMessage").ifEmpty { null },
                insightText    = o.optString("insightText").ifEmpty { null },
                cycleHistory   = parseCycleHistory(o.optJSONArray("cycleHistory")),
                symptomHistory = parseSymptomHistory(o.optJSONArray("symptomHistory")),
                riskHistory    = parseRiskHistory(o.optJSONArray("riskHistory")),
            )
        }.getOrNull()

        private fun parseCycleHistory(arr: JSONArray?): List<SimpleCycleEntry> {
            arr ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                SimpleCycleEntry(e.getLong("date"), e.getInt("cycleLength"), e.optString("flowIntensity", ""))
            }
        }

        private fun parseSymptomHistory(arr: JSONArray?): List<SimpleSymptomEntry> {
            arr ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                SimpleSymptomEntry(
                    date = e.getLong("date"), fatigue = e.getInt("fatigue"),
                    acneScore = e.optDouble("acneScore", 0.0).toFloat(),
                    skinDarkening = e.optBoolean("skinDarkening"),
                    hairIssues = e.optBoolean("hairIssues"),
                    weightGain = e.optBoolean("weightGain"),
                    weight = e.optDouble("weight", 0.0).toFloat(),
                )
            }
        }

        private fun parseRiskHistory(arr: JSONArray?): List<SimpleRiskEntry> {
            arr ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                SimpleRiskEntry(e.getLong("date"), e.getInt("totalScore"), e.optString("riskLevel", ""))
            }
        }
    }
}
