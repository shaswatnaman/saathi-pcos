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
        nearby: NearbyManager,
        scope: CoroutineScope,
    ) {
        if (!prefs.isPaired) return
        scope.launch(Dispatchers.IO) {
            val cycles   = repo.recentCycleLogs(1).first()
            val symptoms = repo.recentSymptomLogs(1).first()
            val packet   = buildPacket(prefs, cycles.firstOrNull(), symptoms.firstOrNull())
            nearby.advertiseAndPush(packet)
            prefs.lastSyncAt = System.currentTimeMillis()
        }
    }

    private fun buildPacket(
        prefs: UserPrefs,
        latestCycle: CycleLog?,
        latestSymptom: SymptomLog?,
    ): SyncPacket {
        val now = System.currentTimeMillis()

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
            cycleDay       = if (prefs.shareCycle) cycleDay else null,
            cycleLength    = if (prefs.shareCycle) cycleLen else null,
            phaseName      = if (prefs.shareCycle) phase else null,
            isInPeriod     = if (prefs.sharePeriod) (cycleDay != null && cycleDay <= 6) else null,
            flowIntensity  = if (prefs.sharePeriod) latestCycle?.flowIntensity else null,
            mood           = if (prefs.shareMood) null else null, // mood comes from HealthEntry if shared
            fatigueLevel   = if (prefs.shareEnergy) latestSymptom?.fatigue else null,
            skinDarkening  = if (prefs.shareSymptoms) latestSymptom?.skinDarkening else null,
            hairIssues     = if (prefs.shareSymptoms) latestSymptom?.hairIssues else null,
            weightGain     = if (prefs.shareSymptoms) latestSymptom?.weightGain else null,
        )
    }
}
