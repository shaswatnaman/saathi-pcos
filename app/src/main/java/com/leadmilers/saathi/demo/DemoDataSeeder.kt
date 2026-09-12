package com.leadmilers.saathi.demo

import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.data.repository.SaathiRepository
import com.leadmilers.saathi.ml.RiskScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object DemoDataSeeder {

    // 30 days of data, each entry = one day, newest last
    private val CYCLE_LENGTHS   = intArrayOf(28,28,29,30,31,31,32,33,34,35,36,37,38,38,37,
                                              36,35,34,33,32,31,30,29,28,38,37,36,35,34,28)
    // acne: gradual upward trend 0.30 → 0.80
    private val ACNE_SCORES     = floatArrayOf(.30f,.32f,.33f,.35f,.37f,.38f,.40f,.42f,.43f,.45f,
                                               .47f,.48f,.50f,.52f,.54f,.55f,.57f,.59f,.60f,.62f,
                                               .64f,.65f,.67f,.69f,.71f,.72f,.74f,.76f,.78f,.80f)
    // voice: downward trend 0.90 → 0.50
    private val VOICE_ENERGY    = floatArrayOf(.90f,.89f,.87f,.86f,.84f,.83f,.81f,.80f,.78f,.77f,
                                               .75f,.74f,.72f,.70f,.69f,.67f,.66f,.64f,.62f,.61f,
                                               .59f,.58f,.56f,.55f,.53f,.52f,.50f,.50f,.50f,.50f)
    // fatigue: escalating 2 → 4
    private val FATIGUE_LEVELS  = intArrayOf(2,2,2,2,3,3,3,3,3,3,3,3,3,3,3,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4)
    private val FLOW_INTENSITIES = arrayOf("Light","Light","Medium","Medium","Heavy","Heavy",
                                           "Medium","Medium","Light","Light","Medium","Heavy",
                                           "Heavy","Medium","Light","Medium","Heavy","Medium",
                                           "Light","Light","Medium","Medium","Heavy","Heavy",
                                           "Medium","Heavy","Medium","Light","Medium","Heavy")

    suspend fun seed(repo: SaathiRepository) = withContext(Dispatchers.IO) {
        repo.clearAll()

        val now = System.currentTimeMillis()
        val dayMs = TimeUnit.DAYS.toMillis(1)

        for (i in 0 until 30) {
            val dayOffset = (29 - i).toLong()           // day 0 = 29 days ago, day 29 = today
            val ts = now - dayOffset * dayMs

            val cycleLen = CYCLE_LENGTHS[i]
            val acne     = ACNE_SCORES[i]
            val voice    = VOICE_ENERGY[i]
            val fatigue  = FATIGUE_LEVELS[i]
            val flow     = FLOW_INTENSITIES[i]

            // Escalating physical symptoms appear in the last 2 weeks
            val skinDarkening = i >= 16
            val weightGain    = i >= 20
            val hairIssues    = i >= 24

            val cycleLog = CycleLog(
                date         = ts,
                cycleLength  = cycleLen,
                flowIntensity = flow,
                notes        = if (cycleLen > 35) "Notably late" else ""
            )
            val cycleId = repo.insertCycleLog(cycleLog)

            val symptomLog = SymptomLog(
                date           = ts,
                fatigue        = fatigue,
                acneScore      = acne,
                voiceEnergyScore = voice,
                weight         = 62f + (i * 0.07f),   // slight weight creep
                waistHip       = 0.78f,
                skinDarkening  = skinDarkening,
                weightGain     = weightGain,
                hairIssues     = hairIssues
            )
            repo.insertSymptomLog(symptomLog)

            // Calculate and store risk for this day's snapshot
            val cycle  = cycleLog.copy(id = cycleId)
            val risk   = RiskScorer.calculateRisk(cycle, symptomLog)
            repo.insertRiskAssessment(risk.copy(date = ts))
        }
    }
}
