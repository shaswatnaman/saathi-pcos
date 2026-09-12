package com.leadmilers.saathi.data.dao

import androidx.room.*
import com.leadmilers.saathi.data.entity.SymptomLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SymptomLog): Long

    @Update
    suspend fun update(log: SymptomLog)

    @Delete
    suspend fun delete(log: SymptomLog)

    @Query("SELECT * FROM symptom_log ORDER BY date DESC")
    fun getAll(): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_log WHERE id = :id")
    suspend fun getById(id: Long): SymptomLog?

    @Query("SELECT * FROM symptom_log ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_log WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getInRange(from: Long, to: Long): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_log ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): SymptomLog?

    @Query("DELETE FROM symptom_log")
    suspend fun deleteAll()
}
