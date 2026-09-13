package com.leadmilers.saathi.companion

import android.content.Context
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.data.repository.SaathiRepository
import com.leadmilers.saathi.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object SyncManager {

    fun buildAndPush(
        context: Context,
        repo: SaathiRepository,
        prefs: UserPrefs,
        nearby: LanSyncManager,
        scope: CoroutineScope,
    ) {
        // Always advertise — gynac can connect without requiring explicit pairing
        scope.launch(Dispatchers.IO) {
            val cycles   = repo.recentCycleLogs(30).first()
            val symptoms = repo.recentSymptomLogs(30).first()
            val risks    = repo.recentRiskAssessments(30).first()
            val packet   = buildPacket(prefs, cycles, symptoms, risks)
            nearby.advertiseAndPush(packet)
            prefs.lastSyncAt = System.currentTimeMillis()
        }
    }

    private fun buildPacket(
        prefs: UserPrefs,
        cycles: List<CycleLog>,
        symptoms: List<SymptomLog>,
        risks: List<com.leadmilers.saathi.data.entity.RiskAssessment>,
    ): SyncPacket {
        val now           = System.currentTimeMillis()
        val latestCycle   = cycles.firstOrNull()
        val latestSymptom = symptoms.firstOrNull()

        val cycleDay = latestCycle?.let {
            ((now - it.date) / 86_400_000L + 1L).toInt().coerceAtLeast(1)
        }
        val cycleLen = latestCycle?.cycleLength ?: 28
        val progress = cycleDay?.let { it.toFloat() / cycleLen }
        val phase = progress?.let {
            when {
                it < 0.14f -> "Menstrual phase"
                it < 0.46f -> "Follicular phase"
                it < 0.55f -> "Ovulation window"
                else       -> "Luteal phase"
            }
        }

        return SyncPacket(
            senderDeviceId = prefs.deviceId,
            timestamp      = now,
            cycleDay       = cycleDay,
            cycleLength    = cycleLen,
            phaseName      = phase,
            isInPeriod     = cycleDay != null && cycleDay <= 6,
            flowIntensity  = latestCycle?.flowIntensity,
            fatigueLevel   = latestSymptom?.fatigue,
            skinDarkening  = latestSymptom?.skinDarkening,
            hairIssues     = latestSymptom?.hairIssues,
            weightGain     = latestSymptom?.weightGain,
            cycleHistory   = cycles.map { c ->
                SimpleCycleEntry(c.date, c.cycleLength, c.flowIntensity)
            },
            symptomHistory = symptoms.map { s ->
                SimpleSymptomEntry(
                    date = s.date, fatigue = s.fatigue, acneScore = s.acneScore,
                    skinDarkening = s.skinDarkening, hairIssues = s.hairIssues,
                    weightGain = s.weightGain, weight = s.weight,
                )
            },
            riskHistory = risks.map { r ->
                SimpleRiskEntry(r.date, r.totalScore, r.riskLevel)
            },
        )
    }
}
