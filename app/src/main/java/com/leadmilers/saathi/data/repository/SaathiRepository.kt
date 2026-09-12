package com.leadmilers.saathi.data.repository

import com.leadmilers.saathi.data.dao.CycleLogDao
import com.leadmilers.saathi.data.dao.RiskAssessmentDao
import com.leadmilers.saathi.data.dao.SymptomLogDao
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.data.entity.SymptomLog
import kotlinx.coroutines.flow.Flow

class SaathiRepository(
    private val cycleLogDao: CycleLogDao,
    private val symptomLogDao: SymptomLogDao,
    private val riskAssessmentDao: RiskAssessmentDao
) {
    // CycleLog
    val allCycleLogs: Flow<List<CycleLog>> = cycleLogDao.getAll()
    fun recentCycleLogs(n: Int = 30): Flow<List<CycleLog>> = cycleLogDao.getRecent(n)
    suspend fun insertCycleLog(log: CycleLog): Long = cycleLogDao.insert(log)
    suspend fun updateCycleLog(log: CycleLog) = cycleLogDao.update(log)
    suspend fun deleteCycleLog(log: CycleLog) = cycleLogDao.delete(log)

    // SymptomLog
    val allSymptomLogs: Flow<List<SymptomLog>> = symptomLogDao.getAll()
    fun recentSymptomLogs(n: Int = 30): Flow<List<SymptomLog>> = symptomLogDao.getRecent(n)
    suspend fun insertSymptomLog(log: SymptomLog): Long = symptomLogDao.insert(log)
    suspend fun updateSymptomLog(log: SymptomLog) = symptomLogDao.update(log)
    suspend fun deleteSymptomLog(log: SymptomLog) = symptomLogDao.delete(log)
    suspend fun getLatestSymptomLog(): SymptomLog? = symptomLogDao.getLatest()

    // RiskAssessment
    val allRiskAssessments: Flow<List<RiskAssessment>> = riskAssessmentDao.getAll()
    val latestRiskAssessment: Flow<RiskAssessment?> = riskAssessmentDao.getLatest()
    fun recentRiskAssessments(n: Int = 30): Flow<List<RiskAssessment>> = riskAssessmentDao.getRecent(n)
    suspend fun insertRiskAssessment(assessment: RiskAssessment): Long = riskAssessmentDao.insert(assessment)
    suspend fun updateRiskAssessment(assessment: RiskAssessment) = riskAssessmentDao.update(assessment)
    suspend fun deleteRiskAssessment(assessment: RiskAssessment) = riskAssessmentDao.delete(assessment)

    // Nuke all data (for demo reset)
    suspend fun clearAll() {
        cycleLogDao.deleteAll()
        symptomLogDao.deleteAll()
        riskAssessmentDao.deleteAll()
    }

    companion object {
        @Volatile private var INSTANCE: SaathiRepository? = null

        fun getInstance(
            cycleLogDao: CycleLogDao,
            symptomLogDao: SymptomLogDao,
            riskAssessmentDao: RiskAssessmentDao
        ): SaathiRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SaathiRepository(cycleLogDao, symptomLogDao, riskAssessmentDao)
                .also { INSTANCE = it }
        }
    }
}
