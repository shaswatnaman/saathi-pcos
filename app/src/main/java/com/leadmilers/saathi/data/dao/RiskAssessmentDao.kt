package com.leadmilers.saathi.data.dao

import androidx.room.*
import com.leadmilers.saathi.data.entity.RiskAssessment
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskAssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: RiskAssessment): Long

    @Update
    suspend fun update(assessment: RiskAssessment)

    @Delete
    suspend fun delete(assessment: RiskAssessment)

    @Query("SELECT * FROM risk_assessment ORDER BY date DESC")
    fun getAll(): Flow<List<RiskAssessment>>

    @Query("SELECT * FROM risk_assessment WHERE id = :id")
    suspend fun getById(id: Long): RiskAssessment?

    @Query("SELECT * FROM risk_assessment ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<RiskAssessment>>

    @Query("SELECT * FROM risk_assessment ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<RiskAssessment?>

    @Query("SELECT * FROM risk_assessment WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getInRange(from: Long, to: Long): Flow<List<RiskAssessment>>

    @Query("DELETE FROM risk_assessment")
    suspend fun deleteAll()
}
