package com.leadmilers.saathi.data.repository

import com.leadmilers.saathi.data.dao.*
import com.leadmilers.saathi.data.entity.*
import kotlinx.coroutines.flow.Flow

class SaathiRepository(
    private val cycleLogDao: CycleLogDao,
    private val symptomLogDao: SymptomLogDao,
    private val riskAssessmentDao: RiskAssessmentDao,
    private val healthEntryDao: HealthEntryDao,
    private val healthModuleDao: HealthModuleDao
) {
    // ── CycleLog ──────────────────────────────────────────────────────────
    val allCycleLogs: Flow<List<CycleLog>> = cycleLogDao.getAll()
    fun recentCycleLogs(n: Int = 30): Flow<List<CycleLog>> = cycleLogDao.getRecent(n)
    suspend fun insertCycleLog(log: CycleLog): Long = cycleLogDao.insert(log)
    suspend fun updateCycleLog(log: CycleLog) = cycleLogDao.update(log)
    suspend fun deleteCycleLog(log: CycleLog) = cycleLogDao.delete(log)

    // ── SymptomLog ────────────────────────────────────────────────────────
    val allSymptomLogs: Flow<List<SymptomLog>> = symptomLogDao.getAll()
    fun recentSymptomLogs(n: Int = 30): Flow<List<SymptomLog>> = symptomLogDao.getRecent(n)
    suspend fun insertSymptomLog(log: SymptomLog): Long = symptomLogDao.insert(log)
    suspend fun updateSymptomLog(log: SymptomLog) = symptomLogDao.update(log)
    suspend fun deleteSymptomLog(log: SymptomLog) = symptomLogDao.delete(log)
    suspend fun getLatestSymptomLog(): SymptomLog? = symptomLogDao.getLatest()

    // ── RiskAssessment ────────────────────────────────────────────────────
    val allRiskAssessments: Flow<List<RiskAssessment>> = riskAssessmentDao.getAll()
    val latestRiskAssessment: Flow<RiskAssessment?> = riskAssessmentDao.getLatest()
    fun recentRiskAssessments(n: Int = 30): Flow<List<RiskAssessment>> = riskAssessmentDao.getRecent(n)
    suspend fun insertRiskAssessment(assessment: RiskAssessment): Long = riskAssessmentDao.insert(assessment)
    suspend fun updateRiskAssessment(assessment: RiskAssessment) = riskAssessmentDao.update(assessment)
    suspend fun deleteRiskAssessment(assessment: RiskAssessment) = riskAssessmentDao.delete(assessment)

    // ── HealthEntry (generic, module-aware) ───────────────────────────────
    val allHealthEntries: Flow<List<HealthEntry>> = healthEntryDao.getAll()
    fun healthEntriesByModule(moduleId: String): Flow<List<HealthEntry>> =
        healthEntryDao.getByModule(moduleId)
    fun healthEntriesByType(moduleId: String, entryType: String, limit: Int = 30): Flow<List<HealthEntry>> =
        healthEntryDao.getByType(moduleId, entryType, limit)
    suspend fun insertHealthEntry(entry: HealthEntry): Long = healthEntryDao.insert(entry)
    suspend fun insertHealthEntries(entries: List<HealthEntry>) = healthEntryDao.insertAll(entries)
    suspend fun deleteHealthEntry(entry: HealthEntry) = healthEntryDao.delete(entry)
    suspend fun getLatestHealthEntry(moduleId: String, entryType: String): HealthEntry? =
        healthEntryDao.getLatestByType(moduleId, entryType)
    suspend fun getRecentHealthEntries(moduleId: String, limit: Int = 30): List<HealthEntry> =
        healthEntryDao.getRecentByModule(moduleId, limit)
    fun recentHealthEntriesByModule(moduleId: String, limit: Int = 30): Flow<List<HealthEntry>> =
        healthEntryDao.getByModule(moduleId)

    // ── HealthModule ──────────────────────────────────────────────────────
    val allModules: Flow<List<HealthModule>> = healthModuleDao.getAll()
    val activeModules: Flow<List<HealthModule>> = healthModuleDao.getActive()
    suspend fun seedDefaultModules() {
        if (healthModuleDao.count() > 0) return
        healthModuleDao.insertAll(listOf(
            HealthModule("pcos", "PCOS Tracker",
                "Cross-signal PCOS screening: cycle, acne, voice energy, fatigue",
                isActive = true),
            HealthModule("general", "General Symptoms",
                "Daily pain, mood, fatigue and bleeding pattern logging",
                isActive = true),
            HealthModule("endometriosis", "Endometriosis",
                "Pelvic pain tracking with cycle correlation and flare detection",
                isActive = false, isComingSoon = true),
            HealthModule("thyroid", "Thyroid Health",
                "Symptom tracking for hypothyroid and hyperthyroid patterns",
                isActive = false, isComingSoon = true),
            HealthModule("perimenopause", "Perimenopause",
                "Hot flush frequency, sleep disruption and hormonal transition tracking",
                isActive = false, isComingSoon = true)
        ))
    }

    // ── Wipe all data ─────────────────────────────────────────────────────
    suspend fun clearAll() {
        cycleLogDao.deleteAll()
        symptomLogDao.deleteAll()
        riskAssessmentDao.deleteAll()
        healthEntryDao.deleteAll()
    }

    companion object {
        @Volatile private var INSTANCE: SaathiRepository? = null

        fun getInstance(
            cycleLogDao: CycleLogDao,
            symptomLogDao: SymptomLogDao,
            riskAssessmentDao: RiskAssessmentDao,
            healthEntryDao: HealthEntryDao,
            healthModuleDao: HealthModuleDao
        ): SaathiRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SaathiRepository(
                cycleLogDao, symptomLogDao, riskAssessmentDao,
                healthEntryDao, healthModuleDao
            ).also { INSTANCE = it }
        }
    }
}
