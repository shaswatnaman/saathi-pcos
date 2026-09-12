package com.leadmilers.saathi.companion

import org.json.JSONObject

/**
 * The data the primary user chooses to share with the companion.
 * Serialised as JSON and transmitted over Nearby Connections.
 * Only fields the primary has enabled in sharing settings are populated.
 */
data class SyncPacket(
    val senderDeviceId: String,
    val timestamp: Long,
    // Cycle
    val cycleDay: Int? = null,
    val cycleLength: Int? = null,
    val phaseName: String? = null,
    // Period
    val isInPeriod: Boolean? = null,
    val flowIntensity: String? = null,
    // Mood
    val mood: String? = null,
    // Energy / fatigue
    val fatigueLevel: Int? = null,
    // Symptoms
    val skinDarkening: Boolean? = null,
    val hairIssues: Boolean? = null,
    val weightGain: Boolean? = null,
    // Partner message (optional)
    val partnerMessage: String? = null,
    // Insights (only if sharing enabled)
    val insightText: String? = null,
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
            )
        }.getOrNull()
    }
}
